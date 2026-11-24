package edu.jxslu.schedule.domain

import edu.jxslu.schedule.data.qzxy.QzxyBill
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * 趣智消费记录的时间段筛选与按日分组（2026-11-05，用户要求「参考一卡通账单支持按日周月」）。
 *
 * 只做**纯计算**：数据仍然只有服务端账单一个来源（`order/query/account/bill/list` 按月查），
 * 这里不推算金额、不猜预扣。页面把已拉到的月份喂进来，按 [BillRange] 过滤后按自然日分组。
 *
 * 口径：
 * - **今日** = 今天 00:00–24:00；**本周** = 今天所在 ISO 周（周一 00:00 到下周一 00:00）；
 *   **本月** = 今天所在自然月；**全部** = 传入的全部账单（页面负责限制最多拉几个月）。
 * - 时间只认 `consumeDate` 的日期部分（`yyyy-MM-dd HH:mm:ss` 前 10 位）：解析不了的行
 *   在「今日/本周/本月」里一律**不出现**（宁缺不猜），在「全部」里照常显示、归到「日期未知」组。
 * - 金额按 `consumeMoney` 的字符串解析成分；解析不了按 0 计，不影响其他行。
 */
enum class BillRange(val label: String) {
    TODAY("今日"),
    WEEK("本周"),
    MONTH("本月"),
    ALL("全部"),
}

/** 一个自然日的账单分组（组内按时间倒序）。 */
data class BillDayGroup(
    val key: String,
    val label: String,
    val bills: List<QzxyBill>,
    val totalCents: Long,
)

/**
 * 解析账单时间；服务端给的是 `yyyy-MM-dd HH:mm:ss`，也接受只到分钟/只到日的形态。
 *
 * 返回 null 表示这条账单的时间不可信（空串、脏数据、只到年月的「2026-11」也算不可信——
 * 那是归属月份不是消费时刻）。
 */
fun parseBillTime(raw: String?): LocalDateTime? {
    val text = raw?.trim().orEmpty()
    if (text.length < 10) return null
    val date = runCatching { LocalDate.parse(text.substring(0, 10)) }.getOrNull() ?: return null
    val startOfDay = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 0, 0)
    if (text.length < 16) return startOfDay
    // 服务端用空格分隔，kotlinx 只认 ISO 的 `T`
    val normalized = text.substring(0, 19).padEnd(19, '0').replace(' ', 'T')
    return runCatching { LocalDateTime.parse(normalized) }.getOrNull() ?: startOfDay
}

fun parseBillDate(raw: String?): LocalDate? = parseBillTime(raw)?.date

/** `"0.80"` → 80；缺省/脏值 → 0（不抛，页面照常显示这一行）。 */
fun parseBillCents(raw: String?): Long = Money.parse(raw)?.fen ?: 0L

/** 某一时段的起止（左闭右开）；`ALL` 没有边界，返回 null。 */
fun billRangeBounds(range: BillRange, today: LocalDate = AppClock.today()): Pair<LocalDate, LocalDate>? =
    when (range) {
        BillRange.TODAY -> today to today.plus(DatePeriod(days = 1))
        BillRange.WEEK -> {
            val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
            monday to monday.plus(DatePeriod(days = 7))
        }
        BillRange.MONTH -> {
            val first = LocalDate(today.year, today.monthNumber, 1)
            first to first.plus(DatePeriod(months = 1))
        }
        BillRange.ALL -> null
    }

/**
 * 按时间段过滤（不排序，调用方负责分组/排序）。
 *
 * 「全部」原样返回（含时间不可解析的行）；其余时段只保留时间可信且落在区间内的行。
 */
fun filterBills(
    bills: List<QzxyBill>,
    range: BillRange,
    today: LocalDate = AppClock.today(),
): List<QzxyBill> {
    val bounds = billRangeBounds(range, today) ?: return bills
    val (from, to) = bounds
    return bills.filter { bill ->
        val date = parseBillDate(bill.consumeDate) ?: return@filter false
        date >= from && date < to
    }
}

/**
 * 按自然日分组，组按日期倒序、组内按时刻倒序。
 *
 * 时间不可解析的行单独归到「日期未知」组并排在最后（「全部」时段才可能遇到）。
 */
fun groupBillsByDay(
    bills: List<QzxyBill>,
    today: LocalDate = AppClock.today(),
): List<BillDayGroup> {
    val buckets = LinkedHashMap<LocalDate?, MutableList<QzxyBill>>()
    for (bill in bills) {
        buckets.getOrPut(parseBillDate(bill.consumeDate)) { mutableListOf() }.add(bill)
    }
    val groups = buckets.map { (date, rows) ->
        val sorted = rows.sortedWith(
            // 时间不可解析的排在该组末尾：用毫秒键代替 `LocalDateTime.MIN`
            compareByDescending<QzxyBill> { bill ->
                parseBillTime(bill.consumeDate)
                    ?.toInstant(TimeZone.currentSystemDefault())
                    ?.toEpochMilliseconds()
                    ?: Long.MIN_VALUE
            },
        )
        BillDayGroup(
            key = date?.toString() ?: UNKNOWN_DAY_KEY,
            label = date?.let { dayLabel(it, today) } ?: "日期未知",
            bills = sorted,
            totalCents = sumCents(sorted),
        )
    }
    return groups.sortedWith(
        compareByDescending<BillDayGroup> { it.key != UNKNOWN_DAY_KEY }
            .thenByDescending { it.key },
    )
}

/** 「日期未知」组的键：排序时永远垫底（`compareByDescending` 先按「是不是未知」排）。 */
const val UNKNOWN_DAY_KEY: String = ""

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今天"
    today.minus(DatePeriod(days = 1)) -> "昨天"
    else -> "${date.monthNumber} 月 ${date.dayOfMonth} 日"
}

fun sumCents(bills: List<QzxyBill>): Long = bills.sumOf { parseBillCents(it.consumeMoney) }

/** 金额文案：`¥12.40` / `¥0.00`。 */
fun billMoneyText(cents: Long): String = MoneyFormat.fromFen(cents)

/**
 * 入口卡右侧的一句话摘要：`本月 12 笔 · ¥8.60`。
 *
 * 只统计**本月**（不是当前选中时段）——入口卡出现在趣智主页，那里没有时段概念，
 * 说「本月」才不会让人以为那是全部。
 */
fun entrySummary(bills: List<QzxyBill>, today: LocalDate = AppClock.today()): String {
    val monthBills = filterBills(bills, BillRange.MONTH, today)
    return if (monthBills.isEmpty()) {
        "本月暂无"
    } else {
        "本月 ${monthBills.size} 笔 · ${billMoneyText(sumCents(monthBills))}"
    }
}
