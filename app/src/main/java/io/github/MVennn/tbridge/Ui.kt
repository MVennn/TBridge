package io.github.MVennn.tbridge

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 进程内 UI 访问层 —— 本模块的全部"提速"都来自这里。
 *
 * 与跨进程方案（uiautomator dump 2.0s / screencap 0.28s）的区别：这里拿的是
 * **已经在内存里的 View 对象**，不序列化到 XML、不过 Binder、不解码 PNG。
 */
object Ui {

    /** 当前进程的包名，用于拼 abstract socket 名 */
    @Volatile var pkgName: String = ""

    /** 模块版本，握手时回给客户端 */
    @Volatile var version: String = ""

    @Volatile var currentAct: String = ""

    /**
     * 所有已知的窗口根 View。
     *
     * 为什么不止一个：Activity 的 decorView 只是最底层窗口，Dialog / PopupWindow /
     * 输入法候选条各自有独立的 ViewRootImpl，它们的根**不在** decorView 树里。
     * 只从 Activity 取，弹窗里的按钮永远找不到 —— 金太阳一启动就连弹两个对话框，
     * 这正是最先会踩的坑。
     *
     * key = identityHashCode，value = 弱引用 + 最后一次 setView 的时间戳。
     */
    private val roots = ConcurrentHashMap<Int, Root>()

    private class Root(val ref: WeakReference<View>, @Volatile var ts: Long)

    fun addRoot(v: View) {
        roots[System.identityHashCode(v)] = Root(WeakReference(v), SystemClock.uptimeMillis())
        // 顺手清掉已经被回收的条目，避免长跑进程里无限增长
        if (roots.size > 32) {
            roots.entries.removeAll { it.value.ref.get() == null }
        }
    }

    fun clearRoots() = roots.clear()

    /**
     * 取"当前最上层的窗口根"。
     *
     * 判据是最后一次 setView 的时间 + isShown —— 新窗口盖在旧窗口上，
     * setView 一定更晚，所以最新的那个就是用户眼前看到的那个。
     */
    private fun topRoot(): View? {
        val alive = roots.values
            .mapNotNull { r -> r.ref.get()?.let { it to r.ts } }
            .filter { (v, _) -> v.isShown && v.width > 0 && v.height > 0 }
        return alive.maxByOrNull { it.second }?.first
    }

    fun activityRoot(): View? = topRoot()

    // ------------------------------------------------------------ 主线程

    /**
     * 在目标进程主线程执行并同步等结果。
     *
     * View 的一切读写（包括 getLocationOnScreen、draw）都必须在主线程，
     * 否则会抛 CalledFromWrongThreadException 或读到撕裂的状态。
     * socket 线程负责解析请求，真正的活都甩到这里。
     */
    fun <T> onMain(timeoutMs: Long = 3000, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val out = AtomicReference<T>()
        val err = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                out.set(block())
            } catch (t: Throwable) {
                err.set(t)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw IllegalStateException("main thread blocked > \${timeoutMs}ms")
        }
        err.get()?.let { throw it }
        return out.get() as T
    }

    // ------------------------------------------------------------ 扫描

    class Node(val idx: Int, val view: View, val depth: Int)

    /**
     * 遍历整棵视图树。
     *
     * 顺序是深度优先、与 `uiautomator` 的 XML 输出一致，所以 idx 可以直接和
     * 旧解析器的下标对齐；节点字段也刻意做成同名，便于复用 `uidump.py`。
     */
    fun scan(): List<Node> {
        val root = topRoot() ?: return emptyList()
        val out = ArrayList<Node>(256)
        var counter = 0
        fun walk(v: View, depth: Int) {
            if (!v.isShown) return
            out.add(Node(counter++, v, depth))
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    fun describe(n: Node): Map<String, Any?> {
        val v = n.view
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return linkedMapOf(
            "idx" to n.idx,
            "cls" to v.javaClass.name,
            "text" to textOf(v),
            "rid" to ridOf(v),
            "desc" to (v.contentDescription?.toString() ?: ""),
            "bounds" to listOf(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height),
            "clickable" to v.isClickable,
            "enabled" to v.isEnabled,
            "visible" to v.isShown,
            "checked" to (v as? android.widget.Checkable)?.isChecked,
            "depth" to n.depth,
        )
    }

    private fun textOf(v: View): String = when (v) {
        is EditText -> if (v.text.isNullOrEmpty()) (v.hint?.toString() ?: "") else v.text.toString()
        is TextView -> v.text?.toString() ?: ""
        else -> ""
    }

    private fun ridOf(v: View): String {
        if (v.id == View.NO_ID) return ""
        return try {
            val res = v.resources
            val e = res.getResourceEntryName(v.id)
            // 系统 id（如 android:id/content）没有包名，统一加上前缀以示区分
            if (v.id ushr 24 == 1) "android:id/$e" else "${v.context.packageName}:id/$e"
        } catch (_: Throwable) {
            ""
        }
    }

    // ------------------------------------------------------------ 查找

    fun find(nodes: List<Node>, text: String, exact: Boolean, cls: String?): List<Node> {
        if (text.isEmpty() && cls == null) return emptyList()
        return nodes.filter { n ->
            val v = n.view
            val hitText = text.isEmpty() || run {
                val t = textOf(v)
                val d = v.contentDescription?.toString() ?: ""
                if (exact) t == text || d == text else t.contains(text) || d.contains(text)
            }
            val hitCls = cls == null || v.javaClass.name.contains(cls)
            hitText && hitCls
        }
    }

    // ------------------------------------------------------------ 动作

    /**
     * 点击。优先走 `dispatchTouchEvent`（能点没有 OnClickListener 的自绘 View），
     * 失败回退 `performClick`。
     *
     * 坐标传的是**视图本地坐标**（宽高的一半），不是屏幕坐标 —— 这是本方案与
     * `input tap x y` 的本质区别：换分辨率不用重取坐标。
     */
    fun click(v: View): String {
        val cx = v.width / 2f
        val cy = v.height / 2f
        if (v.width > 0 && v.height > 0) {
            val ok = runCatching {
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cx, cy, 0)
                val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, cx, cy, 0)
                try {
                    val a = v.dispatchTouchEvent(down)
                    val b = v.dispatchTouchEvent(up)
                    a || b
                } finally {
                    down.recycle(); up.recycle()
                }
            }.getOrDefault(false)
            if (ok) return "dispatch"
        }
        return if (runCatching { v.performClick() }.getOrDefault(false)) "click" else "none"
    }

    fun setText(v: View, s: String): Boolean {
        val e = v as? EditText ?: return false
        e.setText(s)
        e.setSelection(s.length)
        return true
    }

    /** 把根 View 画到 Bitmap 再压成 PNG —— 不经过 SurfaceFlinger，不受弹窗遮挡 */
    fun drawPng(v: View): ByteArray {
        val bmp = android.graphics.Bitmap.createBitmap(
            maxOf(v.width, 1), maxOf(v.height, 1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        v.draw(android.graphics.Canvas(bmp))
        val baos = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        return baos.toByteArray()
    }

    fun actInfo(): Map<String, Any?> = linkedMapOf(
        "act" to currentAct,
        "roots" to roots.size,
    )

    @Suppress("unused")
    private fun unusedActivityRef(a: Activity?) = a
}
