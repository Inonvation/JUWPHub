package edu.jxslu.schedule.ui.ebike

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.SubpageActivity
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.domain.EbikeCardExpiry
import edu.jxslu.schedule.domain.EbikeCardSavings
import edu.jxslu.schedule.domain.EbikeRideStats
import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.domain.capabilities
import edu.jxslu.schedule.ui.common.AccountHeroCard
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppInfoDialog
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.InlineNoticeRow
import edu.jxslu.schedule.ui.common.MetricCell
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PageScaffoldColumn
import edu.jxslu.schedule.ui.common.SessionExpiredNotice
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.postNotice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ChevronRight
import me.rerere.hugeicons.stroke.History
import me.rerere.hugeicons.stroke.InformationCircle
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 快趣账号页（DESIGN §4.32；**2026-11-05 改版**，效果稿 `docs/kvcx-account-redesign.html`）。
 *
 * 三段结构（原先是七块各自带一段说明，读起来像文档）：
 * 1. **身份卡**：账号掩码 + 「已登录」+ 凭证去向一句话。原「打开快趣出行」按钮已删
 *    （用户 2026-11-05 口径：本页只管账号，去向在侧栏与快趣出行页，不该在这儿再开一个口）；
 * 2. **快趣资产**：三列指标（骑行卡剩余 / 已用额度 / 余额）+ 到期警示 + 两条小结
 *    （骑行卡已省、超时费累计）+ 一句注记。会员折扣不显示（学校用不到）；
 * 3. **骑行记录**：服务端「历史订单」入口 + 本机记录（默认收起，点开看最近 8 条）。
 * 页尾只剩「退出登录」（带确认）与一行免责。
 *
 * **超时费累计**（2026-11-05 用户要求）：口径在 [EbikeRideStats]——次数数订单、单元数
 * 15 分钟；数据靠全量扫描服务端订单（[KvcxViewModel.refreshStats]），**只由「统计」按钮触发**，
 * 进页不发请求。
 *
 * **免责边界**：开锁 / 临时锁车 / 还车在工作台逐次确认后执行；支付与免押授权不做。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KvcxScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: KvcxViewModel = viewModel(factory = KvcxViewModel.factory(context))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 使用方式（DESIGN §3.9 / §4.32）：初值阻塞读一次，页面不该先按另一档画一帧
    val ebikePrefs = remember(context) { Graph.displayPrefs(context) }
    val useMode by ebikePrefs.ebikeUseMode.collectAsStateWithLifecycle(
        initialValue = remember { runBlocking { ebikePrefs.ebikeUseMode.first() } },
    )
    // 能力矩阵（DESIGN §3.9）：本页的账号 / 记录属于 App 内用车那一档
    val appRideEnabled = useMode.capabilities().inAppRide
    // 骑行卡节省与已用额度（2026-10-24 / 2026-11-05）：额度 − 服务端剩余次数；
    // 没有可参与推算的卡（无可用卡 / 只有无限次数卡 / 次数缺失）返回 null，指标位显示「—」。
    val cardSavings = state.assets?.let { assets ->
        EbikeCardSavings.computeFromCoupons(assets.coupons, System.currentTimeMillis())
    }

    // 账号方式下上次判定登录过期：进页说一句为什么退回登录表单（小程序方式没有账号，不提示）
    if (appRideEnabled) {
        SessionExpiredNotice(SessionService.Kqcx, state.loggedIn, snackbar)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // 事件流用 postNotice 单开协程发：收集协程不卡在「等这条提示收场」上，
                // 事件成串来时新的提示才能当场顶掉旧的（见 AppNotice.kt）
                is KvcxEvent.Notice -> scope.postNotice(snackbar, event.text, event.tone)
            }
        }
    }

    PageScaffoldColumn(title = "快趣账号", onBack = onBack, snackbar = snackbar) {
        when {
            // 兜底：本页入口在「小程序方式」下是隐藏的（校园服务里那一行按模式显隐），
            // 正常进不来。真进来了也不该给一个死胡同，所以说清去哪儿切
            !appRideEnabled -> InlineNoticeRow(
                message = "当前是「微信小程序」使用方式：本页的账号与本机记录都已收起，" +
                    "开车与还车在微信小程序里完成。要切换使用方式，请到快趣出行页标题栏的" +
                    "「使用方式」入口。",
                tone = NoticeTone.Info,
            )

            state.loggedIn -> {
                KvcxIdentityCard(mobile = state.accountMobile)
                AssetsSection(
                    state = state,
                    savings = cardSavings,
                    onRefreshAssets = viewModel::refreshAll,
                )
                OrdersSection(
                    onOpenOrders = { SubpageActivity.start(context, SubpageScreen.KVCX_ORDERS) },
                )
                LogoutRow(onLogout = viewModel::logout)
            }

            else -> {
                LoginSection(state, viewModel)
            }
        }
        // 边界说明只留一行（此前整段免责区与出码页重复，2026-09-29 收敛）
        FooterDisclaimer(
            body = "**非官方账号页**：开锁 / 锁车 / 还车逐次确认；" +
                "**计费与善后以快趣为准**，不提供支付与免押授权。",
        )
    }

    // 骑行卡券临近到期（前一天）的提醒：查资产时算出，关掉即落「已提醒」键（同一张卡只弹一次）
    state.cardExpiry?.let { coupons ->
        CardExpiryDialog(coupons = coupons, onDismiss = viewModel::dismissCardExpiry)
    }
}

