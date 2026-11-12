# 架构与实现

> **一句话**：模块怎么分层、socket 何时起、View 怎么抓，以及已知限制，给改模块的人看。

TBridge 是 LSPosed 模块：在被测 App 进程内开一个 abstract unix socket `tb.<包名>`，把「截图 + 猜坐标」换成「直接问 View 对象」。模块本身没有 Activity、不申请任何 Android 权限、没有桌面图标，只在 LSPosed 模块列表里出现。

## 1. 源码结构

```
app/src/main/java/io/github/MVennn/tbridge/
├── TBridgeModule.kt     XposedModule 入口：生命周期 + 装钩子
├── BridgeServer.kt      LocalServerSocket：accept / 分发 / 统一补 ms
├── Ui.kt                主线程同步、窗口根、扫描、点击、View.draw 出 PNG
└── Json.kt              手写 JSON：拼输出 + 解析一行请求（无 Gson）

app/src/main/resources/META-INF/xposed/
├── java_init.list       入口类名：io.github.MVennn.tbridge.TBridgeModule
└── module.prop          minApiVersion/targetApiVersion=102、staticScope=false、
                         exceptionMode=protective、autoHotReload=true
```

四个 `.kt` 就是全部运行期代码；依赖只有 `compileOnly` 的 libxposed api / annotation 与 Kotlin 标准库，不引网络库、UI 库、JSON 库。

## 2. 文件职责

| 文件 | 职责 | 不负责 |
| --- | --- | --- |
| `TBridgeModule.kt` | `onModuleLoaded` 判断 system_server、`onPackageReady` 设 `Ui.pkgName` / `Ui.version` 并启服务、挂 `Activity.onResume` 与 `ViewRootImpl.setView`、`onHotReloaded` 卸钩子 | 不写协议字段、不遍历 View |
| `BridgeServer.kt` | `LocalServerSocket("tb." + pkg)`、每连接一线程、一行 JSON 进 / 一行 JSON 出、按 `op` 分发、统一补 `ms` | 不碰 View 对象、不建 forward |
| `Ui.kt` | `onMain(timeoutMs = 3000)`、`roots` 弱引用表、`topRoot()`、`scan()` / `find()` / `click()` / `setText()` / `drawPng()` | 不解析 JSON、不监听 socket |
| `Json.kt` | `objOf()` 输出与 `parse()` 解析 object / array / string / number / bool / null | 不做反射序列化、不引第三方 |

## 3. 通道与生命周期

通道固定为 `adb forward tcp:27110 localabstract:tb.<包名>`。选 abstract unix socket 而不是 TCP：设备上 bind 端口要额外权限、还可能和 App 自身端口冲突，abstract 名字在 AF_UNIX 里唯一且 `adb forward` 原生支持。PC 侧只有这条 forward，设备上没有 TCP 监听。

| 阶段 | 动作 |
| --- | --- |
| `onModuleLoaded` | `param.isSystemServer` 为真就 `detach()` —— system_server 没有 UI 可操作，进去只白挂钩子 |
| `onPackageReady` | 设 `Ui.pkgName = packageName`、`Ui.version = "1.0.0"`；`install()` 挂钩子；`BridgeServer.start(pkg)` 起 `tbridge-accept` 守护线程并 bind `@tb.<包名>` |
| 运行期 | 每个连接起一条 `tbridge-conn` 线程；同一连接可连续发多行请求；`handle()` 统一补 `ms` |
| `onHotReloading` | 返回 `true`：没有自建线程碰框架对象、没有 native 钩子，可安全热重载 |
| `onHotReloaded` | `handles.forEach { unhook() }` + `Ui.clearRoots()`；**不重启 socket**，协议没变，重启反而断客户端连接与 forward |
| 进程退出 | socket 随进程回收，PC 侧需重新建 forward / 重连 |

`install()` 里 `hookSafely` 逐个方法兜 `try/catch`：不同 Android 版本与定制 ROM 的方法签名有差异，一个方法挂失败只打 warn，不影响其它钩子。`Activity.onResume` 用 `declaredMethods` 全挂无参重载，`ViewRootImpl.setView` 取第一个参数 `View` 存进 `Ui.roots`；必须挂后者，因为 Dialog / PopupWindow / 输入法候选条各有独立 ViewRootImpl，它们的根不在 decorView 树里。

## 4. 分层判据

改动该落到哪一层，按下面三条判；跨层就是放错位置：

| 层 | 判据（满足任一条就归这层） | 落点 |
| --- | --- | --- |
| 注入与生命周期 | 关心「什么时候起 / 挂什么 / 什么时候卸」、进程判断、热重载 | `TBridgeModule.kt` |
| 传输 | 关心「一行进一出行出」、`op` 分发、错误包装、耗时统计 | `BridgeServer.kt` + `Json.kt` |
| UI 访问 | 关心「View 对象、主线程、窗口、坐标、像素」 | `Ui.kt` |
| 客户端 | 关心「adb forward、CLI 子命令、multi 编排」 | `tools/tbridge.py`，正本见 [CLI.md](CLI.md) |
| 声明与构建 | 关心「入口类名、Xposed API 版本、SDK / 版本号」 | `module.prop` / `java_init.list` / `app/build.gradle.kts`，见 [BUILD.md](BUILD.md) |

协议字段只在 [PROTOCOL.md](PROTOCOL.md) 定义，其它文档只留一句话概述 + 链接。

## 5. 已知限制

- **View.draw 边界**：`shot` 走 `Ui.drawPng()`（`View.draw(Canvas)` → Bitmap → PNG）。SurfaceView / TextureView 由独立 Surface 合成，WebView 内部 DOM、Flutter / RN 自绘内容都不在 View 树里，这些区域画不出内容，需退回 `screencap`。详见 [PITFALLS.md](PITFALLS.md)。
- **single root 选取**：`Ui.topRoot()` 在 `roots` 里只挑 `isShown && width > 0 && height > 0` 且 `setView` 时间戳最新的**一个**根来扫。新窗口盖旧窗口时这正好是眼前那层，但多窗口同时可见时，其它窗口的节点不会出现在 `tree` 里；`act` 的 `roots` 只给总数，不枚举。
- **同步主线程 3000 ms 超时**：`Ui.onMain(timeoutMs = 3000)` 把每个 op 的读 / 写 / 绘制都 `post` 到主线程，用 `CountDownLatch` 同步等结果。被测 App 主线程自己卡住时所有 op 一起超时，抛 `IllegalStateException`，不会静默返回半初始化状态。
- **点击三路回退**：`dispatchTouchEvent` 送视图本地坐标的 DOWN + UP → `performClick()` → `none`。`none` 时 `tap` 返回 `ok=false`，不自动改用系统 `input tap`。
- **不固定作用域**：`staticScope=false`，模块不对包名做白名单，谁被勾进 LSPosed 作用域就在谁进程里起桥；没勾的进程没有桥。

排错按 [TROUBLESHOOTING.md](TROUBLESHOOTING.md) 走，调试通道边界见 [DISCLAIMER.md](DISCLAIMER.md)。
