plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.MVennn.tbridge"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.MVennn.tbridge"
        // 模块本体只是个"壳"，真正干活的是注入到目标进程的代码。
        // minSdk 27 = LSPosed Manager 的下限，也是 Zygisk 注入的实用下限。
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // 入口类靠 META-INF/xposed/java_init.list 里的类名反射实例化，必须 keep。
            // 除此之外不引任何第三方库，R8 裁不出多少，但开着没坏处。
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    // compileOnly：框架在运行期提供，绝不打进 APK（打进去会与框架类冲突导致模块崩）。
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly("io.github.libxposed:annotation:1.0.0")
}
