package edu.jxslu.schedule.ui.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.local.TypeAmountRow
import edu.jxslu.schedule.data.local.YktTurnoverEntity
import edu.jxslu.schedule.data.ykt.YktCredentialStore
import edu.jxslu.schedule.data.ykt.YktException
import edu.jxslu.schedule.data.ykt.YktRepository
import edu.jxslu.schedule.data.ykt.YktTurnoverSyncer
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.YearMonthKey
import edu.jxslu.schedule.domain.YktTurnoverRow
import edu.jxslu.schedule.ui.common.NoticeTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 消费流水页状态（DESIGN §4.19 L3：本地库优先 + 后台增量同步）。 */
data class StatementUiState(
    /** 当前查看的月份。 */
    val month: YearMonthKey = YearMonthKey.fromDate(AppClock.today()),
    /** 当月流水（Room 流切片，时间倒序）。 */
    val records: List<YktTurnoverEntity> = emptyList(),
    /** 当月支出/收入（分，本地 SQL 汇总）。 */
    val expensesFen: Long = 0,
    val incomeFen: Long = 0,
    /** 当月分类聚合（金额降序，正数支出/负数收入）。 */
    val byType: List<TypeAmountRow> = emptyList(),
    /** 本地库总条数。 */
    val localCount: Int = 0,
    /** 近 12 个月逐月支出（分，含当月；键 `2026-09`；无数据的月不在 map 里）。 */
    val monthlyExpenses: Map<String, Long> = emptyMap(),
    /** 后台同步中。 */
    val syncing: Boolean = false,
    /** 同步错误（null = 无；本地数据照常展示）。 */
    val error: String? = null,
    /** 凭证缺失（去设置页引导）。 */
    val noCredentials: Boolean = false,
    val canRetry: Boolean = true,
)

/** 一次性事件（同步失败等）。 */
sealed interface StatementEvent {
    data class Notice(val text: String, val tone: NoticeTone) : StatementEvent
}

