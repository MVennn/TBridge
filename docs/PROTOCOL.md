# 协议

> **一句话**：一行 JSON 请求与响应的字段口径，给客户端实现者看。

## 1. 传输形状

- 通道：`adb forward tcp:27110 localabstract:tb.<包名>`，由客户端自动建立。
- 连接：同一条 socket 可复用，每次发**一行** JSON 请求，收**一行** JSON 响应，行尾 `\n`，UTF-8。空行跳过，连接关闭即结束。
- 请求必须带 `op`；响应由 `BridgeServer.handle()` 统一补 `ms`（服务端处理耗时，毫秒，整数）。
- JSON 由 `Json.kt` 手写解析 / 输出，不用 Gson；字段形状固定，不引第三方库。

## 2. op 总表

| op | 请求字段 | 响应字段 | 说明 |
| --- | --- | --- | --- |
| `ping` | `op` | `ok` / `pkg` / `ver` / `pid` / `api` / `ms` | 握手；`ver` 来自 `Ui.version`，`api` 是 `Build.VERSION.SDK_INT` |
| `act` | `op` | `ok` / `act` / `roots` / `ms` | 当前 Activity 类名与已记录的窗口根数量 |
| `tree` | `op` | `ok` / `count` / `nodes` / `ms` | 扁平节点列表，深度优先，`idx` 从 0 递增 |
| `find` | `op` / `text` / `exact`(默认 false) / `cls`(可选) | `ok` / `count` / `total` / `hits` / `ms` | 按 text 或 contentDescription 模糊 / 精确匹配，可用 `cls` 过滤类名；`hits` 最多 20 条 |
| `tap` | `op` + 任选一个选点：`idx` / `rid` / `text`，可附 `exact` / `cls` | `ok` / `via` / `node` / `ms`，或 `ok` / `err` / `total` / `ms` | 选点优先级 `idx` > `rid` > `text`；`via` = `dispatch` / `click` |
| `settext` | `op` / `text` + `idx` 或 `rid` | `ok` / `ms` | 只认 `EditText`：`setText` 后把光标移到末尾；失败时 `ok=false` 且**没有** `err` |
| `shot` | `op` | `ok` / `w` / `h` / `bytes` / `b64` / `ms`，或 `ok` / `err` / `ms` | `View.draw` 出 PNG，`b64` 是 Base64 `NO_WRAP` |

`find` 的过滤条件是 `text` / `exact` / `cls`；`rid` 只在 `tap` 与 `settext` 的选点逻辑里生效，不是 `find` 的请求字段。`text` 与 `cls` 都为空时 `find` 返回空列表（`ok=true`、`count=0`）。

## 3. tree 节点字段

`tree` 的 `nodes`、`find` 的 `hits`、`tap` 成功时的 `node` 都是同一套 `Ui.describe()` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `idx` | int | 本次扫描的下标，深度优先顺序，可直接给 `tap` / `settext` 用 |
| `cls` | string | `view.javaClass.name`，全限定类名 |
| `text` | string | `TextView.getText()`；`EditText` 为空时取 `hint`；其它 View 为空串 |
| `rid` | string | 资源 id，形如 `<包名>:id/xxx` 或 `android:id/xxx`；无 id 或解析失败为空串 |
| `desc` | string | `contentDescription`，无则空串 |
| `bounds` | array(4 int) | `[left, top, right, bottom]`，`getLocationOnScreen` + 宽高算出的屏幕坐标 |
| `clickable` / `enabled` / `visible` | bool | `isClickable` / `isEnabled` / `isShown` |
| `checked` | bool 或 null | 仅 `Checkable` 有值，其它 View 是 `null` |
| `depth` | int | 相对当前根的深度，根为 0 |

`tree` 只扫 `Ui.topRoot()` 选中的**一个**根，多窗口场景见 [ARCHITECTURE.md](ARCHITECTURE.md) §5。

## 4. shot 返回

- `w` / `h`：所画根 View 的宽高（不是屏幕分辨率）；`bytes`：PNG 字节数；`b64`：PNG 的 Base64，客户端解码后直接写文件。
- 没有可用根时：`err = "no root view"`；根宽高为 0 时：`err = "root has no size"`。
- SurfaceView / TextureView / WebView / Flutter / RN 区域画不出内容，这是 `View.draw` 的边界，不是编码错误，见 [PITFALLS.md](PITFALLS.md)。

## 5. 错误返回形态

所有失败都带 `ok=false` 与 `ms`，通常再带 `err`：

| 场景 | err |
| --- | --- |
| 请求缺 `op` | `missing op` |
| 未知 op | `unknown op: <op>` |
| 解析 / 执行抛异常 | `<异常类名>: <message>` |
| `tap` / `settext` 选不到节点 | `no such node`（`tap` 另带 `total`） |
| `tap` 三路都被拒 | `dispatch and click both refused` |
| `shot` 无根 / 根无尺寸 | `no root view` / `root has no size` |

`settext` 的目标不是 `EditText` 时是**例外**：只回 `{"ok":false,"ms":N}`，没有 `err`。

## 6. 示例

握手与取树：

```json
{"op":"ping"}
{"ok":true,"pkg":"com.example.app","ver":"1.0.0","pid":12345,"api":31,"ms":1}
{"op":"tree"}
{"ok":true,"count":33,"nodes":[{"idx":0,"cls":"com.android.internal.policy.DecorView","text":"","rid":"","desc":"","bounds":[0,0,900,1600],"clickable":false,"enabled":true,"visible":true,"checked":null,"depth":0}],"ms":2}
```

查找并点击（`idx` 优先于 `text`）：

```json
{"op":"find","text":"登录","exact":false}
{"ok":true,"count":1,"total":33,"hits":[{"idx":17,"cls":"android.widget.Button","text":"登录","rid":"com.example.app:id/btn_login","desc":"","bounds":[320,720,580,800],"clickable":true,"enabled":true,"visible":true,"checked":null,"depth":5}],"ms":1}
{"op":"tap","idx":17}
{"ok":true,"via":"dispatch","node":{"idx":17,"cls":"android.widget.Button"},"ms":1}
```

截图与错误：

```json
{"op":"shot"}
{"ok":true,"w":900,"h":1600,"bytes":48231,"b64":"<REPLACE-BASE64-PNG>","ms":32}
{"op":"nope"}
{"ok":false,"err":"unknown op: nope","ms":0}
{"op":"tap","text":"不存在的按钮"}
{"ok":false,"err":"no such node","total":33,"ms":1}
```

响应里的节点在示例中做了省略，实际以 `Ui.describe()` 的 11 个字段为准。客户端命令见 [CLI.md](CLI.md)。
