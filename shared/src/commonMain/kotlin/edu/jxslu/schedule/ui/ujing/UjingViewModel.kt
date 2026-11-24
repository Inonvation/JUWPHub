package edu.jxslu.schedule.ui.ujing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.prefs.recordGuessOrder
import edu.jxslu.schedule.data.ujing.UjingApiException
import edu.jxslu.schedule.data.ujing.UjingDraftStore
import edu.jxslu.schedule.data.ujing.UjingHistoryStore
import edu.jxslu.schedule.data.ujing.UjingHouse
import edu.jxslu.schedule.data.ujing.UjingHouseStore
import edu.jxslu.schedule.data.ujing.UjingOrderSnapshot
import edu.jxslu.schedule.data.ujing.UjingRepository
import edu.jxslu.schedule.data.ujing.UjingSessionExpiredException
import edu.jxslu.schedule.data.ujing.UjingStore
import edu.jxslu.schedule.data.ujing.UjingWashModel
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.domain.UjingState
import edu.jxslu.schedule.platform.UjingPay
import edu.jxslu.schedule.platform.UjingPayOutcome
import edu.jxslu.schedule.platform.UjingReminder
import edu.jxslu.schedule.platform.locked
import edu.jxslu.schedule.ui.common.NoticeFeedback
import edu.jxslu.schedule.ui.common.NoticeTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 看板一行：[line] 是洗衣机聚合，[dryerLine] 是烘干机聚合（各自为 null = 该店没有这类
 * 设备）；[failed] = 该店拉取失败，[error] 是失败原因（2026-10-21 起显示在行里，
 * 不再只写一句「获取失败」）。
 */
data class UjingBoardRow(
    val house: UjingHouse,
    val line: UjingState.BoardLine?,
    val failed: Boolean,
    val dryerLine: UjingState.BoardLine? = null,
    val error: String? = null,
)

/**
 * 扫码识别状态（主页只管「扫没扫成」）：空闲 → 识别中 → 失败。
 * 成功不留主页——开 [UjingDraftStore] 草稿并跳独立下单页（UJING_ORDER）。
 */
sealed interface UjingScanState {
    data object Idle : UjingScanState
    data object Parsing : UjingScanState
    data class Failed(val message: String) : UjingScanState
}

/** U净 页状态（DESIGN §3.23 / §4.37）。主页：登录 / 看板 / 扫码入口 / 订单卡 / 历史账单入口。 */
data class UjingUiState(
    // ── 登录 ──
    val loggedIn: Boolean = false,
    val mobile: String = "",
    val captcha: String = "",
    val codeSending: Boolean = false,
    val loggingIn: Boolean = false,
    /** 最近一次发送验证码的时刻（0 = 没发过）；60 秒冷却由 UI 按它算。 */
    val codeSentAt: Long = 0L,
    // ── 看板 ──
    val houses: List<UjingHouse> = emptyList(),
    val board: List<UjingBoardRow> = emptyList(),
    val boardRefreshing: Boolean = false,
    // ── 扫码 ──
    /** 主页只展示识别中 / 失败 / 「继续上次识别」；档位与投放选择在独立下单页。 */
    val scan: UjingScanState = UjingScanState.Idle,
    /** 历史账单笔数（入口行角标；0 = 还没有账单）。 */
    val historyCount: Int = 0,
    // ── 订单（P2） ──
    /** 在案订单（下单后出现；跨进程恢复）。null = 没有在案订单。 */
    val order: UjingOrderSnapshot? = null,
    val orderBusy: Boolean = false,
    /**
     * 支付宝 SDK 拉起期间为 true：收银台在那边、SDK 调用阻塞，这段时间订单卡动作
     * （取消/启动/重试支付）不该照常可点。与 [orderBusy] 分开——轮询静默刷新不碰它。
     */
    val paying: Boolean = false,
    // ── 附近洗衣房弹层 ──
    val pickerOpen: Boolean = false,
    val pickerLoading: Boolean = false,
    val pickerStores: List<UjingStore> = emptyList(),
    // ── 使用须知 ──
    val noticeVisible: Boolean = false,
)

