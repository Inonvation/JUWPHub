package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Android 侧：[DisableStretchOverscroll] 把 `LocalOverscrollConfiguration` 置 null。
 *
 * 这是 Android 上关 stretch overscroll 的正规开关（glow 与 stretch 都归它管），
 * 与搬迁前天气页里内联的那一行完全相同——只是搬到了平台侧，因为
 * `LocalOverscrollConfiguration` 只在 Android 变体里。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun DisableStretchOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        content()
    }
}
