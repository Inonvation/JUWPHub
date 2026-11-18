package edu.jxslu.schedule.ui.today

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.R
import edu.jxslu.schedule.domain.GuessPick
import edu.jxslu.schedule.domain.GuessSource
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.ui.common.AppCard
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ChevronRight

/**
 * 今日页「猜你想用」推荐卡（DESIGN §3.33 / §4.51）。
 *
 * 位置：收件箱列表**正上方**（不是收件箱卡内部）——它推荐的是功能入口，不是一条消息。
 * 空态也挂同一张卡，因为它只依赖时间 / 课程信号 / 本机订单，不依赖今天有没有课。
 *
 * 只做渲染：功能名、品牌图标、副文案全部从这里映射；**不读偏好、不算概率**。
 * 触感与点击涟漪由 [AppCard] 统一处理，调用方不要再包 clickable / haptics。
 *
 * 不做「换一个 / 不感兴趣」：多一个动作就多一套交互，设置里的总开关已经是出口。
 */
@Composable
fun GuessYouWantCard(
    pick: GuessPick,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accentReason = pick.source == GuessSource.CLASS_START ||
        pick.source == GuessSource.CLASS_END

    AppCard(
        onClick = onClick,
        onClickLabel = "打开${pick.target.label()}",
        // 页边距给在卡片外：点击命中区不包含外边距，与收件箱列表的 16dp 页边距对齐
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 品牌位图自带彩色底（与侧栏同一套 ic_brand_*），不要再套色块
            Image(
                painter = painterResource(pick.target.brandIcon()),
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "猜你想用",
                    style = MaterialTheme.typography.labelSmall,
                    color = primary.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pick.target.label(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = reasonText(pick),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (accentReason) primary else onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = HugeIcons.ChevronRight,
                contentDescription = null,
                tint = onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 副文案：来源决定语义，功能决定默认短句；上课信号带实时分钟数（由 ViewModel 每 tick 覆盖）。 */
private fun reasonText(pick: GuessPick): String = when (pick.source) {
    GuessSource.CLASS_START ->
        pick.minutes?.let { "还有 $it 分钟上课，骑车过去" } ?: "要上课了，骑车过去"

    GuessSource.CLASS_END ->
        pick.minutes?.let { "还有 $it 分钟下课，提前扫个车" } ?: "快下课了，提前扫个车"

    GuessSource.HABIT -> "你常在这个时间用"

    GuessSource.SCHEDULE,
    GuessSource.WEEKEND,
    GuessSource.WEEKEND_DEFAULT,
    GuessSource.SUNDAY_NOON -> pick.target.defaultReason()
}

/** 功能名：与侧栏「江水生活」段保持同一叫法。 */
private fun GuessTarget.label(): String = when (this) {
    GuessTarget.QIEKJ -> "胖乖生活"
    GuessTarget.KVCX -> "快趣出行"
    GuessTarget.QZXY -> "趣智校园"
    GuessTarget.UJING -> "U净"
    GuessTarget.WENQU -> "问渠"
}

/** 非习惯 / 非上课来源时的一句固定短句：说清这个入口能干什么，不冒充「为你猜的」。 */
private fun GuessTarget.defaultReason(): String = when (this) {
    GuessTarget.QIEKJ -> "接杯热水，刷一下就好"
    GuessTarget.KVCX -> "校园单车，扫一辆就走"
    GuessTarget.QZXY -> "回宿舍洗个热水澡"
    GuessTarget.UJING -> "看看洗衣房有没有空机"
    GuessTarget.WENQU -> "看看有没有新的志愿活动"
}

/** 品牌图标：与侧栏同一套位图（2026-10-21 用户口径）。 */
private fun GuessTarget.brandIcon(): Int = when (this) {
    GuessTarget.QIEKJ -> R.drawable.ic_brand_pangguai
    GuessTarget.KVCX -> R.drawable.ic_brand_kqcx
    GuessTarget.QZXY -> R.drawable.ic_brand_qzxy
    GuessTarget.UJING -> R.drawable.ic_brand_ujing
    GuessTarget.WENQU -> R.drawable.ic_brand_wenqu
}