/**
 * U净 页状态（DESIGN §4.37）。
 *
 * 主页只管：登录、看板、扫码入口（识别成功即开草稿并跳独立下单页）、在案订单卡与
 * 历史账单入口。档位 / 水温 / 投放的选择与确认在 UJING_ORDER 页，选择结果经
 * [UjingDraftStore.submit] 交回这里执行——**写操作仍由主页单点发起**（下单 → 支付宝
 * 拉起 → 支付确认），不因窗口切换换执行者。
 *
 * 会话过期的统一处理在 [handleFailure]：清 token、让登录态流把 UI 带回登录卡，
 * **不做任何自动重登**。写操作（下单 / 取消 / 启动）零自动重试——失败后先查
 * 在案订单，不盲目重发。
 *
 * iOS 移植 S21（2026-11-25）：搬进 `shared/commonMain`。原来构造里的 `appContext` /
 * `payActivityProvider` / `UjingDoneReminder` / `UjingAlipay` 四处 Android 专有依赖
 * 换成两条平台缝（[pay] / [reminder]），`Factory` 删掉——共享层没有 `viewModel()`，
 * 依赖由宿主从 `Services.current` 取好再传进来（`design/ios-port.md` §11.2 ①②③）。
 */
class UjingViewModel(
    private val repo: UjingRepository,
    private val houseStore: UjingHouseStore,
    /**
     * 账单仓：主页记录卡与历史账单页只读它（写入口在 [repo]，本类不碰）。
     *
     * 为什么放开成 `val`：记录卡展示「最近三笔」用的是与历史账单页**同一份**实例，
     * 为这三行再往 [UjingUiState] 抄一份列表等于同一份数据两个来源。
     */
    val historyStore: UjingHistoryStore,
    /**
     * 扫码草稿仓：主页扫码卡的「上次识别的设备」行只读它，写入口在本类与下单页。
     * 放开成 `val` 的理由同 [historyStore]。
     */
    val draftStore: UjingDraftStore,
    private val prefs: DisplayPrefsStore,
    /**
     * 支付宝支付缝；**null = 本平台没有支付宝 SDK**（iOS 待接）。
     *
     * 支付要 `Activity`，所以由宿主在组合期造一个传进来，而不是走 `AppServices`
     * （见 `UjingPay` 的 KDoc）。null 时下单链在**创建订单之前**就拦住，
     * 订单卡的「去支付 / 重试支付」也不渲染成可点按钮——机器被锁 2 分钟却没法付最坏。
     */
    private val pay: UjingPay? = null,
    /**
     * 洗衣完成提醒（闹钟 + 通知）；**null = 本平台没有这项能力**（iOS 待接）。
     * null 时排 / 撤提醒整体跳过，订单卡与 15 秒轮询照常工作。
     */
    private val reminder: UjingReminder? = null,
) : ViewModel() {

    /** 本平台能不能拉起支付宝：订单卡据此决定「去支付」是可点按钮还是禁用说明。 */
    val canPay: Boolean get() = pay != null

    private val _uiState = MutableStateFlow(UjingUiState())
    val uiState: StateFlow<UjingUiState> = _uiState.asStateFlow()

    /** 在途的看板刷新：连点「刷新」时取消上一轮，避免旧结果覆盖新结果。 */
    private var boardJob: Job? = null
    private var boardGeneration = 0

    /**
     * 一次性提示（Snackbar）。**带语气**：U净 这条通道里成功（登录 / 支付 / 启动 / 取消）
     * 与失败、待用户处理的话各占一档，UI 只按 [NoticeFeedback.tone] 选图标与强调色
     * （2026-11-16 修：此前一律 Warning，启动成功的勾成了警告三角，看着像出错）。
     */
    private val _events = Channel<NoticeFeedback>(Channel.BUFFERED)
    val events: Flow<NoticeFeedback> = _events.receiveAsFlow()

    /** 一次性导航：扫码识别成功 → 跳独立下单页。 */
    private val _openOrder = Channel<Unit>(Channel.BUFFERED)
    val openOrder: Flow<Unit> = _openOrder.receiveAsFlow()

    /**
     * 「今日页扫到机身码，但当时还没登录」留下的原文：登录成功后自动接着识别
     * （见 `repo.loggedIn` 收集器）。只放内存、只留一次，超 [PENDING_SCAN_TTL_MILLIS] 作废
     * —— 与草稿（[UjingDraftStore]）同一条口径：几秒到几分钟内的一次性交接，不落盘。
     */
    private var pendingScan: Pair<String, Long>? = null

    init {
        _uiState.update {
            it.copy(
                loggedIn = repo.loggedIn.value,
                mobile = repo.lastMobile(),
                houses = houseStore.list(),
                order = repo.currentOrder(),
            )
        }
        // 登录态跨窗口实时反映（对齐趣智 / 快趣的口径）。任何 false→true 的转变
        // （本页登录、别的窗口重登、会话恢复）都顺带补一次数据：
        // 在案订单对一次服务端、看板重拉——UI 不用再各写一遍。
        viewModelScope.launch {
            repo.loggedIn.collect { loggedIn ->
                _uiState.update {
                    it.copy(
                        loggedIn = loggedIn,
                        board = if (loggedIn) it.board else emptyList(),
                    )
                }
                if (loggedIn) {
                    // 进页补数据走静默：别让订单卡按钮在进页瞬间闪一下加载圈
                    repo.currentOrder()?.takeIf { order -> !order.isTerminal }
                        ?.let { order -> refreshOrder(order.orderId, silent = true) }
                    refreshBoard()
                    // 扫到机身码时还没登录：登录成功后接着把那次识别跑完，用户不用重扫
                    pendingScan?.let { (raw, at) ->
                        pendingScan = null
                        if (AppClock.nowMillis() - at <= PENDING_SCAN_TTL_MILLIS) {
                            onQrScanned(raw)
                        }
                    }
                } else {
                    reminder?.cancel()
                }
            }
        }
        // 在案订单跨窗口实时反映（快照存 Graph 单例的 OrderStore）
        viewModelScope.launch {
            repo.orderState.collect { order -> _uiState.update { it.copy(order = order) } }
        }
        // 历史账单笔数（入口行角标）
        viewModelScope.launch {
            historyStore.state.collect { list ->
                _uiState.update { it.copy(historyCount = list.size) }
            }
        }
        // 下单页交回的选择 → 立即执行下单链（确认动作已在下单页的弹层里完成）
        viewModelScope.launch {
            draftStore.submitted.collect { selection ->
                if (selection != null) {
                    draftStore.consumeSubmitted()
                    confirmPreparedOrder(selection)
                }
            }
        }
        // 恢复在案订单 / 进页拉看板不再单独写：上面的 loggedIn 收集器订阅 StateFlow
        // 会立即发射当前值，已登录时的订单对表与看板首拉都从那里走（单一来源）。
        // 使用须知：首次（或距上次确认超过一周）进页弹一次
        viewModelScope.launch {
            val seenAt = prefs.ujingNoticeSeenAt.first()
            val stale = AppClock.nowMillis() - seenAt > NOTICE_RESHOW_INTERVAL_MILLIS
            if (seenAt <= 0L || stale) {
                _uiState.update { it.copy(noticeVisible = true) }
            }
        }
    }

    // ── 登录 ──

    fun onMobileChange(value: String) {
        _uiState.update { it.copy(mobile = value.filter(Char::isDigit).take(11)) }
    }

    fun onCaptchaChange(value: String) {
        _uiState.update { it.copy(captcha = value.filter(Char::isDigit).take(8)) }
    }

    fun sendCode() {
        val state = _uiState.value
        if (!MOBILE_PATTERN.matches(state.mobile)) {
            notify("请输入 11 位手机号", NoticeTone.Warning)
            return
        }
        if (state.codeSending) return
        // 60 秒冷却：UI 按钮已按 codeSentAt 置灰，这里再兜一道边界
        if (state.codeSentAt > 0 && AppClock.nowMillis() - state.codeSentAt < CODE_COOLDOWN_MILLIS) {
            return
        }
        _uiState.update { it.copy(codeSending = true) }
        viewModelScope.launch {
            runCatching { repo.requestCaptcha(state.mobile) }
                .onSuccess {
                    _uiState.update {
                        it.copy(codeSending = false, codeSentAt = AppClock.nowMillis())
                    }
                    notify("验证码已发送", NoticeTone.Success)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(codeSending = false) }
                    notify(error.message ?: "验证码发送失败，请稍后再试", NoticeTone.Error)
                }
        }
    }

    fun login() {
        val state = _uiState.value
        if (!MOBILE_PATTERN.matches(state.mobile)) {
            notify("请输入 11 位手机号", NoticeTone.Warning)
            return
        }
        if (state.captcha.isBlank()) {
            notify("请输入短信验证码", NoticeTone.Warning)
            return
        }
        if (state.loggingIn) return
        _uiState.update { it.copy(loggingIn = true) }
        viewModelScope.launch {
            runCatching { repo.login(state.mobile, state.captcha) }
                .onSuccess {
                    // 看板重拉与订单对表由 loggedIn 收集器统一接（登录态一变就发）
                    _uiState.update { it.copy(loggingIn = false, captcha = "") }
                    notify("登录成功", NoticeTone.Success)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loggingIn = false) }
                    notify(error.message ?: "登录失败，请检查验证码", NoticeTone.Error)
                }
        }
    }

    fun logout() {
        repo.logout()
        reminder?.cancel()
        notify("已退出登录", NoticeTone.Info)
    }

    // ── 空闲看板 ──

    /**
     * 下拉刷新（2026-11-05，用户要求 U净 也支持）：看板与在案订单一起重拉。
     *
     * 没有在案订单时 [refreshOrder] 自己早退，不会白发请求；看板那套取消 / 代次语义
     * 原样复用 [refreshBoard]，这里不另起一条刷新链。
     */
    fun refreshAll() {
        refreshBoard()
        refreshOrder(silent = true)
    }

    /**
     * 拉取全部收藏店的空闲数据（2026-10-22 定稿）：`devices/reserve` 在本校被服务端
     * 关闭（回 1614「暂不支持预约，请扫码洗衣」），改走 `stores/near?keyword=<店名>`
     * 的 `storeInfo[]`——一店一条，按店名精确命中，不受附近列表截断影响。
     *
     * 多店并发（上限 [BOARD_CONCURRENCY]）：串行时收藏几家就要依次打几条，刷新要等好几秒。
     * 单店失败只让那一行显示失败原因，不打断整板；同一轮里会话过期只提示一次。
     * 连点刷新的旧任务会被取消，且只有最新一轮能写回结果（`boardGeneration`）。
     */
    fun refreshBoard() {
        val houses = houseStore.list()
        _uiState.update { it.copy(houses = houses) }
        if (houses.isEmpty()) {
            boardJob?.cancel()
            // 让被取消那一轮的 generation 失效：它不该在清空之后再把旧 rows 写回去
            boardGeneration++
            _uiState.update { it.copy(board = emptyList(), boardRefreshing = false) }
            return
        }
        boardJob?.cancel()
        val generation = ++boardGeneration
        _uiState.update { it.copy(boardRefreshing = true) }
        boardJob = viewModelScope.launch {
            try {
                // 「同一轮只提示一次会话过期」：并发块里各店各判一次，用一个布尔收口。
                // 原来这里是 `AtomicBoolean`（JVM 专有）；并发块都收敛在 viewModelScope
                // 的主线程调度上，换平台同步缝 `locked` 语义等价（iOS 侧不锁，见它的 KDoc）。
                val expiryGuard = Any()
                var expiryNotified = false
                val limiter = Semaphore(BOARD_CONCURRENCY)
                val rows = coroutineScope {
                    houses.map { house ->
                        async {
                            try {
                                val groups = limiter.withPermit { repo.storeCounts(house.name) }
                                UjingBoardRow(
                                    house = house,
                                    line = repo.boardLine(groups),
                                    failed = false,
                                    dryerLine = repo.dryerLine(groups),
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (error: Exception) {
                                // 401 一次就清会话；并发下别让每家店各弹一条「登录已过期」
                                if (error is UjingSessionExpiredException) {
                                    val first = locked(expiryGuard) {
                                        if (expiryNotified) {
                                            false
                                        } else {
                                            expiryNotified = true
                                            true
                                        }
                                    }
                                    if (first) handleFailure(error)
                                }
                                UjingBoardRow(
                                    house = house,
                                    line = null,
                                    failed = true,
                                    error = error.message?.takeIf { it.isNotBlank() } ?: "未知错误",
                                )
                            }
                        }
                    }.awaitAll()
                }
                if (generation == boardGeneration) {
                    _uiState.update { it.copy(board = rows, boardRefreshing = false) }
                }
            } finally {
                // 被新一轮取消时不碰 boardRefreshing：那一轮已经把它置 true 了
                if (generation == boardGeneration) {
                    _uiState.update { it.copy(boardRefreshing = false) }
                }
            }
        }
    }

    fun removeHouse(house: UjingHouse) {
        houseStore.remove(house.storeId)
        _uiState.update { it.copy(houses = houseStore.list()) }
        refreshBoard()
    }

    // ── 附近洗衣房（配置收藏） ──

    /** 打开弹层进入加载态（定位由页面取好后调 [loadNearby] 填充）。 */
    fun openPicker() {
        _uiState.update {
            it.copy(pickerOpen = true, pickerLoading = true, pickerStores = emptyList())
        }
    }

    fun loadNearby(lat: Double, lng: Double) {
        viewModelScope.launch {
            runCatching { repo.nearbyStores(lat, lng) }
                .onSuccess { stores ->
                    _uiState.update { it.copy(pickerLoading = false, pickerStores = stores) }
                }
                .onFailure { error ->
                    handleFailure(error)
                    _uiState.update { it.copy(pickerLoading = false) }
                    notify(error.message ?: "附近洗衣房获取失败，请稍后再试", NoticeTone.Error)
                }
        }
    }

    fun closePicker() {
        _uiState.update { it.copy(pickerOpen = false) }
    }

    fun addHouse(store: UjingStore) {
        val id = store.id ?: return
        houseStore.add(UjingHouse(storeId = id, name = store.name))
        _uiState.update { it.copy(houses = houseStore.list()) }
        notify("已添加「${store.name}」", NoticeTone.Success)
        refreshBoard()
    }

    // ── 扫码识别 ──

    /**
     * 二维码原文（相机或相册）→ 设备信息 + 套餐。成功即开草稿并请 UI 跳独立下单页
     * （档位 / 水温 / 投放的选择都在那一页，主页不内联展示）。
     *
     * 调用方两处：页内扫码（宿主的扫码窗口回灌原文）与今日页侧栏「扫一扫」
     * （`scanRaw` 令牌），两条都走到这里，不各写一份识别链。
     */
    fun onQrScanned(raw: String) {
        if (raw.isBlank() || _uiState.value.scan is UjingScanState.Parsing) return
        // 未登录先拦一道：机身码的识别接口要 token（匿名回 401），直接发出去只会得到
        // 「登录已过期」这种驴唇不对马嘴的提示——今日页的扫一扫把它带进来时用户
        // 可能压根没用过 U净，让人回页面上登录才是出路
        if (!repo.loggedIn.value) {
            // 留着原文：登录成功后自动接着识别（见 init 里的 loggedIn 收集器）
            pendingScan = raw to AppClock.nowMillis()
            notify("识别洗衣机需要先登录 U净（手机号 + 短信验证码）", NoticeTone.Warning)
            return
        }
        pendingScan = null
        _uiState.update { it.copy(scan = UjingScanState.Parsing) }
        viewModelScope.launch {
            runCatching {
                val scan = repo.scanWasher(raw)
                val deviceId = scan.deviceId?.takeIf { it.isNotBlank() }
                    ?: throw UjingApiException("未识别到设备信息，请确认扫的是洗衣机机身码")
                val program = repo.programInfo(deviceId)
                scan to program
            }
                .onSuccess { (scan, program) ->
                    draftStore.open(UjingDraftStore.Draft(scan, program))
                    _uiState.update { it.copy(scan = UjingScanState.Idle) }
                    _openOrder.trySend(Unit)
                }
                .onFailure { error ->
                    handleFailure(error)
                    _uiState.update {
                        it.copy(scan = UjingScanState.Failed(error.message ?: "识别失败，请重试"))
                    }
                }
        }
    }

    // ── 订单链（P2） ──

    /**
     * 订单链的统一忙态：请求在跑（[UjingUiState.orderBusy]）**或支付宝 SDK 阻塞中**
     * （[UjingUiState.paying]）。所有写入口的守卫都看它——paying 只禁了 UI 按钮、
     * 守卫不认的话，草稿收集器这类非按钮调用点会在支付窗口内绕过禁用发起写操作。
     */
    private val orderChainBusy: Boolean
        get() = _uiState.value.orderBusy || _uiState.value.paying

    /**
     * 下单页交回选择后的执行入口（二次确认弹层在下单页里，用户点「去支付」才走到这）。
     * 守卫：在案订单不允许叠单（写操作红线）、忙态拒发。
     *
     * **终结态订单不挡新单**：完成 / 取消 / 超时 / 失败的快照要留到用户点「知道了」，
     * 但业务上它已不在案——洗完后一直拦新单就是 bug。快照还可能停在旧值「洗涤中」
     * （轮询只在 U净 页可见时跑），所以有非终结快照时先向服务端对一次：服务端说结束了
     * 就放行，仍是进行中才拦；刷新失败保守拦住，不冒险重发。
     */
    fun confirmPreparedOrder(selection: UjingDraftStore.OrderSelection) {
        if (orderChainBusy) return
        val current = _uiState.value.order
        if (current == null || current.isTerminal) {
            createAndPay(
                model = selection.model,
                dryTimeMinutes = selection.dryTimeMinutes,
                washTemperatureId = selection.temperatureId,
                addDetergent = selection.addDetergent,
                addDisinfectant = selection.addDisinfectant,
            )
            return
        }
        _uiState.update { it.copy(orderBusy = true) }
        viewModelScope.launch {
            runCatching { repo.refreshOrder(current.orderId) }
                .onSuccess { snapshot ->
                    _uiState.update { it.copy(orderBusy = false) }
                    if (snapshot.isTerminal) {
                        createAndPay(
                            model = selection.model,
                            dryTimeMinutes = selection.dryTimeMinutes,
                            washTemperatureId = selection.temperatureId,
                            addDetergent = selection.addDetergent,
                            addDisinfectant = selection.addDisinfectant,
                        )
                    } else {
                        notify("已有进行中的订单，请先完成或取消", NoticeTone.Warning)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(orderBusy = false) }
                    handleFailure(error)
                    // 会话过期的提示已由 handleFailure 发过，别叠第二条
                    if (error !is UjingSessionExpiredException) {
                        notify(error.message ?: "订单状态获取失败，请稍后重试", NoticeTone.Error)
                    }
                }
        }
    }

    /** orders/create → 立即请求支付参数并拉起支付宝。 */
    private fun createAndPay(
        model: UjingWashModel,
        dryTimeMinutes: Int?,
        washTemperatureId: Int?,
        addDetergent: Boolean,
        addDisinfectant: Boolean,
    ) {
        val draft = draftStore.draft.value ?: return
        // 付不了就别建单：`orders/create` 受理即锁机 2 分钟，没支付通道等于把机器占住
        // 又付不了钱（iOS 侧 pay 为 null）。在创建之前拦住，草稿留着，用户换平台再下。
        if (pay == null) {
            notify("当前平台不支持支付宝支付，未创建订单", NoticeTone.Warning)
            return
        }
        _uiState.update { it.copy(orderBusy = true) }
        viewModelScope.launch {
            // 下单与取支付参数分两段 try：createOrder 受理成功后订单已建、机器已锁
            // 2 分钟、快照与账单已落（repo.createOrder 内部），此时取参数再失败绝不能
            // 报「下单失败」诱导重发——把订单交给订单卡，让用户从「重试支付」续上。
            val created = runCatching {
                repo.createOrder(
                    scan = draft.scan,
                    program = draft.program,
                    model = model,
                    washTemperatureId = washTemperatureId,
                    addDetergent = addDetergent,
                    addDisinfectant = addDisinfectant,
                    dryTimeMinutes = dryTimeMinutes,
                )
            }
            val snapshot = created.getOrElse { error ->
                handleFailure(error)
                _uiState.update { it.copy(orderBusy = false) }
                notify(error.message ?: "下单失败，请稍后重试", NoticeTone.Error)
                return@launch
            }
            // 「猜你想用」：下单受理 = 一次真实订单（支付重试不会再走 createOrder，不会重复）。
            // 原来是 `Graph.recordGuessOrder`（:app）；偏好存储已在共享层，直接走它。
            runCatching { prefs.recordGuessOrder(GuessTarget.UJING) }
            val paid = runCatching {
                // 下单即拉支付参数（2 分钟独占期宝贵，少一次用户等待）
                repo.paymentOrderInfo(snapshot.orderId)
            }
            paid.fold(
                onSuccess = { orderInfo ->
                    _uiState.update { it.copy(orderBusy = false, order = snapshot) }
                    draftStore.close()
                    notify("下单成功，请在 2 分钟内完成支付", NoticeTone.Success)
                    launchPay(snapshot.orderId, orderInfo)
                },
                onFailure = { error ->
                    handleFailure(error)
                    _uiState.update { it.copy(orderBusy = false, order = snapshot) }
                    draftStore.close()
                    notify(error.message ?: "下单成功，但支付参数获取失败，可在订单卡重试支付", NoticeTone.Warning)
                },
            )
        }
    }

    /** 支付宝 SDK 拉起（阻塞等结果），结束后回查支付状态。 */
    private fun launchPay(orderId: String, orderInfo: String) {
        val gateway = pay
        if (gateway == null) {
            // 走不到（createAndPay / retryPay 都先拦了），留一道防御免得静默卡在 busy
            notify("当前平台不支持支付宝支付", NoticeTone.Error)
            return
        }
        // paying 态：SDK 阻塞在支付宝界面/收银台的整个窗口里，订单卡动作不该照常可点
        // （此前 busy 在拉起前就复位，支付窗口内「取消/启动」全程可点，点取消会和支付结果赛跑）
        _uiState.update { it.copy(paying = true) }
        // 切后台线程由缝的实现方负责（Android 是 `PayTask.payV2` 的阻塞调用）——
        // 原来这里写 `Dispatchers.IO`，那是 JVM 专有，iOS 侧也没有它
        viewModelScope.launch {
            val result = gateway.pay(orderInfo)
            _uiState.update { it.copy(paying = false) }
            when (result.outcome) {
                UjingPayOutcome.Success -> {
                    notify("支付成功", NoticeTone.Success)
                    runCatching { repo.confirmPayment(orderId) }
                        .onFailure { handleFailure(it) }
                }
                UjingPayOutcome.Pending -> {
                    // 8000 = 结果确认中：钱可能已扣，按失败报会诱导用户重付造成重复扣款。
                    // 先双查订单状态（confirmPayment = lastPayStatus + detail），以服务端为准。
                    val settled = runCatching { repo.confirmPayment(orderId) }
                    settled.fold(
                        onSuccess = { snapshot ->
                            if (snapshot.paid) notify("支付成功", NoticeTone.Success)
                            else notify("支付确认中，请稍后在订单卡刷新查看", NoticeTone.Info)
                        },
                        onFailure = { error ->
                            handleFailure(error)
                            // 双查也失败：快照停在 paid=false，订单卡停在「去支付」且没有
                            // 自动恢复（轮询只在页面可见时跑）——补一次对表兜底
                            runCatching { repo.refreshOrder(orderId) }.onFailure { handleFailure(it) }
                            notify("支付确认中，请稍后在订单卡刷新查看", NoticeTone.Info)
                        },
                    )
                }
                UjingPayOutcome.Canceled -> {
                    notify("已取消支付，订单保留 2 分钟，可在订单卡重试", NoticeTone.Warning)
                    runCatching { repo.refreshOrder(orderId) }.onFailure { handleFailure(it) }
                }
                UjingPayOutcome.Failed -> {
                    notify("支付失败（${result.memo}），可在订单卡重试", NoticeTone.Error)
                    runCatching { repo.refreshOrder(orderId) }.onFailure { handleFailure(it) }
                }
            }
        }
    }

    /** 订单卡「重试支付」：重新取支付参数并拉起。 */
    fun retryPay() {
        val order = _uiState.value.order ?: return
        if (orderChainBusy) return
        if (pay == null) {
            notify("当前平台不支持支付宝支付", NoticeTone.Warning)
            return
        }
        _uiState.update { it.copy(orderBusy = true) }
        viewModelScope.launch {
            runCatching { repo.paymentOrderInfo(order.orderId) }
                .onSuccess { orderInfo ->
                    _uiState.update { it.copy(orderBusy = false) }
                    launchPay(order.orderId, orderInfo)
                }
                .onFailure { error ->
                    handleFailure(error)
                    _uiState.update { it.copy(orderBusy = false) }
                    notify(error.message ?: "支付参数获取失败", NoticeTone.Error)
                }
        }
    }

    /**
     * 刷新在案订单（订单卡「刷新」= [silent] = false 置 busy；**15 秒轮询走 silent**，
     * 不置 busy——不然后台轮询每 15 秒把「去支付/启动」闪一次加载圈、周期性禁用）。
     */
    fun refreshOrder(orderId: String? = null, silent: Boolean = false) {
        val id = orderId ?: _uiState.value.order?.orderId ?: return
        if (orderChainBusy) return
        if (!silent) _uiState.update { it.copy(orderBusy = true) }
        viewModelScope.launch {
            runCatching { repo.refreshOrder(id) }
                .onSuccess { snapshot ->
                    _uiState.update { it.copy(orderBusy = false) }
                    // 洗衣完成提醒：
                    // - 洗涤中 → 按「快照时刻 + 剩余」排精确闹钟（每次刷新校准落点）；
                    // - 已支付未运行（20/21/22/35）→ 按「快照时刻 + 模式总时长」排兜底闹钟：
                    //   用户付完/点完启动就可能离开页面，21→40 的转变没人看着；兜底闹钟
                    //   常常早于真实结束，到点 check 会按最新剩余重排，早响比不响好；
                    // - 终结态 → 就地撤销（完成那条由闹钟落点的 check 自己发）。
                    when {
                        snapshot.isTerminal -> reminder?.cancel()
                        snapshot.status == "40" && snapshot.remainSeconds > 0 ->
                            reminder?.schedule(
                                snapshot.orderId,
                                snapshot.snapshotAt + snapshot.remainSeconds * 1000L,
                            )
                        snapshot.paid && snapshot.durationSeconds > 0 ->
                            reminder?.schedule(
                                snapshot.orderId,
                                snapshot.snapshotAt + snapshot.durationSeconds * 1000L,
                            )
                    }
                }
                .onFailure { error ->
                    handleFailure(error)
                    _uiState.update { it.copy(orderBusy = false) }
                    // 轮询失败静默（单次失败不值得打扰，下一轮 15 秒就重试）；手动刷新才报错
                    if (!silent) notify(error.message ?: "订单状态获取失败", NoticeTone.Error)
                }
        }
    }

    /** 取消订单（二次确认在 UI；这里只拦忙态）。 */
    fun cancelOrder() {
        val order = _uiState.value.order ?: return
        if (orderChainBusy) return
        if (!UjingState.canCancel(order.status)) {
            notify("当前状态不可取消（机器已锁定，等它跑完即可）", NoticeTone.Warning)
            return
        }
        _uiState.update { it.copy(orderBusy = true) }
        viewModelScope.launch {
            runCatching { repo.cancelOrder(order.orderId) }
                .onSuccess {
                    _uiState.update { it.copy(orderBusy = false) }
                    reminder?.cancel()
                    notify("订单已取消，机器已释放", NoticeTone.Success)
                }
                .onFailure { error ->
                    handleFailure(error)
                    _uiState.update { it.copy(orderBusy = false) }
                    notify(error.message ?: "取消失败，请刷新订单状态后重试", NoticeTone.Error)
                }
        }
    }

    /** 云端启动（订单窗口内可点；蓝牙机型由 UI 提示靠近机器后再按）。 */
    fun startOrder() {
        val order = _uiState.value.order ?: return
        if (orderChainBusy) return
        if (!UjingState.canStart(order.status)) {
            notify("当前状态不可启动", NoticeTone.Warning)
            return
        }
        _uiState.update { it.copy(orderBusy = true) }
        viewModelScope.launch {
            runCatching { repo.startOrder(order.orderId) }
                .onSuccess {
                    _uiState.update { it.copy(orderBusy = false) }
                    // 文案口径（2026-11-16 用户反馈）：受理判定是「服务端已接下指令」（code==0 或
                    // 1703+errorCode==0，见 UjingState.commandAccepted），对用户就是启动这一步成了；
                    // 原来那句「启动指令已受理」像系统日志，还配警告图标，读着像出错。机器真正转起来
                    // 由随后 refreshOrder 的状态行呈现，所以这里补一句「留意机器是否运转」而不是断言已转。
                    notify("启动成功，请留意洗衣机是否开始运转", NoticeTone.Success)
                    refreshOrder(order.orderId)
                }
                .onFailure { error ->
                    handleFailure(error)
                    _uiState.update { it.copy(orderBusy = false) }
                    notify(error.message ?: "启动失败，请刷新订单状态后重试", NoticeTone.Error)
                }
        }
    }

    /** 终结态订单卡上的「知道了」：清掉在案快照 + 收摊（撤闹钟 / 撤已发通知 / 撤免扰挂起条）。 */
    fun dismissOrder() {
        repo.dismissOrder()
        reminder?.revoke()
        _uiState.update { it.copy(order = null) }
    }

    // ── 使用须知 ──

    fun onNoticeConfirmed() {
        _uiState.update { it.copy(noticeVisible = false) }
        viewModelScope.launch { prefs.setUjingNoticeSeenAt(AppClock.nowMillis()) }
    }

    // ── 内部 ──

    private fun handleFailure(error: Throwable) {
        if (error is UjingSessionExpiredException) {
            repo.markExpired()
            notify(SESSION_EXPIRED_NOTICE, NoticeTone.Warning)
        }
    }

    /**
     * 一次性提示的唯一出口。[tone] **故意不给默认值**：新加一条提示必须当场决定语气，
     * 免得又退回「这条通道一律 Warning」那种成功顶着警告三角的观感（2026-11-16）。
     */
    private fun notify(message: String, tone: NoticeTone) {
        _events.trySend(NoticeFeedback(message, tone))
    }

    private companion object {
        val MOBILE_PATTERN = Regex("^1\\d{10}$")

        /** 发码冷却（服务端同样有 60 秒限制，两边口径一致）。 */
        const val CODE_COOLDOWN_MILLIS = 60_000L

        /** 使用须知静默期：确认后一周内不再弹。 */
        const val NOTICE_RESHOW_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000

        /**
         * 「扫到机身码时还没登录」留住的原文有效期：超过就作废。
         * 登录流程正常在几分钟内走完；留太久的话，用户在别处登录也会被推进一次识别。
         */
        const val PENDING_SCAN_TTL_MILLIS = 10L * 60 * 1000

        /** 看板并发上限（收在 OkHttp 连接池可承受的范围）。 */
        const val BOARD_CONCURRENCY = 4

        /** 订单进行中且页面可见时的轮询间隔（离开页面即停，无后台轮询）。 */
        const val ORDER_POLL_INTERVAL_MILLIS = 15_000L

        /**
         * 会话过期的统一文案，**逐字等于** `:app` 的 `SessionExpiryStore.NOTICE_TEXT`。
         *
         * 为什么抄一份：那个常量所在的类介质是 `SharedPreferences` + `Context`，留在 `:app`
         * 搬不动，共享层看不见它。两处必须一字不差——同一条「登录已过期」在别的页面
         * （胖乖 / 趣智 / 快趣 / 问渠）由 `SessionExpiredNotice` 用那个常量发，这里由
         * `handleFailure` 发。改文案时两边一起改。
         */
        const val SESSION_EXPIRED_NOTICE = "登录状态已过期，请重新登录"
    }
}
