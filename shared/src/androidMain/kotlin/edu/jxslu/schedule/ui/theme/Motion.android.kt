package edu.jxslu.schedule.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * [rememberPlatformAnimationsEnabled] 的 Android 实现：读 `Settings.Global.ANIMATOR_DURATION_SCALE`。
 *
 * 行为与搬进 `:shared` 之前逐字一致：`remember(context)`，换 Context 才重读；
 * 读不到（权限/ROM 差异）按「动画开着」兜底，宁多动不少交代。
 */
@Composable
internal actual fun rememberPlatformAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}