// ── 身份卡 ──

/**
 * 身份卡（2026-11-05 改版）：头像 + 手机号掩码 + 「已登录」+ 凭证去向。
 *
 * 版式已抽到 [AccountHeroCard]（趣智 / 胖乖 / U净 同款）；这里只给快趣自己的三行字。
 */
@Composable
private fun KvcxIdentityCard(mobile: String) {
    AccountHeroCard(
        brandIcon = R.drawable.ic_brand_kqcx,
        title = maskMobile(mobile),
        subtitle = "凭证仅存本机 · 不进云备份",
        pill = "已登录",
    )
}

/** 手机号掩码：11 位取前 3 后 4，其它形态原样（不编数字）。 */
private fun maskMobile(mobile: String): String {
    val trimmed = mobile.trim()
    return if (trimmed.length == 11) {
        "${trimmed.take(3)}****${trimmed.takeLast(4)}"
    } else {
        trimmed.ifBlank { "已登录" }
    }
}

// ── 快趣资产（2026-09-30 新增，2026-11-05 改版） ──

/**
 * 「快趣资产」：三列指标 + 到期警示 + 两条小结 + 一句注记。
 *
 * 充值、退押、买卡都走微信收银台（App 不做支付，DESIGN §4.32 红线），所以这里只有"看"。
 * 进页自动查一次，失败保留已有内容、只在区块内给一行说明（不打扰：资产不是本页的主任务）。
 *
 * **指标口径**（2026-11-05 用户口径）：
 * - 骑行卡 = 可用卡的剩余次数合计（无限次数卡直接写「无限」）；
 * - 已用额度 = [EbikeCardSavings] 的 `usedRides`（额度 − 剩余次数，服务端计数），
 *   **取代原「会员卡 X 折」**——学校用不到会员，用户要求换成已用次数；
 * - 余额 = 充值 + 赠送（官方钱包页同口径）。
 *
 * **超时费累计**：口径在 [EbikeRideStats]（次数数订单、单元数 15 分钟），数据由「统计」
 * 按钮触发全量扫描；行内显示进度，点开看明细。
 */
