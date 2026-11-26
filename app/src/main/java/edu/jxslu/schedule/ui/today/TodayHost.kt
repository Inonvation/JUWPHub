package edu.jxslu.schedule.ui.today

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.client.android.Intents
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.UnlockFlowState
import edu.jxslu.schedule.domain.capabilities
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppPermissions
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.washerScanIntent
import edu.jxslu.schedule.ui.ebike.EbikeFreeRideReminder
import edu.jxslu.schedule.ui.ebike.KvcxAction
import edu.jxslu.schedule.ui.ebike.KvcxRideState
import edu.jxslu.schedule.ui.ebike.kvcxConfirmDialog
import edu.jxslu.schedule.ui.qzxy.QzxyEntrySheet
import edu.jxslu.schedule.ui.qzxy.QzxyEvent
import edu.jxslu.schedule.ui.qzxy.QzxyFlowState
import edu.jxslu.schedule.ui.qzxy.QzxyUiState
import edu.jxslu.schedule.ui.qzxy.QzxyViewModel
import edu.jxslu.schedule.ui.qzxy.WateringReconcileEffect
import edu.jxslu.schedule.ui.reminder.UjingDoneReminder
import edu.jxslu.schedule.ui.water.WaterUiState
import edu.jxslu.schedule.ui.water.WaterViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 今日页在 Android 侧的**槽位填充**（iOS 移植 V1，2026-11-25；服务卡 V2，2026-11-25 晚）。
 *
 * 今日页本体已搬进 `shared/commonMain`（`ui/today/TodayScreen.kt`），「进行中服务卡」本体
 * （`ui/today/ActiveServiceCard.kt`）也一起搬了：开水与 U净 两端同源，快趣与趣智的数据类型
 * 还留在 `:app` 的 `ui/ebike`（高德 SDK）与 `ui/qzxy`（蓝牙流程），所以卡片把那两行收成
 * 可空槽位（`ActiveServiceSlot` / `QzxyWaterSlot`），行内容与状态在这里填。
 *
 * ## 这里为什么不是「再抄一份」
 *
 * 这些代码逐字来自搬走前的 `TodayScreen` / `ActiveServiceCard`（四项真相源收集、RESUMED
 * 静默查询、还车确认弹窗、结束免费计时的确认、快趣行的判据与文案），只是位置从页面 /
 * 卡片搬到了宿主。判据与注释都跟着走，一个字没改语义——特别是「位置判据必须用同步状态」
 * 那条（见 [TodayServiceSlot.pinned]）。
 *
 * ## 谁调用
 *
 * `MainActivity` 的 `Routes.TODAY` 分支：`val slots = rememberTodayHostSlots(...)`，
 * 然后把三个值原样传给 `TodayScreen`。iOS 侧不调用——那里的快趣 / 趣智槽位是 `null`，
 * 服务卡由 `MainViewController` 直接给（只接开水与 U净）。
 */
internal class TodayHostSlots(
    val serviceCards: TodayServiceSlot,
    val qzxyPanel: (@Composable (onDismiss: () -> Unit, onNotice: (String) -> Unit) -> Unit)?,
    val onScanCode: ((onResult: (String?) -> Unit) -> Unit)?,
)

/** U净 弹窗打开时对订单的节流窗口：10 秒内不重复请求（同原生活页口径）。 */
private const val UJING_ORDER_REFRESH_TTL_MILLIS = 10_000L

/**
 * 今日页快趣在案订单查询的节流窗口（2026-10-21）：切 Tab / 从二级页返回都会重建组合
 * 触发 ON_RESUME，60 秒内不重复打扰第三方接口；下拉刷新强制查。
 */
private const val KVCX_RIDE_QUERY_TTL_MILLIS = 60_000L

/**
 * 建好今日页在 Android 侧的全部槽位。
 *
 * 状态收集放在这一层（而不是槽位的 `content` 里）：`content` 只在卡片滚进视野时组合，
 * 滚出去就取消；事件流与 RESUMED 查询必须一直在。
 */
