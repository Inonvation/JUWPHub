package edu.jxslu.schedule.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * [platformDynamicColorScheme] 的 iOS 实现：**恒返回 null**。
 *
 * iOS 没有 Material You 那套「从壁纸取主色」的机制（iOS 26 的 Liquid Glass 是另一回事，
 * 且不对外提供调色板）。所以 iOS 侧 `dynamicColor` 开关形同关闭，主题永远走用户选的
 * 内置配色——这也正是该开关默认关着的原因：一个在 Android 上才有意义的选项，
 * 不该让 iOS 用户看到它没反应。
 *
 * 本分支已被 `.github/workflows/ios-compile.yml` 编译验证（2026-11-24）；运行期观感要等
 * P3 的 `iosApp` 出来才能看。
 */
@Composable
internal actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
