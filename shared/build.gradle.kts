plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    // iOS 移植 P2：`ui/theme` 这批纯 Compose 文件要搬进来，`@Composable` 必须有编译器插件。
    // 版本与根 build.gradle.kts 一致，别单独升。
    id("org.jetbrains.kotlin.plugin.compose")
    // Room 的代码生成（KSP）。`@Database` 在 commonMain 之后，`JuwDatabase_Impl` 与
    // `JuwDatabaseConstructor` 的 actual 都要靠它逐 target 生成。
    id("com.google.devtools.ksp")
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
            // `androidx.compose.material.icons.Icons.*`：`:app` 里有 14 个图标、58 个文件在用它。
            // 这是**坐标问题不是缝**——icons 本身是纯 ImageVector 数据，CMP 有同名产物。
            // 只用 core（Add/Check/Close/Delete/Refresh/Search/Settings/Share/ArrowBack…），
            // 不引 extended：那是个几十 MB 的包，为一个 `FileDownload` 不值得。
            // 注：`FileDownload` / `History` 若不在 core 里，就换成 AppIcons 里的等价图标，
            // 由搬运那批文件的人现场判断——别为了一个图标把 extended 拉进来。
            api("org.jetbrains.compose.material:material-icons-core:1.7.3")
            // ViewModel 从 lifecycle 2.8 起是 KMP 库：`:app` 那 80 个 ViewModel 的**类本身**
            // 可以进 commonMain，不必改写成 UIKit 版本。
            // 只引 `lifecycle-viewmodel`，**不引** `-compose`：后者的 KMP 变体要 Compose 1.9，
            // 我们在 CMP 1.7.3 上会打架。所以 `viewModel()` 这个 Composable 入口暂时留在 `:app`，
            // 等 UI 那一层搬过去时再一起处理。
            // 另注：KMP 下 `viewModel()` 必须给 initializer（Native 无反射），Hilt 也不可用；
            // 我们用 `Graph` 单例装配，改成可注入容器即可。
            api("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
            // DataStore 的 KMP 变体（`:app` 同版本 1.1.1，不抬线；module metadata 里确有
            // iosArm64/X64/SimArm64）。`preferences-core` 是纯 KMP 的读写信箱，靠 okio Path
            // 定位文件；Android 的 `preferencesDataStore` 属性委托是 Android 专属，不能进共享层。
            api("androidx.datastore:datastore-preferences-core:1.1.1")
            // Ktor 3（iOS 移植 P1 第一个试点模块）：OkHttp 与 Retrofit 都是 JVM 库，iOS 上没有；
            // Ktor 的 core 在 commonMain，引擎按平台各自声明（Android 仍走 OkHttp，iOS 走 Darwin）。
            // 版本选 3.0.3 是配现有的 kotlinx-coroutines 1.9.0（3.1+ 要 1.10.x），
            // 避免为了一个试点模块把全仓协程抬一个版本。
            // 只引 core：WeatherApi 是手解析 JSON 的，不需要 content-negotiation。
            api("io.ktor:ktor-client-core:3.0.3")
            // Room 2.7.1 起是 KMP 库：实体 / DAO / `@Database` 都在 commonMain，
            // Android 侧仍由 `:app` 用 Context 打开库（迁移链也留在那边）。
            // 用 api 而不是 implementation：`:app` 的 Repository 直接拿 JuwDatabase 与 DAO 类型当参数。
            api("androidx.room:room-runtime:2.7.1")
            // 自带一份编译好的 SQLite（iOS 侧没有可依赖的系统 SQLite 版本口径）。
            // 代价是 iOS 二进制 +1.5~3MB，见 design/ios-port.md。
            api("androidx.sqlite:sqlite-bundled:2.5.0")
        }

        androidMain.dependencies {
            // 条码生成/识别（Barcode.android.kt）。zxing 是纯 Java jar，只有 Android 侧用；
            // 版本与 `:app` 里声明的一致，避免同一个 Matrix 类出现两份。
            implementation("com.google.zxing:core:3.5.3")
            // Ktor 的 Android 引擎仍走 OkHttp：连接池、IPv4 优先 DNS 这些既有调优继续生效。
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
            // 只给 `platform/LifecycleState.android.kt` 用：那个 actual 就是转调官方的
            // `collectAsStateWithLifecycle`，让搬进 commonMain 的页面在 Android 上保持
            // 「STOPPED 停止收集」的原行为。**不能**写进 commonMain——它的 KMP 变体要
            // Compose 1.9，与本仓的 CMP 1.7.3 打架（见 commonMain 里 lifecycle-viewmodel 的注释）。
            // 版本与 `:app` 一致（2.8.7），Android 侧解析结果不变。
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
            // `repeatOnLifecycle` 在那个 actual 里用（`ui/common/ResumedTicker.android.kt`），
            // 与 `:app` 同版本。iOS 没有等价物，那边的 actual 自己起循环。
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
        }

        iosMain.dependencies {
            // iOS 引擎（Darwin / NSURLSession）。本机无 macOS，这一侧只声明、不编译。
            implementation("io.ktor:ktor-client-darwin:3.0.3")
        }
    }
}

// Room 的 KSP 依赖必须**逐 target**声明（写在 commonMain 里不会生成各平台的 actual）。
// 少了哪一行，那个平台就没有 `JuwDatabase_Impl`，开库时报「no implementation found」。
dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.7.1")
    add("kspIosArm64", "androidx.room:room-compiler:2.7.1")
    add("kspIosX64", "androidx.room:room-compiler:2.7.1")
    add("kspIosSimulatorArm64", "androidx.room:room-compiler:2.7.1")
}

// schema 导出目录：跟着 `@Database` 走，所以从 `app/schemas` 搬到 `shared/schemas`
// （21 个 JSON 一起搬，内容一字未改，git 里应是纯 rename）。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
