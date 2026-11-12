# tools/

> **一句话**：仓内门禁、桥客户端、作用域工具与品牌图生成脚本总表，每个脚本写清用途、用法与退出码。

## check-docs.py

- **用途**：文档门禁。检查 `docs/*.md` 是否都被 `docs/README.md` 索引、每篇前 20 行是否有「一句话」抬头、索引与文档的相对链接是否可解析、文件名是否全大写 + 连字符。
- **用法**：仓根执行 `python tools/check-docs.py`。
- **退出码**：0 通过；1 有失败项（逐条打印 `[FAIL] ...`）。零第三方依赖。

## check-secrets.py

- **用途**：凭据扫描。查私钥头、AWS / GitHub / Slack / Google 令牌前缀、带口令连接串、赋值型凭据；跳过 `.git` / `.work` / `.gradle-home` / `.android-home` / `.tmp` / `build` / `dist` 与二进制。
- **用法**：仓根执行 `python tools/check-secrets.py`。
- **退出码**：0 干净；1 有命中（打印「文件:行号:[规则] 脱敏片段」）。零第三方依赖。

## tbridge.py

- **用途**：PC 端桥客户端。`adb forward tcp:27110 localabstract:tb.<包名>` 后，用一行 JSON 调 `ping / act / tree / find / tap / wait / shot / settext / multi`；`multi` 用 `;;` 串联，`tapif` 带 `maxwait`。
- **用法**：`python tools/tbridge.py <包名> <子命令> [参数]`；`ADB` / `DEVICE` 走环境变量，缺省 `adb` 取 PATH。
- **退出码**：0 正常；非 0 为通道 / 参数 / adb 错误，错误文本写 stderr。

## scope.py

- **用途**：改 LSPosed `modules_config.db` 的 `modules_state` / `scope` 表来切作用域，回读校验；WAL 场景连带 `db-wal` / `db-shm`。
- **用法**：`python tools/scope.py [目标包名]`，默认 `setprop ctl.restart zygote`；`--off` 关模块，`--no-zygote` 只调试不重启。
- **退出码**：0 写入并回读一致；非 0 为 adb / sqlite3 / 设备错误。

## make_social_preview.py

- **用途**：由 `assets/icon.svg` 的几何生成品牌图：512x512 `logo.png`、1280x640 `social-preview.png`、多尺寸 `icon.ico`。依赖 Pillow（仅本脚本需要，构建 APK 用不到）。
- **用法**：仓根执行 `python tools/make_social_preview.py`，可追加输出目录。
- **退出码**：0 三件产物齐全；字体缺失时自动退化到内置字体，不影响退出码。

## 约定

- `check-docs.py` / `check-secrets.py` 零第三方依赖，只用 Python 3 标准库；只读、不联网。
- 桥客户端与作用域工具不写本机绝对路径，`ADB` / `DEVICE` 一律走环境变量。
- 新增 / 改名脚本必须同步更新本文件，否则视同没有文档。