/**
 * 消费流水页（DESIGN §4.19 L3/L4）：本地库优先（秒开、离线可查）+ 后台增量同步。
 *
 * - 数据源 = Room `ykt_turnovers`（进页即显本地；无网络也看得到历史账）；
 * - 后台 [YktTurnoverSyncer] 增量拉新（时间倒序翻页，遇已入库 orderId 即停），
 *   Room 流自动刷新 UI；
 * - 月切换 = 换 Room 查询键，零网络；汇总/分类聚合全部 SQL（响应式流）。
 *
 * iOS 移植 S20（2026-11-25）：搬进 `shared/commonMain`，按 `design/ios-port.md` §11.2：
 *
 * - 构造签名里的 `Context` 去掉，改吃 [JuwDatabase]（本地表，两端都能建）；
 * - 凭证存储可空：**null = 本平台没有加密凭证存储**，此时只显示本地已存流水、
 *   不发同步请求（iOS 的 Keychain 未实现，见 `AppServices.yktCredentialStore`）；
 * - 「新消费入收件箱」原来是 `postYktPaymentInbox(context, row)`（`:app` 的
 *   `Graph.inboxRepository` + `SubpageScreen` 路由），改成 [postInbox] 回调注入：
 *   null = 本平台不接收件箱，同步照跑、只是不留痕（旁路纪律，不影响主链）；
 * - `Dispatchers.IO` → `Dispatchers.Default`：IO 调度器在 commonMain 里不可见，
 *   而 Room / Ktor 的挂起调用本身就在各自的调度器上跑；
 * - 顺带删掉没人调的 `dayGroupLabel(java.time.LocalDate)`（零调用方，日期分组已由
 *   页面自己的 `toDayGroups` 承担）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatementViewModel(
    private val repo: YktRepository,
    /** 凭证存储；null 的语义见类注释。 */
    private val credentialStore: YktCredentialStore?,
    private val db: JuwDatabase,
    /** 本次同步新见到的支出记录逐条投递（null = 本平台没有收件箱）。 */
    private val postInbox: (suspend (YktTurnoverRow) -> Unit)? = null,
) : ViewModel() {

    /** 当前月份键（Room 查询口径 `2026-09`）。 */
    private val monthKey = MutableStateFlow(monthKeyOf(YearMonthKey.fromDate(AppClock.today())))

    /** 月份切换等 UI 侧状态（与数据流分开源，避免同步状态变化重查 SQL）。 */
    private val _uiState = MutableStateFlow(StatementUiState())

    /** 当月流水（月份键变化即换查询）。 */
    private val monthRecords = monthKey.flatMapLatest { key ->
        db.yktTurnoverDao().observeMonth(key)
    }

    /** 当月汇总 + 分类聚合 + 总条数（全是响应式 SQL，表变化自动重发）。 */
    private val aggregates = monthKey.flatMapLatest { key ->
        combine(
            db.yktTurnoverDao().observeMonthSummary(key),
            db.yktTurnoverDao().observeMonthByType(key),
            db.yktTurnoverDao().observeCount(),
        ) { summary, byType, count ->
            Triple(summary, byType, count)
        }
    }

    private val _events = Channel<StatementEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * 年视图窗口：近 12 个月（含当月，升序）。
     *
     * 柱状图与「选择月份」弹窗共用这一份，别再在 UI 里按「今天」另算一次——
     * 两处各算一遍，跨月时弹窗会给出一组和柱子对不上的月份。
     */
    private val window: List<YearMonthKey> = run {
        val now = YearMonthKey.fromDate(AppClock.today())
        (11 downTo 0).map { offset -> now.plusMonths(-offset) }
    }

    private val recentMonthKeys: List<String> = window.map { monthKeyOf(it) }

    /** 月份选择弹窗与柱状图的月份窗口（升序，最后一个是当月）。 */
    fun windowMonths(): List<YearMonthKey> = window

    /** 近 12 个月支出（suspend 一次性查；同步完成后由 syncInBackground 触发重查）。 */
    private val _monthlyExpenses = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** UI 合成流：数据三源 + 同步状态。 */
    val uiState: StateFlow<StatementUiState> = combine(
        monthRecords,
        aggregates,
        monthKey,
        _uiState,
        _monthlyExpenses,
    ) { records, (summary, byType, count), key, base, monthly ->
        base.copy(
            month = monthOf(key),
            records = records,
            expensesFen = summary.expensesFen,
            incomeFen = summary.incomeFen,
            byType = byType,
            localCount = count,
            monthlyExpenses = monthly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatementUiState())

    // postInbox：同步拉到的新扣款静默入箱（2026-10-23，覆盖食堂 POS 直刷等非付款码
    // 消费场景；键与文案在 postYktPaymentInbox 唯一，与扫码命中处同键幂等）。
    private val syncer = YktTurnoverSyncer(repo, db, postInbox)

    init {
        // 进页即增量同步（凭证缺失走 UI 引导；失败保持本地数据并给一次性提示）。
        // force = false：受 YktSyncGate 的 10 分钟闸门管，刚在生活页同步过就不再拉。
        viewModelScope.launch { syncInBackground(force = false) }
    }

    /** 切换月份；未来月禁用由 UI 控制，这里兜底拦截。 */
    fun loadMonth(month: YearMonthKey) {
        if (month > YearMonthKey.fromDate(AppClock.today())) return
        _uiState.update { it.copy(month = month) }
        monthKey.value = monthKeyOf(month)
    }

    /** 重试同步（失败后）。 */
    fun retry() {
        viewModelScope.launch { syncInBackground(force = true) }
    }

    /** 下拉/手动刷新 = 再跑一次增量同步。 */
    fun refresh() {
        viewModelScope.launch { syncInBackground(force = true) }
    }

    /** 后台增量同步：凭证缺失置引导态；失败保持本地数据并给一次性提示。 */
    private suspend fun syncInBackground(force: Boolean) {
        val credentials = credentialStore?.read()
        if (credentials == null) {
            _uiState.update { it.copy(noCredentials = true, canRetry = false, syncing = false) }
            return
        }
        _uiState.update { it.copy(syncing = true, error = null, noCredentials = false) }
        try {
            withContext(Dispatchers.Default) {
                syncer.sync(credentials.username, credentials.password, force = force)
            }
            // 同步后重查年视图（suspend 查询不随表自动刷新）
            _monthlyExpenses.value = db.yktTurnoverDao()
                .monthlyExpenses(recentMonthKeys)
                .associate { it.monthKey to it.amountFen }
            _uiState.update { it.copy(syncing = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: YktException.NeedCaptcha) {
            _uiState.update { it.copy(syncing = false, error = e.message, canRetry = false) }
        } catch (e: YktException.Credential) {
            _uiState.update {
                it.copy(
                    syncing = false,
                    error = "${e.message}。若密码已修改，请在「我的 → 校园卡」重新验证",
                    canRetry = true,
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(syncing = false, error = e.message ?: "同步失败") }
            _events.send(StatementEvent.Notice("同步失败：${e.message ?: "未知错误"}", NoticeTone.Error))
        }
    }

    private fun monthKeyOf(month: YearMonthKey): String = month.toString()

    private fun monthOf(key: String): YearMonthKey =
        YearMonthKey.parse(key) ?: YearMonthKey.fromDate(AppClock.today())

    /** 展示用月份标签（2026-09 → 「2026 年 9 月」）。 */
    fun monthLabel(month: YearMonthKey): String = "${month.year} 年 ${month.month} 月"
}
