plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    // iOS 移植 P2：`ui/theme` 这批纯 Compose 文件要搬进来，`@Composable` 必须有编译器插件。
    // 版本与根 build.gradle.kts 一致，别单独升。
    id("org.jetbrains.kotlin.plugin.compose")
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

    // iOS 侧要把 `:shared` 当 framework 链进 Xcode 工程（P3）。现在还没有 `iosApp`，
    // 但先把产物形状定下来、让 CI 真的链接一次——链接比编译更能暴露「导出面」的问题
    // （比如 commonMain 用了 Kotlin/Native 导出不了的泛型/内联写法）。
    // `isStatic = true`：静态库省掉 embed & sign 那一步，Swift 侧也不用管 dSYM 拷贝。
    // 真要做成可替换的动态库那天再改，别在这里提前优化。
    // 注意三个 target 只在这里声明一次——重复调用 `iosX64()` 会报 target already exists。
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

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
            // 签名/凭证用的是 okio 的 ByteString + md5/sha256（跨平台，Kotlin/Native 上也有）。
            // 版本锁 3.6.0 是 okhttp 4.12.0 自己带的那一版：`domain/` 搬进来以后这是**直接**依赖，
            // 必须显式声明（原先只是 okhttp 的传递依赖，`:shared` 拿不到）。
            api("com.squareup.okio:okio:3.6.0")
            // Compose Multiplatform（iOS 移植 P2）：`ui/theme` 的纯 Compose 文件搬进来要用。
            // 版本选 1.7.3 而不是 1.8.x，是为了把 Android 侧的 androidx.compose 留在 1.7 线上——
            // `:app` 用的是 compose-bom 2024.10.00（1.7.x），Glance 1.2.0 又要求 runtime ≥ 1.7.8；
            // 上 1.8 会把整条 compose 抬一个大版本，Glance 与既有 UI 都得重新验。
            // 这三个是 public API 级依赖（Composable 签名里就有 Color / ColorScheme），所以用 api。
            api("org.jetbrains.compose.ui:ui:1.7.3")
            api("org.jetbrains.compose.material3:material3:1.7.3")
            api("org.jetbrains.compose.foundation:foundation:1.7.3")
            // Ktor 3（iOS 移植 P1 第一个试点模块）：OkHttp 与 Retrofit 都是 JVM 库，iOS 上没有；
            // Ktor 的 core 在 commonMain，引擎按平台各自声明（Android 仍走 OkHttp，iOS 走 Darwin）。
            // 版本选 3.0.3 是配现有的 kotlinx-coroutines 1.9.0（3.1+ 要 1.10.x），
            // 避免为了一个试点模块把全仓协程抬一个版本。
            // 只引 core：WeatherApi 是手解析 JSON 的，不需要 content-negotiation。
            api("io.ktor:ktor-client-core:3.0.3")
        }

        androidMain.dependencies {
            // 条码生成/识别（Barcode.android.kt）。zxing 是纯 Java jar，只有 Android 侧用；
            // 版本与 `:app` 里声明的一致，避免同一个 Matrix 类出现两份。
            implementation("com.google.zxing:core:3.5.3")
            // Ktor 的 Android 引擎仍走 OkHttp：连接池、IPv4 优先 DNS 这些既有调优继续生效。
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
        }

        iosMain.dependencies {
            // iOS 引擎（Darwin / NSURLSession）。本机无 macOS，这一侧只声明、不编译。
            implementation("io.ktor:ktor-client-darwin:3.0.3")
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
