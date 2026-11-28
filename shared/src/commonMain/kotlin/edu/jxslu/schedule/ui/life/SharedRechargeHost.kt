package edu.jxslu.schedule.ui.life

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import edu.jxslu.schedule.data.ykt.rechargeTargetCard
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.RechargeDisclaimer
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.campus.CampusArrivalDialog
import edu.jxslu.schedule.ui.campus.CampusCardViewModel
import edu.jxslu.schedule.ui.campus.CampusPendingConfirmDialog
import edu.jxslu.schedule.ui.campus.RechargeSheet
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.RechargeDisclaimerDialog
import edu.jxslu.schedule.ui.common.postNotice
import edu.jxslu.schedule.ui.common.rememberResumeTick
import kotlinx.coroutines.launch

/**
 * 共享充值宿主（**iOS 用**；2026-11-26）：把 `:app` 的 `RechargeHost` 那条流程原样搬到
 * 共享层，只把两件平台动作换成 [RechargePlatform]。
 *
 * ## 为什么需要它
 *
 * `:app` 的 `RechargeHost` 挂在 `MainActivity` 壳层，靠 `Context` / `Intent` 拉起收银台与
 * 外部小程序；iOS 没有这套东西，`IosDrawer` 的钱包卡因此一直传 `onRecharge = null`。
 * 本类把「免责声明 → 充值弹层 → 下单 → 支付页 → 到账判定」整条链放到 `commonMain`，
 * **一行 `android.*` 都没有**（元数据门禁 `:shared:metadataCommonMainClasses` 会校验）。
 *
 * Android **不用**它：`RechargeHost` 原样保留（行为零变化是硬要求）。两端的行为差异只在
 * [RechargePlatform] 的两个方法上，所以这条宿主不会变成「第二份业务逻辑」。
 *
 * ## 四类充值分别停在哪（口径见 `design/ios-port.md` §10.4）
 *
 * | 充值 | 本宿主怎么走 | 成功判据 |
 * |---|---|---|
 * | 一卡通 | 弹层下单 → [CampusCardViewModel.recharge]（`preferCashierPage = true`）：先直拉 `weixin://`（[RechargePlatform.openExternalUrl]），系统不接受再把同一笔单的收银台 URL 交给 [BankPayPage]（`WKWebView`），页面自己跳微信 | [CampusCardViewModel] 的到账轮询（余额 + 流水双口径），**不靠页面回调**；收页时再补一次 [CampusCardViewModel.onHostResume] |
 * | 寝室电费 | [LifeViewModel.placePowerOrder] → [LifeEvent.OpenBankCashier] → 收银台页 → 回到商户域即收页 | [LifeViewModel.checkBankPaid]（`order.status=1`）+ `PowerReadingStore` 复读 |
 * | 胖乖 / 趣智 | **不在本宿主里**：[RechargePlatform.openExternalUrl] 打开 `alipays://` 深链，入口在各自页面（`WaterScreen.onOpenRecharge` / `QzxyScreen.onOpenRecharge`） | 回到本页后余额重拉（iOS 没有前台回调，只能下拉刷新） |
 *
 * ## 接线（壳层只有三件事）
 *
 * 1. 造**一个** [RechargeRequests]（`remember { RechargeRequests() }`），钱包卡 / 缴费账单页 /
 *    校园卡设置页都往里 `request(...)` / `openPaymentPage(...)`；
 * 2. 把本宿主放在**根组合的最后一个子节点**（整屏收银台页要盖在页面上）；
 * 3. 两个 ViewModel 由宿主建好后传进来（`design/ios-port.md` §11.2 ①：`remember { … }`），
 *    且必须与侧栏钱包卡用的是**同一份**实例——否则充值弹层里的电费读数与钱包卡各走一套。
 *
 * ## 计时器与「回到前台」
 *
 * Android 的宿主用 `LifecycleEventObserver` 收 `ON_RESUME`；iOS 侧 `rememberResumeTick()`
 * 恒 0（CMP 1.7.3 没有 iOS LifecycleOwner，见 `ui/common/ResumedTicker.kt`），所以 iOS 的
 * 「回到前台补检到账」由**收银台页收页**这一下顶上：[BankPayPage] 的 `onClose` /
 * `onMerchantReturn` 都会叫一次 [CampusCardViewModel.onHostResume] 与
 * [LifeViewModel.checkBankPaid]（两者各自有闸，叫错流程是空操作）。从**支付宝**返回
 * （胖乖 / 趣智）没有等价信号——那两条链只能下拉刷新。
 *
 * @param requests 充值请求投递口（见上）。
 * @param platform 平台能力（iOS 传 `IosRechargePlatform`）。
 * @param lifeViewModel 电费那条链（与侧栏电费卡共用一份，宿主不要另建）。
 * @param campusViewModel 一卡通那条链（与钱包卡 / 付款码页共用一份）。
 * @param roomPickerViewModel 「选择 / 改绑寝室」的状态机；null = 拿不到电费仓库，
 *   未绑定寝室时直接在弹层里报平台原话（不摆一个点了没反应的「去设置」）。
 * @param onOpenCampusSettings 一卡通未开启时的落点（「我的 → 校园卡」）。
 * @param onOpenPayCode 到账弹窗的「出示付款码」；null = 本平台没有付款码页 → 只有关闭键。
 */
