package edu.jxslu.schedule.ui.qzxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.data.qzxy.QzxyBill
import edu.jxslu.schedule.domain.BillRange
import edu.jxslu.schedule.domain.billMoneyText
import edu.jxslu.schedule.domain.groupBillsByDay
import edu.jxslu.schedule.domain.parseBillCents
import edu.jxslu.schedule.domain.parseBillTime
import edu.jxslu.schedule.domain.sumCents
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppCardDivider
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.PageEmpty
import edu.jxslu.schedule.ui.common.PageRefreshAction
import edu.jxslu.schedule.ui.common.PageScaffold
import edu.jxslu.schedule.ui.common.SettingsSection

/**
 * 趣智消费记录页（2026-11-05，DESIGN §4.30）。
 *
 * 从趣智校园页的「消费记录」入口卡进来。数据仍是服务端**按月**账单，页面把它摊成
 * 「按时段筛 + 按自然日分组」的流水：今日 / 本周 / 本月 / 全部，金额只认服务端返回。
 *
 * 为什么不放回主页：一个月二三十笔很常见，全展开会把主页拉成两三屏；主页只留一句
 * 「本月 N 笔 · ¥X」的入口摘要。
 */
@Composable
fun QzxyBillScreen(
    onBack: () -> Unit,
    viewModel: QzxyBillViewModel = viewModel(
        factory = QzxyBillViewModel.factory(LocalContext.current),
    ),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val groups = remember(state.months, state.billsByMonth, state.range) {
        groupBillsByDay(state.visibleBills)
    }
    val totalCents = remember(groups) { groups.sumOf { it.totalCents } }

    PageScaffold(
        title = "消费记录",
        onBack = onBack,
        snackbar = snackbar,
        actions = { PageRefreshAction(busy = state.loading, onClick = viewModel::refresh) },
        loading = state.loading && state.months.isEmpty(),
        loadingText = "正在读取消费记录",
        // 空态**不交给 PageScaffold 的 empty 槽**：那一档会把整个内容区换成居中提示，
        // 时段 chips 也一起消失——用户选了「今日」看到「今天没有记录」就切不回去了。
        // 这里把空态当列表里的一张卡渲染，chips 与合计始终在。
    ) {
        item { RangeRow(selected = state.range, onSelect = viewModel::setRange) }
        item { SummaryCard(count = state.visibleBills.size, totalCents = totalCents, range = state.range) }
        state.error?.let { message ->
            item { ErrorCard(message = message, onRetry = viewModel::refresh) }
        }
        if (state.error == null && !state.loading && state.months.isNotEmpty() && groups.isEmpty()) {
            item { EmptyCard(title = emptyTitle(state.range), body = emptyBody(state.range)) }
        }
        groups.forEach { group ->
            item(key = "head-${group.key}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = billMoneyText(group.totalCents),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
            item(key = "day-${group.key}") {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    group.bills.forEachIndexed { index, bill ->
                        if (index > 0) AppCardDivider()
                        BillRow(bill)
                    }
                }
            }
        }
        if (state.range == BillRange.ALL && state.canLoadEarlier) {
            item {
                TextButton(
                    onClick = viewModel::loadEarlier,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.loading) "读取中…" else "加载更早的月份")
                }
            }
        }
        item {
            Column {
                state.loadedRangeText?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
                FooterDisclaimer(
                    body = "**金额以趣智为准**：这里只列服务端账单，预扣与结算到账的口径差异见趣智页的" +
                        "「待核对」提示；充值与退款由学校运营方负责。",
                )
            }
        }
    }
}

/** 时间段选择：今日 / 本周 / 本月 / 全部（本地过滤，不发请求）。 */
@Composable
private fun RangeRow(selected: BillRange, onSelect: (BillRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BillRange.entries.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(item.label) },
            )
        }
    }
}

/** 汇总卡：当前时段合计 + 笔数（金额只来自服务端账单，不做预测）。 */
@Composable
private fun SummaryCard(count: Int, totalCents: Long, range: BillRange) {
    SettingsSection(title = "合计") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = billMoneyText(totalCents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${range.label} · $count 笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    SettingsSection(title = title) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    SettingsSection(title = "读取失败") {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试") }
    }
}

/** 单笔账单行：描述 + 时刻，右侧金额。 */
@Composable
private fun BillRow(bill: QzxyBill) {
    val time = parseBillTime(bill.consumeDate)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = bill.description ?: bill.deviceDescription ?: "热水消费",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = time?.let { "%02d:%02d".format(it.hour, it.minute) }
                    ?: (bill.consumeDate ?: "时间未知"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = billMoneyText(parseBillCents(bill.consumeMoney)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun emptyTitle(range: BillRange): String = when (range) {
    BillRange.TODAY -> "今天还没有消费记录"
    BillRange.WEEK -> "本周还没有消费记录"
    BillRange.MONTH -> "本月还没有消费记录"
    BillRange.ALL -> "还没有消费记录"
}

private fun emptyBody(range: BillRange): String = when (range) {
    BillRange.TODAY -> "换到「本周」或「本月」看看"
    BillRange.WEEK -> "换到「本月」看看，或下拉刷新"
    BillRange.MONTH -> "下个月再来看，或者下拉刷新"
    BillRange.ALL -> "下拉刷新试试；默认只加载最近两个月，更早的点「加载更早的月份」"
}
