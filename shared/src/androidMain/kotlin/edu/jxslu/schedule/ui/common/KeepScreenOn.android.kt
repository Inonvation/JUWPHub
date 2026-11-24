package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * [KeepScreenOnEffect] 的 Android 实现：与搬移前 `WenquGrabScreen` 里那段
 * `LocalView.current.keepScreenOn = ...` **逐字等价**，只是把「离开组合复位」也包进来，
 * 调用点不必自己写 `onDispose`。
 *
 * 键是 [enabled] 而不是 `Unit`：检测中 ↔ 停止时要跟着切，不然要么一直亮、要么不亮。
 */
@Composable
actual fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
