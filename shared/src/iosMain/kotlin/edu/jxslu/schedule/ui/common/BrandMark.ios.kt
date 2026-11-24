package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * [BrandMark] 的 iOS 实现：空。
 *
 * iOS 侧没有 `ic_brand_*` 这套品牌位图（它们在 `app/src/main/res` 里，是 Android 的素材），
 * 所以头像位不渲染。卡片其余部分不受影响：标题、状态胶囊、说明行、动作按钮都在
 * commonMain 里，与平台无关。
 *
 * 要做的是给 iOS 补一套品牌素材（或换成矢量），届时这条缝整体作废、参数改回 `Painter?`；
 * 不要在这里画一个纯色占位块——那会让两端「我是谁」这张卡的观感对不上。
 */
@Composable
actual fun BrandMark(resId: Int, size: Dp) {
    // 有意留空，见 KDoc
}
