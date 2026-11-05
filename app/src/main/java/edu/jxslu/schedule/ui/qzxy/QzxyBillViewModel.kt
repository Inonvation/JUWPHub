package edu.jxslu.schedule.ui.qzxy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.qzxy.QzxyBill
import edu.jxslu.schedule.data.qzxy.QzxyRepository
import edu.jxslu.schedule.domain.BillRange
import edu.jxslu.schedule.domain.filterBills
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 趣智消费记录页状态（2026-11-05，DESIGN §4.30）。
 *
 * 服务端只有**按月查**的账单接口，所以页面自己维护「已加载了哪几个月」：
 * 进页拉当月 + 上个月（跨月那一周要用到上月），切到「全部」时可以再逐月往前补，
 * 上限 [QzxyBillViewModel.MAX_MONTHS] 个月——不为一个「全部」按钮发几十个请求。
 */
data class QzxyBillUiState(
    /** 已加载月份，倒序（`2026-11` 在头）。 */
    val months: List<String> = emptyList(),
    val billsByMonth: Map<String, List<QzxyBill>> = emptyMap(),
    val range: BillRange = BillRange.MONTH,
    val loading: Boolean = false,
    val error: String? = null,
    /** 还能不能再往前补一个月（到上限或已经拉到 2017 年就停）。 */
    val canLoadEarlier: Boolean = true,
) {
    val allBills: List<QzxyBill> get() = months.flatMap { billsByMonth[it].orEmpty() }

    /** 当前时段过滤后的账单（分组与合计在 UI 里用 [edu.jxslu.schedule.domain.groupBillsByDay] 算）。 */
    val visibleBills: List<QzxyBill> get() = filterBills(allBills, range)

    /** 已加载范围的说明：`已加载 2026-10 ~ 2026-11`。 */
    val loadedRangeText: String?
        get() {
            val oldest = months.lastOrNull() ?: return null
            val newest = months.firstOrNull() ?: return null
            return if (oldest == newest) "已加载 $newest" else "已加载 $oldest ~ $newest"
        }
}

/**
 * 消费记录页 ViewModel：只做「按月拉 + 本地按时段筛」两件事。
 *
 * 金额一律来自服务端账单（`order/query/account/bill/list`），本地不推算、不补预扣。
 */
class QzxyBillViewModel(
    private val repo: QzxyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(QzxyBillUiState())
    val ui: StateFlow<QzxyBillUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun setRange(range: BillRange) {
        if (_ui.value.range == range) return
        _ui.update { it.copy(range = range) }
    }

    /** 进页 / 下拉刷新：重拉**已加载过**的月份（首次就是当月 + 上个月）。 */
    fun refresh() {
        if (_ui.value.loading) return
        val months = _ui.value.months.ifEmpty { initialMonths() }
        load(months, replace = true)
    }

    /** 「加载更早」：再往前补一个月，最多 [MAX_MONTHS] 个月。 */
    fun loadEarlier() {
        val state = _ui.value
        if (state.loading || !state.canLoadEarlier) return
        val oldest = state.months.lastOrNull() ?: return
        val next = runCatching { YearMonth.parse(oldest).minusMonths(1) }.getOrNull() ?: return
        if (state.months.size >= MAX_MONTHS || next.year < EARLIEST_YEAR) {
            _ui.update { it.copy(canLoadEarlier = false) }
            return
        }
        load(listOf(next.toString()), replace = false)
    }

    private fun initialMonths(): List<String> {
        val now = YearMonth.now()
        return (0 until INITIAL_MONTHS).map { now.minusMonths(it.toLong()).toString() }
    }

    private fun load(months: List<String>, replace: Boolean) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                // 每个月一个请求、并行发；单月失败只影响那一月，其余照常落库。
                // CancellationException 必须原样抛（吞掉会让页面销毁后仍在拼状态）。
                val results = coroutineScope {
                    months.map { month ->
                        async {
                            month to try {
                                Result.success(repo.billList(month))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Result.failure(e)
                            }
                        }
                    }.awaitAll()
                }
                val loaded = results.mapNotNull { (month, result) ->
                    result.getOrNull()?.let { month to it }
                }
                val firstError = results.firstNotNullOfOrNull { (_, result) -> result.exceptionOrNull() }

                _ui.update { state ->
                    val merged = if (replace) {
                        loaded.toMap()
                    } else {
                        state.billsByMonth + loaded.toMap()
                    }
                    val orderedMonths = merged.keys.sortedDescending()
                    state.copy(
                        months = orderedMonths,
                        billsByMonth = merged,
                        loading = false,
                        error = firstError?.let(::describe),
                        canLoadEarlier = state.canLoadEarlier &&
                            orderedMonths.size < MAX_MONTHS &&
                            (orderedMonths.lastOrNull()?.let {
                                runCatching { YearMonth.parse(it).year }.getOrDefault(EARLIEST_YEAR) >
                                    EARLIEST_YEAR
                            } ?: false),
                    )
                }
            } finally {
                // 被取消（页面销毁 / VM 清理）时也要把 loading 复位：否则下次进页
                // 会以为「还在读」而一直停在加载态。
                _ui.update { if (it.loading) it.copy(loading = false) else it }
            }
        }
    }

    private fun describe(error: Throwable): String {
        val raw = error.message?.trim().orEmpty()
        return raw.ifEmpty { "读取消费记录失败，回到趣智校园页确认登录状态后再试" }
    }

    companion object {
        /** 进页先拉几个月：当月 + 上个月（本周/今日可能跨到上月）。 */
        const val INITIAL_MONTHS = 2

        /** 「加载更早」最多补到几个月（一年，够对账用）。 */
        const val MAX_MONTHS = 12

        /** 平台最早的业务月份（再往前没有数据，别为它发请求）。 */
        private const val EARLIEST_YEAR = 2017

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    QzxyBillViewModel(repo = Graph.qzxy(context.applicationContext)) as T
            }
    }
}
