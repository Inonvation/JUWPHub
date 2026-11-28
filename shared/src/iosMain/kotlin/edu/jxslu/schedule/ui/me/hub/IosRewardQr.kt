package edu.jxslu.schedule.ui.me.hub

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import edu.jxslu.schedule.ui.common.IosBrandAssets

/**
 * 支持开发页的赞赏码大图（iOS 侧，2026-11-27）。
 *
 * ## 它补的是哪一个槽位
 *
 * `SupportDevScreen` 的 `rewardQr: (@Composable (Modifier) -> Unit)?` 在 iOS 上一直传 null，
 * 所以这一页只有「感谢支持 + 说明卡」，中间那张赞赏码不出现。iOS 侧原本既没有
 * `painterResource` 也没有 `R`，现在图在 `iosApp/JUWPHub/BrandAssets/ic_reward_qr.png`。
 *
 * Android 侧的对应物是 `ui/me/hub/AboutPlatform.kt` 的 `REWARD_QR_RES` +
 * `painterResource`；这里走 [IosBrandAssets] 的解码缓存（同一张图整页只解一次）。
 *
 * ## 尺寸与比例由调用方定，这里只负责画
 *
 * `SupportDevScreen` 给的 Modifier 已经是 `fillMaxWidth() + aspectRatio(1f) + 圆角裁剪`，
 * 因为素材固定 1037×1037（比例 1:1）。所以这里**不要**再套 `aspectRatio` / `size`：
 * 多一层约束只会在窄屏上把图挤出框外。`ContentScale.Fit` 与 Android 那份同口径。
 *
 * 取不到图（包里没有 / 解不开）就什么都不画，与 `BrandMark` 的 iOS 实现同一语义：
 * 没有的能力不占位，不画一个灰框假装有。
 *
 * 用法（`IosNavigation.kt` 的 `SubpageScreen.SUPPORT_DEV` 那一行）：
 * `SupportDevScreen(onBack = pop, rewardQr = { IosRewardQr(it) })`。
 */
@Composable
internal fun IosRewardQr(modifier: Modifier = Modifier) {
    val painter = IosBrandAssets.painter(IosBrandAssets.REWARD_QR) ?: return
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
