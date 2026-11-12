<div align="center">

<img src="https://raw.githubusercontent.com/MVennn/TBridge/main/assets/logo.png" width="128" alt="TBridge">

# TBridge

**Android · 进程内 UI 测试桥（LSPosed 模块）**

别再截图猜坐标了，直接问 View 对象。

![版本](https://img.shields.io/github/v/release/MVennn/TBridge) ![CI](https://github.com/MVennn/TBridge/actions/workflows/ci.yml/badge.svg) ![源码仓库](https://img.shields.io/badge/源码仓库-GitHub-181717?logo=github&logoColor=white) ![问题反馈](https://img.shields.io/badge/问题反馈-Issues-181717?logo=github&logoColor=white) ![Android](https://img.shields.io/badge/Android-8.1%2B-3DDC84?logo=android&logoColor=white) ![LSPosed](https://img.shields.io/badge/LSPosed-API%20102-5C6BC0) ![License](https://img.shields.io/badge/License-Apache--2.0-blue)

</div>

---

## 这是什么

TBridge 是一个 LSPosed 模块。它在**被测 App 进程内**开一个 abstract unix socket，
把「截图 + 猜坐标」换成「直接问 View 对象」——每轮取控件树从 **2.2 秒压到约 1.4ms**。
通道是 `adb forward tcp:27110 localabstract:tb.<包名>`，一行 JSON 请求、一行 JSON 响应。
模块没有 Activity、不申请任何权限、没有桌面图标，只在 LSPosed 模块列表里出现。

| 操作 | 实测耗时 | 对照 |
| --- | --- | --- |
| `tree` 取控件树 | 1.0–1.8 ms（连跑 200 次平均 1.40 ms） | `uiautomator dump` 2000–2200 ms |
| `find` 找节点 | 约 0.9 ms | — |
| `tap` 点击 | 1.0–2.1 ms | — |
| `shot` 截图 | 16–64 ms | `screencap` raw 273–280 ms |

高频轮询才让自动化真正跑得动：金太阳冷启动到可操作，旧链路 15791 ms，走桥 221 ms。

上手前记住三点：

- **冷启动有窗口期**：`onPackageReady` 之前 socket 还不存在，先轮询 `ping` 再发操作。
- **弹窗时有时无**：用 `tapif TEXT [SEC]` 条件点击，别用 `wait` 硬等 8 秒。
- **`shot` 画的是 View 层**：SurfaceView / TextureView / WebView / Flutter / RN 画不出内容，
  这类场景退回 `screencap`。

协议字段与 CLI 子命令的完整说明见 [docs/PROTOCOL.md](https://github.com/MVennn/TBridge/blob/main/docs/PROTOCOL.md)
与 [docs/CLI.md](https://github.com/MVennn/TBridge/blob/main/docs/CLI.md)。

## 环境要求

- 安卓 8.1+（`minSdk 27`）
- 支持现代 Xposed API 102 的较新 LSPosed；过旧实现会静默不加载
- PC 端 Python 3 + `adb`
- JDK 21（**仅构建 APK 需要**，用桥调试不用）

## 安装

1. 取得 TBridge 的 APK 并安装到设备（构建方式见 [docs/BUILD.md](https://github.com/MVennn/TBridge/blob/main/docs/BUILD.md)）。
2. LSPosed 管理器 → 模块，启用 TBridge。
3. 作用域勾上**目标 App**（`staticScope=false`，不勾不生效）。
4. 强制停止目标 App 再打开，让模块注入。
5. PC 端验证通道：

```bash
export ADB=adb                          # 默认走 PATH 里的 `adb`
export DEVICE=<REPLACE-DEVICE-SERIAL>  # 可选：多设备时填序列号
python tools/tbridge.py <包名> ping
```

返回带 `ms` 的 `ping` 响应即通道打通；CLI 的 `ADB` / `DEVICE` 都走环境变量，不写死本机路径。

## 反馈

到 [Issues](https://github.com/MVennn/TBridge/issues) 反馈，请带上：

- 模块版本、LSPosed 版本、机型 / 安卓版本
- 目标包名
- `ping` / `tree` 的实际输出
- LSPosed 模块日志

常见现象先查 [docs/TROUBLESHOOTING.md](https://github.com/MVennn/TBridge/blob/main/docs/TROUBLESHOOTING.md)。

## 免责声明

- 本项目仅供学习与受控环境下的测试使用。
- 不采集、不存储、不上传任何数据，不含统计 / 广告 / 回传组件。
- 不申请任何 Android 权限、不联网。
- 桥是经 `adb forward` 暴露的**本地调试通道**，只在受控设备 / 开发环境使用，
  访问控制由使用者自行负责，不对不受信网络开放。
- 使用风险自担。

全文见 [docs/DISCLAIMER.md](https://github.com/MVennn/TBridge/blob/main/docs/DISCLAIMER.md)。

## 开源鸣谢

| 项目 | 用途 | 分发情况 |
| --- | --- | --- |
| [**LSPosed / libxposed**](https://github.com/LSPosed/LSPosed) | 模块框架与现代 Xposed API（API 102） | `compileOnly`，不随 APK 分发 |
| [**Kotlin**](https://github.com/JetBrains/kotlin) | 实现语言 | 标准库随 APK（Apache-2.0） |
| [**Gradle / AGP / Kotlin 插件**](https://gradle.org/) | 构建 | 仅构建期，不分发 |

JSON 手写，未引入第三方 JSON 库（也没有 Gson）。

## 许可证

本项目以 [Apache License 2.0](https://github.com/MVennn/TBridge/blob/main/LICENSE) 发布。
第三方组件版权与许可证声明见 [NOTICE](https://github.com/MVennn/TBridge/blob/main/NOTICE)。
