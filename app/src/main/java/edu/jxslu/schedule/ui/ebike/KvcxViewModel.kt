package edu.jxslu.schedule.ui.ebike

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.kqcx.KqcxOrderCache
import edu.jxslu.schedule.data.kqcx.KqcxSessionRepository
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.RideRecordStore
import edu.jxslu.schedule.domain.EbikeCardExpiry
import edu.jxslu.schedule.domain.EbikeRideStats
import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.domain.KqcxRefreshPolicy
import edu.jxslu.schedule.domain.KvcBusinessError
import edu.jxslu.schedule.domain.capabilities
import edu.jxslu.schedule.ui.common.NoticeTone
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.time.YearMonth

/** 一次性提示事件（对齐 WaterViewModel 模式：缓冲 Channel，页面负责收集）。 */
sealed interface KvcxEvent {
    data class Notice(val text: String, val tone: NoticeTone) : KvcxEvent
}

/** 快趣出行页状态。表单值只在未登录时有意义；骑行卡只在已登录时有意义。 */
data class KvcxUiState(
    val loggedIn: Boolean = false,
    val mobile: String = "",
    val mobileError: String? = null,
    val password: String = "",
    val loggingIn: Boolean = false,
    val accountMobile: String = "",
    val querying: Boolean = false,
    /** null = 已查询且无骑行在案；查询失败沿用上次结果不动它。 */
    val ride: KqcxAuth.Ride? = null,
    /**
     * 快趣资产（2026-09-30，只读）：余额 / 卡券。进页拉一次 + 手动刷新；
     * null = 还没查到过（查询失败沿用 null，页面给"再试一次"的出路）。
     */
    val assets: KqcxAuth.Assets? = null,
    val assetsLoading: Boolean = false,
    /** 最近一次资产查询颗粒无收（三块全空或请求失败）；页面据此给重试出路。 */
    val assetsFailed: Boolean = false,
    /**
     * 待提醒的「临近到期」骑行卡券（DESIGN §4.32，2026-10-14）：非空 = 页面该弹一次
     * 到期提醒。查资产时算出（未提醒过且在到期前一天窗口内），页面关掉时落「已提醒」键。
     */
    val cardExpiry: List<KqcxAuth.AssetCoupon>? = null,
    /**
     * 超时费汇总（2026-11-05）：进页自动算——缓存有数就直接出，缓存空才全量扫一次
     * （常见 20–40 次请求），之后每次进页只增量补最近两月。null = 还没算出过。
     */
    val stats: EbikeRideStats.Summary? = null,
    /** 统计覆盖的月份区间（早停时起点晚于 2017-01，界面要写出来）。 */
    val statsFrom: YearMonth? = null,
    val statsTo: YearMonth? = null,
    val statsLoading: Boolean = false,
    /** 统计进度：已查月数（界面显示「已查 N 个月」）。 */
    val statsScannedMonths: Int = 0,
    /** 诊断：这次拉回多少条原始记录、去重后多少条（差值 = 服务端重复返回的量）。 */
    val statsFetchedRows: Int = 0,
    val statsUniqueOrders: Int = 0,
    /** 按月分布（`2026-11 · 12 次 · ¥9.60`，最新在前，最多 6 行）：明细弹窗的「核实」用。 */
    val statsMonths: List<String> = emptyList(),
    /** 覆盖月份与月均（`覆盖 28 个月：月均 53.0 次超时、约 ¥44.49`）：把总数换算成每月量级。 */
    val statsCoverage: String? = null,
    val statsError: String? = null,
)

