package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 账号页的公共骨架（2026-11-05 统一）：身份卡 + 三列指标。
 *
 * 快趣 / 趣智校园 / 胖乖生活 / U净 四个账号页原本各写一套「余额一行大字 + 下面一串小字」，
 * 数字与标签同权重、页面里 5–7 张卡彼此等权。改版后统一成：
 * 身份卡（我是谁）→ 三列指标（我还有多少）→ 主操作卡（现在能做什么）→ 记录 / 设置 → 页尾。
 *
 * 这几个组件只负责版式，不碰业务：数值怎么算、什么时候刷新，仍由各页自己的 ViewModel 决定。
 */

/**
 * 身份卡：44dp 圆形图标底座 + 标题 + 可选「已登录」胶囊 + 一行说明 + 可选全宽动作按钮。
 *
 * 标题给账号标识（手机号掩码 / 学校名），说明给凭证口径（「Token 仅存本机」）；
 * [trailing] 放在说明行尾，用来挂「眼睛」这类就地切换显示的小按钮。
 */
@Composable
fun AccountHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    /** 标题右侧的胶囊（「已登录」等）；null 不显示。 */
    pill: String? = null,
    /** 说明行尾的自定义内容（如手机号显隐的眼睛）。 */
    trailing: (@Composable () -> Unit)? = null,
    /** 卡片底部的全宽动作（如「充值到趣智账户」）；null 不占位。 */
    action: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (pill != null) {
                        Spacer(Modifier.width(6.dp))
                        HeroPill(pill)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    trailing?.invoke()
                }
            }
        }
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

/** 身份卡标题旁的状态胶囊（「已登录」）：主色 10% 底 + labelSmall。 */
@Composable
fun HeroPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 身份卡底部的全宽动作按钮（充值这类"离开本页"的操作）。 */
@Composable
fun HeroActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 三列指标一行：列间 8dp，各列自己 `Modifier.weight(1f)`。
 *
 * 不用分割线——三列的标签与数值已经足够分组；加了竖线在小屏上会把「¥12.40」挤成两行。
 */
@Composable
fun MetricsRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 一列指标：小标签 + 数值 + 一行说明。
 *
 * 标签给「这一列是什么」，说明给「数字怎么来的」（余额 / 共 260 次 / 昨天 22:10）；
 * 数值用 titleLarge 与说明拉开层级，三列并排时超长一律省略号（不换行，避免三列高低不齐）。
 */
@Composable
fun MetricCell(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
