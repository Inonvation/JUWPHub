package edu.jxslu.schedule

import edu.jxslu.schedule.data.qzxy.QzxyBill
import edu.jxslu.schedule.domain.BillRange
import edu.jxslu.schedule.domain.billMoneyText
import edu.jxslu.schedule.domain.entrySummary
import edu.jxslu.schedule.domain.filterBills
import edu.jxslu.schedule.domain.groupBillsByDay
import edu.jxslu.schedule.domain.parseBillCents
import edu.jxslu.schedule.domain.parseBillDate
import edu.jxslu.schedule.domain.sumCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 趣智消费记录的时间段筛选 / 分组 / 金额口径（2026-11-05）。
 *
 * 钉住的都是「猜错就会显示错数字」的地方：时间段边界（周一算本周、跨月不算本月）、
 * 时间不可解析的行在时段筛选里不出现、金额字符串解析成分。
 */
class QzxyBillStatsTest {

    private val today = LocalDate.of(2026, 11, 5) // 周四

    private fun bill(date: String, money: String?, desc: String = "热水消费") = QzxyBill(
        orderNo = "o-$date-$desc",
        consumeDate = date,
        consumeMoney = money,
        description = desc,
        deviceDescription = "3 号机",
    )

    private val bills = listOf(
        bill("2026-11-05 12:30:00", "0.80", "今天"),
        bill("2026-11-04 08:10:00", "0.40", "昨天"),
        bill("2026-11-02 09:00:00", "1.20", "本周一"),
        bill("2026-11-01 09:00:00", "0.80", "上周日"),
        bill("2026-10-31 09:00:00", "0.40", "上个月"),
    )

    @Test
    fun `今日只留当天`() {
        val rows = filterBills(bills, BillRange.TODAY, today)
        assertEquals(listOf("今天"), rows.map { it.description })
    }

    @Test
    fun `本周从周一算 上周日不算`() {
        val rows = filterBills(bills, BillRange.WEEK, today)
        assertEquals(listOf("今天", "昨天", "本周一"), rows.map { it.description })
    }

    @Test
    fun `本月跨月边界 上个月那笔不算`() {
        val rows = filterBills(bills, BillRange.MONTH, today)
        assertEquals(4, rows.size)
        assertTrue(rows.none { it.description == "上个月" })
    }

    @Test
    fun `全部原样返回`() {
        assertEquals(bills.size, filterBills(bills, BillRange.ALL, today).size)
    }

    @Test
    fun `时间不可解析的行 只在全部里出现`() {
        val dirty = bill("", "0.80", "脏数据")
        assertTrue(filterBills(listOf(dirty), BillRange.TODAY, today).isEmpty())
        assertTrue(filterBills(listOf(dirty), BillRange.MONTH, today).isEmpty())
        assertEquals(1, filterBills(listOf(dirty), BillRange.ALL, today).size)
        assertNull(parseBillDate(""))
        assertNull(parseBillDate("2026-11"))
        assertNull(parseBillDate(null))
    }

    @Test
    fun `按日分组 组倒序 组内按时刻倒序`() {
        val groups = groupBillsByDay(bills, today)
        assertEquals(listOf("今天", "昨天", "11 月 2 日", "11 月 1 日", "10 月 31 日"), groups.map { it.label })
        val sameDay = listOf(
            bill("2026-11-05 08:00:00", "0.40", "早"),
            bill("2026-11-05 21:00:00", "0.80", "晚"),
        )
        val merged = groupBillsByDay(sameDay, today)
        assertEquals(1, merged.size)
        assertEquals(listOf("晚", "早"), merged.first().bills.map { it.description })
        assertEquals(120L, merged.first().totalCents)
    }

    @Test
    fun `日期未知的行单独成组并垫底`() {
        val groups = groupBillsByDay(listOf(bill("", "0.80", "脏")) + bills, today)
        assertEquals("日期未知", groups.last().label)
        assertEquals(1, groups.last().bills.size)
    }

    @Test
    fun `金额字符串解析成分`() {
        assertEquals(80L, parseBillCents("0.80"))
        assertEquals(120L, parseBillCents("1.2"))
        assertEquals(0L, parseBillCents(null))
        assertEquals(0L, parseBillCents(""))
        assertEquals(0L, parseBillCents("--"))
        assertEquals(80L + 40L + 120L + 80L + 40L, sumCents(bills))
        assertEquals("¥8.60", billMoneyText(860))
        assertEquals("¥0.00", billMoneyText(0))
    }

    @Test
    fun `入口摘要只统计本月`() {
        assertEquals("本月 4 笔 · ¥3.20", entrySummary(bills, today))
        assertEquals("本月暂无", entrySummary(listOf(bill("2026-10-31 09:00:00", "0.40")), today))
    }
}
