# AGENTS.md —— TBridge 仓级规则

> **一句话**：本文件是 TBridge 的规则入口，只写与全局、组级不同的部分；冲突按 §1 顺序取更靠前者。

## 1. 生效顺序

1. 用户当轮明确指令
2. 本文件（仓级）
3. 组级 `<WORKSPACE>/_merged/LSPosed/AGENTS.md`（junction 合并视图，只读）
4. 全局规则（`$DSH_HOME/AGENTS.md`）

例外：§5 凭据、§3 路径纪律是硬约束，第 2、3 条不得覆盖。

## 2. 语言与交付

- 简体中文；结论先行，先给能跑的东西，再解释。
- 每个结论配命令 + 真实输出；失败贴原始报错原文，不美化、不概括成「报错了」。
- 交付两段：**改了什么 / 怎么验证**。
- 信息缺口标 `[待补: 缺什么]`，继续做能做的部分，不空转、不重复追问同一件事。

## 3. 路径纪律

- 文档、脚本、配置里只写**仓库内相对路径**；不写本机绝对路径（换机器即断）。
- 命令示例从仓根起算：`python tools/check-docs.py`、`docs/ARCHITECTURE.md`。
- 本仓只写本仓。邻仓与组级视图 `../_merged/LSPosed/` 只读，需要跨仓改动先回到用户。

## 4. 变更纪律

- 改前先读完整文件；不做「顺手重构」，不整文件覆盖式重写。
- 一次改动只解决一个主题；无关文件的格式化、重排、改名一律另起一轮。
- 不主动执行 `git init` / `add` / `commit` / `push`；用户说「提交」= commit + push，不发版。
- 文档改动必须同步 `docs/README.md` 索引，索引与正文同一轮改完。

## 5. 凭据

- key / 口令 / token / 签名口令只从环境变量读，不进源码、文档、日志、缓存、提交信息、聊天汇报。
- 公开内容一律脱敏：`<REDACTED-…>`（凭据）、`<REPLACE-…>`（待填值）。
- `local.properties`、`keystore.properties`、`*.keystore`、`*.jks` 只留本机，已在 `.gitignore` 排除；`local.properties.example` 是唯一可提交的模板。

## 6. 文档门禁

- 仓根执行 `python tools/check-docs.py`，退出码 0 才算文档合格。
- 它查四件事：`docs/*.md` 是否全部被 `docs/README.md` 链接、每篇是否有 `> **一句话**` 块、索引里的相对链接是否可解析、文件名是否全大写 + 连字符。
- 门禁红着不收尾；修不了的部分在回报里贴原始输出并标 `[待补]`。

## 7. 检索顺序

1. 仓根有 `.codegraph/` → 先 `codegraph_explore`
2. 否则 `grep` / `glob` / 读文件（本仓已有实现优先复用）
3. 都查不到再问人，并说明已经查过什么

## 8. 调试产物

- 临时文件、抓包、客户端 `shot` 输出、LSPosed 日志一律进仓内 `.work/` 或 `%TEMP%`；
  工作区根目录零新建，仓根不落散件。
- `.work/` 不进库，不写进文档当路径示例。

## 9. 当前阶段

- 源码四个文件，各管一段：`TBridgeModule.kt`（XposedModule 入口，hook `Activity.onResume` 与 `ViewRootImpl.setView` 抓 root View，`system_server` 里 detach，`onHotReloaded` 只 unhook + `Ui.clearRoots()`、不重启 socket）；`BridgeServer.kt`（`android.net.LocalServerSocket`，`SOCKET_PREFIX="tb."`，手写 JSON 收发）；`Ui.kt`（主线程同步 `onMain(timeoutMs=3000)`、三路点击 `dispatchTouchEvent`→`performClick`→none、`View.draw` 出 PNG）；`Json.kt`（手写 JSON，不引第三方）。入口 `java_init.list` = `io.github.MVennn.tbridge.TBridgeModule`。架构见 `docs/ARCHITECTURE.md`。
- 通道：`adb forward tcp:27110 localabstract:tb.<包名>`，一行 JSON 请求 / 一行 JSON 响应，响应统一带 `ms`；`op = ping/act/tree/find/tap/settext/shot`。协议见 `docs/PROTOCOL.md`。
- 三个必须记住的坑（详见 `docs/PITFALLS.md`）：① 冷启动窗口期，`onPackageReady` 前 socket 不存在，先轮询 `ping` 再操作；② 弹窗时有时无，用 `tapif TEXT [SEC]`，别用 `wait` 硬等 8 秒；③ `View.draw` 画不出 SurfaceView / TextureView / WebView / Flutter / RN，这类退回 `screencap`。
- 另两个：作用域改 `modules_config.db` 后必须 `setprop ctl.restart zygote`，且 WAL 要连 `db-wal` / `db-shm` 一起拉；热重载只清 View 引用、不重启 socket。

## 10. 构建

- JDK 21（`JavaVersion.VERSION_21` / `jvmToolchain(21)`）、Gradle 8.11.1、AGP 8.10.1、Kotlin android 2.4.0、`compileSdk 37` / `minSdk 27` / `targetSdk 34`；`namespace` 与 `applicationId` 均为 `io.github.MVennn.tbridge`。
- 仓根执行 `./gradlew :app:assembleDebug`；产物 `app/build/outputs/apk/debug/app-debug.apk`。
- SDK 解析顺序：环境 `ANDROID_SDK_ROOT` / `ANDROID_HOME`（须存在）→ `local.properties` 的 `sdk.dir`；构建配置里不写死任何绝对路径。
- `libxposed` 的 `api` / `annotation` 都是 `compileOnly`，不进 APK；没有其它运行期依赖。

## 11. 门面与文档口径（系列仓基准 = WxPicLabe）

- README 是**七段式**：`<div align="center">` logo + 标题 + 一行粗体定位 + 标语 + 徽章 `</div>` → 这是什么 / 环境要求 / 安装 / 反馈 / 免责声明 / 开源鸣谢 / 许可证。**禁止「一句话」引言块**，也不链 `docs/README.md` 索引；README 只做概述，机制 / 协议 / 构建 / 排错细节链接到 docs 正本。
- 根目录**文档**只留 `README / CHANGELOG / CONTRIBUTING / SECURITY / LICENSE / NOTICE / AGENTS / PROGRESS`；构建骨架（`build.gradle.kts` / `settings.gradle.kts` / `gradlew` / `app/` / `assets/` / `gradle/` / `tools/`）按实际结构保留；开发者文档下沉到 `docs/`。
- `docs/` 命名全大写 + 连字符，单篇 ≤800 行，唯一索引在 `docs/README.md`（孤儿零容忍）。
- `CONTRIBUTING.md` 必须有「一句话」引言块；Issue 模板在 `.github/ISSUE_TEMPLATE/`（bug / feature / config 三件）。
- 门面改动与缺陷修复要**双记账**：`CHANGELOG.md` 一行；修缺陷再在 `docs/PITFALLS.md` 补「现象 → 根因 → 怎么避」。
- 发版才动 `versionName` / `versionCode`，且**两处同步**：`app/build.gradle.kts` 的 `defaultConfig` 与 `Ui.kt` 的 `Ui.version`；不发布时一律保持 1 / 1.0.0，不打 tag、不发 Release。
