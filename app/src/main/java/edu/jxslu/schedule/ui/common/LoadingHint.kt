package edu.jxslu.schedule.ui.common

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.ui.theme.rememberAnimationsEnabled
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Droplet

/**
 * 统一加载态（DESIGN §3.2「加载态」）：水滴呼吸 + 文案，水平垂直双居中。
 *
 * 替代两种旧形态——裸 `CircularProgressIndicator`（无品牌感、与页面观感脱节）
 * 和「文字版 EmptyHint 当加载态用」（不居中、无加载感）。呼吸动画替代旋转：
 * 水滴意象缓缩放 + 微透明度起伏，动效克制（无旋转、无跳变），
 * 仍明确传达「正在工作」。
 *
 * 居中口径：内容在 [modifier] 决定的容器内双居中。全屏态传
 * `Modifier.fillMaxSize()`；区块态传 `fillMaxWidth() + padding`。
 * 注意外层容器若在 Column 里用 `weight(1f)` 占位，必须同时 `fillMaxWidth()`
 * （Box 默认 wrap 内容、crossAxis 左对齐——今日页加载态左偏的根因）。
 */
@Composable
fun LoadingHint(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    // 系统「移除动画」开启时给静止态（水滴不呼吸）。
    // `rememberInfiniteTransition` 不读 `ANIMATOR_DURATION_SCALE`，不短路的话用户明明
    // 关掉了动画，屏幕上还有一滴水一直在呼吸，而且它每帧都在占帧回调。
    // 只关无限动画、不关一次性过渡：加载态的存在感靠文案，不靠动效。
    //
    // 拿到的是 **State 而不是值**：下面 `graphicsLayer` 里再 `.value`，读发生在绘制阶段，
    // 只失效绘制、不触发重组。改成在组合期读值会让整个 LoadingHint 每帧重组一次
    // （原实现就是靠这一点省开销的，别退化）。
    val transition: InfiniteTransition? =
        if (rememberAnimationsEnabled()) {
            rememberInfiniteTransition(label = "loadingBreath")
        } else {
            null
        }
    val scale: State<Float>? = transition?.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loadingScale",
    )
    val alpha: State<Float>? = transition?.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loadingAlpha",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(
                imageVector = HugeIcons.Droplet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        val s = scale?.value ?: 1f
                        scaleX = s
                        scaleY = s
                        this.alpha = alpha?.value ?: 1f
                    },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 空态提示（原住 `CourseUi.kt`，2026-09-20 移入本文件：空态与加载态同属
 * 「页面暂态提示」，两组件共用调用方）。宽满容器、水平居中，**垂直不居中**——
 * 垂直定位由调用方的容器决定（今日页 `TodayCenteredShell` 负责双居中，
 * 成绩页包 `Arrangement.Center` 的 Column）。
 */
@Composable
fun EmptyHint(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
