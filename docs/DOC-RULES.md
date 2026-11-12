# 文档规则

> **一句话**：docs/ 下文档怎么写、怎么命名、怎么登记、怎么改名，全部以本文件为准，违反任意一条即门禁失败。

## 1. 分类

| 类别 | 文件名形态 | 收什么 | 例子 |
| --- | --- | --- | --- |
| 索引 | `README.md` | 登记表：每篇一行 + 一句话定位 | `docs/README.md` |
| 架构 | 全大写 + 连字符 | 模块怎么搭、Hook 在哪、已知限制 | `ARCHITECTURE.md` |
| 流程 | 全大写 + 连字符 | 构建、版本、发版、维护 | `BUILD.md`、`VERSIONING.md` |
| 协议 / 客户端 | 全大写 + 连字符 | JSON 协议字段、CLI 子命令与环境变量 | `PROTOCOL.md`、`CLI.md` |
| 记录 | 全大写 + 连字符 | 踩坑、事故复盘、排错步骤，只追加不改写 | `PITFALLS.md`、`TROUBLESHOOTING.md` |
| 法律 | 全大写 + 连字符 | 免责声明全文 | `DISCLAIMER.md` |
| 规则 | 全大写 + 连字符 | 文档写法本身 | `DOC-RULES.md` |

判据：`docs/` 下除 `README.md` 外，文件名必须匹配 `^[A-Z0-9]+(-[A-Z0-9]+)*\.md$`；单篇不超过 800 行。

## 2. 单一正本

同一件事只允许一篇文档写规则，其它地方只留**一句话概述 + 链接**。

权威正本映射：

| 规则 | 正本 |
| --- | --- |
| Hook 点、root View 抓取、生命周期、已知限制 | `ARCHITECTURE.md` |
| op / 字段 / 响应形态 / 错误 | `PROTOCOL.md` |
| CLI 子命令、环境变量、退出码 | `CLI.md` |
| 构建环境、脚本行为、产物 | `BUILD.md` |
| 版本号位置与晋升 | `VERSIONING.md` |
| 缺陷的现象 / 根因 / 规避 | `PITFALLS.md` |
| 排错的现场步骤 | `TROUBLESHOOTING.md` |
| 文档写法 | `DOC-RULES.md`（本篇） |
| 免责全文 | `DISCLAIMER.md` |

根目录**文档**只留 `README / CHANGELOG / CONTRIBUTING / SECURITY / LICENSE / NOTICE / AGENTS / PROGRESS`；构建骨架（`build.gradle.kts` / `settings.gradle.kts` / `gradlew` / `gradlew.bat` / `gradle/` / `app/` / `assets/` / `tools/` / `local.properties.example`）按实际结构保留。

## 3. 命名

- 全大写 + 连字符：`ARCHITECTURE.md`、`TROUBLESHOOTING.md`；禁止小写、下划线、空格、中文文件名、驼峰。
- 唯一例外：索引固定叫 `README.md`。

判据：仓根执行 `python tools/check-docs.py`，不出现 `[FAIL]` 命名项。

## 4. 一句话抬头

每篇文档前 20 行内必须有（门禁 `HEAD_LINES = 20`）：

> **一句话**：这篇文档解决什么问题、谁该读它。

一句话不超过 60 字，写「是什么 / 给谁看」。判据：门禁不出现 `[FAIL] 缺一句话块`。

## 5. 索引同步

- `docs/README.md` 是唯一索引，新增 / 改名 / 删除文档都必须同步改它。
- 索引不登记自己。
- 判据：门禁不出现 `[FAIL] 索引漏项`。

## 6. 断链零容忍

- 相对链接必须解析到真实文件，路径相对**当前文档所在目录**。
- 外链只要求写法完整，不检查可达性；根 README 的门面链接一律用 GitHub 绝对地址。
- 判据：门禁不出现 `[FAIL] 断链`。

## 7. 别名三步

改名一次做完三步，中间停下即算破坏仓库：

1. `git mv docs/OLD.md docs/NEW.md`（保住重命名识别）；
2. 全仓 grep 旧名，引用方一并改（含根 README、CHANGELOG、`docs/README.md`）；
3. `python tools/check-docs.py` 退出码 0 才算完成。

## 8. 文档与代码同步

- 用户可感知的改动，必须同时在 `CHANGELOG.md` 写一行。
- 修缺陷时，除 `CHANGELOG.md` 外还要在 `PITFALLS.md` 补「现象 → 根因 → 怎么避」三条。
- 文档里的版本号、命令、路径要与代码一致；不一致时以代码为准并改文档。

