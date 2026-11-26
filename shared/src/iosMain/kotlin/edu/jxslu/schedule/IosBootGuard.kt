package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.jxslu.schedule.platform.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * 启动期的「拦住异常并显示出来」那一层（2026-11-26）。
 *
 * ## 为什么需要
 *
 * 从 2026-11-25 到 11-26，真机上反复出现「装完一打开就闪退」，而**三次都拿不到任何现场**：
 * 包体检查（架构 / minos / rpath / 依赖库 / Info.plist / Assets.car）全绿，说明包是健康的；
 * 用户侧既没有 Console 也没有 Xcode，`logs/app.log` 又因为沙箱目录那条 bug 自己先断了。
 * 结果是排查只能靠猜，而每猜一次就是一次构建 + 一次装机。
 *
 * 这一层把「崩」换成「看得见的一句话」：启动路径上任何一步抛出 Kotlin 异常，
 * 都不再让进程直接消失，而是在首帧上把步骤与栈打出来，用户截一张图就够定位。
 *
 * ## 边界（不能指望它兜住什么）
 *
 * | 兜不住 | 原因 |
 * |---|---|
 * | Kotlin 之前的崩溃（dyld / 签名 / 包体） | 那时还轮不到 Kotlin 代码跑 |
 * | Objective-C 异常（WebKit 主线程断言等） | `NSException` 在 `try/catch` 之外，进程直接终止 |
 * | 首帧之后（协程 / 交互回调里）的异常 | 不在下面这个 `try` 的范围内；协程那条由 `IosServices` 的 `CoroutineExceptionHandler` 接 |
 *
 * 也就是说它只负责**组合期**这一段——而组合期正是 11-26 那次闪退最可疑的地方
 *（根组合里同步读了偏好、同步造了两个 ViewModel）。
 *
 * ## 步骤留痕
 *
 * [IosBootSteps] 与 `MainViewController` 里 ①–⑥ 的编号是同一套，编号不重排：
 * 用户截图里看到「④」就说明死在 ④ 之后。同一步也经 [Logger] 落 `logs/app.log`。
 */
internal object IosBootSteps {

    /** 只由启动路径（主线程）写，错误页也只在这条路径上读。 */
    private val steps = mutableListOf<String>()

    fun step(message: String) {
        steps += message
        runCatching { Logger.i(TAG_BOOT, message) }
    }

    fun snapshot(): List<String> = steps.toList()
}

/**
 * 启动失败的整屏提示。
 *
 * 刻意**不套 `JuwTheme`**：走到这里可能正是主题 / 偏好在组合期炸的，再依赖它们等于
 * 让错误页跟着一起炸。颜色写死（浅底深字），深色模式下也能读。
 *
 * 可整段选中复制（`SelectionContainer`）：用户不一定能截图，能复制文字更保险。
 */
@Composable
internal fun IosBootCrashScreen(title: String, error: Throwable) {
    val detail = runCatching { error.stackTraceToString() }.getOrElse { error.toString() }
    val steps = IosBootSteps.snapshot().joinToString("\n")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF6F4)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = "启动失败：$title",
                style = TextStyle(color = Color(0xFF8C1D18), fontSize = 17.sp),
            )
            Text(
                text = "请把这一屏截图发给开发者。下面两段分别是启动到哪一步、以及异常栈。",
                style = TextStyle(color = Color(0xFF5A3A38), fontSize = 13.sp),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Text(
                text = if (steps.isEmpty()) "（没有任何启动步骤记录）" else steps,
                style = TextStyle(
                    color = Color(0xFF243024),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Text(
                text = detail,
                style = TextStyle(
                    color = Color(0xFF8C1D18),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * 读一条 [Flow] 的安全版 `collectAsState`。
 *
 * `collectAsStateWithLifecycle` 在采集期抛异常时，异常会冒到组合作用域上——Kotlin/Native
 * 上那等于**进程终止**。偏好库（DataStore）在 iOS 上是真实文件读写，首帧恰好是它第一次
 * 打开文件的那一下，所以这条路径值得单独兜住：取不到就停在 [initial]，界面照常画。
 *
 * ## 为什么 [make] 是个 lambda，而不是直接收 `Flow`
 *
 * `DisplayPrefsStore` 的属性是**即时求值**的（`val themeMode: Flow<…> = displayDataStore.data…`），
 * 求值那一下就会碰到 `displayDataStore` 的 `lazy`。所以「取 Flow」本身也可能抛，必须一起包住。
 *
 * 但**不能**在调用点用 `try` 包：Compose 编译器对「composable 调用出现在 try/catch 里」
 * 直接报错（2026-11-26 CI 实测：`Try catch is not supported around composable function
 * invocations`）。所以异常在 [remember] 里用 `runCatching` 接，[remember] 的键用调用方给的
 * [key] 字符串而不是 lambda 实例——lambda 每次重组都是新实例，拿它当键等于每帧重订阅。
 *
 * 与 `collectAsStateWithLifecycle` 的差别只有一条：不随生命周期间断采集（iOS 没有
 * 「Activity stop」那种暂停点，进程在前台就只有一套生命周期）。**别在 Android 侧用**。
 */
@Composable
internal fun <T> rememberSafeFlowState(key: String, initial: T, make: () -> Flow<T>): T {
    val flow = remember(key) { runCatching { make() }.getOrNull() }
    var value by remember(key) { mutableStateOf(initial) }
    LaunchedEffect(flow) {
        val source = flow ?: return@LaunchedEffect
        runCatching { source.collect { value = it } }
            .onFailure { Logger.w(TAG_BOOT, "$key 采集失败（保持默认值）：$it") }
    }
    return value
}
