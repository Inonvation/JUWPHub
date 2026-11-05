package edu.jxslu.schedule.ui.ebike

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.kqcx.KqcxOrderCache
import edu.jxslu.schedule.data.kqcx.KqcxSessionRepository
import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.domain.KqcxRefreshPolicy
import edu.jxslu.schedule.domain.KvcBusinessError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 历史订单页状态（DESIGN §3.9 / §4.32，2026-11-05）。 */
data class KvcxOrdersUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    /** 1 = 电单车 / 2 = 单车（官方 `deviceType`）。 */
    val deviceType: Int = KvcxOrdersViewModel.DEVICE_EBIKE,
    val loading: Boolean = false,
    /** null = 这个月还没查到过；空列表 = 查到了、这个月没有订单。 */
    val orders: List<KqcxAuth.OrderItem>? = null,
    val error: String? = null,
    /** 有订单记录的月份（倒序，含当月）；月份选择器与前后翻页都只在这些月份里走。 */
    val months: List<YearMonth> = emptyList(),
    /** 只看有费用的单（2026-11-05 用户要求「仅显示金额大于 0」）。 */
    val onlyPaid: Boolean = false,
) {
    /** 当前月之前的最近一个有记录月份；null = 没有更早的记录。 */
    val olderMonth: YearMonth? get() = months.filter { it < yearMonth }.maxOrNull()

    /** 当前月之后的最近一个有记录月份；null = 已经是最新的记录。 */
    val newerMonth: YearMonth? get() = months.filter { it > yearMonth }.minOrNull()

    /** 列表实际展示的订单：开了筛选就只留实付大于 0 的（进行中的单金额还是 0，自然被滤掉）。 */
    val visibleOrders: List<KqcxAuth.OrderItem>
        get() = orders.orEmpty().filter { !onlyPaid || it.hasFee }
}

/**
 * 服务端历史订单（DESIGN §3.9 / §4.32，2026-11-05）。
 *
 * 接口 `queryOrderList` **按「年 + 月 + 车型」查**（官方历史订单页同款），所以状态就是
 * 「当前这个月 × 车型」，切月份 / 切车型各自重拉一次——没有一次给全部历史的接口，
 * 也不做后台批量预取（几十个月就是几十个请求，用户没点就不该发）。
 */
