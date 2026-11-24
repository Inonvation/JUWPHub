package edu.jxslu.schedule.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * kotlinx-datetime 在本工程里的 API 面貌（iOS 移植 P-1 第 6 批，2026-11-24）。
 *
 * 作用不是验证库本身，而是把「0.8.0 上到底能用哪些写法」钉成可执行的事实：
 * 后续把 `java.time` 逐文件换过来时照着这里已编译通过的写法改，不用反复试错。
 *
 * 已确认可用：`LocalDate` / `LocalTime` / `LocalDateTime` / `TimeZone` / `DatePeriod`、
 * `Instant.fromEpochMilliseconds`、`toLocalDateTime(zone)`、`atStartOfDayIn(zone)`。
 * **没有**等价物：`java.time.format.DateTimeFormatter`——自定义格式要手写（见 [TextFmt]）。
 */
class KotlinxDateTimeSmokeTest {

    @Test
    fun `日期加减按日历规则`() {
        val d = LocalDate(2026, 10, 5)
        assertEquals(LocalDate(2026, 10, 12), d.plus(DatePeriod(days = 7)))
        assertEquals(LocalDate(2026, 12, 31), LocalDate(2026, 12, 30).plus(DatePeriod(days = 1)))
    }

    @Test
    fun `时刻到本地时间`() {
        val midnight = LocalDate(2026, 10, 5).atStartOfDayIn(TimeZone.UTC)
        val local = midnight.toLocalDateTime(TimeZone.UTC)
        assertEquals(LocalDate(2026, 10, 5), local.date)
        assertEquals(LocalTime(0, 0), local.time)
    }

    @Test
    fun `epoch 毫秒换算`() {
        val t = Instant.fromEpochMilliseconds(1_700_000_000_000L).toLocalDateTime(TimeZone.UTC)
        assertEquals(LocalDateTime(LocalDate(2023, 11, 14), LocalTime(22, 13, 20)), t)
    }
}
