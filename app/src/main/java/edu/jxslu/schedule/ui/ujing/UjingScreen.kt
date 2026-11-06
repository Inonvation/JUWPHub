package edu.jxslu.schedule.ui.ujing

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.client.android.Intents
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.data.ujing.UjingDraftStore
import edu.jxslu.schedule.data.ujing.UjingHistoryEntry
import edu.jxslu.schedule.data.ujing.UjingHouse
import edu.jxslu.schedule.data.ujing.UjingOrderSnapshot
import edu.jxslu.schedule.domain.QzxyPhoneMask
import edu.jxslu.schedule.domain.UjingState
import edu.jxslu.schedule.ui.common.AccountHeroCard
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppCardDivider
import edu.jxslu.schedule.ui.common.AppPermissions
import edu.jxslu.schedule.ui.common.DialogWindowFadeEffect
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.HeroTone
import edu.jxslu.schedule.ui.common.ImeAwareModalBottomSheet
import edu.jxslu.schedule.ui.common.MetricCell
import edu.jxslu.schedule.ui.common.MetricsRow
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PageScaffoldColumn
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.SessionExpiredNotice
import edu.jxslu.schedule.ui.common.SkeletonBox
import edu.jxslu.schedule.ui.common.StaggeredReveal
import edu.jxslu.schedule.ui.common.StatusHeroCard
import edu.jxslu.schedule.ui.common.emphasizedText
import edu.jxslu.schedule.ui.common.heroToneAccent
import edu.jxslu.schedule.ui.common.lineHeightDp
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberCloseLock
import edu.jxslu.schedule.ui.ebike.BikeLocator
import edu.jxslu.schedule.ui.ebike.EbikeScanActivity
import edu.jxslu.schedule.ui.ebike.LocateResult
import edu.jxslu.schedule.ui.ebike.showNotice
import edu.jxslu.schedule.ui.theme.Motion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Plus
import me.rerere.hugeicons.stroke.QrCodeScan
import me.rerere.hugeicons.stroke.Refresh

/** 订单进行中且页面可见时的轮询间隔（与 ViewModel 口径一致，只在 UI 侧用）。 */
private const val ORDER_POLL_INTERVAL_MILLIS = 15_000L

// 页面区块的出现 / 退出过渡不再各留一份毫秒常量：统一走 `ui/theme/Motion.kt` 的四档
// （出现 = reveal 220ms 减速曲线，退出 = state 180ms）。原先这里是 220 / 160 两个私有常量，
// 与「胖乖水控」「趣智」两张页面各写各的数——同一种「区块进场」在三个页面三个时长。

/** 记录卡默认展示的账单条数（更早的走「查看全部」进历史账单页）。 */
private const val RecentRecordCount = 3

/** 使用须知正文（DESIGN §4.37）：三条事实告知；归属声明由页尾单独承担，避免重复。 */
private val NoticeItems = listOf(
    "本功能是 **U净（美的校园洗衣）的第三方客户端**，登录凭据只在本机加密保存，" +
        "登录时只发给 U净 官方接口。",
    "费用与支付全部走官方渠道，本应用**不经手资金**，也不提供价格优惠或免费使用。",
    "第三方接口**可能随平台调整而失效**，届时以官方 App / 小程序为准。",
)

/** 页尾免责声明（DESIGN §3.2「页尾免责声明」：五个第三方账号页统一走 FooterDisclaimer）。 */
private const val FooterDisclaimerText =
    "**第三方非官方功能**：与美的集团、无锡小净及相关运营方均无关联。"

/**
 * U净 洗衣房主页（DESIGN §3.23 / §4.37；**2026-11-05 改版**）。
 *
 * 骨架与「快趣账号 / 趣智校园 / 胖乖生活」四个账号页一致，从上到下：
 *
 * 1. **身份卡** [AccountHeroCard]：手机号掩码 + 「已登录」+ 凭证去向。本页没有充值这类
 *    外部动作，所以不挂 action（原「账号与记录」卡里的手机号行已收进这里）。
 * 2. **三列指标**：空闲洗衣机 / 空闲烘干机 / 收藏洗衣房（口径见 [BoardTotals]），
 *    右上角是全页唯一的刷新口（原来在「看板」卡底部）。
 * 3. **在案订单**：仍是唯一一张 [StatusHeroCard]（2026-10-16 口径不变），
 *    设备行 → 状态行（+ 剩余时间 / 进度）→ 主按钮，终结态由「知道了」收起。
 * 4. **扫码**：主操作卡，设备行（上次识别的设备）→ 状态行 → 48dp 主按钮。
 * 5. **记录**：最近 [RecentRecordCount] 笔账单 + 「查看全部」→ 历史账单页。
 * 6. **设置**：收藏洗衣房看板（行尾删除 / 底部添加）。
 * 7. **页尾**：退出登录（保留二次确认）+ [FooterDisclaimer]。
 *
 * **业务口径一字未改**：写操作零自动重试、订单 15 秒轮询（离开页面即停）、
 * 完成提醒闹钟、扫码跳独立下单页、会话过期不自动重登——这次只换排版与文案层级。
 *
 * 洗涤中订单由 [edu.jxslu.schedule.ui.reminder.UjingDoneReminder] 在到点时发系统通知
 * （渠道：洗衣完成提醒）。
 */
