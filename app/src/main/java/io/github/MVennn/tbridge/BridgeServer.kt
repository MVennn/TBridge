package io.github.MVennn.tbridge

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Base64
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 进程内 socket server —— 协议是「一行 JSON 请求，一行 JSON 响应」。
 *
 * 传输用 **abstract unix socket**（Android 的 [LocalServerSocket] 就是 abstract namespace），
 * 名字固定为 `tb.<包名>`，PC 侧一条 `adb forward tcp:N localabstract:tb.<包名>` 直连。
 *
 * 为什么不用 TCP：设备上 bind 任意端口要额外权限、还要处理与 App 自身端口的冲突；
 * abstract socket 在 AF_UNIX 里按名字唯一，天生无冲突，adb forward 原生支持。
 * LSPosed 自己的 `lspd` 用的就是这条路（/proc/net/unix 里的 `@lspbridge-*`）。
 */
object BridgeServer {

    private const val TAG = "TBridge"
    private const val SOCKET_PREFIX = "tb."

    @Volatile private var server: LocalServerSocket? = null
    @Volatile private var started = false

    fun start(pkg: String) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            Thread({ acceptLoop(pkg) }, "tbridge-accept").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun acceptLoop(pkg: String) {
        val name = SOCKET_PREFIX + pkg
        try {
            server = LocalServerSocket(name)
            Log.i(TAG, "listening on @$name pid=${android.os.Process.myPid()}")
        } catch (t: Throwable) {
            Log.e(TAG, "bind @$name failed", t)
            started = false
            return
        }
        while (true) {
            val sock = try {
                server?.accept() ?: break
            } catch (t: Throwable) {
                Log.w(TAG, "accept failed, retry in 500ms", t)
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
                continue
            }
            Thread({ serve(sock) }, "tbridge-conn").apply { isDaemon = true; start() }
        }
    }

    private fun serve(sock: LocalSocket) {
        try {
            val r = BufferedReader(InputStreamReader(sock.inputStream, Charsets.UTF_8))
            val w = OutputStreamWriter(sock.outputStream, Charsets.UTF_8)
            while (true) {
                val line = r.readLine() ?: break
                if (line.isBlank()) continue
                val resp = handle(line)
                w.write(resp); w.write("\n"); w.flush()
            }
        } catch (_: Throwable) {
            // 客户端断开是常态
        } finally {
            runCatching { sock.close() }
        }
    }

    // ------------------------------------------------------------ 分发

    /** 所有 handler 只返回 Map，耗时字段由这里统一补，避免各 handler 各拼一遍 JSON */
    private fun handle(line: String): String {
        val t0 = System.nanoTime()
        val body: Map<String, Any?> = try {
            val req = Json.parse(line)
            val op = req["op"]?.toString()
            if (op == null) mapOf("ok" to false, "err" to "missing op")
            else when (op) {
                "ping" -> ping()
                "act" -> Ui.onMain { Ui.actInfo() + mapOf("ok" to true) }
                "tree" -> tree()
                "find" -> find(req)
                "tap" -> tap(req)
                "settext" -> settext(req)
                "shot" -> shot()
                else -> mapOf("ok" to false, "err" to "unknown op: $op")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "op failed", t)
            mapOf("ok" to false, "err" to (t.javaClass.simpleName + ": " + (t.message ?: "")))
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        return Json.objOf(body + ("ms" to ms))
    }

    private fun ping(): Map<String, Any?> = mapOf(
        "ok" to true,
        "pkg" to Ui.pkgName,
        "ver" to Ui.version,
        "pid" to android.os.Process.myPid(),
        "api" to android.os.Build.VERSION.SDK_INT,
    )

    private fun tree(): Map<String, Any?> = Ui.onMain {
        val nodes = Ui.scan()
        mapOf<String, Any?>(
            "ok" to true,
            "count" to nodes.size,
            "nodes" to nodes.map { Ui.describe(it) },
        )
    }

    private fun find(req: Map<String, Any?>): Map<String, Any?> {
        val text = req["text"]?.toString() ?: ""
        val exact = req["exact"] as? Boolean ?: false
        val cls = req["cls"]?.toString()
        return Ui.onMain {
            val all = Ui.scan()
            val hits = Ui.find(all, text, exact, cls)
            mapOf<String, Any?>(
                "ok" to true,
                "count" to hits.size,
                "total" to all.size,
                "hits" to hits.take(20).map { Ui.describe(it) },
            )
        }
    }

    private fun tap(req: Map<String, Any?>): Map<String, Any?> = Ui.onMain {
        val nodes = Ui.scan()
        val target = pick(nodes, req)
        if (target == null) {
            mapOf<String, Any?>("ok" to false, "err" to "no such node", "total" to nodes.size)
        } else {
            val via = Ui.click(target.view)
            if (via == "none") mapOf<String, Any?>("ok" to false, "err" to "dispatch and click both refused")
            else mapOf<String, Any?>("ok" to true, "via" to via, "node" to Ui.describe(target))
        }
    }

    private fun settext(req: Map<String, Any?>): Map<String, Any?> = Ui.onMain {
        val s = req["text"]?.toString() ?: ""
        val target = pick(Ui.scan(), req)
        mapOf<String, Any?>("ok" to (target != null && Ui.setText(target.view, s)))
    }

    /** 统一的选点逻辑：idx 优先，其次 text/rid/cls */
    private fun pick(nodes: List<Ui.Node>, req: Map<String, Any?>): Ui.Node? {
        req["idx"]?.let { raw ->
            val i = (raw as? Number)?.toInt() ?: -1
            return nodes.firstOrNull { it.idx == i }
        }
        req["rid"]?.let { raw ->
            val rid = raw.toString()
            return nodes.firstOrNull { Ui.describe(it)["rid"] == rid }
        }
        req["text"]?.let { raw ->
            val t = raw.toString()
            val exact = req["exact"] as? Boolean ?: false
            return Ui.find(nodes, t, exact, req["cls"]?.toString()).firstOrNull()
        }
        return null
    }

    private fun shot(): Map<String, Any?> = Ui.onMain {
        val root = Ui.activityRoot()
            ?: return@onMain mapOf<String, Any?>("ok" to false, "err" to "no root view")
        if (root.width <= 0 || root.height <= 0) {
            return@onMain mapOf<String, Any?>("ok" to false, "err" to "root has no size")
        }
        val png = Ui.drawPng(root)
        mapOf<String, Any?>(
            "ok" to true,
            "w" to root.width,
            "h" to root.height,
            "bytes" to png.size,
            "b64" to Base64.encodeToString(png, Base64.NO_WRAP),
        )
    }
}