@Composable
internal fun rememberTodayHostSlots(
    todayViewModel: TodayViewModel,
    waterViewModel: WaterViewModel?,
    qzxyViewModel: QzxyViewModel?,
    onOpenEbike: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenQzxy: () -> Unit,
    onOpenUjing: () -> Unit,
    onOpenQzxyDebug: (String?) -> Unit,
): TodayHostSlots {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 触感实例：还车确认弹窗的「确认」是还车链路在今日页的动作点（按钮点击处已有 tap，
    // 分发器不重复发，见 dispatchKvcxAction 注释）。
    val serviceHaptics = rememberAppHaptics()

    // ── 开水面板的状态（服务卡要读「正在结束中」与「能否直接结束」两个布尔）──
    val waterEntryState by waterViewModel?.uiState
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(WaterUiState()) }
    // 趣智同理：面板里的计时与流程态要逐帧跟随，不能读打开瞬间的快照
    val qzxyEntryState by qzxyViewModel?.uiState
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(QzxyUiState()) }

    // ── 快趣（Graph 单例，与骑行页同一份状态）──
    // 只读 state 流，查询在 RESUMED 时静默打一拍 + 下拉刷新一拍，**不轮询**。
    val kvcxRide = remember(context) { runCatching { Graph.kvcxRide(context) }.getOrNull() }
    val kvcxRideState by kvcxRide?.state
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<KvcxRideState?>(null) }
    val kvcxLoggedIn by kvcxRide?.loggedIn
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    // 冷启动位置提示（2026-11-18）：上一次进程有在案骑行时，服务卡条目从第一帧就留在顶部，
    // 不等第一次订单查询回包才归位（否则会从列表下方弹到顶部）。
    val kvcxRideHint by kvcxRide?.rideHint
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    // 使用方式原始值：能力闸判据（小程序方式不露骑行行、不查询），读真值不读合并流
    val ebikeUseMode by todayViewModel.ebikeUseMode.collectAsStateWithLifecycle()
    val ebikeRideStartAt by todayViewModel.ebikeRideStartAt.collectAsStateWithLifecycle()
    val kvcxInAppRide = remember(ebikeUseMode) { ebikeUseMode.capabilities().inAppRide }

    // ── 胖乖「出水中」的跨窗口真相源（2026-10-21）：服务卡与侧栏高亮都读它 ──
    val qiekjWaterStore = remember(context) { Graph.qiekjWatering(context) }
    val qiekjWateringSession by qiekjWaterStore.watering.collectAsStateWithLifecycle()

    // ── 趣智登录态：卡片与开水面板都要，直接订阅仓库那条流 ──
    val qzxyRepo = remember(context) { Graph.qzxy(context) }
    val qzxyLoggedIn by qzxyRepo.loggedIn.collectAsStateWithLifecycle()
    // 侧栏那份 Activity 作用域 VM 关掉了 init 自动取数（从课表/教务启动时不白拉）；
    // 今日页才是趣智卡片的实际展示位，进页补一次。已加载过则不再重复。
    LaunchedEffect(qzxyViewModel) {
        val vm = qzxyViewModel ?: return@LaunchedEffect
        if (!vm.uiState.value.balanceLoaded) vm.refreshAccount()
    }

    // ── U净 在案订单：落盘快照直接订阅；对表沿用 10s TTL 节流 ──
    val ujingRepo = remember(context) { Graph.ujing(context) }
    val ujingOrder by ujingRepo.orderState.collectAsStateWithLifecycle()

    // ── 快趣动作分发 ──
    // 锁车免确认（与骑行页同闸）；还车走确认弹窗（danger）；其余免确认动作直发。
    // **不在这里触感**：按钮点击处（ActiveServiceCard 的 ActionChip）已经 tap 过，
    // 这里再 tap 一次就是用户报的「弹两次」——触感归交互点，分发器只管动作。
    var pendingKvcxAction by remember { mutableStateOf<KvcxAction?>(null) }
    val dispatchKvcxAction: (KvcxAction) -> Unit = { action ->
        when (action) {
            KvcxAction.LOCK -> kvcxRide?.tempLock()
            KvcxAction.RESUME, KvcxAction.RETRY_UNLOCK -> {
                // 骑行卡上的开锁 = 对在案订单重发解锁；两段式未开与临时锁后继续骑同一条路
                kvcxRide?.resumeRide()
            }

            KvcxAction.RETURN -> pendingKvcxAction = KvcxAction.RETURN
            else -> Unit
        }
    }

    // 小程序免费计时行的「结束计时」确认（2026-10-21）
    var pendingEndFreeRide by remember { mutableStateOf(false) }

    // ── 静默查询（进页 / 回页 / 下拉各一拍，**不轮询**）──
    var kvcxQueriedAt by remember { mutableStateOf(0L) }
    val queryKvcxQuietly: (Boolean) -> Unit = { force ->
        val now = AppClock.nowMillis()
        if (kvcxInAppRide && kvcxLoggedIn &&
            (force || now - kvcxQueriedAt > KVCX_RIDE_QUERY_TTL_MILLIS)
        ) {
            kvcxQueriedAt = now
            kvcxRide?.query(quiet = true)
        }
    }
    var ujingOrderRefreshedAt by remember { mutableStateOf(0L) }
    val refreshUjingOrderIfStale: () -> Unit = {
        val id = ujingRepo.currentOrder()?.orderId
        val now = AppClock.nowMillis()
        if (id != null && ujingRepo.loggedIn.value &&
            now - ujingOrderRefreshedAt > UJING_ORDER_REFRESH_TTL_MILLIS
        ) {
            ujingOrderRefreshedAt = now
            scope.launch { runCatching { ujingRepo.refreshOrder(id) } }
        }
    }

    // ── 完成态停留与位置判据 ──
    // 有在案骑行时把「进行中服务」卡提到课程区之前（2026-10-22 用户口径）。
    // 位置判据 = **同步状态**（快趣单例 state + 登录态），不是过了能力闸的那份：
    // 能力闸读 DataStore 冷流、新组合首帧恒为默认档，位置会晚一帧翻真，
    // `animateItem` 就把它演成「从底部跳到顶部」（2026-11-12 用户报）。
    // 还车成功后的 8 秒完成态（2026-10-29 / 时长 2026-10-31 改）也要留在这个位置：
    // `ride` 在成功那一刻就没了，只看 ride 的话卡片会带着「已还车」当场挪回列表中间。
    val kvcxDone = rememberDoneLinger(
        visible = kvcxRideState?.ride != null,
        ending = kvcxRideState?.busy == KvcxAction.RETURN,
    )
    // 完成态退场（160ms 淡出 + 收拢）期间卡片还在画：这时把条目挪回下方位置，`animateItem`
    // 会演成「一边淡出一边往下掉」。位置比完成态多留 200ms，换位发生在卡片已经不可见之后。
    var kvcxPinned by remember { mutableStateOf(false) }
    LaunchedEffect(kvcxDone) {
        if (kvcxDone) {
            kvcxPinned = true
        } else {
            delay(ServiceCardExitGraceMillis)
            kvcxPinned = false
        }
    }
    val kvcxRideActive = kvcxRideState?.ride != null && kvcxLoggedIn
    val rideInProgress = kvcxRideActive || kvcxDone || kvcxPinned || kvcxRideHint

    // ── 服务卡的两个平台槽位（快趣 / 趣智）──
    // 卡片本体已搬进共享层（`shared/commonMain` 的 `ui/today/ActiveServiceCard.kt`）：
    // 开水与 U净 两端同源；快趣与趣智的数据类型还留在 `:app`（`ui/ebike` 的控制器状态、
    // `ui/qzxy` 的蓝牙流程），所以只把「能力有没有 + 当前状态」交给卡片。
    //
    // 退场那 160ms 卡片还会再画一次进行中内容，而那一刻 `ride` 已经是 null——用
    // `rememberLastValue` 留住最后一份非空值，否则淡出途中会变成空白行（2026-10-31 的老坑）。
    val lastKvcxRide = rememberLastValue(kvcxRideState?.ride)
    val lastKvcxSummary = rememberLastValue(kvcxRideState?.returnSummary)
    val kvcxSlot = if (kvcxInAppRide && kvcxLoggedIn) {
        ActiveServiceSlot(
            active = kvcxRideState?.ride != null,
            done = kvcxDone,
            // 明确未锁车才点亮卡边：临时锁车 / 锁状态未知不点亮（与卡片里那条同口径）
            highlighted = kvcxRideState?.ride?.locked == false,
            activeContent = {
                lastKvcxRide?.let { ride ->
                    KvcxActiveRow(
                        ride = ride,
                        state = kvcxRideState ?: KvcxRideState(),
                        freeStartAt = ebikeRideStartAt,
                        onUnlock = { dispatchKvcxAction(KvcxAction.RESUME) },
                        onLock = { dispatchKvcxAction(KvcxAction.LOCK) },
                        onReturn = { dispatchKvcxAction(KvcxAction.RETURN) },
                        onOpen = onOpenEbike,
                        brandId = R.drawable.ic_brand_kqcx,
                    )
                }
            },
            doneContent = {
                KvcxDoneRow(
                    summary = kvcxRideState?.returnSummary ?: lastKvcxSummary,
                    onOpen = onOpenEbike,
                    brandId = R.drawable.ic_brand_kqcx,
                )
            },
        )
    } else {
        null
    }

    // 趣智：**能力存在时就要给槽位**（`watering` 可为 null）——完成态那一行（「已结束用水」
    // + 实扣金额）要在会话消失之后再亮 8 秒，槽位跟着会话一起变 null 就没有载体了。
    val qzxySlot = if (qzxyViewModel != null) {
        QzxyWaterSlot(
            watering = qzxyEntryState.watering,
            deviceEnded = qzxyEntryState.wateringDeviceEnded,
            // 结束用水的两段反馈（2026-10-29）：行切「正在结束…」+ 副行展示当前步骤
            stopping = qzxyEntryState.flow is QzxyFlowState.Working,
            stopStep = (qzxyEntryState.flow as? QzxyFlowState.Working)?.step,
            // 关水后的完成态要报实扣金额（2026-11-06）
            settlement = qzxyEntryState.lastSettlement,
            onStop = { qzxyViewModel?.stopWater() },
        )
    } else {
        null
    }

    val activeServicesCard: @Composable (Modifier) -> Unit = { cardModifier ->
        ActiveServiceCard(
            // 快趣骑行 / 趣智开水两个槽位：数据在 `:app`，行内容与状态由这里给；
            // 能力不存在时传 null（卡片按 §11.2 ④ 整行不渲染）。
            kvcx = kvcxSlot,
            freeRideStartAt = ebikeRideStartAt.takeIf { !kvcxInAppRide },
            // 品牌位图是 Android 素材（`ic_brand_*`）；iOS 没有这套位图，那边传 null
            brands = ServiceBrands(
                kqcx = R.drawable.ic_brand_kqcx,
                pangguai = R.drawable.ic_brand_pangguai,
                qzxy = R.drawable.ic_brand_qzxy,
                ujing = R.drawable.ic_brand_ujing,
            ),
            waterSession = qiekjWateringSession,
            waterStopping = (waterEntryState.flow as? UnlockFlowState.Working)?.stopping == true,
            waterCanStop = waterEntryState.flow is UnlockFlowState.Working,
            qzxy = qzxySlot,
            ujingOrder = ujingOrder,
            waterOnEnd = { waterViewModel?.endSession() },
            onOpenEbike = onOpenEbike,
            onOpenWater = onOpenWater,
            onOpenQzxy = onOpenQzxy,
            onOpenUjing = onOpenUjing,
            // 取衣提醒行尾「我已取走」：与 U净 页「知道了」同一语义——清快照 +
            // 收摊（撤完成闹钟 / 撤已发通知 / 撤免扰队列挂起的那条）
            onUjingDismiss = {
                ujingRepo.dismissOrder()
                UjingDoneReminder.revoke(context)
            },
            onEndFreeRide = { pendingEndFreeRide = true },
            modifier = cardModifier,
        )
    }

    // 页面级副作用：必须在页面根上无条件调用（见 TodayServiceSlot.effects 的 KDoc）
    val effects: @Composable (onNotice: (String, NoticeTone) -> Unit) -> Unit = { onNotice ->
        // 单例事件流 → 今日页提示（控制器拿不到页面级提示宿主；单例化后事件都走流）。
        // `onNotice` 自身就是「单开协程发」的形状（页面侧 `scope.launch { snackbar… }`），
        // 收集协程不会卡在等这条提示收场上——与 postNotice 同一口径。
        LaunchedEffect(kvcxRide) {
            kvcxRide?.notices?.collect { event ->
                onNotice(event.text, event.tone)
            }
        }
        // 趣智事件流（2026-10-29）：服务卡上的「结束用水」是今日页的动作点，而结算成功 /
        // 被挡的原因只在这条事件流里。没人收的话点了结束只有卡片自己变，失败原因连一句提示都没有。
        LaunchedEffect(qzxyViewModel) {
            qzxyViewModel?.events?.collect { event ->
                when (event) {
                    is QzxyEvent.Notice -> onNotice(event.text, event.tone)
                }
            }
        }
        // 趣智用水对账（DESIGN §3.18）：今日页可见期间把本地记账与设备核对。
        // 页面侧（SubpageActivity）有自己的同款驱动，repeatOnLifecycle(RESUMED) 保证
        // 同一时刻只有前台那个在跑。
        qzxyViewModel?.let { vm ->
            WateringReconcileEffect(viewModel = vm, enabled = qzxyEntryState.watering != null)
        }
        // 还车确认弹窗
        pendingKvcxAction?.let { action ->
            val ride = kvcxRideState?.ride
            KvcxConfirmAlertDialog(
                action = action,
                carLabel = ride?.carNum?.let { "车 $it" } ?: "选中的车",
                onConfirm = {
                    // 确认键是「还车」真正的动作点：触感在这里发（骑行的还车键同口径）
                    serviceHaptics.tap()
                    pendingKvcxAction = null
                    kvcxRide?.returnBike()
                },
                onDismiss = { pendingKvcxAction = null },
            )
        }
        // 小程序免费计时行的「结束计时」确认（2026-10-21）：收计时会连带撤掉倒计时提醒与
        // 通知，误触代价是「骑行中不再提醒免费额度」——值得一次确认。
        if (pendingEndFreeRide) {
            // 纯文案两键 → 公共件。**不给 danger**：这里不删任何数据，代价只是本机不再提醒
            // 免费额度（还能重新开），红色确认键会把日常操作说成危险动作。
            AppConfirmDialog(
                title = "结束免费计时？",
                message = "只在你已经还车时使用。结束本机的免费时长计时，" +
                    "同时撤掉倒计时提醒与通知栏倒计时。",
                confirmLabel = "结束计时",
                onConfirm = {
                    serviceHaptics.tap()
                    pendingEndFreeRide = false
                    scope.launch {
                        runCatching { EbikeFreeRideReminder.endRide(context) }
                    }
                },
                onDismiss = { pendingEndFreeRide = false },
            )
        }
        // 进页 / 回页各静默查一拍（骑行页同款 LifecycleEventObserver 写法）。
        // 60 秒节流：今日页是高停留页，NavHost 切 Tab / 从二级页返回都会重建组合触发
        // ON_RESUME，不挡的话一次来回就打扰第三方接口一次（下拉刷新传 force 绕过）。
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, kvcxInAppRide, kvcxLoggedIn) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    queryKvcxQuietly(false)
                    refreshUjingOrderIfStale()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                queryKvcxQuietly(false)
            }
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val qzxyPanel: (@Composable (onDismiss: () -> Unit, onNotice: (String) -> Unit) -> Unit)? =
        if (qzxyViewModel == null) {
            null
        } else {
            { onDismiss, onNotice ->
                TodayQzxyPanelHost(
                    vm = qzxyViewModel,
                    loggedIn = qzxyLoggedIn,
                    onOpenDiagnostics = onOpenQzxyDebug,
                    onOpenPage = onOpenQzxy,
                    onDismiss = onDismiss,
                    onNotice = onNotice,
                )
            }
        }

    return TodayHostSlots(
        serviceCards = TodayServiceSlot(
            pinned = rideInProgress,
            content = activeServicesCard,
            effects = effects,
            onRefresh = {
                queryKvcxQuietly(true)
                refreshUjingOrderIfStale()
            },
        ),
        qzxyPanel = qzxyPanel,
        onScanCode = rememberWasherScanner(),
    )
}

