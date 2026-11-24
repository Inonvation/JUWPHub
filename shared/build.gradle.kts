plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * 跨平台共享模块（iOS 移植 P0，2026-11-24）。
 *
 * 这里放的是**两个平台都要用的纯逻辑**：`domain/`（课程/作息/学期/金额/协议规则……）
 * 以及后续从 `ui/` 抽出的、不含平台 API 的那部分。Android 侧 `:app` 只留平台实现
 * （Activity/Compose 宿主/Room/高德/通知/蓝牙……）。
 *
 * ## 为什么 `commonMain` 是硬门禁
 *
 * `commonMain` 的元数据编译（`compileCommonMainKotlinMetadata`）只对着 Kotlin **公共**
 * 标准库编译，看不见 JDK。任何 `java.time` / `javax.crypto` / `okhttp3` / `com.google.zxing`
 * 混进来都会当场编译失败——这比 `scripts/check-domain-portability.ps1` 的文本扫描硬得多。
 * 而且这个任务在 Windows 上就能跑，不需要 macOS 与 Xcode。**这是本机唯一能验证
 * 「域层真纯净」的手段**，改域层后必须跑它。
 *
 * ## 为什么 iOS target 声明在 Windows 上也不报错
 *
 * target 声明只是让 Gradle 知道要产出哪几个变体，真正的 Kotlin/Native 编译要 macOS。
 * 在 Windows 上这几个 target 的编译任务会被跳过（KGP 给一条 unsupported host 警告），
 * 但 `commonMain` 的元数据编译照常执行——所以本地的门禁是有效的。
 */
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // kotlinx-datetime 0.8.0 起 `Instant` / `Clock` 改由 kotlin.time 提供，
        // 在 Kotlin 2.1.21 上仍是实验 API。理由与 `:app` 里同一行相同，见 app/build.gradle.kts。
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            // 与 `:app` 里声明的版本必须一致：两个模块各自解析出不同版本时，
            // 同一个 `LocalDate` 会在 classpath 上出现两份，症状是莫名其妙的重载解析失败。
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
    }
}

android {
    namespace = "edu.jxslu.schedule.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
