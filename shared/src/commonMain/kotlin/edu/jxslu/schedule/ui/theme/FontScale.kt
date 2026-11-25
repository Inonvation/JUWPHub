package edu.jxslu.schedule.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import edu.jxslu.schedule.domain.FontScalePolicy

/**
 * 字体缩放锁定（「固定字体大小」）：把 Compose 树的 `fontScale` 钉在
 * [FontScalePolicy.effectiveFontScale] 的结果上（锁定档固定 0.9×）。
 *
 * ## 为什么在共享层
 *
 * 2026-11-26 之前这份实现只在 `:app` 的 `MainActivity` 里（`private fun ProvideFixedFontScale`），
 * iOS 根用的是无参 `JuwTheme { }`，于是 iOS 上全站 `sp` 恒 **1.0×**——比 Android 大 11%，
 * 而「设置 → 外观 → 固定字体大小」这个开关在 iOS 上是死的。函数搬到这里之后两端共用一份，
 * [FontScalePolicy] 那条「落点只有一处」仍然成立：**唯一实现**在这里，两个根各调一次。
 *
 * ## 只覆盖 `fontScale`
 *
 * 密度（dp/px）原样透传，图标、间距、命中区、自适应行高等一切按 dp 的几何都不受影响；
 * `sp` 的换算从此与系统「字体大小」设置脱钩。弹层 / 底部弹层 / 下拉菜单是同一合成树的
 * 子组合，继承这层 `LocalDensity`，无需各自处理。
 *
 * 未锁定时与系统值恒等，不额外包一层 Provider（避免无谓的 `Density` 实例）。
 */
@Composable
fun ProvideFixedFontScale(locked: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = FontScalePolicy.effectiveFontScale(locked, density.fontScale)
    if (scale == density.fontScale) {
        content()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, scale),
        ) {
            content()
        }
    }
}
