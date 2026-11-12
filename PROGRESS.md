# PROGRESS —— TBridge

> **一句话**：本仓任务与验收记录，按批次追加。

## 2026-11-12 · 首次建仓：门面补齐与公开仓库落地

**需求（用户）**：按系列仓门面规范首次建仓，补齐 TBridge 根目录门面与仓库配置（分片 C）：
覆盖重写七段式 README，新增 AGENTS / CONTRIBUTING / SECURITY / CHANGELOG / LICENSE / NOTICE / PROGRESS，
补 .gitignore / .gitattributes / local.properties.example；不执行 `git init/add/commit/push`，
版本号保持 1 / 1.0.0，不打 tag、不发 Release；文档链接走 GitHub 绝对地址、不写本机绝对路径。

**改动**
- `README.md`：七段式（这是什么 / 环境要求 / 安装 / 反馈 / 免责声明 / 开源鸣谢 / 许可证），
  `<div align="center">` 首屏 + 7 枚徽章一行，耗时对照表与三条坑，协议与 CLI 细节外链 docs
- `AGENTS.md`：11 节仓级规则（生效顺序 / 语言交付 / 路径 / 变更 / 凭据 / 文档门禁 /
  检索 / 调试产物 / 当前阶段 / 构建 / 门面与文档口径）
- `CONTRIBUTING.md`、`SECURITY.md`、`CHANGELOG.md`、`NOTICE`、`PROGRESS.md`：门面与协作文档
- `LICENSE`：从 WxPicLabe/LICENSE 逐字节复制（Apache-2.0 全文 202 行）
- `.gitignore`：以 WxPicLabe 45 行为底，补品牌图片例外与本地调试产物忽略
- `.gitattributes`：以 WxPicLabe 8 行为底，追加 svg / yml / md / kts / ico 规则
- `local.properties.example`：从 WxPicLabe 复制（3 行，`<REPLACE-ANDROID-SDK-DIR>`）

**验证**
- 本分片逐文件复核：抬头、行数、字节、链接、无本机绝对路径、UTF-8 无 BOM / LF 换行。

---

## 2026-11-12 · 主线集成：三路验收、缺口补齐、双门禁与首次发布

**需求（用户）**：核查 TBridge 未发 GitHub、未挂进 LSPosed 合并视图（`_merged/LSPosed`），按系列仓门面规范建仓。

**改动**

- 目录地图：父目录 `AGENTS.md` 的 TBridge 行改为 `MVennn/TBridge | 公开（脱敏）`，仓数与门禁计数按实测校正；`_merged/LSPosed/AGENTS.md` 模块表与门禁行补 TBridge。
- 视图：`cmd /c mklink /J` 挂 `_merged/LSPosed/TBridge` 到真实项目，现有三个 junction。
- 源码脱敏：`tools/tbridge.py` 的 `ADB` 默认值改为环境变量 + 默认 `adb`；复制两份门禁脚本。
- 验收三路子代理：分片 C 根门面齐全；分片 A 只落 6 篇 docs、分片 B 零产物，按回退口径由主线接管。
- 主线补齐 4 篇 docs（PITFALLS / TROUBLESHOOTING / DISCLAIMER / DOC-RULES）与 `tools/README.md`；补齐 CI（JDK 21）/ Issue 三件 / `assets/` 品牌资源 / `make_social_preview.py`。
- 修 914 处字面量反斜杠加反引号、`PROTOCOL.md` 两处 Markdown 破损、两处本机路径示例文字。
- `.gitattributes` 对齐系列仓完整口径：全局 `* text=auto eol=lf` + `gradlew` 显式 `eol=lf`，CHANGELOG / PITFALLS 双记账补第 9 条。

**验证**

- `python tools/check-docs.py` exit 0：扫描 10 篇文档，索引完整、无断链、无孤儿。
- `python tools/check-secrets.py` exit 0：扫描 49 个文件，命中 0 处。
- 5 个 `tools/*.py` 全部 `py_compile` exit 0；4 个 `.github/**/*.yml` 全部 `yaml.safe_load` 通过。
- 品牌图三件由 `make_social_preview.py` 实跑生成并核对尺寸；全仓文本体检 `VERDICT PASS`；README 七段式 101 行。
- `git init -b main` + 本地身份（M.Venn / GitHub noreply）已配；`local.properties`、`.gradle/`、`.work/`、`evidence/` 均被忽略。
- `git add --renormalize .` 后 `git ls-files --eol` 复核：暂存 blob 全 `i/lf`，`gradlew` 与 `gradlew.bat` 按属性分别 LF / CRLF。

**发版**：本次不升版本号、不打 tag、不发 Release。

**收尾**：首次提交、GitHub 公开仓发布、CI 核验与项目记忆三篇回写均由主线完成，本批闭环。
