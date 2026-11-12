# 踩坑正本

> **一句话**：TBridge 实测踩过的坑，每条按「现象 → 根因 → 怎么避」记账，改代码前先翻这里。

本文件只追加不改写。修缺陷时除 `CHANGELOG.md` 一行外，必须在这里补一条三段式记录；具体排错动作见 [TROUBLESHOOTING.md](TROUBLESHOOTING.md)。

## 1. 冷启动窗口期：ping 一直连不上

- **现象**：目标 App 刚强停重开，前几秒 `ping` 全部连不上，`adb forward` 端口在但读写立刻 EOF。
- **根因**：`TBridgeModule.onPackageReady` 才调用 `BridgeServer.start()`；在此之前进程内没有 `LocalServerSocket`，abstract unix socket `tb.<包名>` 还不存在。
- **怎么避**：先轮询 `ping`（CLI 的 `tbready` / `wait` 就是干这个），拿到带 `ms` 的响应再发 `tree` / `tap`。别把「连不上」当成模块没装。

## 2. 弹窗时有时无：硬等 8 秒还是点空

- **现象**：某个「同意 / 跳过」弹窗在部分启动里出现、部分不出现，脚本固定 sleep 后 `tap` 要么点空要么误触。
- **根因**：弹窗由远端配置 / 首启状态决定，不是必现节点；固定时延既盖不住慢启动，也挡不住弹窗根本不出现。
- **怎么避**：用 `tapif TEXT [SEC]` 条件点击——在 `SEC` 窗口内按文字找节点，找到才点，没找到就跳过。协议层用 `find` + `tap` 组合，别用 `wait` 硬等。

## 3. shot 出来是黑图 / 白板

- **现象**：`shot` 返回的 PNG 里某些区域全黑或全白，但 `tree` 能看到对应节点。
- **根因**：`shot` 走主线程 `View.draw(Canvas)`，只画 View 层；SurfaceView / TextureView / WebView / Flutter / React Native 的内容由别的渲染管线合成，不进这棵 View 树。
- **怎么避**：视频、地图、游戏、Flutter 页面一律退回 `screencap`（实测 raw 273–280 ms）；`shot`（16–64 ms）只用于原生 View 层的快速比对。

## 4. 改了作用域还是不生效

- **现象**：`tools/scope.py` 回读 `modules_state` / `scope` 表已是目标值，但目标 App 进程里 `ping` 仍连不上。
- **根因**：LSPosed 读 SQLite，WAL 模式下 `modules_config.db` 的改动可能还留在 `db-wal` / `db-shm`；已运行的 zygote 也不会重读作用域。
- **怎么避**：写库时把 `db-wal` / `db-shm` 一起处理；`scope.py` 默认 `setprop ctl.restart zygote` 重启，重启后强停目标 App 再打开。`--no-zygote` 只用于调试，别拿它判断生效。

## 5. LSPosed 里勾了模块却什么都没发生

- **现象**：模块列表显示已启用，日志里没有 `TBridge` 加载记录，目标 App 完全没被注入，桌面上也找不到图标。
- **根因**：`module.prop` 声明 `minApiVersion=102` / `targetApiVersion=102`，过旧的 LSPosed 实现会静默不加载；`AndroidManifest.xml` 没有 Activity，本来就不会有桌面图标。
- **怎么避**：先确认 LSPosed 支持现代 Xposed API 102；确认作用域勾的是**目标 App**（`staticScope=false`，不勾不生效）；看模块日志，别找图标。

## 6. 公开仓里残留本机 SDK 路径

- **现象**：早期 `README.md` 把 JAVA_HOME 写死成本机 JDK 路径，`local.properties` 的 `sdk.dir` 指向本机私有 SDK 目录，`tools/tbridge.py` 默认拼邻仓私有目录下的 adb 可执行文件。
- **根因**：本机调试图省事把绝对路径写进文档和脚本默认值，一公开就把机器布局和邻仓结构带了出去。
- **怎么避**：`local.properties` 保持 `.gitignore` 忽略，另给 `local.properties.example` 放 `<REPLACE-ANDROID-SDK-DIR>`；`ADB` / `DEVICE` 一律走环境变量，缺省交给 PATH 里的 `adb`；文档只写相对路径与占位符。发布前跑 `python tools/check-secrets.py`。

## 7. CI 用了 JDK 17 直接编不过

- **现象**：照抄邻仓 CI 的 `java-version: '17'`，`assembleDebug` 在 Kotlin / AGP 阶段报 toolchain 不匹配。
- **根因**：`app/build.gradle.kts` 写死 `JavaVersion.VERSION_21` 与 `jvmToolchain(21)`，JDK 17 起不来 Gradle daemon。
- **怎么避**：`.github/workflows/ci.yml` 的 `setup-java` 固定 `java-version: '21'`；本地构建同样用 JDK 21，构建口径正本见 [BUILD.md](BUILD.md)。

## 8. docs 里出现字面量反斜杠 + 反引号

- **现象**：批量生成的 6 篇 docs 里，所有行内代码和代码块围栏都写成反斜杠加反引号，GitHub 渲染出多余反斜杠；`PROTOCOL.md` 另有一处 `ms` 后跟反斜杠美元、一处末尾多一个反引号。
- **根因**：正文经一层字符串转义后再落盘，反引号被当成需转义字符；`check-docs.py` 只查索引 / 抬头 / 命名 / 断链，不查 Markdown 渲染层。
- **怎么避**：落盘后跑一遍全仓文本体检，判据是「字面量反斜杠加反引号 = 0、反引号成对、围栏闭合、本机绝对路径 = 0、LF 无 BOM」；正则判据写进行内代码即可，别再手工加转义。质检脚本放 `.work/`，不进库。

## 9. Windows 上 `git add` 报 LF will be replaced by CRLF

- **现象**：`git add` 后一批文本文件告警 `LF will be replaced by CRLF the next time Git touches it`；`gradlew` 被 `* text=auto` 覆盖，Windows 检出若开 `core.autocrlf` 会把 shebang 变成 `#!/bin/sh\r`，Linux CI 报 `bad interpreter`。
- **根因**：`.gitattributes` 只有 `* text=auto` 没有全局 `eol=lf`，`gradlew` 与 `.kt`/`.xml`/`.md` 全靠本机 `system.autocrlf=true` 兜底；暂存 blob 本身是 LF，但下次检出会漂移，跨系统行为不确定。
- **怎么避**：`.gitattributes` 首行改 `* text=auto eol=lf` 全局钉死，另给 `gradlew` 显式 `text eol=lf`；`.bat`/`.cmd` 保留 `eol=crlf`；改完用 `git add --renormalize .` 让 index 与工作区一致，再用 `git ls-files --eol` 复核 `i/lf`。门禁正本见 [BUILD.md](BUILD.md)。


