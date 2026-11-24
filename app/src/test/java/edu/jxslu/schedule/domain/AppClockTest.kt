package edu.jxslu.schedule.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AppClock.dateTimeOf` 的时区口径（iOS 移植 P-1，2026-11-24）。
 *
 * 它是「epoch 毫秒 → 本地墙上时刻」的唯一入口，`QzxyWateringFormat.clockText` 这类文案都经它折算。
 * 断言全部按**运行时的系统默认时区**推，不写死东八区的期望值：写死字面量的测试在两种实现下
 * 都过不了或都能过，验不出「用的是不是默认时区」这件事。
 */
class AppClockTest {

    @Test
    fun `epoch 零点按系统默认时区折算`() {
        val zone = TimeZone.currentSystemDefault()
        val local = AppClock.dateTimeOf(0L)
        // 同一个瞬间折进同一时区再折回来，必须原样复原。写死 Asia/Shanghai 的实现在别处跑
        // 这份断言会差出整个时区偏移，这里就能拦住。
        assertEquals(0L, local.toInstant(zone).toEpochMilliseconds())
        // 1970-01-01T00:00Z 在西半球是 1969-12-31 的晚上，所以不钉唯一日期，
        // 只要求落在这两天之一（时区偏移绝对值不超过 14 小时）。
        assertTrue(local.date in setOf(LocalDate(1969, 12, 31), LocalDate(1970, 1, 1)))
    }
}
