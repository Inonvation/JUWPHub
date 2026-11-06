package edu.jxslu.schedule.ui.common

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 二级页「返回手势跟手」宿主（2026-11-06）。
 *
 * ## 为什么不用系统的跨窗口动画
 *
 * 原方案是 `WindowTransitions.enablePredictiveBackTransitions()`：API 34+ 由窗口用
 * `overrideActivityTransition` 声明过渡，指望系统把手势进度交给它。真机（Redmi K70 /
 * HyperOS 3 / Android 16）实测**这条不成立**：开发者选项里的「预测性返回动画」开着，
 * 二级页返回时既没有预览、也没有跟手，松手才切页。同机对比课表页的显示设置面板
 * （`DisplaySettingsOverlay`，用的是 `PredictiveBackHandler`）**跟手正常**——说明系统
 * 会把 `OnBackProgressed` 送进来，只是它自己不做跨窗口动画。
 *
 * 所以改成：**App 自己接管返回手势，用同一份进度驱动页面**。这条路不依赖 ROM 的
 * 窗口动画实现，能跟手的机器一定跟手。
 *
 * ## 代价（必须知道）
 *
 * 注册 [PredictiveBackHandler] 等于接管返回，系统那套跨窗口预览会被关掉。在真正支持
 * 预测性返回的机器（Pixel 等）上，这套是「用我们自己的动画换掉系统的」——所以这里只
 * 做平移 + 缩放 + 淡出，尽量贴近系统的观感。
 *
 * 页面在独立窗口里、窗口本身不透明，所以**看不到下面那一页**（那是系统窗口级动画才有的
 * 能力）。退场的落点是窗口底色，因此这里铺一层 `surface` 底，避免深色主题下透出浅色
 * 窗口背景。要让真页面露出来，得把窗口改成半透明（`windowIsTranslucent`），代价是
 * 所有页面必须自带不透明底，风险更大，暂不做。
 *
 * ## 用法
 *
 * ```kotlin
 * PredictiveBackExitHost(onExited = { finish() }) { exit ->
 *     SubpageContent(onBack = exit, ...)   // 顶栏返回也走同一个出口
 * }
 * ```
 *
 * [onExited] 在退场动画播完后调用（通常是 `finish()`），调用方**不要**再自己 `finish()`，
 * 否则动画会被截断。
 */
@Composable
fun PredictiveBackExitHost(
    onExited: () -> Unit,
    content: @Composable (exit: () -> Unit) -> Unit,
) {
    // API 33 及以下没有手势进度：`PredictiveBackHandler` 在那里只在松手时补发一个
    // progress = 1 的事件，照这套驱动会让页面**瞬间跳到位**，比原来的窗口滑出还差。
    // 所以低版本整体退回旧路径（窗口动画），这里只做透传。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        content(onExited)
        return
    }

    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // 只放行一次：手势提交与顶栏返回可能同时触发
    var exiting by remember { mutableStateOf(false) }

    // 统一出口：顶栏返回 / 手势提交都从这里走，保证两种退场长得一样
    val exit: () -> Unit = {
        if (!exiting) {
            exiting = true
            scope.launch {
                progress.animateTo(1f, tween(ExitMillis, easing = FastOutSlowInEasing))
                onExited()
            }
        }
    }

    PredictiveBackHandler { events ->
        if (exiting) return@PredictiveBackHandler
        try {
            events.collect { event -> progress.snapTo(event.progress) }
            // 提交：补完剩下的一段再退出，避免「滑到七成、页面突然消失」
            exiting = true
            progress.animateTo(1f, tween(ExitCommitMillis, easing = LinearEasing))
            onExited()
        } catch (c: CancellationException) {
            // 取消：弹回原位。spring 吸收松手瞬间的速度由 snapTo 的末值自然带出
            exiting = false
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            throw c
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    translationX = p * size.width * ExitTranslateFraction
                    val s = 1f - p * ExitScaleFraction
                    scaleX = s
                    scaleY = s
                    alpha = 1f - p * ExitFadeFraction
                },
        ) {
            content(exit)
        }
    }
}

/** 顶栏返回的退场时长：短一档，点按后不要有等待感。 */
private const val ExitMillis = 160

/** 手势提交后补完剩余位移的时长。 */
private const val ExitCommitMillis = 120

/** 退场位移：屏宽的比例。不做到 1.0——整页滑出去会露出大片窗口底色。 */
private const val ExitTranslateFraction = 0.28f

/** 退场缩放：跟着位移一起收一点，读成「页面在后退」。 */
private const val ExitScaleFraction = 0.04f

/** 退场淡出：与位移同步，弱化「滑到一半被截断」的硬边。 */
private const val ExitFadeFraction = 0.75f
