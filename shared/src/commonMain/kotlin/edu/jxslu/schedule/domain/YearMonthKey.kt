package edu.jxslu.schedule.domain

import kotlinx.datetime.LocalDate

/**
 * 「不带日」的月份键（iOS 移植 S20，2026-11-25）。
 *
 * ## 为什么不能直接用 kotlinx-datetime 的类型
 *
 * **kotlinx-datetime 没有 `YearMonth`**：它的最小粒度就是 [LocalDate]（「某一天」）。
 * 而账单、消费流水、快充订单这些按月分页的界面要的正是「月份」本身——没有日、没有时区、
 * 也不做任何时点换算。拿 [LocalDate] 顶替，就得约定一个「用 1 号」的隐含口径，
 * 谁按月末或 31 号构造一次，比较结果与 `toString` 立刻分叉；写进 Room 查询参数时
 * 还会变成 `2026-08-01` 与 `2026-08-31` 两个键查同一批数据。
 *
 * 另一条路 `java.time.YearMonth` 在 Kotlin/Native 上不存在——它正是 iOS 移植的阻塞点：
 * 共享层的 `MonthPicker` 用它，于是所有调用它的页面都搬不动。
 *
 * ## `toString()` 定宽，字典序 = 时间序
 *
 * 恒为 `yyyy-MM`（4 位年 + 2 位月）。本仓有多处**依赖这条性质**：月份键被直接当排序键
 * 与 Room 查询参数用（`ykt_turnovers.monthKey`、`PowerBill.recentMonthKeys`、
 * `KvcxOrderStore` 的 `yyyy-MM` 键、`QzxyBillStats` 的按月分桶）。改成不定宽
 * （如 `2026-8`）会让 `2026-10` 排在 `2026-8` 前面，柱状图与分页会静默错序。
 *
 * 4 位年对公元 10000 年以后会退化成 5 位、这条性质随之失效；不为那种情况加分支。
 *
 * ## 没有 `lastDay()` / `atEndOfMonthDay()`
 *
 * 逐处核过调用方（`MonthPicker`、缴费账单页、消费流水页、快充订单页）：全都只用
 * 「加/减月份」「比大小」「格式化」三件事，没有任何一处需要月末那一天的日号。
 * 真要用时再加，别在这里预先摆一个没人调的成员。
 */
data class YearMonthKey(val year: Int, val month: Int) : Comparable<YearMonthKey> {

    init {
        // 月份越界只可能来自手写的字面量或解析漏洞；与其让它悄悄参与比较，
        // 不如在构造期就炸出来（parse 已先校验，正常路径走不到这里）
        require(month in 1..12) { "月份越界：$month" }
    }

    /** `2026-01` 形态（定宽，理由见类注释）。 */
    override fun toString(): String = "${TextFmt.pad(year, 4)}-${TextFmt.pad(month)}"

    override fun compareTo(other: YearMonthKey): Int =
        if (year != other.year) year.compareTo(other.year) else month.compareTo(other.month)

    /**
     * 加减月份（可负、可跨年）。
     *
     * 用「总月序」一次算完再拆回去：`2026-01 - 1` 与 `2025-12 + 1` 都必须落在 `2025-12`。
     * 整除取 `floorDiv` / `mod`（向下取整）而不是 `/` 与 `%`——后者对负数向上取整，
     * 会算出 `0000-00` 这种月份 0 的非法键。
     */
    fun plusMonths(delta: Int): YearMonthKey {
        val total = year * 12 + (month - 1) + delta
        return YearMonthKey(total.floorDiv(12), total.mod(12) + 1)
    }

    companion object {

        /**
         * `2026-08` → 键；其它形态一律 null（含 `2026-8`、`2026-08-01`、空串）。
         *
         * 严格只认定宽形态：宽松解析会让「脏键」通过，之后 `toString()` 又把它规范成
         * 另一种写法，同一批数据出现两个键。调用方原本都是 `runCatching { YearMonth.parse }`
         * 或 `?: 默认值`，改成可空返回后语义不变。
         */
        fun parse(text: String): YearMonthKey? {
            val value = text.trim()
            if (value.length != 7 || value[4] != '-') return null
            if (!value.take(4).all { it in '0'..'9' }) return null
            if (!value[5].isDigit() || !value[6].isDigit()) return null
            val year = value.substring(0, 4).toIntOrNull() ?: return null
            val month = value.substring(5, 7).toIntOrNull() ?: return null
            if (month !in 1..12) return null
            return YearMonthKey(year, month)
        }

        /**
         * 日期 → 它所在的月。
         *
         * 「现在几月」不在这里另立 `now()`：时间源统一是 [AppClock]，
         * 调用方写 `YearMonthKey.fromDate(AppClock.today())` 才看得出这个值从哪来、
         * 单测也才能传固定日期。
         */
        fun fromDate(date: LocalDate): YearMonthKey = YearMonthKey(date.year, date.monthNumber)
    }
}
