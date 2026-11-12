package io.github.MVennn.tbridge

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Method

/**
 * TBridge 入口（libxposed API 102）。
 *
 * 与 GSAuxiliary 的区别：本模块**没有固定作用域**，凡是被 LSPosed 勾进 scope 的包
 * 都会走到 [onPackageReady]。所以这里不做包名白名单判断，只做进程判断 ——
 * system_server 里没有 UI 可操作，进去只会白挂钩子。
 *
 * 不持有任何指向宿主的强引用（root View 全部走 WeakReference，见 [Ui]），
 * 因此允许热重载。
 */
class TBridgeModule : XposedModule() {

    private val tag = "TBridge"
    private val handles = ArrayList<XposedInterface.HookHandle>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        if (param.isSystemServer) {
            detach()
            return
        }
        Log.i(tag, "loaded in ${param.processName} pid=${android.os.Process.myPid()}")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        Ui.pkgName = param.packageName
        Ui.version = "1.0.0"
        try {
            install(param.packageName, param.classLoader)
            BridgeServer.start(param.packageName)
        } catch (t: Throwable) {
            // protective 模式下抛异常会让框架停用本模块，这里自己兜住
            Log.e(tag, "install failed for ${param.packageName}", t)
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        // 没有自建线程会碰框架对象、没有 native 钩子，可以安全热重载
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        handles.forEach { runCatching { it.unhook() } }
        handles.clear()
        Ui.clearRoots()
        // socket server 是常驻线程，热重载不重启它 —— 协议没变，重启反而会断客户端连接
    }

    // ------------------------------------------------------------ 装钩子

    private fun install(pkg: String, cl: ClassLoader) {
        hookActivityResume(cl)
        hookViewRoot(cl)
        Log.i(tag, "hooks installed for $pkg (${handles.size})")
    }

    /**
     * Activity.onResume —— 提供两件事：当前 Activity 名（断言用）、以及最底层窗口的根。
     *
     * 用 `declaredMethods` 而不是 `getMethod("onResume")`：某些定制 ROM 会加
     * 带参数的重载，按名字全挂才不会漏。
     */
    private fun hookActivityResume(cl: ClassLoader) {
        val actCls = runCatching { Class.forName("android.app.Activity", false, cl) }.getOrNull() ?: return
        for (m in actCls.declaredMethods.filter { it.name == "onResume" }) {
            if (m.parameterCount != 0) continue
            m.isAccessible = true
            hookSafely(m) { chain ->
                val r = chain.proceed()
                (chain.thisObject as? android.app.Activity)?.let { a ->
                    Ui.currentAct = a.javaClass.name
                    runCatching { a.window?.decorView?.let { Ui.addRoot(it) } }
                }
                r
            }
        }
    }

    /**
     * ViewRootImpl.setView —— 抓**每一个窗口**的根 View。
     *
     * 必须挂这一处：Activity 的 decorView 只是最底层窗口，Dialog / PopupWindow /
     * 输入法候选条各有独立的 ViewRootImpl，它们的根不在 decorView 树里。
     * 只从 Activity 取的话，弹窗里的按钮永远找不到。
     */
    private fun hookViewRoot(cl: ClassLoader) {
        val vrCls = runCatching { Class.forName("android.view.ViewRootImpl", false, cl) }.getOrNull() ?: return
        for (m in vrCls.declaredMethods.filter { it.name == "setView" }) {
            m.isAccessible = true
            hookSafely(m) { chain ->
                val r = chain.proceed()
                (chain.args.getOrNull(0) as? android.view.View)?.let { Ui.addRoot(it) }
                r
            }
        }
    }

    /**
     * 单个方法挂失败不能影响其它钩子 —— 不同 Android 版本 / 定制 ROM 的方法签名
     * 差异是常态，一个失败就整体放弃会让模块在某些机型上完全不可用。
     */
    private inline fun hookSafely(m: Method, crossinline body: (XposedInterface.Chain) -> Any?) {
        try {
            val h = hook(m).setId("tbridge:" + m.declaringClass.simpleName + "." + m.name)
                .intercept { chain -> body(chain) }
            handles.add(h)
        } catch (t: Throwable) {
            Log.w(tag, "hook ${m.declaringClass.name}#${m.name}${m.parameterCount} failed: ${t.message}")
        }
    }
}
