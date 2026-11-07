package edu.jxslu.schedule.ui.water

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.data.qiekj.BalanceData
import edu.jxslu.schedule.data.qiekj.DeviceItem
import edu.jxslu.schedule.data.qiekj.OrderHistoryItem
import edu.jxslu.schedule.data.qiekj.QiekjDailySignInResult
import edu.jxslu.schedule.data.qiekj.QiekjRepository
import edu.jxslu.schedule.data.qiekj.QiekjWateringSession
import edu.jxslu.schedule.data.qiekj.QiekjWateringStore
import edu.jxslu.schedule.data.qiekj.ServerOrderItem
import edu.jxslu.schedule.data.qiekj.TokenExpiredException
import edu.jxslu.schedule.data.qiekj.UnlockException
import edu.jxslu.schedule.data.qiekj.UnlockStopSignal
import edu.jxslu.schedule.data.qiekj.UnlockStoppedException
import edu.jxslu.schedule.data.qiekj.shouldReportFlowFailure
import edu.jxslu.schedule.data.session.SessionExpiryStore
import edu.jxslu.schedule.domain.UnlockFlowState
import edu.jxslu.schedule.domain.UnlockResult
import edu.jxslu.schedule.domain.calculateActualCost
import edu.jxslu.schedule.domain.InboxKeys
import edu.jxslu.schedule.domain.InboxKind
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.widget.WaterWidgetSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class WaterUiState(
    // ── 登录 ──
    val loggedIn: Boolean = false,
    val phone: String = "",
    val code: String = "",
    val phoneError: String? = null,
    val sendingCode: Boolean = false,
    val loggingIn: Boolean = false,
    val showTokenLogin: Boolean = false,
    val tokenLoginInput: String = "",
    val tokenLoggingIn: Boolean = false,
    // ── 资产 / 设备 ──
    val balance: BalanceData? = null,
    val devices: List<DeviceItem> = emptyList(),
    val selectedDevice: DeviceItem? = null,
    val loadingDevices: Boolean = false,
    val loadingBalance: Boolean = false,
    /** 余额是否至少拉过一次（成功失败都算）：卡片副行区分「读取中」与「拉过但没有」用。 */
    val balanceLoaded: Boolean = false,
    /** 下拉刷新进行中（余额 + 设备两轮都算）；进页首载不算，别拿它当 loading 用。 */
    val refreshing: Boolean = false,
    /**
     * 账户手机号：取自仓库记住的登录手机号（短信登录成功时落盘）。Token 登录不更新它，
     * 沿用记住的值；没有就不显示。与登录输入框的 [phone] 是两回事——那个值用户随时在改。
     */
    val accountPhone: String = "",
    val showPhone: Boolean = false,
    // ── 开水流程 ──
    val flow: UnlockFlowState = UnlockFlowState.Idle,
    val usePoints: Boolean = true,
    // ── 订单 ──
    val orderHistory: List<OrderHistoryItem> = emptyList(),
    // ── 服务端账单（order/list，2026-10-14）：账号下全部订单，与本地快照互补 ──
    val serverOrders: List<ServerOrderItem> = emptyList(),
    val loadingServerOrders: Boolean = false,
    /** 服务端账单是否已成功拉过一次（进弹层按需拉，已拉过不重复）。 */
    val serverOrdersLoaded: Boolean = false,
    /** 正在查看的服务端账单明细（点了单笔后拉到）；null 表示没有打开的明细。 */
    val serverOrderDetail: UnlockResult? = null,
    // ── 每日签到（2026-10-23）：手动或打开软件自动触发；当天已签不再发写请求 ──
    val signInRunning: Boolean = false,
    val signInResult: QiekjDailySignInResult? = null,
    val signInError: String? = null,
    /**
     * 今天是否已签到（服务端 `signin/todaySignIn` 的判定）：null = 还没问过 / 问失败。
     * 自动签到在主窗口 onResume 就跑完了，本页的 [signInResult] 永远是空——不向服务端问
     * 一次，签到行会一直显示「签到」（2026-11-07 用户报障）。
     */
    val signInTodaySigned: Boolean? = null,
)

sealed interface WaterEvent {
    /** 一次性结果提示（登录/验证码/出水超时/查询失败…）；[tone] 决定页面提示卡的语气。 */
    data class Notice(val text: String, val tone: NoticeTone) : WaterEvent
}

/**
 * 胖乖生活页状态（DESIGN §4.10）。
 *
 * 开水流程生命周期与参考实现对齐：
 * - Mutex 防重入，进行中再点直接忽略；
 * - Working 阶段计时；165s（AUTO_SETTLE_SECONDS）仍在 Working → 视为服务端超时自动关阀，
 *   状态归 Idle 并刷新余额（此时轮询协程仍在后台收尾，完成后 Success 会重新落卡）；
 * - TokenExpiredException 统一清登录态并弹 Toast，不进 Failed 状态机。
 */
