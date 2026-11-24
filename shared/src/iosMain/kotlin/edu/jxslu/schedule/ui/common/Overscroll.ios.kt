package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * iOS 侧：[DisableStretchOverscroll] 直接跑内容——没有可关的东西。
 *
 * CMP 1.7.3 的 iOS 滚动容器不做 Android 那种 stretch 拉伸回弹，那条报障的成因在 iOS 上
 * 不存在。等哪天 iOS 也出现同样的拉伸卡顿，再在这个 actual 里补对应开关。
 */
@Composable
actual fun DisableStretchOverscroll(content: @Composable () -> Unit) {
    content()
}
