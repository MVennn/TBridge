# 排错手册

> **一句话**：按现象查 TBridge 通道 / 节点 / 截图 / CI，每条给「现象 → 步骤 → 原因 / 解法」。

先确认三件事：LSPosed 支持 API 102、作用域勾了目标 App、目标 App 强停重开过。三件都过了再看下面。Issue 模板默认把本文链接给反馈者。

## 1. ping 连不上

- **现象**：`python tools/tbridge.py <包名> ping` 报连接被拒 / 立刻 EOF。
- **步骤**：① `adb devices` 看设备在线；② 重跑 `adb forward tcp:27110 localabstract:tb.<包名>`；③ 连着轮询 `ping` 十秒；④ 看 LSPosed 模块日志有没有 `TBridgeModule`。
- **原因 / 解法**：多数是冷启动窗口期（`onPackageReady` 之前 socket 不存在），见 [PITFALLS.md](PITFALLS.md) 第 1 条；日志完全没有加载记录则是作用域或 API 版本问题，见第 5 / 6 条。

## 2. tree 返回空树 / 只有 decor

- **现象**：`tree` 只有根 DecorView，没有业务节点。
- **步骤**：① 确认目标 App 已到前台可见页面；② 等 `Activity.onResume` 之后再取；③ 用 `act` 对比当前 Activity。
- **原因 / 解法**：根节点靠 hook `Activity.onResume` 与 `ViewRootImpl.setView` 抓取，页面没 resume 或刚切后台时 `Ui.roots` 还是空的。

## 3. tap 点了没反应

- **现象**：`tap` 返回 ok 且带 `ms`，界面无变化。
- **步骤**：① `find` 先确认节点 id / 文字；② 看节点 `clickable` / `bounds`；③ 换 `settext` 验证是不是同一个节点。
- **原因 / 解法**：`Ui.click` 依次尝试 `dispatchTouchEvent` → `performClick` → 无回退，部分自绘控件三种都不吃；这类节点改用坐标点击或退回 `screencap` + 手势。

## 4. shot 黑图

- **现象**：`shot` 的 PNG 局部全黑或全白。
- **步骤**：① `tree` 确认该区域是不是 SurfaceView / TextureView / WebView；② 改用 `adb shell screencap` 对比。
- **原因 / 解法**：`View.draw` 只画 View 层，见 [PITFALLS.md](PITFALLS.md) 第 3 条。

## 5. 作用域改了还不生效

- **现象**：`scope.py` 回读已是目标值，进程里仍不注入。
- **步骤**：① 确认 `modules_config.db` 的 `db-wal` / `db-shm` 已落盘；② `setprop ctl.restart zygote`；③ 强停目标 App 重开。
- **原因 / 解法**：WAL 未 checkpoint + zygote 缓存旧作用域，见 [PITFALLS.md](PITFALLS.md) 第 4 条。

## 6. CI 门禁红了

- **现象**：`文档与凭据门禁` job 打印 `[FAIL]`。
- **步骤**：① 仓根跑 `python tools/check-docs.py`；② 跑 `python tools/check-secrets.py`；③ 本地都 exit 0 再推。
- **原因 / 解法**：`docs/README.md` 漏登记新文档、缺「一句话」抬头、文件名不是全大写 + 连字符，或提交了带凭据的文本。构建 job 另需 JDK 21 与 `platforms;android-37.0`。