@Composable
private fun AssetsSection(
    state: KvcxUiState,
    savings: EbikeCardSavings.Result?,
    onRefreshAssets: () -> Unit,
) {
    var showStatsDetail by remember { mutableStateOf(false) }
    var showSavingsDetail by remember { mutableStateOf(false) }
    val assets = state.assets
    val now = System.currentTimeMillis()
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("快趣资产", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "只读展示；充值、退押、买卡在快趣小程序完成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (state.assetsLoading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onRefreshAssets) {
                    Icon(
                        HugeIcons.Refresh,
                        contentDescription = "刷新资产",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (assets == null) {
            Text(
                text = if (state.assetsLoading) "正在查询资产…" else "资产查询失败，点右上角刷新再试一次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCell(
                    label = "骑行卡",
                    value = remainingValue(assets),
                    // 余额降到这行小字（2026-11-05 用户要求）：三列留给「还剩多少次 / 用了多少 / 什么时候到期」
                    caption = if (assets.balance == null) "余额查询失败" else "余额 ${balanceValue(assets)}",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = "已用额度",
                    value = savings?.let { "${it.usedRides} 次" } ?: "—",
                    // 无限次数卡没有「260 次」基准，不参与推算——写「无限次数卡」而不是「未开卡」
                    caption = when {
                        savings != null -> "共 ${savings.quotaTotal} 次"
                        assets.coupons.any { it.unlimited } -> "无限次数卡"
                        else -> "未开卡"
                    },
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = "到期",
                    value = expiryValue(assets),
                    caption = expiryCaption(assets, now),
                    modifier = Modifier.weight(1f),
                )
            }
            cardWarning(assets, now)?.let { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            savings?.let { result ->
                SumRow(
                    label = "骑行卡已省",
                    value = EbikeRideStats.moneyText(result.savedCents),
                    // 口径说明收进感叹号弹窗（2026-11-05 用户要求）：一行里塞不下换算方式
                    onInfo = { showSavingsDetail = true },
                )
            }
            StatsSumRow(
                state = state,
                onClick = { if (state.stats != null) showStatsDetail = true },
            )
        }
    }
    if (showStatsDetail) {
        val summary = state.stats
        val from = state.statsFrom
        val to = state.statsTo
        if (summary != null && from != null && to != null) {
            StatsDetailDialog(
                summary = summary,
                from = from,
                to = to,
                months = state.statsMonths,
                coverage = state.statsCoverage,
                scanLine = EbikeRideStats.scanLine(
                    scannedMonths = state.statsScannedMonths,
                    fetchedRows = state.statsFetchedRows,
                    uniqueOrders = state.statsUniqueOrders,
                ),
                onDismiss = { showStatsDetail = false },
            )
        }
    }
    if (showSavingsDetail && savings != null) {
        SavingsDetailDialog(result = savings, onDismiss = { showSavingsDetail = false })
    }
}

/**
 * 一列指标（2026-11-05 改版）：小标签 + 数值 + 一行说明，三列并排。
 * 版式已抽到 [MetricCell]（`ui/common/AccountCards.kt`），趣智 / 胖乖 / U净 同款。
 */

/** 骑行卡剩余次数：无限次数卡写「无限」，没有可用卡写「—」（不编 0）。 */
private fun remainingValue(assets: KqcxAuth.Assets): String {
    if (assets.coupons.any { it.unlimited }) return "无限"
    val remaining = assets.coupons.sumOf { it.remainFrequency ?: 0L }
    val usable = assets.coupons.any { it.remainFrequency != null }
    return if (usable) "$remaining 次" else "—"
}

/**
 * 到期列（2026-11-05 用户要求：余额那列换成到期日，显示到具体时间）。
 *
 * 数值只放 `MM-dd`——三列并排时 `titleLarge` 塞不下「2026-12-31」；时分放到下面那行小字里
 * （[expiryCaption]），跨年再补年份。取**最近一张可用卡**的到期时刻，多张卡时以最早的为准。
 */
private fun expiryValue(assets: KqcxAuth.Assets): String {
    val endAt = nearestCardEndAt(assets) ?: return "—"
    return MONTH_DAY_FORMAT.format(Instant.ofEpochMilli(endAt).atZone(ZoneId.systemDefault()))
}

/** 到期列的说明：`23:59 到期`（跨年补 `2027 年`）；没有可用卡写「无可用卡」。 */
private fun expiryCaption(assets: KqcxAuth.Assets, now: Long): String {
    val endAt = nearestCardEndAt(assets) ?: return "无可用卡"
    val zoned = Instant.ofEpochMilli(endAt).atZone(ZoneId.systemDefault())
    val time = TIME_OF_DAY_FORMAT.format(zoned)
    val currentYear = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).year
    return if (zoned.year != currentYear) "${zoned.year} 年 $time 到期" else "$time 到期"
}

