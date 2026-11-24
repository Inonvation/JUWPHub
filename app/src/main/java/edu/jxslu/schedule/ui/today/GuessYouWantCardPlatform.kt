package edu.jxslu.schedule.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import edu.jxslu.schedule.R
import edu.jxslu.schedule.domain.GuessTarget

/**
 * 「猜你想用」推荐卡的品牌位图（S16，2026-11-25）。
 *
 * ## 为什么留在这里
 *
 * `GuessYouWantCard` 已经搬进 `shared/commonMain`（iOS 也要用这张卡），而它原来直接读
 * `R.drawable.ic_brand_*` —— `R` 只有 Android 有。五张图是**位图素材**，不是平台能力，
 * 所以不做 `expect/actual`（那会多一条必然返回空实现的缝），改成页面传一个 `Painter?`。
 *
 * iOS 侧暂时没有这套品牌图，传 `null` 即可：卡片的功能名、副文案、箭头照常渲染，
 * 只是图标位不出现（见 `GuessYouWantCard` 的参数说明）。等 iOS 补上素材再接。
 *
 * 与侧栏（`ui/life/SideDrawer.kt`）用的是同一套 `ic_brand_*`，两处各写一张映射是有意的：
 * 侧栏那张带状态与余额、这张只给一个 30dp 图标，合并会把两套排版耦在一起。
 */
@Composable
fun rememberBrandIconPainter(target: GuessTarget): Painter =
    painterResource(brandDrawableOf(target))

/** 功能 → 品牌位图（2026-10-21 用户口径：与侧栏同一套位图）。 */
private fun brandDrawableOf(target: GuessTarget): Int = when (target) {
    GuessTarget.QIEKJ -> R.drawable.ic_brand_pangguai
    GuessTarget.KVCX -> R.drawable.ic_brand_kqcx
    GuessTarget.QZXY -> R.drawable.ic_brand_qzxy
    GuessTarget.UJING -> R.drawable.ic_brand_ujing
    GuessTarget.WENQU -> R.drawable.ic_brand_wenqu
}
