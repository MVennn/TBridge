# 版本规范

> **一句话**：版本号有两处，发版才动；tag / Release / 资产怎么命名看本篇。

## 1. 命名与进位

三段式 `MAJOR.MINOR.PATCH`，当前 `versionName = "1.0.0"`、`versionCode = 1`。

| 段 | 什么时候 +1 | 例子 |
| --- | --- | --- |
| `PATCH` | 修缺陷、内部重构、文档与门禁类改动 | `1.0.0` → `1.0.1` |
| `MINOR` | 新增用户可感知的能力（新 op、新 CLI 子命令） | `1.0.0` → `1.1.0` |
| `MAJOR` | 不兼容大改（协议形状变了、换注入框架） | `1.0.0` → `2.0.0` |

`versionCode` 是单调递增整数，每次发版 +1，与 `versionName` 的段位不必一一对应。

## 2. Release 的三件命名

| 东西 | 写法 | 以当前值为例（仅示意） |
| --- | --- | --- |
| tag | `{versionCode}-{versionName}` | `1-1.0.0` |
| Release 标题 | **只写 `v{versionName}`**，不加描述 / 副标题 | `v1.0.0` |
| APK 资产名 | `TBridge_{versionCode}-{versionName}.apk` | `TBridge_1-1.0.0.apk` |

tag 自带构建号，回退与覆盖安装时一眼能看出先后；标题塞中文描述是错的——版本列表靠 tag 与标题对齐，标题一长每行都不一样，还会和正文第一行重复。示例值只说明命名形状，不代表仓库里已有这些 tag / Release。

## 3. 版本号有两处

| 位置 | 字段 | 谁读它 |
| --- | --- | --- |
| `app/build.gradle.kts` | `versionName` / `versionCode` | APK 包、LSPosed 模块列表、tag 与资产名 |
| `app/src/main/java/io/github/MVennn/tbridge/TBridgeModule.kt` | `onPackageReady` 里 `Ui.version = "1.0.0"` | `ping` 响应的 `ver` 字段 |

**发版时两处必须同步**：只改 Gradle 会让 `ping` 回旧版本，客户端按 `ver` 做兼容判断就会误判；只改 `Ui.version` 则包与 tag 对不上。改动 `versionName` 后顺带确认 `ping` 实测回的新值，协议字段见 [PROTOCOL.md](PROTOCOL.md)。

## 4. 发版才动版本号

1. 未发版前改代码**不动** `versionName` / `versionCode`，也不打 tag。
2. 只有明确说「发版」才：升两处版本号 → 写 `CHANGELOG.md` → 出 Release 正文 → 打 `{versionCode}-{versionName}` tag → 发 Release（标题 `v{versionName}`，资产按第 2 节改名）。
3. 用户可感知的改动进 `CHANGELOG.md`；修缺陷另在 [PITFALLS.md](PITFALLS.md) 补「现象 → 根因 → 怎么避」三段，见 [DOC-RULES.md](DOC-RULES.md) §8。

**本次是首次建仓**：`versionCode = 1` / `versionName = 1.0.0` 原样保留，不打 tag、不发 Release、不改 `Ui.version`。构建与产物见 [BUILD.md](BUILD.md)。
