package edu.jxslu.schedule.domain

import edu.jxslu.schedule.data.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 上课倒计时卡片（灵动岛，DESIGN §3.7，2026-10-29）的纯函数口径。
 *
 * 这三件事算错都只能在真机上看出来（芯片上「剩 9 分」跳错一分钟、进度条倒着走、
 * 地点被省略成 `@()`），后台链路上完全不可见，所以在 JVM 里钉死：
 * - [minutesLeft]：向上取整，已上课不出现 0 / 负数；
 * - [progressPercent]：提前量窗口走完的比例，闹钟迟到时从中间起步；
 * - [liveClassLine] / [liveChipText] / [chipPlace]：正文与芯片的文案口径
 *   （芯片只有一个文字槽，地点与分钟数压缩拼接）。
 */
class LiveClassUpdateTest {

    private val slots = DefaultData.defaultTimeSlots

    /** 第 3-4 节 10:15–11:40（`DefaultData` 作息表）。 */
    private fun plan(
        startAt: LocalDateTime,
        leadMinutes: Long = 10,
        course: Course = course(),
    ) = ReminderPlan(
        course = course,
        date = startAt.toLocalDate(),
        startMinutes = startAt.hour * 60 + startAt.minute,
        startAt = startAt,
        triggerAt = startAt.minusMinutes(leadMinutes),
    )

    private fun course(
        position: String = "教学南大楼(南B208)",
        isCustomTime: Boolean = false,
        customStartTime: String = "",
    ) = Course(
        id = 1,
        name = "高等数学",
        teacher = "陈磊",
        position = position,
        day = 4,
        startSection = 3,
        endSection = 4,
        weeks = (1..10).toSet(),
        isCustomTime = isCustomTime,
        customStartTime = customStartTime,
        customEndTime = if (isCustomTime) "13:10" else "",
    )

    private fun at(hour: Int, minute: Int, second: Int = 0): LocalDateTime =
        LocalDateTime.of(LocalDate.parse("2026-09-17"), LocalTime.of(hour, minute, second))

    // ---- 剩余分钟数（芯片「剩 N 分」）----

    @Test
    fun minutesLeft_roundsUp() {
        val p = plan(at(10, 15))
        // 还剩 8 分 59 秒 → 说「剩 9 分」；向下取整会少报一分钟，用户按它出门会迟到
        assertEquals(9L, p.minutesLeft(at(10, 6, 1)))
        assertEquals(10L, p.minutesLeft(at(10, 5, 0)))
        assertEquals(1L, p.minutesLeft(at(10, 14, 30)))
    }

    @Test
    fun minutesLeft_neverBelowOne() {
        val p = plan(at(10, 15))
        // 上课那一刻 / 闹钟迟到跨过上课时刻：芯片不出现「剩 0 分」「剩 -1 分」
        assertEquals(1L, p.minutesLeft(at(10, 15)))
        assertEquals(1L, p.minutesLeft(at(10, 20)))
    }

    // ---- 进度条 ----

    @Test
    fun progress_walksTheLeadWindow() {
        val p = plan(at(10, 15)) // 窗口 10:05–10:15
        assertEquals(0, p.progressPercent(at(10, 5)))
        assertEquals(50, p.progressPercent(at(10, 10)))
        assertEquals(100, p.progressPercent(at(10, 15)))
    }

    @Test
    fun progress_delayedAlarmStartsMidway() {
        // 闹钟被 ROM 推迟到只剩 3 分钟才发：进度条从 70% 起步，如实反映而不是假装从 0 起
        val p = plan(at(10, 15))
        assertEquals(70, p.progressPercent(at(10, 12)))
    }

    @Test
    fun progress_clampedOutsideWindow() {
        val p = plan(at(10, 15))
        assertEquals(0, p.progressPercent(at(10, 0)))
        assertEquals(100, p.progressPercent(at(10, 30)))
    }

    @Test
    fun progress_zeroLead_doesNotDivideByZero() {
        val p = plan(at(10, 15), leadMinutes = 0)
        assertEquals(100, p.progressPercent(at(10, 15)))
    }

    // ---- 卡片正文与芯片文案 ----

    @Test
    fun liveClassLine_locationFirst() {
        // 地点在前：倒计时卡是「马上要出门」的场景，地点是唯一不能猜的信息
        assertEquals("@南B208 · 10:15 上课", liveClassLine(slots, course()))
    }

    @Test
    fun liveClassLine_withoutLocation() {
        assertEquals("10:15 上课", liveClassLine(slots, course(position = "")))
    }

    @Test
    fun liveClassLine_customTimeCourse() {
        val custom = course(isCustomTime = true, customStartTime = "12:30")
        assertEquals("@南B208 · 12:30 上课", liveClassLine(slots, custom))
    }

    @Test
    fun liveChipText_placeBeforeMinutes() {
        // 折叠芯片只有**一个**文字槽（没有放图标的第二格），地点只能压缩拼进这一行；
        // 地点放前位——被系统截断时先丢分钟数，而不是先丢教室号
        assertEquals("南B208·8分", liveChipText("教学南大楼(南B208)", 8))
        assertEquals("南B208·30分", liveChipText("教学南大楼(南B208)", 30))
    }

    @Test
    fun liveChipText_longPlaceKeepsRoomNumber() {
        // 楼名超过 5 字：留「首字 + 房间号」，房间号是唯一不能猜的部分
        assertEquals("工210·8分", liveChipText("工程训练中心210", 8))
    }

    @Test
    fun liveChipText_withoutPlace_fallsBackToCountdownOnly() {
        // 无地点 / 教务返回的 `()` 脏数据：退回旧的「剩 N 分」，不留 `·8分` 这种断头文案
        assertEquals("剩8分", liveChipText("", 8))
        assertEquals("剩8分", liveChipText("()", 8))
        assertEquals("剩30分", liveChipText("  ", 30))
    }

    @Test
    fun chipPlace_budgetIsFiveChars() {
        // 括号楼名先由 compactPosition 剥成房号，本来就在预算内
        assertEquals("南B302", chipPlace("教学南大楼(南B302)"))
        assertEquals("工210", chipPlace("工程训练中心210"))
        // 没有房间号的纯楼名：截前 5 字，宁可给楼名也不给半个房号
        assertEquals("工程训练中", chipPlace("工程训练中心"))
    }
}