class KvcxOrdersViewModel(
    private val session: KqcxSessionRepository,
    /** 订单缓存（2026-11-05）：翻到哪个月就把那个月的订单落库，账号页统计直接受益。 */
    private val orderCache: KqcxOrderCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(KvcxOrdersUiState())
    val uiState: StateFlow<KvcxOrdersUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** 本次会话里每个「月 × 车型」最近一次成功联网的时刻（节流用，不落盘）。 */
    private val fetchedAt = mutableMapOf<Pair<YearMonth, Int>, Long>()

    init {
        reload(pickLatest = true)
    }

    fun selectDevice(deviceType: Int) {
        if (deviceType == _uiState.value.deviceType) return
        // 月份由 reload 按新车型重定（落到该车型最新有记录的月份），这里只清列表
        _uiState.update { it.copy(deviceType = deviceType, orders = null, error = null) }
        reload()
    }

    fun selectMonth(yearMonth: YearMonth) {
        if (yearMonth == _uiState.value.yearMonth) return
        _uiState.update { it.copy(yearMonth = yearMonth, orders = null, error = null) }
        reload()
    }

    /** 往前翻：只走「有记录」的月份，跳过整月空白的那些（2026-11-05 用户要求）。 */
    fun prevMonth() {
        val older = _uiState.value.olderMonth ?: return
        selectMonth(older)
    }

    /** 往后翻：同样只走有记录的月份，最新那条之后就没有「下一月」了。 */
    fun nextMonth() {
        val newer = _uiState.value.newerMonth ?: return
        selectMonth(newer)
    }

    /** 「仅显示有费用」开关（2026-11-05 用户要求）。 */
    fun toggleOnlyPaid() = _uiState.update { it.copy(onlyPaid = !it.onlyPaid) }

    /** 下拉刷新：绕过「刚拉过就不发」的节流，强制联网一次。 */
    fun refresh() = reload(force = true)
    /**
     * 拉当前「月 × 车型」：先定月份 → 先出缓存 → 会话内没拉过（或强制）才联网 → 落缓存并更新月份列表。
     *
     * [pickLatest] 只给首次进页用：落到**最新有记录的月份**（用户打开就想看最近一次骑行），
     * 其余入口（切月 / 切车型 / 刷新）都尊重当前选中的月份。
     */
    private fun reload(force: Boolean = false, pickLatest: Boolean = false) {
        val requested = _uiState.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val choices = loadMonths(requested.deviceType)
            val month = when {
                pickLatest -> choices.recorded.firstOrNull() ?: YearMonth.now()
                requested.yearMonth in choices.selectable -> requested.yearMonth
                else -> choices.selectable.firstOrNull() ?: requested.yearMonth
            }
            _uiState.update { it.copy(months = choices.selectable, yearMonth = month) }

            val key = month to requested.deviceType
            val cached = runCatching { orderCache.ordersFor(month, requested.deviceType) }
                .getOrDefault(emptyList())
            if (cached.isNotEmpty()) {
                _uiState.update { it.copy(orders = cached) }
            }
            if (!force && KqcxRefreshPolicy.isFresh(fetchedAt[key], System.currentTimeMillis())) {
                _uiState.update { it.copy(loading = false, error = null) }
                return@launch
            }
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val orders = session.queryOrderHistory(month.year, month.monthValue, requested.deviceType)
                // 顺手落缓存（失败不影响列表展示：缓存只是统计的加速层）
                runCatching { orderCache.store(orders) }
                fetchedAt[key] = System.currentTimeMillis()
                // 新拉到的月份可能让「有记录的月份」变多，翻页范围跟着更新
                val monthsAfter = loadMonths(requested.deviceType).selectable
                _uiState.update {
                    // 期间用户换了车型 / 月份：这次结果作废，别把旧列表盖上去
                    if (it.deviceType != requested.deviceType || it.yearMonth != month) {
                        it
                    } else {
                        it.copy(loading = false, orders = orders, months = monthsAfter)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update {
                    if (it.deviceType != requested.deviceType || it.yearMonth != month) {
                        it
                    } else {
                        it.copy(loading = false, error = errorText(error))
                    }
                }
            }
        }
    }

    /** 月份候选：[recorded] = 真有订单的月份（定初始月份用），[selectable] = 选择器里的列表。 */
    private data class MonthChoices(val recorded: List<YearMonth>, val selectable: List<YearMonth>)

    /**
     * 月份候选：缓存里攒下的（按车型，倒序）+ 当月。
     *
     * 缓存全空（新装 / 刚登录还没同步过）时 [selectable] 退回最近 [FALLBACK_MONTHS] 个月——
     * 否则页面只有当月一个月份可翻，用户会以为历史订单丢了；账号页同步跑完后自然收敛成真实月份。
     */
    private suspend fun loadMonths(deviceType: Int): MonthChoices {
        val recorded = runCatching { orderCache.months(deviceType) }.getOrDefault(emptyList())
        val thisMonth = YearMonth.now()
        val selectable = if (recorded.isEmpty()) {
            (0 until FALLBACK_MONTHS).map { thisMonth.minusMonths(it.toLong()) }
        } else {
            (recorded + thisMonth).distinct().sortedDescending()
        }
        return MonthChoices(recorded = recorded, selectable = selectable)
    }
    /** 查询失败的文案分类（与快趣账号页同口径：业务错误给服务端原文，网络类给可操作建议）。 */
    private fun errorText(error: Throwable): String = when (error) {
        is KvcBusinessError ->
            if (error.message?.contains("未登录") == true) {
                "快趣登录已失效，请重新登录"
            } else {
                error.message ?: "查询失败"
            }
        is SocketTimeoutException -> "查询超时，请稍后重试"
        is InterruptedIOException -> "查询超时，请稍后重试"
        is IOException -> "网络不可用，请检查网络后重试"
        else -> "查询异常：${error.message ?: error.javaClass.simpleName}"
    }

    companion object {
        const val DEVICE_EBIKE = 1
        const val DEVICE_BIKE = 2

        /** 缓存全空时月份选择器退回的月数（一年，够用户先翻起来）。 */
        private const val FALLBACK_MONTHS = 12

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    KvcxOrdersViewModel(
                        session = Graph.kqcx(context.applicationContext),
                        orderCache = Graph.kqcxOrderCache(context.applicationContext),
                    ) as T
            }
    }
}