@Composable
fun UjingScreen(
    onBack: () -> Unit = {},
    /** 扫码识别成功 → 跳独立下单页（导航由窗口层接，页里只发事件）。 */
    onOpenOrder: () -> Unit = {},
    /** 历史账单入口。 */
    onOpenHistory: () -> Unit = {},
    /**
     * 今日页「+ → 扫一扫」扫到机身码时带进来的二维码原文（`UjingScanHandoff` 一次性令牌）：
     * 进页即按普通扫码处理，识别成功照常跳下单页。普通入口传 null。
     */
    scanRaw: String? = null,
    // payActivityProvider 的 lambda 会在支付时（非组合上下文）被调，这里在组合期把
    // Activity 捕获进闭包（LocalContext.current 不能在普通 lambda 里再取）
    viewModel: UjingViewModel = LocalContext.current.let { entryContext ->
        viewModel(
            factory = UjingViewModel.Factory(
                Graph.ujing(entryContext),
                Graph.ujingHouses(entryContext),
                Graph.ujingHistory(entryContext),
                Graph.ujingDraft(entryContext),
                Graph.displayPrefs(entryContext),
                appContext = entryContext.applicationContext,
                payActivityProvider = { entryContext as? Activity },
            ),
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()

    // 上次在别处判定登录过期：进页说一句为什么退回登录表单（只提示一次）
    SessionExpiredNotice(SessionService.Ujing, state.loggedIn, snackbar)
    var houseToRemove by remember { mutableStateOf<UjingHouse?>(null) }
    var confirmCancelOrder by remember { mutableStateOf(false) }
    // 扫码草稿（设备行「上次识别的设备」用）；下单页关掉后仍留着，直到下单受理或重扫覆盖
    val draftStore = remember { Graph.ujingDraft(context) }
    val draft by draftStore.draft.collectAsStateWithLifecycle()
    // 记录卡直接订阅账单仓：与 ViewModel 的 historyCount、历史账单页同一个实例（Graph 单例），
    // 只读、不参与下单链，也就不必为「最近三笔」再往 ViewModel 加状态。
    val historyStore = remember { Graph.ujingHistory(context) }
    val history by historyStore.state.collectAsStateWithLifecycle()

    // 一次性提示（会话过期、看板错误等）。**语气按 Warning**：这条通道当前唯一的来源是
    // `UjingViewModel.handleFailure` 的会话失效（`SessionExpiryStore.NOTICE_TEXT`），
    // 属于「你要处理一下」而不是「告知你一件事」——旧实现一律用 Info，看起来像播报。
    // 若以后真要发纯信息类事件，给 Channel 带上 tone，别在调用点再写死。
    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> showNotice(scope, snackbar, message, NoticeTone.Warning) }
    }
    // 一次性导航：识别成功 → 独立下单页
    LaunchedEffect(Unit) {
        viewModel.openOrder.collect { onOpenOrder() }
    }
    // 今日页扫到的原文：进页识别一次。键是 scanRaw 本身——重组不会重跑，
    // 配置变更后 Activity 重建时令牌早已消费（拿到 null），也不会重扫
    LaunchedEffect(scanRaw) {
        if (!scanRaw.isNullOrBlank()) viewModel.onQrScanned(scanRaw)
    }

    // 订单进行中且页面可见时的 15 秒轮询（红线 §4.37：**不后台轮询**）。
    // 2026-11-06 修：原来只用 `LaunchedEffect + while` 包，而组合**不随 `onStop` 拆**——
    // 退到后台 / 锁屏 / 进支付宝时循环照跑，后台每 15 秒打一次第三方平台（U净 对高频请求
    // 会限流数十分钟），正是红线禁止的形状。改 `repeatOnLifecycle(RESUMED)`：灭屏或切后台
    // 立刻停，回前台立刻续。
    // 回前台**补一拍**（首次进入除外）：退后台那段时间可能正好是「洗完 / 启动完成 / 支付到账」
    // 发生的时候，等 15 秒才刷新会让用户先看到旧状态。首次进入不补，是因为
    // `UjingViewModel` 的 loggedIn 收集器进页已经对过一次表（UjingViewModel.kt:171-172），
    // 再补就是同一秒两次请求。
    // shortcut: 用「首次 RESUMED 跳过」而不是时间戳判新鲜，因为进页那一刻拿不到
    // 「这次刷新是不是刚发生」；等 ViewModel 提供 lastRefreshedAt 再换（现在不值得加状态）。
    val lifecycleOwner = LocalLifecycleOwner.current
    var polledOnce by remember(state.order?.orderId) { mutableStateOf(false) }
    LaunchedEffect(state.order?.orderId, state.order?.isTerminal) {
        val order = state.order ?: return@LaunchedEffect
        if (order.isTerminal) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (polledOnce) viewModel.refreshOrder(silent = true)
            polledOnce = true
            while (isActive) {
                delay(ORDER_POLL_INTERVAL_MILLIS)
                viewModel.refreshOrder(silent = true)
            }
        }
    }

    // 附近洗衣房：定位一次 → 拉门店列表（与单车地图同一套定位口径：权限先申请、8 秒超时）
    val locateAndLoad: () -> Unit = {
        viewModel.openPicker()
        scope.launch {
            when (val result = BikeLocator.currentLocation(context)) {
                is LocateResult.Ok -> viewModel.loadNearby(result.lat, result.lng)
                is LocateResult.Failed -> {
                    viewModel.closePicker()
                    showNotice(scope, snackbar, result.message, NoticeTone.Warning)
                }
            }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            locateAndLoad()
        } else {
            showNotice(scope, snackbar, "没有定位权限，无法获取附近洗衣房", NoticeTone.Warning)
        }
    }
    val requestNearby: () -> Unit = {
        haptics.tap()
        if (!BikeLocator.hasPermission(context)) {
            locationPermissionLauncher.launch(AppPermissions.location.toTypedArray())
        } else {
            locateAndLoad()
        }
    }

    // 扫码：复用内置取景窗口的原文模式（相机权限由取景窗口自己处理，与骑行页同一条通道）
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val raw = result.data?.getStringExtra(Intents.Scan.RESULT)
        if (result.resultCode == Activity.RESULT_OK && !raw.isNullOrBlank()) {
            viewModel.onQrScanned(raw)
        }
    }
    val startScan: () -> Unit = {
        haptics.tap()
        scanLauncher.launch(
            Intent(context, EbikeScanActivity::class.java).apply {
                putExtra(Intents.Scan.PROMPT_MESSAGE, "对准洗衣机机身上的二维码")
                putExtra(EbikeScanActivity.EXTRA_RESULT_MODE, EbikeScanActivity.MODE_RAW)
            },
        )
    }

    // 订单卡退场期间快照可能已被清空（点「知道了」），AnimatedVisibility 在 exit 时
    // 仍继续组合旧内容——留一份最后非空快照，退场才是「卡片收起」而不是「空块收缩」。
    val liveOrder = state.order
    var lastOrder by remember { mutableStateOf<UjingOrderSnapshot?>(null) }
    LaunchedEffect(liveOrder) { if (liveOrder != null) lastOrder = liveOrder }
    val heroOrder = liveOrder ?: lastOrder

    PageScaffoldColumn(
        title = "U净",
        onBack = onBack,
        snackbar = snackbar,
        // 下拉刷新（2026-11-05）：登录后才接——未登录时没有可刷的数据，下拉只会空转
        refreshing = state.loggedIn && state.boardRefreshing,
        onRefresh = if (state.loggedIn) {
            {
                haptics.tap()
                viewModel.refreshAll()
            }
        } else {
            null
        },
    ) {
        // 登录态切换走尺寸 + 淡入淡出过渡（两态高度差很大，硬切会「跳」一截）
        AnimatedContent(
            targetState = state.loggedIn,
            transitionSpec = {
                fadeIn(Motion.reveal()) togetherWith fadeOut(Motion.state())
            },
            label = "ujingAuth",
        ) { loggedIn ->
            if (!loggedIn) {
                LoginSection(state = state, viewModel = viewModel)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // ① 身份卡
                    AccountHeroCard(
                        brandIcon = R.drawable.ic_brand_ujing,
                        title = QzxyPhoneMask.mask(state.mobile),
                        subtitle = "凭证仅存本机 · 不进云备份",
                        pill = "已登录",
                    )
                    // ② 三列指标（空闲看板合计）
                    MetricsSection(
                        state = state,
                        onRefresh = {
                            haptics.tap()
                            viewModel.refreshBoard()
                        },
                    )
                    // ③ 在案订单（唯一一张英雄卡；出现 / 退场各有过渡）
                    AnimatedVisibility(
                        visible = liveOrder != null,
                        enter = fadeIn(Motion.reveal()) +
                            expandVertically(
                                Motion.reveal(),
                                expandFrom = Alignment.Top,
                            ),
                        exit = fadeOut(Motion.state()) +
                            shrinkVertically(
                                Motion.state(),
                                shrinkTowards = Alignment.Top,
                            ),
                    ) {
                        heroOrder?.let { order ->
                            OrderHero(
                                order = order,
                                // 支付 SDK 阻塞期间（paying）同样禁动作：收银台在那边，这里点取消
                                // 会和支付结果赛跑（busy 只覆盖请求本身，支付窗口是另一段）
                                busy = state.orderBusy || state.paying,
                                onRefresh = { viewModel.refreshOrder() },
                                onRetryPay = viewModel::retryPay,
                                onCancel = { confirmCancelOrder = true },
                                onStart = viewModel::startOrder,
                                onDismiss = viewModel::dismissOrder,
                            )
                        }
                    }
                    // ④ 主操作：扫码（常驻区块错峰落位；订单卡由 AnimatedVisibility 管，别叠加）
                    StaggeredReveal(index = 0) {
                        ScanSection(
                            state = state,
                            draft = draft,
                            onScan = startScan,
                            onOpenOrder = onOpenOrder,
                        )
                    }
                    // ⑤ 记录
                    StaggeredReveal(index = 1) {
                        RecordsSection(entries = history, onOpenAll = onOpenHistory)
                    }
                    // ⑥ 设置：收藏洗衣房看板
                    StaggeredReveal(index = 2) {
                        BoardSection(
                            state = state,
                            onAdd = requestNearby,
                            onRemove = { houseToRemove = it },
                        )
                    }
                }
            }
        }
        // 页尾：退出登录只在已登录时给（未登录时它没有意义）
        if (state.loggedIn) {
            LogoutRow(onLogout = viewModel::logout)
        }
        FooterDisclaimer(FooterDisclaimerText)
    }

    // ── 弹层与对话框 ──
    if (state.pickerOpen) {
        HousePickerSheet(state = state, viewModel = viewModel)
    }
    state.order?.let { order ->
        if (confirmCancelOrder) {
            AlertDialog(
                onDismissRequest = { confirmCancelOrder = false },
                title = { Text("取消订单") },
                text = {
                    // 弹窗进出场动画必须挂在本弹窗窗口上：调用点要落在内容槽第一行，
                    // 写在 AlertDialog 外面拿到的是页面窗口（死代码，见 DialogWindowFadeEffect）
                    DialogWindowFadeEffect()
                    Text("取消「${order.modelName}」并释放机器？未支付订单取消不产生费用。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmCancelOrder = false
                            viewModel.cancelOrder()
                        },
                    ) { Text("取消订单") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmCancelOrder = false }) { Text("保留") }
                },
            )
        }
    }
    houseToRemove?.let { house ->
        AlertDialog(
            onDismissRequest = { houseToRemove = null },
            title = { Text("删除洗衣房") },
            text = {
                DialogWindowFadeEffect()
                Text("从看板中删除「${house.name}」？不会影响 U净 账号里的任何数据。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeHouse(house)
                        houseToRemove = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { houseToRemove = null }) { Text("取消") }
            },
        )
    }
    if (state.noticeVisible) {
        UjingNoticeDialog(onConfirm = viewModel::onNoticeConfirmed)
    }
}

