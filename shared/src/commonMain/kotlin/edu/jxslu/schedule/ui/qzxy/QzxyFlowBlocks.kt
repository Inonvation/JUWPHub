package edu.jxslu.schedule.ui.qzxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.QzxyWateringFormat

/**
 * 趣智校园流程三态（工作 / 结算 / 失败）的公共块（2026-11-13）。
 *
 * 页面（`QzxyScreen`）与今日页入口面板（`QzxyEntrySheet`）各写过一份同构实现，2026-11-06
 * 审计记的「状态机 + 4 区块重复」。合并只抽**结构与文案**；两边确实存在的观感差异
 * （面板空间小、页面区块标题更轻）用参数保留，不在这次顺手统一——
 * 统一观感要单独点验，不属于消除重复。
 *
 * 三块的职责边界：只渲染传入的状态与回调，不读 ViewModel、不做状态判定。
 * 页面侧把 `viewModel` 动作包成回调，面板侧把 `onStart`/`onResend` 等直接传入。
 */
@Composable
internal fun QzxyWorkingBlock(
    step: String,
    /** 面板里显示不定态进度条（那几秒唯一在动的东西）；整页里不显示。 */
    showProgressBar: Boolean,
    topPadding: Dp,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(Modifier.padding(top = topPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = step,
                style = MaterialTheme.typography.bodyMedium,
                color = onSurface.copy(alpha = 0.75f),
            )
        }
        if (showProgressBar) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
        // 给个时间预期：这一段要连蓝牙再下单，几秒内没动才是异常
        Text(
            text = "连接热水器并下单，通常几秒",
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun QzxySettlementBlock(
    settlement: QzxySettlement,
    /** 余额文案（不带 `¥`）；null = 不显示余额那一段。 */
    balanceText: String?,
    titleStyle: TextStyle,
    topPadding: Dp,
    /** 「本次用水 · 余额」那一行的上间距：页面无、面板 2dp（合并前就不同，保留）。 */
    partsTopPadding: Dp,
    onDone: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Text(
        text = "已结束用水",
        style = titleStyle,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = topPadding),
    )
    Text(
        text = QzxyWateringFormat.money(settlement.consumeMoneyMilli),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
    val parts = buildList {
        settlement.durationMillis?.let { add("本次用水 ${QzxyWateringFormat.duration(it)}") }
        balanceText?.let { add("余额 ¥$it") }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = partsTopPadding),
        )
    }
    settlement.note?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    Text(
        text = "金额来自服务端账单，不是本地推算",
        style = MaterialTheme.typography.bodySmall,
        color = onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 6.dp),
    )
    OutlinedButton(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(48.dp),
    ) {
        Text("完成")
    }
}

@Composable
internal fun QzxyFailureBlock(
    failure: QzxyFlowState.Failed,
    /** 主按钮可点：页面 = `state.hasUsableDevice`，面板 = 调用方同口径传入。 */
    canRetry: Boolean,
    /** 次要按钮「清除设备记录」可点：页面 = `state.selected != null`。 */
    canClear: Boolean,
    topPadding: Dp,
    onRetry: () -> Unit,
    onResend: () -> Unit,
    onClear: () -> Unit,
    onSettle: () -> Unit,
    onClearAndOpen: () -> Unit,
    onDetail: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Text(
        text = failure.reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = topPadding),
    )
    failure.detail?.let { detail ->
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.6f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Button(
        // 开阀被「未结算的老记录」挡住时，主出口是先去结算——提示让点「结束用水」，
        // 这里就必须真的给出那个按钮（旧版只有「重试开阀」，用户只能卡住）
        onClick = {
            when {
                failure.clearThenOpen -> onClearAndOpen()
                failure.resendRecovery -> onResend()
                failure.settleRecovery -> onSettle()
                else -> onRetry()
            }
        },
        enabled = canRetry,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(48.dp),
    ) {
        Text(
            when {
                failure.clearThenOpen -> "清除并继续开阀"
                failure.resendRecovery -> "重发开阀数据"
                failure.settleRecovery -> "结算设备记录"
                else -> "重试开阀"
            },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onClear,
            enabled = canClear,
            modifier = Modifier
                .weight(1f)
                .height(42.dp),
        ) {
            Text(if (failure.clearThenOpen) "只清除记录" else "清除设备记录")
        }
        OutlinedButton(
            onClick = onDetail,
            modifier = Modifier
                .weight(1f)
                .height(42.dp),
        ) {
            Text("详情")
        }
    }
}
