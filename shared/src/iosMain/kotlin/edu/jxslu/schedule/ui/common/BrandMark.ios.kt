package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

/**
 * [BrandMark] 的 iOS 实现：按**合成句柄**取打包位图并画出来（2026-11-27）。
 *
 * ## 传进来的 `Int` 不是 Android 的 `R` 值
 *
 * 共享层这条缝的形参是 `Int`，Android 那半边传的是 `R.drawable.ic_brand_*`。iOS 没有 `R`，
 * 所以宿主（`IosNavigation` / `MainViewController` / `IosDrawer`）要传的是
 * [IosBrandAssets] 里那组**合成句柄**（`IosBrandAssets.KQCX` 等，从 `0x1A01` 起），
 * 图在 `iosApp/JUWPHub/BrandAssets/*.png`。两边只是共用同一个形参类型，取值空间不相干。
 *
 * ## 认不出的 id 渲染为空
 *
 * [IosBrandAssets.painter] 对表里没有的 id、对包里缺失或解不开的图都回 null，这里就什么都不画
 * （**不画占位块、不留底色**）：卡片本体（标题 / 胶囊 / 说明行 / 动作按钮）都在 commonMain，
 * 头像位缺席不影响阅读顺序。这与「平台动作可空回调」同一语义——没有的能力不假装有。
 *
 * ## 与 Android 实现逐条对齐
 *
 * 用 `Image` 而不是 `Icon(painter)`：`Icon` 会把位图整体染成 `LocalContentColor`（黑色块）。
 * `ContentScale.Fit` 保持素材自身比例，品牌图自带彩色底，不再套圆形底座。
 * 尺寸只认调用方给的 [size]，与 Android 那份一致。
 */
@Composable
actual fun BrandMark(resId: Int, size: Dp) {
    val painter = IosBrandAssets.painter(resId) ?: return
    Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
