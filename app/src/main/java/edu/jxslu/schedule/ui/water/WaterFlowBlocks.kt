package edu.jxslu.schedule.ui.water

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.Money
import edu.jxslu.schedule.domain.MoneyFormat
import edu.jxslu.schedule.domain.UnlockResult
import edu.jxslu.schedule.domain.calculateActualCost
import edu.jxslu.schedule.domain.cashPaidAmount
import edu.jxslu.schedule.domain.ticketPaidAmount

/**
 * 开水流程「待机 / 预检 / 出水 / 结算」四块的公共实现（2026-11-13）。
 *
 * 页面（`WaterScreen.PageFlowStatus`）与今日页面板（`WaterEntrySheet.SheetFlowStatus`）
 * 各写过一份，2026-11-06 审计记的「~450 行已分叉的重复」。两边真正不同的只有三处观感，
 * 用参数保留、没有顺手统一：
 * - 待机块：面板多一行「确保积分充足」（2026-10-08 用户要求，页面当时没加）；
 * - 出水块：进度条上间距 4dp（面板）/ 8dp（页面）；
 * - 失败块：布局差异大（面板竖排、页面横排），本次不抽，各留各的。
 *
 * 失败块之外的四块只渲染传入状态与回调，不读 ViewModel、不做状态判定。
 */
@Composable
internal fun WaterIdleBlock(
    usePoints: Boolean,
    onToggleUsePoints: () -> Unit,
    /** 面板显示「确保积分充足」；页面当时没有这行，保留差异。 */
    showPointsHint: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("使用积分抵扣", style = MaterialTheme.typography.bodyMedium)
            Text(
                "关闭后开水不消耗积分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            // 2026-10-08 用户要求：「确保积分充足」——积分充足才会真正抵扣；
            // 积分不足开水照常可用，只是不抵扣积分（用户订正，别写成「会失败」）
            if (showPointsHint) {
                Text(
                    "确保积分充足",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        Switch(checked = usePoints, onCheckedChange = { onToggleUsePoints() })
    }
}

@Composable
internal fun WaterPreCheckingBlock(step: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            step,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
internal fun WaterWorkingBlock(
    step: String,
    elapsedSeconds: Int,
    /** 进度条上间距：面板 4dp、页面 8dp（合并前就不同，保留）。 */
    progressTopPadding: Dp,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "正在出水 ${waterClock(elapsedSeconds)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${(WaterViewModel.AUTO_SETTLE_SECONDS - elapsedSeconds).coerceAtLeast(0)} 秒后自动关闭",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.height(progressTopPadding))
        LinearProgressIndicator(
            progress = {
                (elapsedSeconds.toFloat() / WaterViewModel.AUTO_SETTLE_SECONDS).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            step,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

/** 成功后的结算明细块（用户拍板口径）：本次花费大行 + 支付构成副行 + 「订单详情 ›」。 */
@Composable
internal fun WaterSettlementBlock(result: UnlockResult, onShowDetail: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "花费 ¥${calculateActualCost(result)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "订单详情 ›",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onShowDetail),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            // 支付构成副行（DESIGN §4.10 账单口径）：小票是账户余额支付，
            // 现金实付 0.00 只代表没走在线支付，不能当作「本次花费 0」
            waterCostBreakdownText(result),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

/** 结算明细副行（DESIGN §4.10 账单口径，成功卡与面板共用这一份）。 */
internal fun waterCostBreakdownText(result: UnlockResult): String {
    val parts = buildList {
        ticketPaidAmount(result)?.takeIf { it > Money.ZERO }
            ?.let { add("小票支付 ${MoneyFormat.fromYuan(it)}") }
        result.integralCost.takeIf { it != "-" }?.let { add("积分抵扣 $it") }
        cashPaidAmount(result)?.takeIf { it > Money.ZERO }?.let { cash ->
            add("${result.payTypeName ?: "现金"} ${MoneyFormat.fromYuan(cash)}")
        }
    }
    return parts.joinToString(" · ").ifBlank { "开水成功" }
}

/** `mm:ss`（页面与面板同一实现，合并前各有一份 `clock` / `formatClock`）。 */
internal fun waterClock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
