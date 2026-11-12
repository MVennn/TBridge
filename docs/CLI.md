# 客户端

> **一句话**：两条客户端脚本怎么用，命令、环境变量与退出码都在这里。

## 1. tools/tbridge.py 用法

```bash
python tools/tbridge.py <包名> <子命令> [参数...]
```

- `ADB`：adb 可执行文件，默认 `adb`（走 PATH）。要指定就导出环境变量，脚本里不写死任何路径。
- `DEVICE`：设备序列号，等价于给每条 adb 命令加 `-s <serial>`；不设则走默认设备。
- 端口固定 `27110`。`Bridge.connect()` 自动执行 `adb forward tcp:27110 localabstract:tb.<包名>`，不需要手工建 forward；同一条 socket 复用，读完一行响应即返回。
- 每次调用结束在 stderr 打 `-- N ms --`（客户端侧总耗时），stdout 是格式化 JSON。

| 子命令 | 参数 | 行为 |
| --- | --- | --- |
| `ping` | — | 握手，回 `pkg` / `ver` / `pid` / `api` |
| `act` | — | 当前 Activity 类名与窗口根数量 |
| `tree` | `--json` 可选 | 默认打印节点清单后只回 `{ok, count}`；带 `--json` 回完整 `nodes` |
| `find` | `TEXT` | 按文字 / contentDescription 模糊查找 |
| `tap` | `IDX` 或 `TEXT` | 纯数字或 `#12` 当 `idx`，否则当 `text` |
| `wait` | `TEXT [SEC]` | 20 ms 一轮轮询，默认 10 秒；回 `found` / `polls` / `waited_ms` |
| `tapif` | `TEXT [SEC]` | 出现就点、超时就跳过，默认 0 秒；跳过时 `ok=true` + `skipped=true` |
| `settext` | `IDX TEXT` | 给指定 `EditText` 输入文字 |
| `shot` | `[PATH]` | 默认写 `shot.png`，打印 `saved <path> (<n> bytes)` |
| `multi` | `"OP;;OP;;..."` | 一次 Python 启动跑完整条链路，逐条打印耗时，末行回 `{ok, ops}` |

`multi` 用 `;;` 串联，每段按 `shlex` 拆词，支持 `tap` / `wait` / `tapif` / `tree` / `find` / `shot` / `settext` / `act` / `sleep`；写 `wait 登录页 5` 是等文字出现，写 `tapif "知道了" 5` 是「弹窗出现就点、5 秒内没出现就跳过」。冷启动弹窗时有时无，用 `tapif` 而不是 `wait` 硬等 8 秒，理由见 [PITFALLS.md](PITFALLS.md)。

**退出码**：0 成功；2 参数不足或未知子命令；adb / 连接 / 桥异常未捕获时由 Python 抛出并非零退出。注意 `tapif` 的「跳过」是成功语义（`ok=true`），不是失败。

## 2. 示例

```bash
export ADB=adb
export DEVICE=<REPLACE-DEVICE-SERIAL>

python tools/tbridge.py com.example.app ping
python tools/tbridge.py com.example.app tree --json
python tools/tbridge.py com.example.app find "登录"
python tools/tbridge.py com.example.app tap "#17"
python tools/tbridge.py com.example.app tapif "知道了" 5
python tools/tbridge.py com.example.app settext "#9" "hello"
python tools/tbridge.py com.example.app shot shot.png
python tools/tbridge.py com.example.app multi "act;;wait 登录页 5;;tap 登录;;tree;;shot a.png"
```

示例里的 `<REPLACE-DEVICE-SERIAL>` 是待填值占位；命令一律用仓内相对路径与环境变量，不写本机绝对路径。协议字段见 [PROTOCOL.md](PROTOCOL.md)。

## 3. tools/scope.py 用法

改 LSPosed 的作用域表 `modules_config.db`，不用点界面。脚本把 SQL 推到设备上用 `sqlite3` 执行（不 pull 主库、不覆盖 WAL），执行后回读当前作用域确认。

```bash
export ADB=adb
export DEVICE=<REPLACE-DEVICE-SERIAL>

python tools/scope.py io.github.MVennn.tbridge com.example.app          # 勾上
python tools/scope.py io.github.MVennn.tbridge com.example.app --off     # 取消
python tools/scope.py io.github.MVennn.tbridge com.example.app --no-zygote  # 改完不重启
python tools/scope.py io.github.MVennn.tbridge com.example.app com.example.other  # 多个目标
```

| 参数 | 作用 |
| --- | --- |
| `--off` | 从 `scope` 表删除目标，而不是插入 |
| `--no-zygote` | 改完不执行 `setprop ctl.restart zygote`；**默认会重启** |

作用域配置在 zygote fork 时读，不重启不生效；用 `--no-zygote` 就得自己补一次 `adb shell su -c 'setprop ctl.restart zygote'`。WAL 相关的坑见 [PITFALLS.md](PITFALLS.md)，改完仍不生效按 [TROUBLESHOOTING.md](TROUBLESHOOTING.md) 第 6 节查。

**退出码**：0 成功；1 参数不足、SQL 文件 push 失败或设备侧 `sqlite3` 失败。

## 4. 约定

- 两个脚本零第三方依赖，只用 Python 3 标准库；`tbridge.py` 需要可达的 adb 与受控设备。
- 脚本总表见 [tools/README.md](../tools/README.md)；仓内门禁跑法见 [BUILD.md](BUILD.md)。
- 用完可清掉 forward：`adb forward --remove tcp:27110`。
