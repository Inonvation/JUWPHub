package edu.jxslu.schedule.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 进程内唯一的「现在」来源（iOS 移植 P-1 第 18 批，2026-11-24）。
 *
 * `java.time` 的 `LocalDate.now()` / `ZoneId.systemDefault()` 在 Kotlin/Native 上不存在。
 * 逐处替换容易漏，这里收成两个入口：默认参数统一写 `AppClock.today()`，
 * 需要固定日期的测试自己传值，不走这里。
 */
object AppClock {

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    fun nowDateTime(): LocalDateTime = dateTimeOf(nowMillis())

    /**
     * epoch 毫秒 → 本地墙上时刻。
     *
     * 与 `Instant.ofEpochMilli(x).atZone(ZoneId.systemDefault()).toLocalDateTime()` 同口径：
     * 时区取**系统默认**，不写死东八区——设备出国、用户手动改时区时，界面上「开阀 21:02」
     * 这类文案要跟着走，钉死 `Asia/Shanghai` 会显示成别的时区的钟点。
     */
    fun dateTimeOf(epochMillis: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

    fun today(): LocalDate = nowDateTime().date

    /**
     * 两个墙上时刻相差多少秒（[to] − [from]）。
     *
     * kotlinx-datetime 没有 `Duration.between(LocalDateTime, LocalDateTime)`；
     * 两侧都用 UTC 折算，时区偏移在相减时抵消，结果与 java.time 的墙上差一致。
     */
    fun secondsBetween(from: LocalDateTime, to: LocalDateTime): Long =
        (to.toInstant(TimeZone.UTC).toEpochMilliseconds() -
            from.toInstant(TimeZone.UTC).toEpochMilliseconds()) / 1000L

    /**
     * 墙上时刻加 [minutes] 分钟（可负、可跨天）。
     *
     * kotlinx-datetime 的 `LocalDateTime.plus(Int, DateTimeUnit)` 在本版本里签名对不上
     * （`DateTimeUnit` 只收 `TimeBased`），这里用「当天分钟数 + 天数进位」自己算，
     * 跨零点与负偏移都覆盖。
     */
    fun plusMinutes(value: LocalDateTime, minutes: Int): LocalDateTime {
        val total = value.hour * 60 + value.minute + minutes
        val dayShift = Math.floorDiv(total, MINUTES_PER_DAY)
        val inDay = Math.floorMod(total, MINUTES_PER_DAY)
        return LocalDateTime(
            value.year, value.monthNumber, value.dayOfMonth, inDay / 60, inDay % 60,
        ).let { if (dayShift == 0) it else shiftDays(it, dayShift) }
    }

    private const val MINUTES_PER_DAY = 24 * 60

    private fun shiftDays(value: LocalDateTime, days: Int): LocalDateTime {
        val date = value.date
        val shifted = LocalDate.fromEpochDays(date.toEpochDays() + days)
        return LocalDateTime(
            shifted.year, shifted.monthNumber, shifted.dayOfMonth, value.hour, value.minute,
        )
    }
}
