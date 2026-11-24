package edu.jxslu.schedule.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android 侧：[AppBackHandler] 直接转调 `androidx.activity.compose.BackHandler`。
 *
 * 转调而不是复制逻辑：`BackHandler` 依赖 Activity 的 `OnBackPressedDispatcher`，
 * 复制一份等于把 androidx 的实现搬进本仓。这里只做一层名字转发，行为与搬迁前完全一致。
 */
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
