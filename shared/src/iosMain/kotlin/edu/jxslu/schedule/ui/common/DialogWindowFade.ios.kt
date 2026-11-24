package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * [DialogWindowFadeEffect] 的 iOS 实现：空。
 *
 * iOS 上没有「弹窗窗口」这个概念——Compose Multiplatform 的 `Dialog` / `AlertDialog` 在 iOS
 * 上就是当前组合里的一个层，没有 window 可以挂 `windowAnimations`。现在这一侧弹窗是瞬现的，
 * 与 Android 用窗口动画补出来的效果不等价；真要统一观感，正确做法是在 iOS 侧给弹窗内容包一层
 * compose 的 `AnimatedVisibility`，而不是往这条缝里塞东西（那条路对 Android 会造成动画叠加）。
 *
 * 退场条件同 commonMain：material3 1.4.0 起弹窗自带进出场动画，这条缝整体作废。
 */
@Composable
actual fun DialogWindowFadeEffect() {
    // 有意留空，见 KDoc
}