/**
 * 趣智校园开水面板宿主：状态在这里收（面板里的 `flow.elapsedSeconds` 每秒自增，
 * 读在页面根上会把整页带走），开阀前的蓝牙权限也在这里就地补——缺权限时面板里点
 * 开始用水直接拉起系统授权，授完接着开阀，不把用户赶回趣智校园页。
 */
@Composable
private fun TodayQzxyPanelHost(
    vm: QzxyViewModel,
    loggedIn: Boolean,
    onOpenDiagnostics: (String?) -> Unit,
    onOpenPage: () -> Unit,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { granted -> granted }) {
            vm.openValveFromCard()
        } else {
            onNotice("缺少「附近的设备」权限，连不上热水器")
        }
    }
    val startValve: () -> Unit = {
        val missing = AppPermissions.missingBluetoothScan(context)
        if (missing.isEmpty()) {
            vm.openValveFromCard()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    QzxyEntrySheet(
        state = state,
        loggedIn = loggedIn,
        onStart = startValve,
        onResend = { vm.resendOpenData() },
        onStop = { vm.stopWater() },
        onAbandon = { vm.abandonWatering() },
        onClearDeviceRecord = { vm.clearDeviceRecord() },
        onClearAndOpen = { vm.clearAndOpen() },
        onRetryOpen = { vm.retryOpenAfterFailure() },
        onClearFromRecovery = { vm.clearFromRecovery() },
        onDismissRecovery = { vm.dismissRecovery() },
        onDismissSettlement = { vm.dismissSettlement() },
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenPage = onOpenPage,
        onDismiss = onDismiss,
    )
}

/**
 * 扫码取原文：洗衣机机身码交给页面识别（`domain/QuickScan` 的三档判据在页面里）。
 *
 * 为什么回调是参数、动作是返回值：`rememberLauncherForActivityResult` 的结果回调必须在
 * **组合期**定下来，而「这一次扫到的东西交给谁」要等用户点下去那一刻才知道。所以用一份
 * `pending` 持有当次的回调（与 `ui/platform/FilePickers` 同一手法）。
 */
@Composable
private fun rememberWasherScanner(): (onResult: (String?) -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(String?) -> Unit>({}) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        pending(result.data?.getStringExtra(Intents.Scan.RESULT))
    }
    return remember(launcher) {
        { onResult ->
            pending = onResult
            launcher.launch(washerScanIntent(context))
        }
    }
}