class WaterViewModel(
    private val repo: QiekjRepository,
    /**
     * 「正在出水」的落盘事实（2026-10-21）：开阀成功登记、结算/失败/超时清掉。
     * 今日页服务卡与开水页各持一份 ViewModel，只有它能在两个窗口之间同步状态。
     */
    private val wateringStore: QiekjWateringStore,
    /**
     * 是否在 init 里自动拉余额与设备。侧栏那份实例传 false：冷启动没人看，等侧栏展开
     * 或开水面板打开时由宿主显式刷新；页面实例保持默认 true，进页行为不变。
     */
    private val autoLoad: Boolean = true,
) : ViewModel() {

    private val unlockMutex = Mutex()
    private var unlockJob: Job? = null
    private var timerJob: Job? = null

    /**
     * 165 秒兜底。2026-11-06 起由 `onStep`（仓库的 IO 上下文）首次进入 Working 时启动，
     * 写入在 IO、`cancelFlowJobs` 读取在主线程——加 `@Volatile` 保证取消看得到最新引用
     * （漏取消只会多一个到点自检就退出的空 job，但仍不该留这个缝）。
     */
    @Volatile
    private var timeoutJob: Job? = null

    /** 当前这轮开水的手动结束开关（「结束用水」置位，仓库在轮询里看到就收尾）。 */
    private var stopSignal: UnlockStopSignal? = null

    /**
     * 本轮是否已在**软件层面**结束（2026-11-06 面板「结束用水」，见 [endSessionLocally]）。
     * 置位后仓库的步骤回执不再改界面；迟到的失败 / 未确认静默收尾
     * （[shouldReportFlowFailure]），结算成功仍照常落 Success——账单照样显示。
     * 每次 [unlock] 重新置 false。
     */
    @Volatile
    private var endedLocally = false

    private var codeSentAt = 0L

    private val _uiState = MutableStateFlow(
        WaterUiState(
            // 初始值取自仓库那条流，[syncLoginState] 的相等判断才有意义（两处口径一致）
            loggedIn = repo.loggedIn.value,
            phone = repo.readPhone() ?: "",
            accountPhone = repo.readPhone().orEmpty(),
            orderHistory = if (repo.loggedIn.value) repo.orderHistory() else emptyList(),
        ),
    )
    val uiState: StateFlow<WaterUiState> = _uiState.asStateFlow()

    private val _events = Channel<WaterEvent>(Channel.BUFFERED)

    /**
     * 一次性提示事件。页面（开水页 / 今日页开水卡）负责收集并展示——
     * 两边都必须有收集者，否则事件会留在缓冲里直到下一次有人收，
     * 或随 ViewModel 一起消失（今日页此前就不收，出水超时与登录失效在那里是静默的）。
     */
    val events = _events.receiveAsFlow()

    /** 提示（默认中性语气）。失败路径一律给 [NoticeTone.Error]，见各调用点。 */
    private fun notice(text: String, tone: NoticeTone = NoticeTone.Info) =
        _events.trySend(WaterEvent.Notice(text, tone))

    init {
        // 登录态以仓库的 [QiekjRepository.loggedIn] 为准：今日页开水卡与开水页各持一份
        // WaterViewModel，本 VM 自己登录/退出会就地改 uiState，另一份只能从这条流得知。
        viewModelScope.launch {
            repo.loggedIn.collect { syncLoginState(it) }
        }
        if (autoLoad && _uiState.value.loggedIn) {
            refreshBalance()
            refreshDevices()
            refreshSignInState()
        }
    }

    /**
     * 登录态翻转的统一落点。相等直接跳过——本 VM 自己走的登录/退出路径已经就地改过
     * uiState，不设这道闸会把余额与设备各多发一轮。
     */
    private fun syncLoginState(loggedIn: Boolean) {
        if (loggedIn == _uiState.value.loggedIn) return
        if (loggedIn) {
            _uiState.update {
                it.copy(loggedIn = true, accountPhone = repo.readPhone().orEmpty(), orderHistory = repo.orderHistory())
            }
            refreshBalance()
            refreshDevices()
            refreshSignInState()
        } else {
            cancelFlowJobs()
            _uiState.update { WaterUiState() }
            // 桌面胖乖开水卡（DESIGN §3.6「开水两卡」）跟着切回未登录形态；
            // 登录方向的推送由 refreshBalance 成功接管，这里不重复取数
            viewModelScope.launch {
                runCatching { WaterWidgetSync.refreshQiekjWidget(Graph.appContext) }
            }
        }
    }

    // ── 登录 ──

    fun updatePhone(value: String) = _uiState.update {
        val digits = value.filter(Char::isDigit).take(11)
        it.copy(phone = digits, phoneError = if (digits.isNotEmpty() && digits.length < 11) "请输入 11 位手机号" else null)
    }

    fun updateCode(value: String) = _uiState.update { it.copy(code = value.filter(Char::isDigit).take(6)) }

    fun toggleTokenLogin() = _uiState.update {
        it.copy(showTokenLogin = !it.showTokenLogin, tokenLoginInput = "")
    }

    fun updateTokenLoginInput(value: String) = _uiState.update { it.copy(tokenLoginInput = value) }

    fun sendCode() = viewModelScope.launch {
        val phone = _uiState.value.phone.trim()
        val elapsed = System.currentTimeMillis() - codeSentAt
        if (elapsed < 60_000) {
            notice("验证码已发送，请 ${(60 - elapsed / 1000).toInt()} 秒后再试", NoticeTone.Warning)
            return@launch
        }
        if (phone.length != 11) {
            _uiState.update { it.copy(phoneError = "请输入 11 位手机号") }
            return@launch
        }
        runCatching {
            _uiState.update { it.copy(sendingCode = true) }
            repo.sendCode(phone)
        }.onSuccess {
            codeSentAt = System.currentTimeMillis()
            notice("验证码已发送", NoticeTone.Success)
        }.onFailure {
            notice(it.message ?: "验证码发送失败", NoticeTone.Error)
        }
        _uiState.update { it.copy(sendingCode = false) }
    }

    fun login() = viewModelScope.launch {
        val s = _uiState.value
        if (s.phone.length != 11) {
            _uiState.update { it.copy(phoneError = "请输入 11 位手机号") }
            return@launch
        }
        if (s.code.isBlank()) {
            notice("请输入验证码", NoticeTone.Warning)
            return@launch
        }
        runCatching {
            _uiState.update { it.copy(loggingIn = true) }
            repo.login(s.phone, s.code)
        }.onSuccess {
            repo.savePhone(s.phone)
            // syncLoginState(true) 可能在 savePhone 前就被流翻转触发、读到旧手机号，这里以刚登录的为准
            _uiState.update { it.copy(accountPhone = s.phone) }
            onLoginSuccess("登录成功")
        }.onFailure {
            _uiState.update { it.copy(loggingIn = false) }
            notice(it.message ?: "登录失败", NoticeTone.Error)
        }
    }

    fun loginWithToken() = viewModelScope.launch {
        val token = _uiState.value.tokenLoginInput.trim()
        if (token.isBlank()) {
            notice("请输入 Token", NoticeTone.Warning)
            return@launch
        }
        runCatching {
            _uiState.update { it.copy(tokenLoggingIn = true) }
            // 先验后存：存了就会翻转共享登录态（今日页开水卡立刻切形态），验失败再回滚是可见闪跳
            repo.validateToken(token)
            repo.saveToken(token)
        }.onSuccess {
            onLoginSuccess("登录成功")
        }.onFailure {
            repo.logout()
            _uiState.update { it.copy(tokenLoggingIn = false) }
            notice(it.message ?: "Token 无效或已过期", NoticeTone.Error)
        }
    }

    /**
     * 登录成功的收尾（清表单 + 提示）。**不在这里改 `loggedIn`、也不拉余额/设备**：
     * token 一落盘 [syncLoginState] 就接手了，两处都做会让每次登录多发两轮请求。
     */
    private fun onLoginSuccess(message: String) {
        _uiState.update {
            it.copy(
                loggingIn = false,
                tokenLoggingIn = false,
                showTokenLogin = false,
                tokenLoginInput = "",
                code = "",
                phoneError = null,
            )
        }
        notice(message, NoticeTone.Success)
    }

    fun logout() {
        cancelFlowJobs()
        repo.logout()
        _uiState.update { WaterUiState() }
        notice("已退出胖乖登录")
    }

    private fun handleTokenExpired() {
        cancelFlowJobs()
        repo.markExpired()
        _uiState.update { WaterUiState() }
        notice(SessionExpiryStore.NOTICE_TEXT, NoticeTone.Warning)
    }

    // ── 资产 / 设备 ──

    fun refreshBalance() = viewModelScope.launch {
        if (!_uiState.value.loggedIn) return@launch
        // 冷启动、侧栏展开、面板打开可能在同一拍触发：在途时不再叠第二条。
        if (_uiState.value.loadingBalance) return@launch
        runCatching {
            _uiState.update { it.copy(loadingBalance = true) }
            repo.queryBalance()
        }.onSuccess { balance ->
            _uiState.update { it.copy(balance = balance, loadingBalance = false, balanceLoaded = true) }
            // 桌面胖乖开水卡（DESIGN §3.6「开水两卡」）：余额已经在手上，顺手推一次
            // （零额外请求；后台另有 2 小时闸门兜底）
            runCatching { WaterWidgetSync.pushQiekjBalance(Graph.appContext, balance) }
        }.onFailure {
            _uiState.update { it.copy(loadingBalance = false, balanceLoaded = true) }
            if (it is TokenExpiredException) {
                handleTokenExpired()
            } else {
                notice(it.message ?: "查询余额失败", NoticeTone.Error)
            }
        }
    }

    /**
     * 今天是否已签到（`signin/todaySignIn` 只读，2026-11-07）。
     *
     * 自动签到在主窗口 onResume 完成，签到页自己从没点过签到，[WaterUiState.signInResult]
     * 是空的——不问一次服务端，签到行会一直显示「签到」。进页、登录、下拉刷新各问一次，
     * 拿到 true 就把行摆成「已签到」并禁掉按钮。
     *
     * 失败保持「未知」（null）：按钮照常可点，真签过的话服务端回 33001，由签到动作自己
     * 报「今天已签」，不在这里猜。Token 失效照常清登录态。
     */
    fun refreshSignInState() {
        if (!_uiState.value.loggedIn) return
        // 进页 / 登录 / 下拉可能同一拍触发：在途时不再叠第二条
        if (signInStateJob?.isActive == true) return
        signInStateJob = viewModelScope.launch {
            runCatching { repo.isSignedInToday() }
                .onSuccess { signed ->
                    _uiState.update {
                        // 判定为已签就把上一次的失败文案收掉：否则会出现「红色失败行 + 已签到按钮」
                        it.copy(signInTodaySigned = signed, signInError = if (signed) null else it.signInError)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (error is TokenExpiredException) handleTokenExpired()
                }
        }
    }

    /** 签到状态查询的 Job（在途去重，见 [refreshSignInState]）。 */
    private var signInStateJob: Job? = null

    /**
     * 每日签到：只跑签到，不碰任务/领奖。重复点由仓库层幂等处理
     * （服务端返回 33001 按「今天已签」展示）。成功后顺手把余额推给桌面小组件。
     */
    fun runDailySignIn() = viewModelScope.launch {
        if (!_uiState.value.loggedIn) return@launch
        if (_uiState.value.signInRunning) return@launch
        _uiState.update { it.copy(signInRunning = true, signInError = null) }
        runCatching { repo.runDailySignIn() }
            .onSuccess { result ->
                _uiState.update {
                    it.copy(
                        signInRunning = false,
                        signInResult = result,
                        // 走到这里就说明今天签过了（真签与幂等已签两条路都算）
                        signInTodaySigned = true,
                        balance = result.balance ?: it.balance,
                        balanceLoaded = it.balanceLoaded || result.balance != null,
                    )
                }
                result.balance?.let { balance ->
                    runCatching { WaterWidgetSync.pushQiekjBalance(Graph.appContext, balance) }
                }
                notice("签到已完成", NoticeTone.Success)
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(signInRunning = false, signInError = error.message ?: "签到失败")
                }
                if (error is TokenExpiredException) {
                    handleTokenExpired()
                } else {
                    notice(error.message ?: "签到失败", NoticeTone.Error)
                }
            }
    }

    /** 最近一次设备列表请求的 Job：直达开水要等它回来才知道选哪台，不补发重复请求。 */
    private var devicesJob: Job? = null

    fun refreshDevices() = (viewModelScope.launch {
        if (!_uiState.value.loggedIn) return@launch
        runCatching {
            _uiState.update { it.copy(loadingDevices = true) }
            repo.latestDevices()
        }.onSuccess { devices ->
            _uiState.update { s ->
                // 默认选第一台；原选中的设备若还在列表里则保持
                val keep = s.selectedDevice?.let { cur -> devices.firstOrNull { it.effectiveGoodsId == cur.effectiveGoodsId } }
                s.copy(devices = devices, loadingDevices = false, selectedDevice = keep ?: devices.firstOrNull())
            }
        }.onFailure {
            _uiState.update { it.copy(loadingDevices = false) }
            if (it is TokenExpiredException) {
                handleTokenExpired()
            } else {
                notice(it.message ?: "查询历史设备失败", NoticeTone.Error)
            }
        }
    }).also { devicesJob = it }

    fun selectDevice(device: DeviceItem) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    /**
     * 小组件「去开水」直达（DESIGN §3.6 开水两卡，2026-09-28 用户拍板）：进页即开水。
     *
     * 一次进页只触发一次；未登录 / 流程进行中静默跳过（页面自己会把状态摆出来）。
     * 设备沿用「最近使用」口径：init 已在拉列表，等它回来选默认那台；拉完仍没有
     * （账号下没有历史设备）就交还用户手动处理。**绕过双击确认设置**——那颗开关
     * 管的是页面里的大按钮，桌面胶囊是更明确的主动手势。
     */
    fun requestAutoUnlock() {
        if (autoUnlockRequested) return
        autoUnlockRequested = true
        viewModelScope.launch {
            if (!_uiState.value.loggedIn) return@launch
            if (_uiState.value.flow !is UnlockFlowState.Idle) return@launch
            if (_uiState.value.selectedDevice == null) devicesJob?.join()
            if (_uiState.value.selectedDevice == null) {
                notice("没有可用设备，请手动选择", NoticeTone.Warning)
                return@launch
            }
            unlock()
        }
    }

    private var autoUnlockRequested = false

    /** 已保存的登录 token，给「复制 Token」用（另一台设备粘贴 Token 登录）；未登录返回 null。 */
    fun exportToken(): String? = repo.readToken()

    fun toggleShowPhone() = _uiState.update { it.copy(showPhone = !it.showPhone) }

    /**
     * 下拉刷新：余额与设备各拉一轮，任一在途就不重复发起（对齐趣智页 refreshAccount 的
     * 防重入口径）。refreshBalance / refreshDevices 各自返回 Job，这里 join 两轮都结束后
     * 才收 [WaterUiState.refreshing]——指示器不能在设备还没回来时就停。
     *
     * 订单快照跟着刷新走（2026-10-07）：`orderHistory()` 是本地读（无网络），此前只在
     * 进页 / 登录时读一次，开水完回到页面下拉，快照还是旧的，要重进页面才更新。放在
     * 两轮 join **之后**：刚完成的订单这轮回写后才读得到。
     */
    fun refresh() = viewModelScope.launch {
        if (_uiState.value.refreshing) return@launch
        _uiState.update { it.copy(refreshing = true) }
        val balanceJob = refreshBalance()
        val devicesJob = refreshDevices()
        // 签到状态不参与指示器：它是只读小请求，回来就更新签到行（在途去重见 refreshSignInState）
        refreshSignInState()
        balanceJob.join()
        devicesJob.join()
        if (_uiState.value.loggedIn) {
            _uiState.update { it.copy(orderHistory = repo.orderHistory()) }
            // 服务端账单跟着刷新走（已拉过才重拉，没进过账单弹层就不为它多打一次请求）
            if (_uiState.value.serverOrdersLoaded) loadServerOrders(force = true)
        }
        _uiState.update { it.copy(refreshing = false) }
    }

    // ── 服务端账单（order/list，2026-10-14） ──

    /**
     * 拉服务端账单列表。默认只在没拉过时拉（进弹层按需触发）；下拉刷新走 force=true。
     * 在途不重发；Token 失效照常清登录态。
     */
    fun loadServerOrders(force: Boolean = false) {
        if (!_uiState.value.loggedIn) return
        if (_uiState.value.loadingServerOrders) return
        if (!force && _uiState.value.serverOrdersLoaded) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingServerOrders = true) }
            runCatching { repo.serverOrders() }
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(serverOrders = list, loadingServerOrders = false, serverOrdersLoaded = true)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(loadingServerOrders = false) }
                    if (it is TokenExpiredException) {
                        handleTokenExpired()
                    } else {
                        notice(it.message ?: "查询服务端账单失败", NoticeTone.Error)
                    }
                }
        }
    }

    /** 点服务端账单某一条：拉明细（order/detail）后由界面弹详情。 */
    fun openServerOrder(item: ServerOrderItem) = viewModelScope.launch {
        runCatching { repo.serverOrderDetail(item) }
            .onSuccess { result -> _uiState.update { it.copy(serverOrderDetail = result) } }
            .onFailure {
                if (it is TokenExpiredException) {
                    handleTokenExpired()
                } else {
                    notice(it.message ?: "读取账单明细失败", NoticeTone.Error)
                }
            }
    }

    fun dismissServerOrderDetail() = _uiState.update { it.copy(serverOrderDetail = null) }

    fun toggleUsePoints() = _uiState.update { it.copy(usePoints = !it.usePoints) }

    // ── 开水 ──

    /**
     * 开水整链：下单 → 出水 → 等设备回报结算。
     *
     * **取消不是失败**（2026-11-06 修）：用户退出页面 / Activity 被回收时 `viewModelScope`
     * 被取消，那是「观察者没了」，不是这笔水的结论——服务端可能已经预扣、设备还在出水，
     * 而账单只有流程回报才拿得到。所以取消必须 ① 原样抛出（吞掉会让协程以为正常收尾）；
     * ② **不清** `wateringStore`（清了今日页/侧栏立刻显示「没在用」，用户回来最可能直接
     * 再开一次 = 二次预扣）。记账留给 `QiekjWateringStore` 的过期兜底与下次进页的状态卡。
     * 页面侧另有 `BackHandler` 在流程中拦返回（`WaterScreen.requestBack`），两条闸都要留：
     * 前者挡得住「点返回」，挡不住系统回收。
     */
    fun unlock() {
        val device = _uiState.value.selectedDevice ?: run {
            notice("请先选择设备", NoticeTone.Warning)
            return
        }
        // 每次开水新建一个手动结束开关（「结束用水」用）；上一轮的作废
        val signal = UnlockStopSignal()
        val previous = unlockJob
        unlockJob = viewModelScope.launch {
            // 上一轮在面板上「结束用水」后可能还挂着后台等账单：新一轮先收掉它再开，
            // 否则 Mutex 被占着，这次的「开水」会点了没反应（那笔账单走平台账单）。
            previous?.cancelAndJoin()
            if (!unlockMutex.tryLock()) return@launch
            // 上一轮的本地结束标记不能带到新一轮（迟到回报只认本轮）
            endedLocally = false
            stopSignal = signal
            // 本轮是否已登记「出水中」：**不能用 store 非空来挡**——上一轮残留会话
            // （页面被关、VM 已清、5 分钟内未过期）会把新一轮的起水时刻挡掉，
            // 今日页卡片显示的会是上一轮的旧起点。登记标记跟着本轮流程走。
            var sessionRegistered = false
            try {
                _uiState.update {
                    it.copy(flow = UnlockFlowState.PreChecking("准备开水"))
                }
                startTimer()
                // 165 秒兜底**不在这里起算**（2026-11-06）：它是服务端自动关阀窗口，
                // 从「服务端确认出水」起算才对齐。PreChecking 慢（弱网十几秒）时旧写法会
                // 提前收尾——水还在流，本地已经报「等待超时」并清了出水中登记；超过 165 秒
                // 更糟：到点时 flow 还是 PreChecking，收尾检查落空，Working 阶段再无兜底。
                try {
                    val result = repo.unlockDevice(
                        device,
                        usePoints = _uiState.value.usePoints,
                        stopSignal = signal,
                    ) { step ->
                        // 面板已「结束用水」（endSessionLocally）：步骤回执不再改界面——
                        // 计时停了、面板收了，不能把 Working 又写回来
                        if (endedLocally) return@unlockDevice
                        val working = step.contains("等待") || step.contains("设备工作")
                        if (working && !sessionRegistered) {
                            // 服务端确认出水：登记到落盘会话（跨窗口展示的唯一真相源）。
                            // 只登记一次——后续 onStep 是同一轮出水的步骤推进，不重开计时。
                            sessionRegistered = true
                            // 165 秒兜底从这一刻起算（与服务端关阀窗口同起点，见 unlock 注释）
                            startTimeout()
                            wateringStore.begin(
                                QiekjWateringSession(
                                    startedAtMillis = System.currentTimeMillis(),
                                    deviceName = device.goodsName,
                                    goodsId = device.effectiveGoodsId,
                                ),
                            )
                        }
                        _uiState.update { s ->
                            // 与「结束用水」撞车时以后者为准（回执在 IO 线程，标记是 volatile 的）
                            if (endedLocally) s else s.copy(
                                flow = if (working) {
                                    // 保留 elapsedSeconds 与 stopping：onStep 会重建 Working，
                                    // 不带上 stopping 会把「正在结束用水」态冲掉
                                    val prev = s.flow as? UnlockFlowState.Working
                                    UnlockFlowState.Working(
                                        step = step,
                                        elapsedSeconds = prev?.elapsedSeconds ?: 0,
                                        stopping = prev?.stopping ?: false,
                                    )
                                } else {
                                    UnlockFlowState.PreChecking(step)
                                },
                            )
                        }
                    }
                    cancelFlowJobs()
                    stopSignal = null
                    // 结算完成：出水中结束（迟到重投时再清一次是空操作）
                    wateringStore.clear()
                    _uiState.update {
                        it.copy(
                            // 本机结束后才迟到的结算：账单照样落卡（2026-11-06 用户口径），
                            // 面板下次打开能看到这一笔
                            flow = UnlockFlowState.Success(result),
                            orderHistory = repo.orderHistory(),
                        )
                    }
                    // 开水结算留痕收件箱（2026-10-21）：数据（订单号/金额）已到手才投，
                    // post 内部吞错不影响主链路。每次开水一条事实，按订单号去重。
                    launch {
                        runCatching { postWaterDoneInbox(result, device) }
                    }
                    refreshBalance()
                } catch (e: CancellationException) {
                    // 页面 / Activity 退出：原样抛出，不当作失败收尾（理由见 unlock 的 KDoc）。
                    // 尤其**不能**落到下面的 wateringStore.clear()——那会抹掉「出水中」的
                    // 界面痕迹，而这笔水在服务端仍然成立。
                    stopSignal = null
                    throw e
                } catch (e: Throwable) {
                    cancelFlowJobs()
                    stopSignal = null
                    // 流程失败（含手动结束未确认 / Token 失效）：本机的出水中登记一并收掉
                    wateringStore.clear()
                    if (e is TokenExpiredException) {
                        handleTokenExpired()
                        return@launch
                    }
                    // 面板已在本机结束：后台收尾失败 / 未确认不再打扰（账单以平台为准），
                    // 只静默刷一次余额（这次会话可能已经扣了小票）
                    if (!shouldReportFlowFailure(endedLocally)) {
                        refreshBalance()
                        return@launch
                    }
                    // 手动结束：仓库会先请求平台远程关阀再等回报；等不到就按「未确认用水」收尾
                    if (e is UnlockStoppedException) {
                        finishWithoutSettlement(e.message ?: "已结束等待。设备未回报结束，本次未确认用水")
                        return@launch
                    }
                    val failed = if (e is UnlockException) {
                        UnlockFlowState.Failed(e.message ?: "开水失败", e.diagnosis.step, e.diagnosis.rawError, e.diagnosis.suggestions)
                    } else {
                        UnlockFlowState.Failed(e.message ?: "开水失败", "未知", e.message ?: "")
                    }
                    _uiState.update { it.copy(flow = failed) }
                }
            } finally {
                unlockMutex.unlock()
            }
        }
    }

    fun dismissFlow() {
        cancelFlowJobs()
        stopSignal = null
        _uiState.update { it.copy(flow = UnlockFlowState.Idle) }
    }

    /**
     * 「结束用水」（2026-10-05 起；2026-10-16 改）：只在出水中有意义，出水满
     * [END_SESSION_MIN_SECONDS] 秒才给按钮（界面侧判）。置位后交给仓库：机型支持时请求
     * 平台远程关阀（`goods/endWater`）；机型不支持或平台拒绝时退回落「在设备上点击
     * 『结束』」提示。两条路都**继续轮询**等设备回报结束照常读账单；只有平台已受理但
     * 20 秒没回报才按「未确认用水」收尾。
     *
     * 这里**不取消协程**：收尾由仓库完成，取消会连结算路径一起掐掉。
     * 立即把 flow 置 stopping 态（禁用按钮 + 走字），别让用户以为没点上。
     *
     * 这是**开水页 / 今日页服务卡**的口径（页面里能一直看到「正在结束用水」）；
     * 弹层上那颗按钮走 [endSessionLocally]（2026-11-06 用户口径），点下即收面板。
     */
    fun endSession() {
        val flow = _uiState.value.flow
        if (flow !is UnlockFlowState.Working || flow.stopping) return
        stopSignal?.request()
        _uiState.update { it.copy(flow = flow.copy(stopping = true)) }
    }

    /**
     * 弹层上的「结束用水」（2026-11-06 用户口径）：**在软件层面结束这一轮**，不等设备回报。
     *
     * 为什么不等：平台没有可靠的在线判据（`trade/check_device_status` 已于 2026-10-16 删除），
     * 误触开水时设备可能根本没出水，等下去只会把用户扣在弹窗里看「正在结束用水…」。
     *
     * 做四件事：① 置位 [UnlockStopSignal]——仓库下一轮仍会 best-effort 请求远程关阀
     * （机型支持时才发，见 `shouldRequestRemoteEnd`）；② 停计时与 165 秒兜底；
     * ③ 清「出水中」登记（今日页服务卡 / 侧栏高亮立刻撤）；④ 状态归 [UnlockFlowState.Idle]，
     * 弹层由调用方收起（先播退场动画再调这里）。
     *
     * **不取消后台协程**：设备真的出水时，迟到的结算照常建单落卡（账单照样显示）；
     * 迟到的失败 / 未确认静默收尾（[shouldReportFlowFailure]）。下一次 [unlock] 会先收掉
     * 这条后台观察——那笔账单以平台账单为准。
     */
    fun endSessionLocally() {
        val flow = _uiState.value.flow
        if (flow !is UnlockFlowState.Working || flow.stopping) return
        stopSignal?.request()
        endedLocally = true
        cancelTimerOnly()
        wateringStore.clear()
        _uiState.update { it.copy(flow = UnlockFlowState.Idle) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val flow = _uiState.value.flow
                if (flow is UnlockFlowState.Working) {
                    _uiState.update { it.copy(flow = flow.copy(elapsedSeconds = flow.elapsedSeconds + 1)) }
                } else if (flow !is UnlockFlowState.PreChecking) {
                    return@launch
                }
            }
        }
    }

    /**
     * 165s 兜底：服务端超时会自动关阀，本地把状态收回 Idle 并刷新余额；
     * 仓库层轮询协程不取消，收尾完成后 Success 会重新落卡（订单快照不丢）。
     *
     * 话术不写「已结算」（2026-10-05 改）：设备断电时 `workStatus` 一直回 2，
     * 仓库根本走不到建单那一步，说结算就是假话。
     */
    private fun startTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(AUTO_SETTLE_SECONDS * 1_000L)
            if (_uiState.value.flow is UnlockFlowState.Working) {
                cancelTimerOnly()
                finishWithoutSettlement("等待超时。设备未回报结束，实际用量以胖乖平台账单为准")
            }
        }
    }

    /**
     * 「没读到订单」的统一收尾：状态归 Idle、给一句实话、顺手刷新余额与设备。
     * 与 [UnlockFlowState.Success] 相对——那条路是设备回报了结束并读到了账单。
     */
    private fun finishWithoutSettlement(message: String) {
        stopSignal = null
        // 165 秒超时 / 手动结束未确认：服务端已（或即将）自动关阀，本机登记同步收掉
        wateringStore.clear()
        _uiState.update { it.copy(flow = UnlockFlowState.Idle) }
        notice(message, NoticeTone.Warning)
        refreshBalance()
        refreshDevices()
    }

    private fun cancelTimerOnly() {
        timerJob?.cancel()
        timeoutJob?.cancel()
        timerJob = null
        timeoutJob = null
    }

    private fun cancelFlowJobs() {
        timerJob?.cancel()
        timeoutJob?.cancel()
        timerJob = null
        timeoutJob = null
    }

    /**
     * 开水结算留痕收件箱（2026-10-21）：调用点在流程落 `Success` 处——订单号与金额
     * 已到手。金额走 [calculateActualCost]（小票+现金口径），与结果卡同源不另算。
     * post 内部吞错；这里不 catch 也是旁路（launch 内异常由调用点 runCatching 兜住）。
     */
    private suspend fun postWaterDoneInbox(result: UnlockResult, device: DeviceItem) {
        val deviceName = device.goodsName.ifBlank { "开水设备" }
        val body = buildString {
            append(deviceName)
            append(" · 花费 ¥").append(calculateActualCost(result))
            if (result.payTypeName?.isNotBlank() == true) {
                append("（").append(result.payTypeName).append("）")
            }
        }
        Graph.inboxRepository(Graph.appContext).post(
            kind = InboxKind.WATER_SESSION,
            dedupeKey = InboxKeys.WATER_PREFIX + result.orderNo.ifBlank { result.orderId },
            title = "开水已完成",
            body = body,
            // 落点 = 开水页（订单结果卡 / 本地订单快照在那里）；静默留痕不点红点
            route = SubpageScreen.WATER.name,
            silent = true,
        )
    }

    /**
     * 页面销毁只收本页临时态（计时 / 超时两个 Job 由 `viewModelScope` 一并取消）。
     *
     * **不许在这里清 `wateringStore`**（2026-11-06 定）：它是「服务端这笔水还在」的镜像，
     * 不是本页的界面状态。页面被关掉时水可能还在流、账单还没读回来，清掉登记会让今日页
     * 立刻显示「没在用」，用户回去最可能直接再开一次（二次预扣）。真正的清理由三处承担：
     * 流程拿到结算结果、`finishWithoutSettlement` 收尾、`QiekjWateringStore` 的 5 分钟过期。
     */
    override fun onCleared() {
        cancelFlowJobs()
        super.onCleared()
    }

    companion object {
        /** 服务端自动关阀兜底秒数：唯一出处 `QiekjWateringSession.AUTO_CLOSE_SECONDS`。 */
        const val AUTO_SETTLE_SECONDS = QiekjWateringSession.AUTO_CLOSE_SECONDS

        /** 「结束用水」按钮的出场门槛：出水满 5 秒才给（2026-10-05 用户拍板）。 */
        const val END_SESSION_MIN_SECONDS = 5
    }

    class Factory(
        private val repo: QiekjRepository,
        private val wateringStore: QiekjWateringStore,
        private val autoLoad: Boolean = true,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WaterViewModel(repo, wateringStore, autoLoad) as T
    }
}
