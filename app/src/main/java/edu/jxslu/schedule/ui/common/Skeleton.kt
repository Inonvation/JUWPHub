package edu.jxslu.schedule.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.ui.theme.rememberAnimationsEnabled

/**
 * 骨架占位块：淡色底上扫过一道高光的圆角块（shimmer 流光）。
 *
 * 用在「加载态与成品同几何」的地方（付款码页的码位、生活页流水卡的行）。全项目骨架只有
 * 这一份实现，别在各页再写一份 `rememberInfiniteTransition`。此前是透明度呼吸
 * （700ms、0.25→0.5），2026-09-26 应用户反馈换成流光：块不动、光在动，等待感更轻。
 *
 * 动画值在 [drawBehind] 里读：每帧只触发重绘、不触发重组，一屏十几个占位块同时扫也
 * 不额外花重组。
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    // 系统「移除动画」开启时不铺流光：`rememberInfiniteTransition` 不读
    // `ANIMATOR_DURATION_SCALE`，留着它用户关掉动画后仍有一道光在一屏占位块上来回扫。
    // 静止态保留底色块——骨架交代的是"这里将来会有什么"，不靠动效。
    //
    // 拿的是 **State 而不是值**：下面 `drawBehind` 里再 `.value`，读发生在绘制阶段，
    // 每帧只重绘、不重组（一屏十几个占位块同时扫也不额外花重组，这是原实现的关键性质）。
    val sweep: State<Float>? =
        if (rememberAnimationsEnabled()) {
            rememberInfiniteTransition(label = "skeletonShimmer").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "skeletonSweep",
            )
        } else {
            null
        }
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(base)
            .drawBehind {
                val progress = sweep?.value ?: return@drawBehind
                // 高光带从左画外扫到右画外：restart 的跳回落在画外，循环无缝。
                // 带宽不小于 24dp——小到图标位（18dp）也有一次完整的光扫过
                val band = maxOf(size.width * 0.6f, 24.dp.toPx())
                val x = (size.width + band) * progress - band
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(x, 0f),
                        end = Offset(x + band, 0f),
                    ),
                )
            },
    )
}

/** 进行中行首的呼吸圆点：只改透明度，动画值在绘制阶段读，不触发重组。 */
@Composable
fun BreathingDot(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 8.dp,
) {
    // 同上：关掉动画时给常亮静止点（透明度 1），不留一条无限呼吸。
    // 读 State 在绘制阶段完成，不触发重组。
    val alpha: State<Float>? =
        if (rememberAnimationsEnabled()) {
            rememberInfiniteTransition(label = "breathingDot").animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "breathingDotAlpha",
            )
        } else {
            null
        }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha?.value ?: 1f }
            .background(color, CircleShape),
    )
}

/**
 * 行内一根骨架条：包在**整行行高**的盒子里垂直居中，条高 = 行高 × [SKELETON_LINE_RATIO]。
 * 行高走 [lineHeightDp]（跟随当前生效的字体缩放），不写死 dp。
 */
@Composable
fun SkeletonLine(
    style: TextStyle,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    widthFraction: Float = 1f,
    centered: Boolean = false,
) {
    val line = lineHeightDp(style)
    Box(
        modifier = modifier.height(line),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
    ) {
        val box = Modifier.height(line * SKELETON_LINE_RATIO)
        SkeletonBox(
            modifier = if (width != null) box.width(width) else box.fillMaxWidth(widthFraction),
            shape = RoundedCornerShape(4.dp),
        )
    }
}

/** 骨架条占行盒高的比例：一行文字的字形大约只画行盒中间的六成。 */
private const val SKELETON_LINE_RATIO = 0.62f
