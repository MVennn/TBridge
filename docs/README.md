# 文档索引

> **一句话**：TBridge 文档唯一索引，新增文档必须先在这里加一行，否则 `check-docs.py` 判失败。

本索引只登记 `docs/` 下的开发者文档，索引自身不登记。每篇前 20 行内必须有 `> **一句话**：…` 抬头，文件名一律全大写 + 连字符（唯一例外是本索引 `README.md`）。规则正本见 [DOC-RULES.md](DOC-RULES.md)，门禁在仓根执行 `python tools/check-docs.py`。

## 一、架构与实现

| 文档 | 一句话定位 |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 模块分层、四个 Kotlin 文件职责、socket 通道与生命周期、已知限制 |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | 排错手册：按「现象 → 步骤 → 原因 / 解法」逐节定位 |

## 二、流程与规范

| 文档 | 一句话定位 |
| --- | --- |
| [BUILD.md](BUILD.md) | 构建：版本钉死、debug 产物、SDK 解析顺序、缓存与 CI |
| [VERSIONING.md](VERSIONING.md) | 版本号两处、三段式进位、tag / Release / 资产命名，发版才动 |
| [PITFALLS.md](PITFALLS.md) | 踩坑正本：每条严格「现象 → 根因 → 怎么避」 |
| [DOC-RULES.md](DOC-RULES.md) | 文档规则：四类、单一正本、命名、抬头、索引、断链、改名三步 |

## 三、协议与客户端

| 文档 | 一句话定位 |
| --- | --- |
| [PROTOCOL.md](PROTOCOL.md) | 一行 JSON 请求 / 响应的 op 字段、节点字段、错误形态与示例 |
| [CLI.md](CLI.md) | `tools/tbridge.py` 与 `tools/scope.py` 的子命令、环境变量、退出码与示例 |

## 四、法律与声明

| 文档 | 一句话定位 |
| --- | --- |
| [DISCLAIMER.md](DISCLAIMER.md) | 免责声明全文（根 README 里只放摘要） |

新增 / 改名 / 删除文档后，先改本表再跑 `python tools/check-docs.py`，退出码 0 才算完成。根 README 是七段式门面，指向本仓文档的链接用 GitHub 绝对地址，不放文档索引链接。
