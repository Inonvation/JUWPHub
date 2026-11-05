package edu.jxslu.schedule.domain

import edu.jxslu.schedule.data.qzxy.QzxyBill
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

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

private val BILL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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
    if (text.length < 16) return date.atStartOfDay()
    val normalized = text.substring(0, 19).padEnd(19, '0')
    return runCatching { LocalDateTime.parse(normalized, BILL_DATE_TIME) }.getOrNull()
        ?: date.atStartOfDay()
}

fun parseBillDate(raw: String?): LocalDate? = parseBillTime(raw)?.toLocalDate()

/** `"0.80"` → 80；缺省/脏值 → 0（不抛，页面照常显示这一行）。 */
fun parseBillCents(raw: String?): Long {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
    val value = text.toBigDecimalOrNull() ?: return 0L
    return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

/** 某一时段的起止（左闭右开）；`ALL` 没有边界，返回 null。 */
fun billRangeBounds(range: BillRange, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate>? =
    when (range) {
        BillRange.TODAY -> today to today.plusDays(1)
        BillRange.WEEK -> {
            val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            monday to monday.plusDays(7)
        }
        BillRange.MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(1).plusMonths(1)
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
    today: LocalDate = LocalDate.now(),
): List<QzxyBill> {
    val bounds = billRangeBounds(range, today) ?: return bills
    val (from, to) = bounds
    return bills.filter { bill ->
        val date = parseBillDate(bill.consumeDate) ?: return@filter false
        !date.isBefore(from) && date.isBefore(to)
    }
}

/**
 * 按自然日分组，组按日期倒序、组内按时刻倒序。
 *
 * 时间不可解析的行单独归到「日期未知」组并排在最后（「全部」时段才可能遇到）。
 */
fun groupBillsByDay(
    bills: List<QzxyBill>,
    today: LocalDate = LocalDate.now(),
): List<BillDayGroup> {
    val buckets = LinkedHashMap<LocalDate?, MutableList<QzxyBill>>()
    for (bill in bills) {
        buckets.getOrPut(parseBillDate(bill.consumeDate)) { mutableListOf() }.add(bill)
    }
    val groups = buckets.map { (date, rows) ->
        val sorted = rows.sortedWith(
            compareByDescending<QzxyBill> { parseBillTime(it.consumeDate) ?: LocalDateTime.MIN },
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
    today.minusDays(1) -> "昨天"
    else -> "${date.monthValue} 月 ${date.dayOfMonth} 日"
}

fun sumCents(bills: List<QzxyBill>): Long = bills.sumOf { parseBillCents(it.consumeMoney) }

/** 金额文案：`¥12.40` / `¥0.00`。 */
fun billMoneyText(cents: Long): String =
    "¥" + BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

/**
 * 入口卡右侧的一句话摘要：`本月 12 笔 · ¥8.60`。
 *
 * 只统计**本月**（不是当前选中时段）——入口卡出现在趣智主页，那里没有时段概念，
 * 说「本月」才不会让人以为那是全部。
 */
fun entrySummary(bills: List<QzxyBill>, today: LocalDate = LocalDate.now()): String {
    val monthBills = filterBills(bills, BillRange.MONTH, today)
    return if (monthBills.isEmpty()) {
        "本月暂无"
    } else {
        "本月 ${monthBills.size} 笔 · ${billMoneyText(sumCents(monthBills))}"
    }
}