@Composable
fun SharedRechargeHost(
    requests: RechargeRequests,
    platform: RechargePlatform,
    lifeViewModel: LifeViewModel,
    campusViewModel: CampusCardViewModel,
    roomPickerViewModel: PowerRoomPickerViewModel? = null,
    onOpenCampusSettings: () -> Unit = {},
    onOpenPayCode: (() -> Unit)? = null,
) {
    val powerState by lifeViewModel.uiState.collectAsStateWithLifecycle()
    val powerRecharge by lifeViewModel.powerRecharge.collectAsStateWithLifecycle()
    val campusEnabled by campusViewModel.enabled.collectAsStateWithLifecycle()
    val balance by campusViewModel.balance.collectAsStateWithLifecycle()
    val arrival by campusViewModel.arrivalState.collectAsStateWithLifecycle()
    val pendingConfirm by campusViewModel.pendingConfirmVisible.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    // 宿主自己持一份提示条：iOS 根组合没有 Snackbar（各页各持一份），而这一层的提示
    //（已拉起微信 / 收银台打不开 / 到账超时）不属于任何页面
    val snackbar = remember { SnackbarHostState() }

    // 免责声明的静默窗口（口径唯一来源 domain/RechargeDisclaimer，电费与一卡通共用一份）
    val prefs = remember { Services.current.displayPrefs() }
    val disclaimerSuppressUntil by prefs.rechargeDisclaimerSuppressUntil
        .collectAsStateWithLifecycle(initialValue = null)
    val disclaimerSeenAt by prefs.rechargeDisclaimerSeenAt
        .collectAsStateWithLifecycle(initialValue = null)
    // 本次进程内刚勾下的截止时刻：DataStore 落盘是异步的，等它回来再判会漏掉「刚勾完
    // 又立刻点充值」这一下（与 `:app` 的 RechargeHost 同一条）
    var sessionSuppressUntil by remember { mutableStateOf(0L) }
    var disclaimerFor by remember { mutableStateOf<RechargeKind?>(null) }

    var showCampusSheet by remember { mutableStateOf(false) }
    var showPowerSheet by remember { mutableStateOf(false) }
    var showRoomPicker by remember { mutableStateOf(false) }

    // 内嵌支付页开着时先收起电费弹层：整屏页与 ModalBottomSheet 谁在上取决于弹层的窗口
    // 实现（Android 是独立 Activity，iOS 是同一棵组合），收起是两种实现都成立的处置。
    var restorePowerSheet by remember { mutableStateOf(false) }

    fun openRoomSetup() {
        val vm = roomPickerViewModel
        if (vm == null) {
            // 拿不到选择器：不摆「去设置」，直接把弹层打开——下单时服务端会报未绑定寝室的
            // 原话（`PowerException.RoomNotBound`），用户至少知道卡在哪
            showPowerSheet = true
            return
        }
        vm.start()
        showRoomPicker = true
    }

    fun reallyOpenRecharge(kind: RechargeKind) {
        // 两个充值共用同一份一卡通凭证：没开启时先去设置页，不在充值弹层里卡住
        if (!campusEnabled) {
            onOpenCampusSettings()
            return
        }
        when (kind) {
            // 打开弹层即重置流程并清一遍未支付单（平台不自动清，堆积会让新下单 500）
            RechargeKind.Power -> {
                // 未设置寝室（平台侧判据，DESIGN §4.24）：没有房间可读，充值无从谈起
                if (powerState.power.roomNotBound) {
                    openRoomSetup()
                } else {
                    lifeViewModel.preparePowerRecharge()
                    showPowerSheet = true
                }
            }

            RechargeKind.Campus -> showCampusSheet = true
        }
    }

    fun openRecharge(kind: RechargeKind) {
        val until = maxOf(disclaimerSuppressUntil ?: 0L, sessionSuppressUntil)
        if (RechargeDisclaimer.isSuppressed(until, AppClock.nowMillis())) {
            reallyOpenRecharge(kind)
        } else {
            disclaimerFor = kind
        }
    }

    /**
     * 把**收银台页** URL 交给平台；平台不承载这一页时用共享层的 [BankPayPage]。
     *
     * 一卡通直拉微信那条**不走这里**：`weixin://` 由 [CampusCardViewModel.recharge] 里的
     * [RechargePlatform.openExternalUrl] 直接交给系统，只有系统不接受时才退到这一页
     * （2026-11-28 改，此前 iOS 只有这一页）。收银台页里点「立即支付」也会自己跳微信，
     * 所以它是兜底而不是废路。
     */
    fun openPayment(url: String, title: String, merchantReturnHost: String?): Boolean {
        if (platform.openPaymentPage(url)) return true
        requests.openPaymentPage(url, title = title, merchantReturnHost = merchantReturnHost)
        return true
    }

    /**
     * 收银台页收页之后的收尾：两边各问一次（各自有闸，叫错流程是空操作）。
     *
     * 电费只在 `Step.BankPay` 才查单；一卡通只在「正在等到账 / 有未确认充值记录」时才动。
     * 所以这里不记「这一页是哪条流程开的」——多记一份状态就多一处能和 VM 对不上的地方。
     */
    fun afterPaymentPageClosed() {
        requests.closePaymentPage()
        lifeViewModel.checkBankPaid(silent = true)
        campusViewModel.onHostResume()
    }

    // 只补这一项要用的数据（都走缓存/闸门）。一卡通充值不需要电费读数，电费充值也不必
    // 再刷一卡通余额（支付方式在收银台网页里选，余额由侧栏/页面自己刷）
    LaunchedEffect(requests.pending) {
        when (requests.pending) {
            null -> Unit
            RechargeKind.Campus -> campusViewModel.refreshBalance(force = false)
            RechargeKind.Power -> lifeViewModel.refreshPower(force = false)
        }
    }

    // 等免责声明的静默窗口读到（DataStore 首帧）再取走待办：与 `:app` 同一时序——
    // 先取走再等会出现「点了充值没反应」（弹层在 prefs 到达前就被消费掉）
    LaunchedEffect(requests.pending, disclaimerSuppressUntil, disclaimerSeenAt) {
        val kind = requests.pending ?: return@LaunchedEffect
        if (disclaimerSuppressUntil == null || disclaimerSeenAt == null) return@LaunchedEffect
        requests.consumePending()
        openRecharge(kind)
    }

    LaunchedEffect(lifeViewModel) {
        lifeViewModel.events.collect { event ->
            when (event) {
                // 事件流用 postNotice 单开协程发：收集协程不卡在「等这条提示收场」上
                is LifeEvent.Notice -> scope.postNotice(snackbar, event.text, event.tone)

                // 收银台链接现取现用（一次性会话）：Android 交给 PowerBankPayActivity，
                // 本平台不承载时交给共享的内嵌页
                is LifeEvent.OpenBankCashier -> {
                    if (!platform.openPaymentPage(event.url)) {
                        requests.openPaymentPage(
                            url = event.url,
                            title = "电费支付",
                            merchantReturnHost = POWER_MERCHANT_HOST,
                        )
                    }
                }
            }
        }
    }

    // 回到前台：立即补检一轮到账（iOS 恒 0，即只在进页/收页时做——见 KDoc）
    val resumeTick = rememberResumeTick()
    LaunchedEffect(resumeTick) {
        if (resumeTick <= 0) return@LaunchedEffect
        campusViewModel.onHostResume()
        if (powerRecharge.step == LifeViewModel.PowerRechargeUi.Step.BankPay) {
            lifeViewModel.checkBankPaid(silent = true)
        }
    }

    // 支付页开合与电费弹层的互斥（见 restorePowerSheet 的注释）
    val paymentUrl = requests.paymentUrl
    LaunchedEffect(paymentUrl) {
        if (paymentUrl != null) {
            if (showPowerSheet) {
                showPowerSheet = false
                restorePowerSheet = true
            }
        } else if (restorePowerSheet) {
            restorePowerSheet = false
            // 只有「还在等支付结果」才把弹层放回来：金额步重开一张空白弹层等于把用户
            // 刚填的金额抹掉，而失败提示本来就该留在收银台页上
            if (powerRecharge.step == LifeViewModel.PowerRechargeUi.Step.BankPay) {
                showPowerSheet = true
            }
        }
    }

    if (showPowerSheet) {
        PowerRechargeSheet(
            state = powerRecharge,
            // `"%.2f 度".format(...)` 是 JVM 专有，共享层用 TextFmt（与电费卡同一出口）
            roomLabel = powerState.power.snapshot?.meter?.room?.room,
            remainText = powerState.power.snapshot?.meter?.remain?.let { "${TextFmt.decimals(it, 2)} 度" },
            onDismiss = {
                showPowerSheet = false
                lifeViewModel.dismissPowerRecharge()
            },
            onPlaceOrder = { yuan -> lifeViewModel.placePowerOrder(yuan) },
            onReopenCashier = { lifeViewModel.reopenBankCashier() },
            onCheckPaid = { lifeViewModel.checkBankPaid() },
        )
    }

    if (showCampusSheet) {
        RechargeSheet(
            balanceFen = balance?.cardFen,
            accountFen = balance?.accountFen,
            // 二次确认弹窗里加粗展示的目标卡号：与下单链路同一份选取口径
            //（`rechargeTargetCard` = 第一张非挂失卡）
            cardAccount = balance?.cards?.rechargeTargetCard()?.account,
            // 主路径是直拉微信（拉起失败才退收银台页），说明文案用 Android 那一套
            //（`opensCashierPage` 的默认值）：说「打开收银台里选支付方式」现在不成立
            //（2026-11-28）。兜底真发生的那一次，收银台页自己会说明。
            onDismiss = { showCampusSheet = false },
            onLaunch = { yuan, toElectric ->
                showCampusSheet = false
                scope.launch {
                    val target = if (toElectric) campusViewModel.electricAccountType() else null
                    campusViewModel.recharge(
                        yuan = yuan,
                        targetAccount = target,
                        // iOS：先直拉 weixin://，系统不接受再退收银台 URL 进 WKWebView
                        //（顺序唯一实现在 `paymentAttempts`，这里只提供两条平台通道）
                        preferCashierPage = true,
                        openExternalUrl = { url -> platform.openExternalUrl(url) },
                        launchExternal = { url -> openPayment(url, "校园卡充值", null) },
                    ) { notice -> scope.postNotice(snackbar, notice.text, notice.tone) }
                }
            },
        )
    }

    if (showRoomPicker) {
        val vm = roomPickerViewModel
        if (vm != null) {
            PowerRoomPickerSheet(
                viewModel = vm,
                onDismiss = { showRoomPicker = false },
                onBound = { room ->
                    showRoomPicker = false
                    // 绑定后立即按新房间刷新（force：平台刚写完，缓存里那份还是「没房间」）
                    lifeViewModel.refreshPower(force = true)
                    scope.postNotice(snackbar, "已设置寝室：$room", NoticeTone.Success)
                },
            )
        }
    }

    // 充值免责声明弹窗：点「继续充值」落「已确认」（勾选则再落一周静默）后放行，
    // 取消只落「已确认」不静默——下次充值还会再弹，但立即可关
    disclaimerFor?.let { kind ->
        RechargeDisclaimerDialog(
            closableAfterMs = RechargeDisclaimer.closeLockMs(
                disclaimerSeenAt,
                AppClock.nowMillis(),
            ),
            onContinue = { suppressWeek ->
                disclaimerFor = null
                if (suppressWeek) {
                    val until = RechargeDisclaimer.suppressUntil(AppClock.nowMillis())
                    sessionSuppressUntil = until
                    scope.launch { prefs.setRechargeDisclaimerSuppressUntil(until) }
                }
                scope.launch { prefs.markRechargeDisclaimerSeen() }
                reallyOpenRecharge(kind)
            },
            onDismiss = {
                disclaimerFor = null
                scope.launch { prefs.markRechargeDisclaimerSeen() }
            },
        )
    }

    val arrived = arrival as? CampusCardViewModel.ArrivalState.Arrived
    if (arrived != null) {
        CampusArrivalDialog(
            arrived = arrived,
            onDismiss = { campusViewModel.dismissArrival() },
            onOpenPayCode = onOpenPayCode,
        )
    }

    // 「正在确认充值结果」：从微信 / 收银台返回且余额还没更新时给反馈（关闭不影响后台轮询）。
    // 与成功弹窗互斥——到账那一刻 VM 会先把本弹窗撤下（markArrived），不会两张叠着
    val watchingArrival = arrival as? CampusCardViewModel.ArrivalState.Watching
    if (pendingConfirm && watchingArrival != null) {
        CampusPendingConfirmDialog(
            orderFen = watchingArrival.orderFen,
            onDismiss = { campusViewModel.dismissPendingConfirm() },
            onNotPaid = { campusViewModel.notPaid() },
        )
    }

    // 到账窗口超时（30 分钟没检测到）：这条链路此前是静默的——「正在确认到账」标识悄悄
    // 消失，用户不知道结果。给一句、看完即 dismiss 转回 Idle（只弹一次）
    LaunchedEffect(arrival) {
        if (arrival is CampusCardViewModel.ArrivalState.Timeout) {
            scope.postNotice(
                snackbar,
                "暂未检测到到账——充值常有延迟，到账后「消费流水」会自动显示",
                // 钱相关的不确定态用 Warning，别用播报用的 Info
                NoticeTone.Warning,
            )
            campusViewModel.dismissArrival()
        }
    }

    // 提示条：只画这一条，不铺背景（这个 Box 没有 pointerInput，点击照样穿透到页面）
    Box(Modifier.fillMaxSize()) {
        AppSnackbarHost(state = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // 收银台页放在最后：整屏、铺底，盖住上面所有弹层
    if (paymentUrl != null) {
        BankPayPage(
            url = paymentUrl,
            title = requests.paymentTitle,
            merchantReturnHost = requests.paymentMerchantHost,
            onMerchantReturn = { afterPaymentPageClosed() },
            onClose = { afterPaymentPageClosed() },
            openExternalUrl = { url -> platform.openExternalUrl(url) },
        )
    }
}

/**
 * 电费收银台「回到商户域」的判据 host（缴费平台）。
 *
 * **只认这个域**：收银台页面里连回商户的只有「返回商户」那一跳，认错会把用户从收银台里
 * 踢出去。一卡通收银台本身就在商户域上（`yktwx.juwp.edu.cn/payment/`），那边传 null。
 */
private const val POWER_MERCHANT_HOST = "charge.juwp.edu.cn"
