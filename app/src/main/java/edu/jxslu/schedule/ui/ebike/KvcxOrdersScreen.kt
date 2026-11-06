package edu.jxslu.schedule.ui.ebike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.domain.RideRecord
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppInfoDialog
import edu.jxslu.schedule.ui.common.EmptyHint
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.InlineNoticeRow
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.MonthNavRow
import edu.jxslu.schedule.ui.common.MonthPickerDialog
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PageBackButton
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberRefreshHold
import edu.jxslu.schedule.ui.theme.semanticColors
import java.time.YearMonth

/**
 * 服务端历史订单（DESIGN §3.9 / §4.32，2026-11-05）。
 *
 * 数据源是快趣服务端的 `queryOrderList`——官方小程序「历史订单」页同一个接口
 * （`pages/order`：月份选择器 + 电单车/单车两个 tab）。**接口按「年 + 月 + 车型」查**，
 * 所以这里也是「选到哪个月拉哪个月」，不做全量预取；要看更早的订单就翻月份。
 *
 * 与快趣账号页的「最近骑行」分工：那个是本机记录（只记 App 里开的车，最多 100 条），
 * 这里是账号下的全部订单（微信里骑的、换手机之前的都在）。金额显示**不是账单**，
 * 以快趣为准——页尾一行常驻说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KvcxOrdersScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: KvcxOrdersViewModel = viewModel(factory = KvcxOrdersViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val thisMonth = remember { YearMonth.now() }
    var showMonthPicker by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<KqcxAuth.OrderItem?>(null) }
    val visible = state.visibleOrders
    // 有订单但被「仅显示有费用」滤空了：空态文案要说清是筛掉了，而不是这个月没骑车
    val filteredEmpty = state.onlyPaid && !state.orders.isNullOrEmpty() && visible.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史订单") },
                navigationIcon = { PageBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DeviceTabs(
                deviceType = state.deviceType,
                onSelect = {
                    haptics.tap()
                    viewModel.selectDevice(it)
                },
            )
            MonthNavRow(
                label = "${state.yearMonth.year} 年 ${state.yearMonth.monthValue} 月",
                onPrev = viewModel::prevMonth,
                onNext = viewModel::nextMonth,
                onPick = { if (state.months.isNotEmpty()) showMonthPicker = true },
                // 只往「有记录」的月份翻（2026-11-05 用户要求）：没有更早 / 更新的记录就置灰
                prevEnabled = state.olderMonth != null,
                nextEnabled = state.newerMonth != null,
                showThisMonth = state.yearMonth != thisMonth && thisMonth in state.months,
                onThisMonth = { viewModel.selectMonth(thisMonth) },
            )
            FilterRow(
                onlyPaid = state.onlyPaid,
                count = visible.size,
                totalCents = visible.sumOf { it.payMoneyCents ?: 0L },
                onToggle = {
                    haptics.tap()
                    viewModel.toggleOnlyPaid()
                },
            )
            // 指示器驻留口径见 ui/common/RefreshHold.kt（2026-11-05）：点亮只认 onRefresh，
            // busy 只续命——本页 loading 在「定月份 + 读缓存」两步之后才置真，直接接它会漏帧。
            val refreshHold = rememberRefreshHold(busy = state.loading && state.orders != null)
            PullToRefreshBox(
                // 首屏加载由下面的 LoadingHint 表达；只有「已有数据再刷新」才转下拉指示器
                isRefreshing = refreshHold.isRefreshing,
                onRefresh = {
                    refreshHold.begin()
                    haptics.tap()
                    viewModel.refresh()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    state.loading && state.orders == null -> LoadingHint(
                        title = "正在读取订单",
                        modifier = Modifier.fillMaxSize(),
                    )
                    state.error != null && state.orders == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyHint(
                            title = "订单读取失败",
                            body = state.error.orEmpty(),
                            actionLabel = "重试",
                            onAction = viewModel::refresh,
                        )
                    }
                    state.orders.isNullOrEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyHint(
                            title = "这个月没有订单",
                            body = "换个月份或车型看看。订单与金额都以快趣为准。",
                        )
                    }
                    filteredEmpty -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyHint(
                            title = "这个月没有收费订单",
                            body = "关掉「仅显示有费用」能看到全部 ${state.orders.orEmpty().size} 单。",
                            actionLabel = "显示全部",
                            onAction = viewModel::toggleOnlyPaid,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 刷新失败但手上还有上次的数据：不拿空态盖掉，顶部一行说明这次没刷上
                        state.error?.let { message ->
                            item(key = "refresh-error") {
                                InlineNoticeRow(message = message, tone = NoticeTone.Error)
                            }
                        }
                        // 稳定 key + animateItem：切「仅显示有费用」时被滤掉的卡片淡出、留下的补位，
                        // 不再整列硬跳；换月份时也是整批淡入淡出（与订单页自身的 Crossfade 同族）
                        items(visible, key = { it.listKey }) { order ->
                            OrderCard(
                                order = order,
                                onClick = { detail = order },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item {
                            FooterDisclaimer(
                                body = "数据来自快趣服务端，按月份查询；金额与结算以快趣为准。",
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            // 只列有记录的月份（2026-11-05 用户要求）：官方那套 2017-01 起的格子一百多个，基本都是空的
            months = state.months,
            selected = state.yearMonth,
            onDismiss = { showMonthPicker = false },
            onSelect = { month ->
                showMonthPicker = false
                viewModel.selectMonth(month)
            },
            title = "选择有订单的月份",
        )
    }

    detail?.let { order ->
        OrderDetailDialog(order = order, onDismiss = { detail = null })
    }
}

/**
 * 筛选行（2026-11-05 用户要求）：左边「仅显示有费用」开关，右边本页合计。
 *
 * 合计跟着筛选走——开了筛选就是「这个月实际花了多少」，看账单时最先想知道的就是这个数；
 * 没开时也照常显示，只是把 0 元单一起算进去（合计不变，因为 0 元单不贡献金额）。
 */