// ── 三列指标（空闲看板合计） ──

/**
 * 看板合计：洗衣机 / 烘干机各一行（[total] == 0 = 没有这类设备，或还没拉到数据）。
 *
 * 只做求和：一家店的聚合仍由 `UjingState.boardLine` / `dryerLine` 给（口径唯一），
 * 这里跨店相加只是为了在指标位上说一句「收藏的店现在有多少台空着」。
 */
private data class BoardTotals(val free: Int, val total: Int) {
    /** 主数值：有数据写「N 台」，没有就不编数字。 */
    val valueText: String get() = if (total > 0) "$free 台" else "—"

    /** 说明行：数字从哪来（共几台 / 为什么没有）。 */
    val captionText: String get() = if (total > 0) "共 $total 台" else "暂无数据"
}

/** 跨店求和：只累加「有这类设备」的行（null 表示该店没有这类设备）。 */
private fun boardTotals(rows: List<UjingBoardRow>, dryer: Boolean): BoardTotals {
    val lines = rows.mapNotNull { if (dryer) it.dryerLine else it.line }
    return BoardTotals(free = lines.sumOf { it.free }, total = lines.sumOf { it.total })
}

/**
 * 三列指标卡（2026-11-05 改版）：空闲洗衣机 / 空闲烘干机 / 收藏洗衣房。
 *
 * 原来「洗衣房空闲」是一张纵向列表卡，每店两行设备，数字埋在小字里；现在把合计提到
 * 页面顶部（[MetricCell] 版式与快趣账号页同款），逐店明细留给下面的「收藏洗衣房」卡。
 * 刷新口从看板卡底部挪到本卡右上角——两处都放会变成两个入口。
 */
