package edu.jxslu.schedule.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * [platformDynamicColorScheme] 的 Android 实现：Android 12（API 31）起读壁纸主色。
 *
 * 低于 31 返回 null，调用方回落到内置配色——与搬进 `:shared` 之前的
 * `dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` 等价。
 */
@Composable
internal actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