@Composable
private fun FilterRow(onlyPaid: Boolean, count: Int, totalCents: Long, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = onlyPaid,
            onClick = onToggle,
            label = { Text("仅显示有费用", style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.height(32.dp),
        )
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                text = "$count 单 · 合计 ${moneyText(totalCents)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

/** 车型分档：官方历史订单页的两个 tab（`deviceType` 1 / 2）。 */
@Composable
private fun DeviceTabs(deviceType: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        listOf(
            KvcxOrdersViewModel.DEVICE_EBIKE to "电单车",
            KvcxOrdersViewModel.DEVICE_BIKE to "单车",
        ).forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = deviceType == value,
                onClick = { onSelect(value) },
                // 不显示选中对勾：选中段自带填充色（与缴费账单页同口径）
                icon = {},
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

/**
 * 列表项的稳定 key。
 *
 * 为什么不能只写 `id.toString()`：`id` 是 `Long?`，缺了就是 `"null"`——一列里多行共用同一个 key
 * 会让 LazyColumn 直接抛「Key was already used」。这里按「服务端 id → 订单号 → 车号@结束时刻」
 * 逐级退：`KqcxAuth` 解析阶段已经把「车号和订单号都为空」的行 mapNotNull 掉了，所以退到底也不会是空串。
 *
 * **每一级必须带命名空间前缀**（2026-11-06 复核）：`id` 是 Long 转字符串、`orderNum` 是字符串，
 * 两者数值恰好相同时（如 `id=1234567890` 与 `orderNum="1234567890"`）会撞成同一个 key。
 */
private val KqcxAuth.OrderItem.listKey: String
    get() = id?.let { "id:$it" }
        ?: orderNum?.let { "no:$it" }
        ?: "car:$carNum@$endAt"

@Composable
private fun OrderCard(
    order: KqcxAuth.OrderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = order.carNum?.let { "车 $it" } ?: "订单 ${order.orderNum.orEmpty()}".trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = orderSubtitle(order),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = moneyText(order.payMoneyCents),
                    style = MaterialTheme.typography.titleMedium,
                    // 0 元单（卡券覆盖 / 进行中）压灰：一眼能和真花钱的单分开
                    color = if (order.hasFee) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
                Text(
                    text = order.statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(order),
                )
            }
        }
    }
}

@Composable
private fun OrderDetailDialog(order: KqcxAuth.OrderItem, onDismiss: () -> Unit) {
    // 单键详情（只有「关闭」）：走 ui/common 的 [AppInfoDialog]，套确认框会凭空多一个
    // 什么也不做的「取消」。内层 Column 只为 `spacedBy(6.dp)` 的行距，不承担滚动
    AppInfoDialog(title = "订单详情", onDismiss = onDismiss, dismissLabel = "关闭") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DetailRow("状态", order.statusText)
            DetailRow("车型", order.deviceLabel)
            DetailRow("车号", order.carNum ?: "—")
            DetailRow("订单号", order.orderNum ?: "—")
            DetailRow("结束时间", order.endAt?.trim().orEmpty().ifEmpty { "进行中" })
            DetailRow("骑行时长", order.totalSeconds?.let { RideRecord.durationText(it) } ?: "—")
            DetailRow("原价", moneyText(order.orderMoneyCents))
            order.dispatchMoneyCents?.takeIf { it > 0 }?.let { DetailRow("调度费", moneyText(it)) }
            DetailRow(if (order.unpaid) "应付" else "实付", moneyText(order.payMoneyCents))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 列表副行：结束时刻 · 时长 ·（有调度费时补一条）。 */
private fun orderSubtitle(order: KqcxAuth.OrderItem): String = buildString {
    append(orderTimeText(order.endAt))
    order.totalSeconds?.let { append(" · 骑行 ").append(RideRecord.durationText(it)) }
    val dispatch = order.dispatchMoneyCents ?: 0L
    if (dispatch > 0) append(" · 调度费 ").append(moneyText(dispatch))
}

/**
 * 结束时刻压成「11-05 18:32」：月视图已经说明了哪个月，再带年份只是挤占行宽。
 * 服务端给的是 `2026-11-05 18:32:11` 这种定长串；形态不符就原样显示，不猜格式。
 */
private fun orderTimeText(endAt: String?): String {
    val raw = endAt?.trim().orEmpty()
    if (raw.isEmpty()) return "进行中"
    return if (raw.length >= 16) raw.substring(5, 16) else raw
}

private fun moneyText(cents: Long?): String =
    cents?.let { "¥%.2f".format(it / 100.0) } ?: "—"

@Composable
private fun statusColor(order: KqcxAuth.OrderItem): Color = when {
    order.running -> MaterialTheme.colorScheme.primary
    order.unpaid -> MaterialTheme.semanticColors.warning
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
}