/**
 * 今日页进行中服务卡的还车确认弹窗：规格与文案的唯一出处仍是
 * `KvcxRideController.kvcxConfirmDialog`（`KvcxAction.RETURN` 档），这里只做排版。
 *
 * 排版走 [AppConfirmDialog]（`ui/common/ConfirmDialog.kt`）：窗口淡入、长文滚动、危险色
 * 三件事只该有一份实现，本文件不再自己拼 `AlertDialog`。
 *
 * 与骑行页那份（`ui/ebike/RideScreen.kt` 里的 `KvcxConfirmDialog`）的**唯一差异**：
 * 这里没有「以后开锁不再确认」勾选。那个勾选只对开锁有意义（`skipNextTime` 记的是
 * 「开锁免确认」），而今日页这张卡只会弹还车确认——**别为了「三处统一」把勾选搬过来**：
 * 还车是花钱那一刻，一次性跳过等于把这次确认永久关掉。
 */
@Composable
private fun KvcxConfirmAlertDialog(
    action: KvcxAction,
    carLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialog = kvcxConfirmDialog(action, carLabel) ?: return
    AppConfirmDialog(
        title = dialog.title,
        confirmLabel = dialog.confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        // 还车是花钱那一刻：确认键用错误色，与骑行页同一口径
        danger = true,
    ) {
        // 正文是编号要点。滚动与最大高度已由 AppConfirmDialog 的正文槽套好，
        // 这里**不要再包一层 `verticalScroll`**（嵌套滚动会让长文只滚一小段就停）。
        dialog.points.forEachIndexed { index, point ->
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(20.dp),
                )
                Text(text = point, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
