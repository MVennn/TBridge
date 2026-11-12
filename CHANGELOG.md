# Changelog

## [Unreleased]

- 订正文档数字口径（docs 九篇正文 + 唯一索引共 10 个 .md；tools 六个文件）与系列仓格式基准表述，清理指向已归档篇的失效指针；详见 `docs/PITFALLS.md` #10。

## [1.0.0] - 2026-11-12

首次建仓：门面补齐与公开仓库落地。

### 新增

- 七段式 `README.md`、`AGENTS.md`、`CONTRIBUTING.md`、`SECURITY.md`、`LICENSE`、`NOTICE`、`CHANGELOG.md`、`PROGRESS.md`
- `docs/` 九篇与索引 `docs/README.md`
- `.github/` Issue 模板三件（bug / feature / config）与 `ci.yml`
- `assets/` 品牌资源
- `tools/` 门禁脚本与脚本索引

### 调整

- `tools/tbridge.py` 的 `ADB` 默认值由跨邻仓相对路径改为环境变量 + 默认 `adb`

### 修复

- 旧 `README.md` 里的本机绝对路径与目录树
- `docs/` 六篇里 914 处字面量反斜杠 + 反引号，以及 `PROTOCOL.md` 两处 Markdown 破损（GitHub 会多渲染一个反斜杠）
- `local.properties.example` 与 `PITFALLS.md` 里残留的本机 / 邻仓路径示例文字
- CI 的 `setup-java` 从邻仓模板的 JDK 17 校正为 JDK 21（对齐 `jvmToolchain(21)`）
- `.gitattributes` 由 `* text=auto` 对齐为全局 LF，并给 `gradlew` 显式钉死 `eol=lf`，避免 Windows 检出 CRLF 破坏 shebang
