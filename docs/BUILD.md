# 构建

> **一句话**：本仓怎么编出 debug APK，版本钉死、SDK 怎么解析、产物在哪。

## 1. 版本

| 项 | 值 | 落点 |
| --- | --- | --- |
| JDK | 21 | `JavaVersion.VERSION_21` / `jvmToolchain(21)` |
| Gradle | 8.11.1 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP | 8.10.1 | `build.gradle.kts` |
| Kotlin android 插件 | 2.4.0 | `build.gradle.kts` |
| compileSdk | 37 | `app/build.gradle.kts`，`android.suppressUnsupportedCompileSdk=37,37.0` |
| minSdk | 27 | `app/build.gradle.kts` |
| targetSdk | 34 | `app/build.gradle.kts` |
| namespace / applicationId | `io.github.MVennn.tbridge` | `app/build.gradle.kts` |
| versionCode / versionName | `1` / `1.0.0` | `app/build.gradle.kts`；本次首次建仓不动 |
| Xposed API | 102 | `module.prop` 的 `minApiVersion` / `targetApiVersion` |

依赖只有 `compileOnly("io.github.libxposed:api:102.0.0")` 与 `compileOnly("io.github.libxposed:annotation:1.0.0")`：框架运行期提供，不打进 APK（打进去会与框架类冲突）。没有 UI 库、网络库、JSON 库、Gson。

## 2. 构建命令与产物

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。`app/build/` 是本机产物，已被 gitignore，不进库；仓库不存 APK。要全量重建就删掉 `app/build` 再跑一次。

## 3. SDK 解析顺序

Gradle 按下面顺序取 SDK，前一个命中就用它：

| 顺序 | 来源 | 说明 |
| --- | --- | --- |
| 1 | `ANDROID_SDK_ROOT` / `ANDROID_HOME` | 调用方环境里已设且目录存在时采用 |
| 2 | `local.properties` 的 `sdk.dir` | 环境变量都没有时由 Gradle 自己读 |

- `local.properties` 不进库（已 gitignore）；模板见 `local.properties.example`，值写成 `<REPLACE-ANDROID-SDK-DIR>`，由使用者按本机情况填。
- **平台包名带小版本号**：`compileSdk = 37` 对应 `platforms;android-37.0`，写成 `platforms;android-37` 会 `Failed to find package`。
- 文档、脚本、配置里不写本机绝对路径；SDK 位置一律走环境变量或 `local.properties`。

## 4. 缓存与忽略

| 目录 | 用途 |
| --- | --- |
| `.gradle-home/` | `GRADLE_USER_HOME` 重定向的 Gradle 缓存 |
| `.android-home/` | `ANDROID_USER_HOME` 重定向的 Android 缓存与调试密钥库 |
| `.tmp/` | `TMP` / `TEMP` / `TMPDIR` 重定向的临时目录 |

三个目录都属于本机缓存，均由 `.gitignore` 排除，不提交。手工跑 Gradle 时把这三组变量指到仓内同名目录，可避免污染用户目录，也不需要管理员权限。

## 5. CI

CI 用 `actions/setup-java` 的 **temurin JDK 21**（`java-version: '21'`、`distribution: temurin`），与 `jvmToolchain(21)` 对齐；不用 17。CI 里同样先准备 `platforms;android-37.0`，再跑第 2 节的 `assembleDebug` 命令，并执行 `python tools/check-docs.py` 与 `python tools/check-secrets.py`（脚本总表见 [tools/README.md](../tools/README.md)）。

版本晋升与 tag / Release 命名见 [VERSIONING.md](VERSIONING.md)，模块结构与运行期边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。
