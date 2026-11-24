package edu.jxslu.schedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import edu.jxslu.schedule.domain.ThemePalette

@Composable
fun JuwTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: ThemePalette = ThemePalette.SkyBlue,
    content: @Composable () -> Unit,
) {
    // 优先级：动态取色（Material You，默认关）> 内置配色 > 晴空蓝兜底。
    // 动态取色是平台缝：Android 12+ 读壁纸主色，iOS 没有这套机制、返回 null 直接落到内置配色。
    val colorScheme = (if (dynamicColor) platformDynamicColorScheme(darkTheme) else null)
        ?: paletteScheme(palette, darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = JuwTypography,
    ) {
        // 窗口级默认内容色。`Text` / `Icon` 不写 `color` / `tint` 时读的就是它，
        // 而它唯一的正式提供方是 M3 的 `Surface`（`Scaffold` / `Card` / `Button` 内部都有）。
        // 页面根节点只用 `Modifier.background` 画底色时没人提供，`Text` 会落回
        // `LocalContentColor` 的默认值**纯黑**——浅色主题下看不出，深色主题下正文糊进背景
        // （2026-09-29 首启引导页的标题就是这么丢的）。这里给整个窗口兜一个 onBackground，
        // 各 `Surface` / `Scaffold` 仍按自己的底色覆盖，互不冲突。
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            // 语气色（成功/警告）M3 无对应角色，随深浅色在这里统一提供（提示卡用，见 AppNotice）
            ProvideSemanticColors(darkTheme) { content() }
        }
    }
}

/**
 * 动态取色（Material You）的平台缝（iOS 移植 P2）。
 *
 * Android 12+ 用 `dynamicLightColorScheme` / `dynamicDarkColorScheme` 从壁纸取主色；
 * iOS 没有对应机制，`actual` 恒返回 null，[JuwTheme] 就落到用户选的内置配色。
 *
 * 返回值可空是关键：判断「这台设备支不支持动态取色」不该由公共代码问系统版本，
 * 那是平台自己的事。
 */
@Composable
internal expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?