class KvcxViewModel(
    private val session: KqcxSessionRepository,
    private val records: RideRecordStore,
    /**
     * 使用方式（DESIGN §3.9 / §4.32）：本页只属于**账号登录方式**，小程序方式下页面整片
     * 收起（入口也从「校园服务」隐藏），所以这里的查询也要跟着停——否则一个已经退到
     * 后台栈里的本页实例还会去打扰第三方接口。
     */
    private val prefs: DisplayPrefsStore,
    /** 服务端订单缓存（2026-11-05）：统计在本地算，网络只负责增量刷新。 */
    private val orderCache: KqcxOrderCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        KvcxUiState(
            loggedIn = session.loggedIn.value,
            accountMobile = session.currentMobile.orEmpty(),
        ),
    )
    val uiState: StateFlow<KvcxUiState> = _uiState.asStateFlow()

    private val _events = Channel<KvcxEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 骑行查询序号：外部结束订单 / 新查询发起时 +1，晚到的旧包直接丢弃。 */
    private var rideQuerySeq = 0L

    private fun notice(text: String, tone: NoticeTone = NoticeTone.Info) =
        _events.trySend(KvcxEvent.Notice(text, tone))

    init {
        // 自动还车 Worker 结束订单后，本页的 ride 不会自己变——订阅进程内信号清卡
        viewModelScope.launch {
            EbikeRideSignal.ended.collect { text ->
                rideQuerySeq++
                _uiState.update { it.copy(ride = null, querying = false) }
                if (text.isNotBlank()) notice(text, NoticeTone.Success)
                // 刚还完车：立刻增量拉订单并重算超时费，不能等 30 分钟节流过期
                syncStats(force = true)
            }
        }
        // 登录态跨窗口共享（hub 与本页各持一份 VM）：以仓库那条流为准
        viewModelScope.launch {
            session.loggedIn.collect { loggedIn ->
                _uiState.update {
                    if (loggedIn == it.loggedIn) return@update it
                    if (loggedIn) {
                        it.copy(loggedIn = true, accountMobile = session.currentMobile.orEmpty())
                    } else {
                        rideQuerySeq++ // 退出登录：作废还在飞的骑行查询，别把旧卡写回来
                        KvcxUiState() // 退出：表单与骑行卡一起归零
                    }
                }
            }
        }
        // 进页自动查一次骑行状态：只在账号登录方式下（见 [accountMode]）
        viewModelScope.launch {
            if (session.loggedIn.value && accountMode()) {
                refreshRide()
                refreshAssets()
                recheckUnsettledRecords()
                // 超时费：缓存有数就立刻出、没有就全量补一次，之后每次进页只走增量
                syncStats()
            }
        }
        // 自动还车兜底：进账号页也做一次对账（闹钟丢了补跑 / 退出登录、切方式后撤销），
        // 幂等，见 [EbikeAutoReturnReminder.reconcile]
        viewModelScope.launch {
            EbikeAutoReturnReminder.reconcile(Graph.appContext)
        }
    }

    /** 当前是否账号登录方式（读原始流；读失败按「不是」处理，宁可不查）。 */
    private suspend fun accountMode(): Boolean =
        runCatching { prefs.ebikeUseMode.first().capabilities().inAppRide }.getOrDefault(false)

    // ── 登录表单 ──

    fun updateMobile(value: String) = _uiState.update {
        val digits = value.filter(Char::isDigit).take(11)
        it.copy(
            mobile = digits,
            mobileError = if (digits.isNotEmpty() && digits.length < 11) "请输入 11 位手机号" else null,
        )
    }

    fun updatePassword(value: String) = _uiState.update { it.copy(password = value.take(64)) }

    /** 登录：失败保留输入（用户改完直接重试），提示经 Snackbar。[remember] 默认开。 */
    fun login(remember: Boolean = true) = viewModelScope.launch {
        val s = _uiState.value
        if (s.mobile.length != 11) {
            _uiState.update { it.copy(mobileError = "请输入 11 位手机号") }
            return@launch
        }
        _uiState.update { it.copy(loggingIn = true) }
        runCatching { session.login(s.mobile, s.password, remember) }
            .onSuccess {
                // 登录态由仓库流回灌（init 里的 collect 会置 loggedIn / accountMobile）
                notice("登录成功", NoticeTone.Success)
                _uiState.update { it.copy(loggingIn = false, password = "") }
                refreshRide()
                refreshAssets()
                recheckUnsettledRecords()
                viewModelScope.launch { syncStats() }
                // 登录成功后按设置重排自动还车（登录前它不会排）
                EbikeAutoReturnReminder.reconcile(Graph.appContext)
            }
            .onFailure {
                _uiState.update { it.copy(loggingIn = false) }
                notice(loginErrorText(it), NoticeTone.Error)
            }
    }

    fun logout() {
        rideQuerySeq++
        session.logout()
        // 退出登录后凭证没了：自动还车排着也执行不了，撤销（reconcile 内部按登录态判断）
        viewModelScope.launch {
            EbikeAutoReturnReminder.reconcile(Graph.appContext)
        }
        // 订单缓存属于这个手机号，退出即清（换账号不能串号）
        viewModelScope.launch { runCatching { orderCache.clear() } }
        // 仓库流会回灌归零；这里不重复改 uiState
    }

    // ── 骑行状态 ──

    /** 进页自动查一次（init）+ 手动刷新共用。查询失败不动上次结果，只出提示。 */
    fun refreshRide() = viewModelScope.launch {
        if (!session.loggedIn.value) return@launch
        if (!accountMode()) return@launch
        val seq = ++rideQuerySeq
        _uiState.update { it.copy(querying = true) }
        runCatching { session.queryUnderway() }
            .onSuccess { ride ->
                if (seq != rideQuerySeq) return@onSuccess
                _uiState.update { it.copy(querying = false, ride = ride) }
                if (ride == null) notice("当前没有进行中的骑行", NoticeTone.Info)
            }
            .onFailure {
                if (seq != rideQuerySeq) return@onFailure
                _uiState.update { it.copy(querying = false) }
                notice(queryErrorText(it), NoticeTone.Error)
            }
    }

    // ── 快趣资产（2026-09-30，只读） ──

    /**
     * 查一次账户资产（余额 / 卡券）：进页、登录成功、手动刷新各一次。
     * 会员卡接口已停拉（会员行已删除，2026-11-05 减少请求），见 [KqcxSessionRepository.queryAssets]。
     * **失败沿用上次结果**：资产是展示性数据，查不到不该把用户已看到的清成空白；
     * 三块全空才算"颗粒无收"，页面给重试出路。失败不打扰（不弹提示，区块内给一行说明）。
     */
    fun refreshAssets() = viewModelScope.launch {
        if (!session.loggedIn.value) return@launch
        if (!accountMode()) return@launch
        if (_uiState.value.assetsLoading) return@launch
        _uiState.update { it.copy(assetsLoading = true) }
        runCatching { session.queryAssets() }
            .onSuccess { assets ->
                _uiState.update {
                    if (assets.isEmpty) {
                        it.copy(assetsLoading = false, assetsFailed = true)
                    } else {
                        it.copy(assetsLoading = false, assetsFailed = false, assets = assets)
                    }
                }
                // 骑行卡券临近到期（前一天）就弹一次（2026-10-14）：卡券随资产一起回来，
                // 不必为它另发请求；已提醒过的卡（按 endTime 去重）不再进队列。
                maybeRemindCardExpiry(assets.coupons)
            }
            .onFailure {
                _uiState.update { it.copy(assetsLoading = false, assetsFailed = true) }
            }
    }

    /** 算一次「该提醒的到期卡券」并落状态；空列表不动（别把已展示的提醒抹掉）。 */
    private suspend fun maybeRemindCardExpiry(coupons: List<KqcxAuth.AssetCoupon>) {
        val notified = runCatching { prefs.ebikeCardExpiryNotifiedKeys.first() }.getOrDefault(emptySet())
        val pending = EbikeCardExpiry.pendingReminders(coupons, notified, System.currentTimeMillis())
        if (pending.isNotEmpty()) {
            _uiState.update { it.copy(cardExpiry = pending) }
        }
    }

    /**
     * 关掉到期提醒：把这次提醒过的卡的 [EbikeCardExpiry.dedupKey] 落盘（同一张卡只提醒
     * 一次），并清掉状态。写失败不重试——顶多下次进页再提醒一次，方向是保守那一侧。
     */
    fun dismissCardExpiry() {
        val coupons = _uiState.value.cardExpiry ?: return
        _uiState.update { it.copy(cardExpiry = null) }
        val keys = coupons.mapNotNull { it.endAt?.trim()?.takeIf(String::isNotEmpty) }
            .map(EbikeCardExpiry::dedupKey)
            .toSet()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            runCatching { prefs.updateEbikeCardExpiryNotifiedKeys { it + keys } }
        }
    }

    // ── 超时费汇总（2026-11-05） ──

    /** 手点资产区右上角刷新：资产重拉 + 统计强制同步（订单也绕过节流）。 */
    fun refreshAll() {
        refreshAssets()
        viewModelScope.launch { syncStats(force = true) }
    }

    /**
     * 统计的唯一入口（进页 / 登录成功 / 手点刷新 / 还车结束都走它）。
     *
     * 先读缓存出数（不联网）→ 缓存空就全量扫一次，否则增量拉最近两月 → 再出数。
     * 全量扫描 20–40 次请求，只发生在缓存为空（首次进页 / 刚登录 / 刚清过）时；
     * 常规进页最多 4 次请求，且受 30 分钟节流（[KqcxRefreshPolicy]）约束。
     */
    private suspend fun syncStats(force: Boolean = false) {
        if (!session.loggedIn.value || !accountMode()) return
        recomputeStats()
        if (runCatching { orderCache.count() }.getOrDefault(0) == 0) {
            scanAllOrders()
        } else if (refreshRecentOrders(force)) {
            recomputeStats()
        }
    }

    /** 全量扫描（缓存空时自动跑）：结果整批落缓存，统计仍从缓存算。失败保留上次结果，只出提示。 */
    private suspend fun scanAllOrders() {
        if (_uiState.value.statsLoading) return
        _uiState.update { it.copy(statsLoading = true, statsScannedMonths = 0, statsError = null) }
        runCatching {
            session.scanOrderHistory(
                onProgress = { months ->
                    _uiState.update { s -> if (s.statsLoading) s.copy(statsScannedMonths = months) else s }
                },
            )
        }
            .onSuccess { scan ->
                runCatching { orderCache.store(scan.orders) }
                recomputeStats()
                _uiState.update {
                    it.copy(
                        statsLoading = false,
                        statsScannedMonths = scan.scannedMonths,
                        statsFetchedRows = scan.fetchedRows,
                        statsFrom = it.statsFrom ?: scan.from,
                        statsTo = it.statsTo ?: scan.to,
                    )
                }
            }
            .onFailure {
                _uiState.update { s -> s.copy(statsLoading = false, statsError = queryErrorText(it)) }
                notice(queryErrorText(it), NoticeTone.Error)
            }
    }

    /** 读缓存本地算一遍（不联网）；缓存空则保持现状（首次进页由 [scanAllOrders] 补）。 */
    private suspend fun recomputeStats() {
        val orders = runCatching { orderCache.load() }.getOrNull().orEmpty()
        if (orders.isEmpty()) return
        val range = EbikeRideStats.monthRange(orders)
        _uiState.update {
            it.copy(
                stats = EbikeRideStats.summarize(orders),
                statsFrom = range?.first?.let(YearMonth::parse) ?: it.statsFrom,
                statsTo = range?.second?.let(YearMonth::parse) ?: it.statsTo,
                statsUniqueOrders = orders.size,
                statsMonths = EbikeRideStats.monthlyLines(orders),
                statsCoverage = EbikeRideStats.coverageLine(orders),
            )
        }
    }

    /** 增量刷新最近两个月（≤4 请求）；返回是否写进了新数据（节流命中或全失败 = false）。 */
    private suspend fun refreshRecentOrders(force: Boolean = false): Boolean {
        if (!session.loggedIn.value || !accountMode()) return false
        // 节流：缓存半小时内刚写过就不再发请求；force 用于手点刷新与「刚还完车」
        val lastSync = runCatching { orderCache.lastSyncedAt() }.getOrNull()
        if (!force && KqcxRefreshPolicy.isFresh(lastSync, System.currentTimeMillis())) return false
        val thisMonth = YearMonth.now()
        val fetched = buildList {
            for (month in listOf(thisMonth, thisMonth.minusMonths(1))) {
                for (device in intArrayOf(
                    KvcxOrdersViewModel.DEVICE_EBIKE,
                    KvcxOrdersViewModel.DEVICE_BIKE,
                )) {
                    addAll(
                        runCatching {
                            session.queryOrderHistory(month.year, month.monthValue, device)
                        }.getOrDefault(emptyList()),
                    )
                }
            }
        }
        if (fetched.isEmpty()) return false
        return runCatching { orderCache.store(fetched) }.getOrDefault(0) > 0
    }
    // ── 结清复查（2026-09-30） ──

    /**
     * 核对快趣侧的结清状态，把本机过期的「未结清」记录翻成「已结清」。
     *
     * 本机记录的「未结清」只是**还车那一刻**的扣款确认没赶上（结果卡会后台补查），
     * 用户随后在微信里付清时 App 并不知道，记录就永远挂着「未结清」（2026-09-30
     * 用户实测：小程序里付了 0.8 元，本机记录仍显示未结清）。快趣同时至多一笔
     * 待支付订单（有欠费不让开新车，官方同款约束），所以「快趣现在无欠费」⇒
     * 历史上那些没确认到的订单都已结清，可以放心翻。
     *
     * 时机：进页 / 登录成功各一次；先查本机有没有「未结清」记录，**没有就不打扰
     * 第三方接口**（同「查询也要跟着停」的口径）。查询失败静默，下次进页再核。
     */
    fun recheckUnsettledRecords() = viewModelScope.launch {
        if (!session.loggedIn.value || !accountMode()) return@launch
        if (runCatching { records.countUnsettled() }.getOrDefault(0) == 0) return@launch
        val state = runCatching { session.confirmUnpaidSettled(attempts = 1) }.getOrNull()
        if (state is KqcxAuth.UnpayState.Settled) {
            val changed = runCatching { records.markUnsettledSettled() }.getOrDefault(0)
            if (changed > 0) {
                notice("快趣侧已无欠费；本机 $changed 条「未结清」记录已更新为已结清", NoticeTone.Success)
            }
        }
    }

    /** 登录失败的文案分类：业务错误给服务端原文，网络类给可操作建议。 */
    private fun loginErrorText(error: Throwable): String = when (error) {
        is KvcBusinessError -> error.message ?: "登录失败"
        is SocketTimeoutException -> "登录超时，请检查网络后重试"
        is InterruptedIOException -> "登录超时，请检查网络后重试"
        is IOException -> "网络不可用，请检查网络后重试"
        else -> "登录异常：${error.message ?: error.javaClass.simpleName}"
    }

    private fun queryErrorText(error: Throwable): String = when (error) {
        is KvcBusinessError ->
            if (error.message?.contains("未登录") == true) "快趣登录已失效，请重新登录"
            else error.message ?: "查询失败"
        is SocketTimeoutException -> "查询超时，请稍后重试"
        is InterruptedIOException -> "查询超时，请稍后重试"
        is IOException -> "网络不可用，请检查网络后重试"
        else -> "查询异常：${error.message ?: error.javaClass.simpleName}"
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    KvcxViewModel(
                        session = Graph.kqcx(context.applicationContext),
                        records = Graph.rideRecordStore(context.applicationContext),
                        prefs = Graph.displayPrefs(context.applicationContext),
                        orderCache = Graph.kqcxOrderCache(context.applicationContext),
                    ) as T
            }
    }
}