/** 最近一张**可用**卡（有次数）的到期时刻；无限次数卡没有到期日时返回 null。 */
private fun nearestCardEndAt(assets: KqcxAuth.Assets): Long? =
    assets.coupons
        .filter { it.remainFrequency != null }
        .mapNotNull { EbikeCardExpiry.parseEndAt(it.endAt) }
        .minOrNull()

/** 余额 = 充值 + 赠送（官方钱包页同口径）；两项都缺写「—」。 */
private fun balanceValue(assets: KqcxAuth.Assets): String {
    val balance = assets.balance ?: return "—"
    if (balance.rechargeCents == null && balance.giftCents == null) return "—"
    val total = (balance.rechargeCents ?: 0L) + (balance.giftCents ?: 0L)
    return EbikeRideStats.moneyText(total)
}

/** 到期警示：已过期 / 3 天内到期才出一行（与卡券列表同一个窗口口径）。 */
private fun cardWarning(assets: KqcxAuth.Assets, now: Long): String? {
    val expired = assets.coupons.firstOrNull { coupon ->
        EbikeCardExpiry.parseEndAt(coupon.endAt)?.let { it <= now } == true
    }
    if (expired != null) {
        return "${expired.deviceLabel}骑行卡已过期；续费请到快趣小程序"
    }
    val soon = assets.coupons.firstOrNull { coupon ->
        val endAt = EbikeCardExpiry.parseEndAt(coupon.endAt)
        endAt != null && endAt - now <= CARD_EXPIRY_SOON_MS
    } ?: return null
    val endAt = EbikeCardExpiry.parseEndAt(soon.endAt) ?: return null
    return "${soon.deviceLabel}骑行卡 ${EbikeCardExpiry.remainingText(endAt, now)}；到期后按快趣规则计费"
}

/**
 * 小结一行：左标签、右金额、最右一个「口径说明」感叹号（2026-11-05 用户要求）。
 *
 * 原先这里是一行灰色小字（「估算 · 以快趣为准」），换算方式根本写不下；改成图标点开弹窗后，
 * 主行只留标签与金额，要看细节的点图标。
 */