@Composable
private fun MetricsSection(state: UjingUiState, onRefresh: () -> Unit) {
    val washer = boardTotals(state.board, dryer = false)
    val dryer = boardTotals(state.board, dryer = true)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("空闲看板", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "收藏店合计 · 约 30 秒滞后",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // 忙碌时固定 48dp 占位（只换内容不缩宽），与快趣账号页的刷新口同一写法
            if (state.boardRefreshing) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        HugeIcons.Refresh,
                        contentDescription = "刷新空闲数",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        MetricsRow(Modifier.padding(top = 10.dp)) {
            MetricCell(
                label = "空闲洗衣机",
                value = washer.valueText,
                caption = washer.captionText,
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = "空闲烘干机",
                value = dryer.valueText,
                caption = dryer.captionText,
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = "洗衣房",
                value = "${state.houses.size} 家",
                caption = "本机收藏",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── 登录 ──

@Composable
private fun LoginSection(state: UjingUiState, viewModel: UjingViewModel) {
    // 60 秒冷却倒计时：只按发码时刻算，冷却归零（或压根没发过码）就停循环——
    // 不然进页即永久每秒唤醒重组。
    // 2026-11-06 加 `repeatOnLifecycle`：原来只随组合停，而组合不随 `onStop` 拆，
    // 发完码立刻切后台的话这 60 秒仍在后台每秒重组（与订单轮询同一个坑）。
    val owner = LocalLifecycleOwner.current
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = state.codeSentAt) {
        val deadline = state.codeSentAt + 60_000L
        if (state.codeSentAt > 0L && System.currentTimeMillis() < deadline) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (System.currentTimeMillis() < deadline) {
                    value = System.currentTimeMillis()
                    delay(1_000)
                }
            }
        }
        value = System.currentTimeMillis()
    }
    val cooldownSeconds = if (state.codeSentAt > 0L) {
        ((state.codeSentAt + 60_000L - now).coerceAtLeast(0L) + 999L) / 1000L
    } else {
        0L
    }

    SettingsSection(
        title = "登录 U净",
        subtitle = "美的校园洗衣 · 手机号短信登录",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.mobile,
                onValueChange = viewModel::onMobileChange,
                label = { Text("手机号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !state.loggingIn,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.captcha,
                    onValueChange = viewModel::onCaptchaChange,
                    label = { Text("短信验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !state.loggingIn,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = viewModel::sendCode,
                    enabled = cooldownSeconds <= 0L && !state.codeSending,
                ) {
                    Text(
                        when {
                            state.codeSending -> "发送中…"
                            cooldownSeconds > 0L -> "$cooldownSeconds 秒"
                            else -> "获取验证码"
                        },
                    )
                }
            }
            Button(
                onClick = viewModel::login,
                enabled = !state.loggingIn,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (state.loggingIn) "登录中…" else "登录")
            }
            // 凭证口径收成一句（原为两行长句，身份卡副标题已承担「不进云备份」）
            Text(
                emphasizedText("登录凭据**只存本机**加密存储，**不保存密码**。"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

// ── 记录（历史账单） ──

/**
 * 账单记录（2026-11-05 改版）：最近 [RecentRecordCount] 笔 + 「查看全部」。
 *
 * 数据源与历史账单页同一个仓（[Graph.ujingHistory]，本机留痕、新的在前）；服务端账单
 * 接口没有协议样本，**别猜端点**（口径见 `.agents/rules/ujing.md`）。
 * 行式平铺 + [AppCardDivider]：卡里不再套卡（ui-common「卡片里放列表用平铺行」）。
 */
@Composable
private fun RecordsSection(entries: List<UjingHistoryEntry>, onOpenAll: () -> Unit) {
    val recent = entries.take(RecentRecordCount)
    AppCard {
        Text("账单记录", style = MaterialTheme.typography.bodyLarge)
        Text(
            if (entries.isEmpty()) "扫码下单后自动记一笔" else "本机留痕 · 共 ${entries.size} 笔",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 2.dp),
        )
        if (recent.isNotEmpty()) {
            recent.forEachIndexed { index, entry ->
                if (index > 0) AppCardDivider()
                RecordRow(entry)
            }
            AppCardDivider()
            TextButton(onClick = onOpenAll, modifier = Modifier.fillMaxWidth()) {
                Text("查看全部")
            }
        }
    }
}

/** 一笔账单：设备 · 模式 / 时间 · 状态 / 金额（与历史账单页同一套字段与文案）。 */
@Composable
private fun RecordRow(entry: UjingHistoryEntry) {
    val timeText = remember(entry.displayAt) {
        SimpleDateFormat("M月d日 HH:mm", Locale.CHINESE).format(Date(entry.displayAt))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append(entry.deviceName.ifBlank { "洗衣机" })
                    if (entry.modelName.isNotBlank()) append(" · ").append(entry.modelName)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$timeText · ${UjingState.statusText(entry.status)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (entry.priceFen > 0) {
            Text(
                "¥${UjingState.fen2yuan(entry.priceFen)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── 设置：收藏洗衣房 ──

@Composable
private fun BoardSection(
    state: UjingUiState,
    onAdd: () -> Unit,
    onRemove: (UjingHouse) -> Unit,
) {
    SettingsSection(
        title = "收藏洗衣房",
        subtitle = "行尾可删除，底部可添加",
    ) {
        if (state.houses.isEmpty()) {
            Text(
                "还没有收藏洗衣房，点下面「添加洗衣房」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        // 首拉（有收藏、还没数据）先渲染与成品同几何的骨架，不许先空态再跳
        // （ui-common「等数据的地方先给骨架」）。高度差由 animateContentSize 收平。
        val firstLoad = state.boardRefreshing && state.board.isEmpty() && state.houses.isNotEmpty()
        Column(Modifier.animateContentSize()) {
            if (firstLoad) {
                state.houses.forEach { BoardSkeletonRow() }
            } else {
                state.board.forEach { row ->
                    BoardHouseRow(row = row, onRemove = { onRemove(row.house) })
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAdd) {
                Icon(HugeIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (state.houses.isEmpty()) "添加洗衣房" else "添加")
            }
        }
    }
}

/** 看板行首拉的骨架：一行店名 + 两行设备，几何与 [BoardHouseRow] 对齐。 */
@Composable
private fun BoardSkeletonRow() {
    val nameHeight = lineHeightDp(MaterialTheme.typography.bodyLarge)
    val lineHeight = lineHeightDp(MaterialTheme.typography.bodySmall)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SkeletonBox(Modifier.height(nameHeight).fillMaxWidth(0.42f))
        SkeletonBox(Modifier.height(lineHeight).fillMaxWidth(0.55f))
        SkeletonBox(Modifier.height(lineHeight).fillMaxWidth(0.48f))
    }
}

@Composable
private fun BoardHouseRow(row: UjingBoardRow, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.house.name, style = MaterialTheme.typography.bodyLarge)
            if (row.failed) {
                BoardSubtitle(
                    row.error?.let { "获取失败：$it" } ?: "获取失败，点「刷新」重试",
                    highlight = false,
                )
            } else {
                row.line?.let { DeviceLine(label = "洗衣机", line = it) }
                row.dryerLine?.let { DeviceLine(label = "烘干机", line = it) }
                if (row.line == null && row.dryerLine == null) {
                    BoardSubtitle("该店暂无设备数据", highlight = false)
                }
            }
        }
        // 命中区 40dp（M3 IconButton 自带 48dp 触摸目标），此前 32dp 低于无障碍下限
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(
                HugeIcons.Delete02,
                contentDescription = "删除 ${row.house.name}",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 一行设备：类型标签 + 空闲数（放大走主色）/ 全满文案。
 * 数字取 [UjingState.BoardLine.freeCountText]（语义单一来源，UI 不另拼）。
 */
@Composable
private fun DeviceLine(label: String, line: UjingState.BoardLine) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.width(40.dp),
        )
        val freeText = line.freeCountText
        if (freeText != null) {
            Text(
                freeText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "空闲",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        } else {
            Text(
                listOfNotNull(line.primaryText, line.secondaryText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

/** 看板行的一行小字（失败 / 空态共用）：[highlight] 时空闲数走主色。 */
@Composable
private fun BoardSubtitle(text: String, highlight: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (highlight) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        },
        // 失败原因可能是一段服务端文案，最多三行，别把看板撑开
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

// ── 扫码识别（主操作） ──

/**
 * 扫码入口（主操作卡）：**设备行 → 状态行 → 主按钮**（2026-11-05 改版）。
 *
 * 主页只管「扫没扫成」：识别成功即跳独立下单页（[UjingOrderScreen]），档位 / 水温 / 投放
 * 的选择都不在这页。识别过但没下完单的，草稿还在——设备行直接显示那台设备并给「接着选」，
 * 不再另起一行「继续上次识别的设备」（原来那行把同一件事说了两遍）。
 *
 * 识别中的反馈落在主按钮自己身上（按钮常显、就地变 loading），另起一行状态会把
 * 下方内容顶动一下，而那正是「跳变」的来源。
 */
@Composable
private fun ScanSection(
    state: UjingUiState,
    draft: UjingDraftStore.Draft?,
    onScan: () -> Unit,
    onOpenOrder: () -> Unit,
) {
    val parsing = state.scan is UjingScanState.Parsing
    val failed = state.scan as? UjingScanState.Failed
    AppCard {
        // 设备行：上次识别到的那台（草稿还在），否则就是「未识别」
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    draft?.program?.deviceTypeName?.takeIf { it.isNotBlank() } ?: "未识别设备",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    draft?.let { draft ->
                        draft.program.storeName.takeIf { it.isNotBlank() } ?: "上次识别 · 待选档位"
                    } ?: "扫机身码识别设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (draft != null && !parsing) {
                TextButton(onClick = onOpenOrder) { Text("接着选") }
            }
        }
        AppCardDivider(Modifier.padding(vertical = 10.dp))
        // 状态行：识别中 / 失败 / 空闲引导，常驻一行（切换只换内容，不新增块）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                parsing -> {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        "正在识别设备…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                failed != null -> {
                    Icon(
                        HugeIcons.AlertCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        failed.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                else -> Text(
                    "对准机身二维码即可",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        // 主按钮：高 48dp、整行宽。识别中的反馈交给上面那行状态（常驻槽位），
        // 按钮自己只置灰——两处都写「正在识别」等于同一句话说两遍
        Button(
            onClick = onScan,
            enabled = !parsing,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(HugeIcons.QrCodeScan, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("扫码识别洗衣机")
        }
    }
}

// ── 订单英雄卡 ──

/**
 * 在案订单：唯一一张 [StatusHeroCard]。**设备行 → 状态行 → 主按钮**（2026-11-05 改版），
 * 状态行右侧是大剩余时间，下面依次是进度条、支付窗口提示、主按钮、次动作行。
 *
 * 倒计时以快照时刻本地换算（不每秒请求），服务端轮询由页面的 15 秒 `LaunchedEffect` 负责。
 * 两条本地换算（都不发请求）：
 * - 洗涤中（status 40）：进度 = 1 − 剩余/总时长（总时长取下单时所选模式，旧快照没有就不显）；
 * - 未支付：机器独占窗口 = 下单时刻 + 2 分钟（协议口径），窗口用尽提示刷新确认。
 *
 * 主按钮按状态只出一枚（去支付 / 启动 / 知道了）——三者互斥，原来横排三枚按钮时
 * 用户要在一排里找「哪个才是现在该点的」。
 *
 * 倒计时文字**不做 crossfade**（每秒都在变，动画会一直闪）；变化感交给进度条：
 * 进度值走 400ms 过渡，每秒更新接续平滑，观感是连续推进。
 */
@Composable
private fun OrderHero(
    order: UjingOrderSnapshot,
    busy: Boolean,
    onRefresh: () -> Unit,
    onRetryPay: () -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 本地每秒 tick：剩余 = 快照 remainSeconds −（现在 − 快照时刻），只对运行中订单有意义；
    // 终态订单倒计时没有意义，不再每秒唤醒（刷新必写新 snapshotAt，状态翻转时本 tick 会重启）。
    // 2026-11-06 加 `repeatOnLifecycle`：洗涤中订单最长 40 分钟，原来退后台 / 锁屏后仍在
    // 每秒重组（组合不随 `onStop` 拆）——与「不后台轮询」同一口径，只在前台走秒。
    val terminal = order.isTerminal
    val owner = LocalLifecycleOwner.current
    val now by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = order.snapshotAt,
        key2 = terminal,
    ) {
        if (terminal) return@produceState
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }
    val running = order.status == "40" && !terminal
    val remainSeconds = if (running) {
        order.remainSeconds - ((now - order.snapshotAt) / 1000).toInt()
    } else {
        order.remainSeconds
    }
    val payWindowRemain = if (!order.paid && !terminal) {
        UjingState.payWindowRemainSeconds(order.createdAt, now)
    } else {
        -1
    }
    // 语气：完成 = 成功，其余终结态（取消 / 超时 / 失败）= 中性，进行中 = 主色
    val tone = when {
        !terminal -> HeroTone.Primary
        order.status == "50" -> HeroTone.Success
        else -> HeroTone.Neutral
    }
    val accent = heroToneAccent(tone)
    // 状态行右侧大数值：洗涤中给剩余时间，未支付给支付窗口倒计时（最要紧的那个数）
    val metric = when {
        running && order.remainSeconds > 0 -> UjingState.remainText(remainSeconds) to accent
        payWindowRemain > 0 -> UjingState.remainText(payWindowRemain) to MaterialTheme.colorScheme.error
        else -> null
    }
    val primaryModifier = Modifier
        .padding(top = 12.dp)
        .fillMaxWidth()
        .height(48.dp)
    StatusHeroCard(tone = tone) {
        // 设备行
        Text(
            buildString {
                append(order.deviceName.ifBlank { "洗衣机" })
                append(" · ").append(order.modelName.ifBlank { "洗衣模式" })
                if (order.priceFen > 0) {
                    append(" · ¥").append(UjingState.fen2yuan(order.priceFen))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        // 状态行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                UjingState.statusLabel(order.status, order.statusRemark),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            metric?.let { (text, color) ->
                Text(text, style = MaterialTheme.typography.titleLarge, color = color)
            }
        }
        if (running && order.durationSeconds > 0) {
            val target = (
                1f - remainSeconds.coerceAtLeast(0).toFloat() / order.durationSeconds
                ).coerceIn(0f, 1f)
            // 例外：这是**进度条按 1 秒一次的 tick 补间**，时长要盖住两次 tick 之间的空档，
            // 且必须保持线性（曲线一弯，每秒一格就会读出「一顿一顿」的脉动）。
            // 四档里最长 280ms 只盖住 1 秒的 28%，套上去反而更像跳格；这里按「数据节奏」
            // 而不是「用户动作」定长，是全 App 唯一这样的动画。
            val progress by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
                label = "ujingWashProgress",
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
        if (payWindowRemain >= 0) {
            Text(
                if (payWindowRemain > 0) {
                    "机器已保留，请在倒计时内完成支付"
                } else {
                    "支付窗口已到点，点「刷新」确认订单状态（超时未付会自动取消）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (payWindowRemain > 0) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // 主按钮：按状态只出一枚（不摆点不了的按钮骗人）
        when {
            terminal -> Button(onClick = onDismiss, modifier = primaryModifier) { Text("知道了") }

            !order.paid -> Button(
                onClick = onRetryPay,
                enabled = !busy,
                modifier = primaryModifier,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("去支付")
            }

            UjingState.canStart(order.status) -> Button(
                onClick = onStart,
                enabled = !busy,
                modifier = primaryModifier,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("启动")
            }
        }
        // 次动作行（洗涤中只有刷新；未支付可取消；终结态只留主按钮「知道了」）
        if (!terminal) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (!order.paid && UjingState.canCancel(order.status)) {
                    TextButton(onClick = onCancel, enabled = !busy) { Text("取消订单") }
                }
                TextButton(onClick = onRefresh, enabled = !busy) { Text("刷新") }
            }
        }
        if (UjingState.canStart(order.status)) {
            Text(
                "蓝牙机型需靠近机器（10 米内）再启动。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * 在案订单的紧凑状态行（生活页 U净 弹窗复用）：设备名 + 状态文案 + 洗涤中剩余时间。
 * 剩余时间以快照时刻本地换算（不请求）；是否显示由调用方定（如仅非终结订单）。
 */
@Composable
internal fun UjingOrderStatusLine(order: UjingOrderSnapshot, modifier: Modifier = Modifier) {
    val running = order.status == "40"
    // 与订单卡同一口径（2026-11-06）：只在前台走秒。这一份还被今日页 U净 弹窗复用，
    // 而今日页的组合是常驻的——不挂生命周期就会在后台每秒重组整个弹窗子树。
    val owner = LocalLifecycleOwner.current
    val now by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = order.snapshotAt,
        key2 = running,
    ) {
        if (!running) return@produceState
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }
    val remain = UjingState.liveRemainSeconds(order.status, order.remainSeconds, order.snapshotAt, now)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            order.deviceName.ifBlank { "洗衣机" },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildString {
                append(UjingState.statusLabel(order.status, order.statusRemark))
                if (running && order.remainSeconds > 0) {
                    append(" · 剩余 ").append(UjingState.remainText(remain))
                }
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── 页尾：退出登录 ──

/**
 * 页尾退出登录（2026-11-05 从「账号与记录」卡里挪下来）：低频破坏性动作，
 * 单独一行 + 二次确认（确认文案与原实现一致，行为不变）。
 */
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
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("退出登录") },
            text = {
                DialogWindowFadeEffect()
                Text("退出后需要重新用短信验证码登录。确定退出？")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onLogout()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("取消") }
            },
        )
    }
}

// ── 附近洗衣房弹层 ──

@Composable
private fun HousePickerSheet(state: UjingUiState, viewModel: UjingViewModel) {
    ImeAwareModalBottomSheet(onDismiss = viewModel::closePicker) {
        Text(
            "附近的洗衣房",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            "点击添加到你自己的看板，可以加多间。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        when {
            state.pickerLoading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在获取附近洗衣房…")
            }

            state.pickerStores.isEmpty() -> Text(
                "附近没有找到洗衣房。到校后重试，或检查定位权限。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(20.dp),
            )

            else -> Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            ) {
                state.pickerStores.forEach { store ->
                    val added = state.houses.any { it.storeId == store.id }
                    SettingItem(
                        title = store.name,
                        subtitle = if (added) "已添加" else "点击添加",
                        enabled = !added,
                        onClick = { if (!added) viewModel.addHouse(store) },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── 使用须知 ──

/**
 * 使用须知（DESIGN §4.37）：首次弹出锁 3 秒，确认后一周内不再弹
 * （与充值免责声明同一种"先知情再使用"的口径，正文见 [NoticeItems]）。
 */
@Composable
private fun UjingNoticeDialog(onConfirm: () -> Unit) {
    // 首次进页异步弹出（等 ujingNoticeSeenAt 读到才置 true），没有窗口动画就是
    // 凭空一跳——与快趣确认弹窗同款修法（2026-10-03）
    val remainingMs = rememberCloseLock(3_000L)
    val locked = remainingMs > 0L
    AlertDialog(
        // 锁定期间点遮罩 / 返回键也不放走；解锁后点遮罩视为已读
        onDismissRequest = { if (!locked) onConfirm() },
        title = { Text("U净 使用须知") },
        text = {
            // 动画调用点必须在弹窗自己的组合里（内容槽第一行），放 AlertDialog 外面是死代码
            DialogWindowFadeEffect()
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                NoticeItems.forEachIndexed { index, item ->
                    Text(
                        emphasizedText("${index + 1}. $item"),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !locked) {
                Text(
                    if (locked) "知道了（${ceil(remainingMs / 1000.0).toInt()} 秒）" else "知道了",
                )
            }
        },
    )
}
