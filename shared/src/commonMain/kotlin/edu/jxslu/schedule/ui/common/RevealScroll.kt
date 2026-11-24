package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 展开长卡片时希望卡片下方能露出来的纵向空间（≈半屏）。 */
private val RevealSpace = 240.dp

/**
 * 上面那个空间的像素值。用组合里的 `LocalDensity` 换算，跟随系统字体缩放。
 */
@Composable
fun rememberRevealSpacePx(): Float = with(LocalDensity.current) { RevealSpace.toPx() }

/**
 * 展开长卡片时**只滚最小必要距离**，不把卡片顶到视口顶部（2026-11-19 收口，此前 5 份拷贝）。
 *
 * 卡头在屏幕下半部时点开，明细大多落在屏幕外，用户会怀疑「点了没反应」；这里只补足下方
 * 缺的那点空间，上限是卡头贴到视口顶部（再滚卡片就跑出屏幕，用户不知道自己在看哪一组）。
 *
 * 用 `animateScrollBy` 而不是 `animateScrollToItem`：展开动画期间卡片还在变高，
 * 后者会追着它抖；前者滚固定距离，不追。
 */
fun LazyListState.scrollToReveal(
    key: Any,
    revealSpacePx: Float,
    scope: CoroutineScope,
) {
    val layout = layoutInfo
    val info = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return
    val leftover = layout.viewportEndOffset - (info.offset + info.size)
    val need = revealSpacePx - leftover
    if (need <= 0f) return
    val maxScroll = (info.offset - layout.viewportStartOffset).coerceAtLeast(0).toFloat()
    val delta = minOf(need, maxScroll)
    if (delta > 0f) scope.launch { animateScrollBy(delta) }
}