@Composable
private fun SumRow(label: String, value: String, onInfo: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (onInfo != null) {
            IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
                Icon(
                    HugeIcons.InformationCircle,
                    contentDescription = "$label 的算法说明",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 「骑行卡已省」的算法说明（2026-11-05 用户要求：注释改成感叹号，点开看具体换算）。
 *
 * 数字来自 [EbikeCardSavings]：开卡 260 次额度、每次最多省 ¥0.80、开卡 ¥18.80。
 * 这只是估算——真实扣费在快趣那边，所以最后一句必须写清「以快趣为准」。
 *
 * 排版走 ui/common 的 [AppInfoDialog]：这是**单键详情**，套确认框会凭空多一个
 * 什么也不做的「取消」。内层 Column 只为 `spacedBy(6.dp)` 的行距，不承担滚动。
 */
@Composable
private fun SavingsDetailDialog(result: EbikeCardSavings.Result, onDismiss: () -> Unit) {
    AppInfoDialog(title = "骑行卡已省怎么算", onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "按开卡额度 ${EbikeCardSavings.CARD_QUOTA} 次、每次最多省 " +
                    "${EbikeRideStats.moneyText(EbikeCardSavings.PER_RIDE_CENTS)} 估算，不是快趣给的账单。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "已用 ${result.usedRides} 次 × " +
                    "${EbikeRideStats.moneyText(EbikeCardSavings.PER_RIDE_CENTS)} = " +
                    EbikeRideStats.moneyText(result.savedCents),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "开卡成本 ${EbikeRideStats.moneyText(EbikeCardSavings.CARD_PRICE_CENTS)} → " +
                    "净省 ${EbikeRideStats.moneyText(result.netCents)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (result.netCents < 0) {
                Text(
                    text = "还差 ${result.ridesToBreakEven} 次回本（每次按省 " +
                        "${EbikeRideStats.moneyText(EbikeCardSavings.PER_RIDE_CENTS)} 算）。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "只算骑行卡覆盖的订单；没开卡时按 15 分钟 0.80 元起扣的部分不计入。以快趣为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * 「超时费累计」一行（2026-11-05 用户要求：进页自动增量重算，去掉「重算」按钮）。
 *
 * 数据每次进页自动补：先用本地缓存出数（不联网），再增量拉最近两个月后重算；
 * 缓存为空时自动全量扫一次。所以这里**没有按钮**——有结果时整行可点开明细（右侧箭头提示可点），
 * 统计中只把进度写在副行里。
 */
@Composable
private fun StatsSumRow(
    state: KvcxUiState,
    onClick: () -> Unit,
) {
    val summary = state.stats
    val statsError = state.statsError
    val caption = when {
        state.statsLoading -> "正在同步…已查 ${state.statsScannedMonths} 个月"
        statsError != null && summary == null -> statsError
        summary != null -> buildString {
            append(EbikeRideStats.caption(summary))
            // 缓存可能只覆盖最近两个月（进页增量刷新），把起点写出来，别让部分数据看着像全量
            state.statsFrom?.let { append(" · 自 $it") }
        }
        else -> "正在同步…"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (summary != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("超时费累计", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Text(
            text = summary?.let { EbikeRideStats.moneyText(it.overtimeCents) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (summary != null) {
            Icon(
                HugeIcons.ChevronRight,
                contentDescription = "查看超时费明细",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(14.dp),
            )
        }
    }
}

/**
 * 超时费明细弹窗：口径逐行写清（次数数订单、单元数 15 分钟、只含骑行费）+ 按月分布 + 扫描诊断行。
 *
 * 排版走 ui/common 的 [AppInfoDialog]：单键详情，且正文的 `heightIn(max = 400.dp)` +
 * 纵向滚动由它提供——这里**不再套第二层** `verticalScroll`（嵌套滚动会让长内容只滚一小段就停）。
 */
@Composable
private fun StatsDetailDialog(
    summary: EbikeRideStats.Summary,
    from: YearMonth,
    to: YearMonth,
    months: List<String>,
    coverage: String?,
    scanLine: String?,
    onDismiss: () -> Unit,
) {
    AppInfoDialog(title = "超时费累计", onDismiss = onDismiss) {
        EbikeRideStats.detailLines(summary, from.toString(), to.toString()).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        // 覆盖月份 + 月均：把「¥1245」换算成「每月 53 单 / ¥44」，和记忆对得上才算核实
        coverage?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        // 按月拆开（2026-11-05 用户要求「核实一下」）：总数对不上时，先看是哪个月离谱
        if (months.isNotEmpty()) {
            Text(
                text = "按月（最近 ${months.size} 个月）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
            )
            months.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        scanLine?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ── 骑行记录（服务端订单） ──

/**
 * 「骑行记录」：只留服务端历史订单的入口。
 *
 * 2026-11-05 用户要求删掉本机记录（「把多余的本机记录删掉吧」）——服务端订单已经
 * 覆盖全部骑行，本机那份只记 App 内开的车，重复且容易和账单对不上。数据层
 * （`ride_records` 表 / `RideRecordStore`）保留：自动还车的未结清对账还在用它。
 */
@Composable
private fun OrdersSection(onOpenOrders: () -> Unit) {
    SettingsSection(
        title = "骑行记录",
        subtitle = "服务端账单，按月份查询。",
    ) {
        SettingItem(
            title = "历史订单",
            subtitle = "电单车 / 单车分档，按月份查询",
            icon = HugeIcons.History,
            onClick = onOpenOrders,
        )
    }
}

/** 「临近到期」的提示窗口：3 天（与卡券提醒的 1 天窗口不同：这里只是列表里的一句话）。 */
private const val CARD_EXPIRY_SOON_MS = 3L * 24 * 60 * 60 * 1000

private val MONTH_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.US)

/** 到期时刻只显示到分：服务端给到秒，秒对用户没有意义，还会把三列挤爆。 */
private val TIME_OF_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

// ── 页尾：退出登录 ──

/** 页尾退出登录（2026-11-05 从账号卡里挪下来）：低频破坏性动作，单独一行 + 二次确认。 */
@Composable
private fun LogoutRow(onLogout: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    TextButton(
        onClick = { confirm = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("退出登录", color = MaterialTheme.colorScheme.error)
    }
    if (confirm) {
        AppConfirmDialog(
            title = "退出登录？",
            message = "退出后本机凭证会被清除，再进本页需要重新输入手机号与密码；本机骑行记录不受影响。",
            confirmLabel = "退出",
            onConfirm = {
                confirm = false
                onLogout()
            },
            onDismiss = { confirm = false },
            // 会清掉本机凭证（再进本页要重新输手机号 + 密码）：确认键用错误色
            danger = true,
        )
    }
}

// ── 骑行卡到期提醒弹窗 ──

/**
 * 「骑行卡即将到期」提醒弹窗（DESIGN §4.32，2026-10-14）：资产查询发现卡券落在
 * 「到期前一天」窗口内时弹一次。明细与后果文案都从 [EbikeCardExpiry] 取（唯一出处），
 * 这里只排版。
 *
 * 排版走 ui/common 的 [AppInfoDialog]：单键详情，长文的 `heightIn(max = 400.dp)` +
 * 纵向滚动由它提供（小屏与大字体档位下按钮不会被挤出），这里不再套第二层。
 */
@Composable
private fun CardExpiryDialog(coupons: List<KqcxAuth.AssetCoupon>, onDismiss: () -> Unit) {
    val lines = remember(coupons) {
        EbikeCardExpiry.reminderLines(coupons, System.currentTimeMillis())
    }
    AppInfoDialog(title = "骑行卡即将到期", onDismiss = onDismiss) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            text = EbikeCardExpiry.REMINDER_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ── 未登录：表单 ──

@Composable
private fun LoginSection(state: KvcxUiState, viewModel: KvcxViewModel) {
    var showPassword by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    // 记住登录（2026-10-21 用户拍板：默认开）：默认把凭证加密存在本机，token 失效时静默重登
    var remember by remember { mutableStateOf(true) }
    // 卡片化（2026-09-29 统一风格）：本页其余区块都是 SettingsSection，登录表单裸在页面
    // 背景上会显得"没装进容器"；它又是本页的主任务，套同一套容器最稳
    SettingsSection(
        title = "登录",
        subtitle = "账号即快趣出行 App / 小程序的注册手机号与密码。",
    ) {
        OutlinedTextField(
            value = state.mobile,
            onValueChange = viewModel::updateMobile,
            label = { Text("手机号") },
            isError = state.mobileError != null,
            supportingText = state.mobileError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation =
                if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                // 眼睛图标与一卡通 / 胖乖 / 「我的」页同一套（2026-10-07 统一，原是文字按钮）
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) HugeIcons.ViewOff else HugeIcons.View,
                        contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("记住登录", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "关闭则不保存密码；下次打开需要重新登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Switch(checked = remember, onCheckedChange = { remember = it })
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.login(remember) },
            enabled = !state.loggingIn && state.mobile.isNotBlank() && state.password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        // 忘记密码的出路（2026-09-30 用户口径）：改密码只能在小程序里做，App 这边既不存
        // "找回"流程也没有客服通道，所以必须把去哪儿改写清楚。两个落点从解包产物核对过：
        // 小程序登录页的「忘记密码」（手机号 + 短信验证码重设，`pages/login/forget`），
        // 登录后「设置 → 修改密码」（只填新密码，`pages/setting/modifypassword`）。
        // 2026-11-05：从一段 100+ 字的正文收进弹窗，表单本身只留一颗文字按钮。
        TextButton(onClick = { showForgot = true }) { Text("忘记密码？") }
    }
    if (showForgot) {
        // 单键详情（只有「知道了」）：走 [AppInfoDialog]，套确认框会多一个无意义的「取消」
        AppInfoDialog(title = "忘记或不知道密码", onDismiss = { showForgot = false }) {
            Text(
                "在微信里打开「快趣出行」小程序：登录页点「忘记密码」，用手机号 + 短信验证码重设；" +
                    "登录后在「设置 → 修改密码」也能改。\n\n" +
                    "密码默认加密保存在本机（可关闭「记住登录」）；不进云备份，请妥善保管。",
            )
        }
    }
}
