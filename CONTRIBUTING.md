# 参与贡献

> **一句话**：本仓是「在被测 App 进程内直接问 View 对象」的 LSPosed 测试桥；改动前先读
> [docs/DOC-RULES.md](docs/DOC-RULES.md)（文档规则）与 [docs/BUILD.md](docs/BUILD.md)（构建），本文只讲「怎么提」。

本模块只做**进程内 View 查询与显式下发的操作**，不联网、不采集、不申请权限。建议与 PR 请落在这条线内。

## 提 Issue

- **不生效 / socket 连不上 / 操作没反应** → 用「问题反馈（Bug）」模板，**务必贴 `ping` / `tree` 输出与 LSPosed 模块日志**：

```bash
python tools/tbridge.py <包名> ping
python tools/tbridge.py <包名> tree
```

- **想要新的 op 或更顺手的 CLI**（例如新的匹配条件、批量操作）→ 用「功能建议」模板，先说场景。

排错步骤见 [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)。

## 提 Pull Request

1. 从 `main` 开分支，一条改动一个分支。
2. 改动前过一遍 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 的边界约定：**桥只在主线程同步读 View、执行显式下发的操作，hooker 抛异常时目标 App 不能崩**（`exceptionMode=protective`）。
3. 提交前至少跑过：

```bash
./gradlew :app:assembleDebug    # 能编出 APK
python tools/check-docs.py      # 文档门禁
python tools/check-secrets.py   # 凭据门禁
```

4. PR 描述里写清「怎么验的」——真机没验就明说没验（贴 `ping` / `tree` / `shot` 的实际输出最直观）。

## 几条硬规矩

| 规矩 | 在哪定义 |
| --- | --- |
| 每条规则只在一篇文档里定义，别处只留链接（单一正本） | [docs/DOC-RULES.md](docs/DOC-RULES.md) §2 |
| 发版才动 `versionCode` / `versionName`，两处同步 | [docs/VERSIONING.md](docs/VERSIONING.md) |
| 用户可感知的改动要进 [CHANGELOG.md](CHANGELOG.md) | [docs/DOC-RULES.md](docs/DOC-RULES.md) §5 |
| 修缺陷必须补「现象 → 根因 → 怎么避」三段 | [docs/PITFALLS.md](docs/PITFALLS.md) |

## 免责声明

提交 Issue / PR 即表示你已知悉：本模块经 `adb forward` 暴露本地调试通道，仅在受控设备 / 开发环境使用，访问控制与使用风险由使用者自行承担。
全文见 [docs/DISCLAIMER.md](docs/DISCLAIMER.md)。
