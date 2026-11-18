package edu.jxslu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「猜你想用」规则表与概率（DESIGN §4.51）。
 *
 * 这里钉三件事：时段表覆盖与边界、上课信号对 7 天生效、习惯权重 / 抽样方向。
 * 订单钩子（谁在什么时候写一条 [GuessUsage]）不在本文件——那些是业务成功点的事。
 */
class GuessYouWantTest {

    private val now = 1_800_000_000_000L

    private fun usage(day: Int, minute: Int, target: GuessTarget, at: Long = now) =
        GuessUsage(atMillis = at, dayOfWeek = day, slot = minute / 30, target = target)

    private fun decide(
        day: Int,
        minute: Int,
        usages: List<GuessUsage> = emptyList(),
        signal: ClassSignal? = null,
        roll: Double = 0.0,
    ): GuessPick? = GuessRules.decide(day, minute, signal, usages, now, roll)

    // ---------- 时段表 ----------

    @Test
    fun `平日时段表每个边界都落在规格上`() {
        val expected = mapOf(
            5 * 60 + 59 to null,
            6 * 60 to GuessTarget.QIEKJ,
            7 * 60 + 29 to GuessTarget.QIEKJ,
            7 * 60 + 30 to GuessTarget.KVCX,
            8 * 60 + 29 to GuessTarget.KVCX,
            8 * 60 + 30 to null,
            10 * 60 + 59 to null,
            11 * 60 to GuessTarget.KVCX,
            12 * 60 + 29 to GuessTarget.KVCX,
            12 * 60 + 30 to GuessTarget.QIEKJ,
            12 * 60 + 59 to GuessTarget.QIEKJ,
            13 * 60 to GuessTarget.KVCX,
            13 * 60 + 59 to GuessTarget.KVCX,
            14 * 60 to null,
            16 * 60 + 29 to null,
            16 * 60 + 30 to GuessTarget.KVCX,
            17 * 60 + 59 to GuessTarget.KVCX,
            18 * 60 to GuessTarget.QIEKJ,
            18 * 60 + 29 to GuessTarget.QIEKJ,
            18 * 60 + 30 to GuessTarget.KVCX,
            19 * 60 + 29 to GuessTarget.KVCX,
            19 * 60 + 30 to GuessTarget.QZXY,
            22 * 60 + 59 to GuessTarget.QZXY,
            23 * 60 to null,
        )
        expected.forEach { (minute, target) ->
            val name = "%02d:%02d".format(minute / 60, minute % 60)
            assertEquals("$name 应推荐 $target", target, GuessRules.windowAt(minute)?.target)
        }
    }

    @Test
    fun `时段表有序不重叠且空档与规格一致`() {
        val windows = GuessRules.weekdayWindows
        assertEquals(6 * 60, windows.first().startMinute)
        assertEquals(23 * 60, windows.last().endMinute)
        windows.forEach { assertTrue("区间必须非空", it.startMinute < it.endMinute) }
        windows.zipWithNext().forEach { (a, b) ->
            assertTrue("时段不能重叠或逆序", a.endMinute <= b.startMinute)
        }
        // 表里只放「有推荐」的段，空档是故意省略的；只允许规格里的两个日间空档
        val gaps = windows.zipWithNext()
            .filter { (a, b) -> a.endMinute < b.startMinute }
            .map { (a, b) -> a.endMinute to b.startMinute }
        assertEquals(
            listOf(8 * 60 + 30 to 11 * 60, 14 * 60 to 16 * 60 + 30),
            gaps,
        )
    }

    @Test
    fun `平日命中时段表走基础权重`() {
        val pick = decide(day = 2, minute = 7 * 60 + 40, roll = 0.99)
        assertEquals(GuessTarget.KVCX, pick?.target)
        assertEquals(GuessSource.SCHEDULE, pick?.source)
    }

    @Test
    fun `平日空档没有习惯就不出卡`() {
        assertNull(decide(day = 2, minute = 10 * 60))
    }

    // ---------- 上课 / 下课信号 ----------

    @Test
    fun `下课二十分钟内命中且优先于上课`() {
        val signal = GuessRules.classSignal(
            hasOngoing = true,
            minutesToOngoingEnd = 20,
            hasNext = true,
            minutesToNext = 25,
        )
        assertEquals(GuessSignalKind.AFTER_CLASS, signal?.kind)
        assertEquals(20, signal?.minutes)
    }

    @Test
    fun `下课二十一分钟不命中`() {
        assertNull(
            GuessRules.classSignal(
                hasOngoing = true,
                minutesToOngoingEnd = 21,
                hasNext = false,
                minutesToNext = null,
            ),
        )
    }

    @Test
    fun `上课三十分钟内命中三十一分钟不命中`() {
        assertEquals(
            GuessSignalKind.BEFORE_CLASS,
            GuessRules.classSignal(false, null, true, 30)?.kind,
        )
        assertNull(GuessRules.classSignal(false, null, true, 31))
    }

    @Test
    fun `刚下课零点不推荐`() {
        assertNull(GuessRules.classSignal(true, 0, false, null))
    }

    @Test
    fun `周六有课同样走快趣下课信号`() {
        val pick = decide(
            day = 6,
            minute = 9 * 60 + 40,
            signal = ClassSignal(GuessSignalKind.AFTER_CLASS, 18),
        )
        assertEquals(GuessTarget.KVCX, pick?.target)
        assertEquals(GuessSource.CLASS_END, pick?.source)
        assertEquals(18, pick?.minutes)
    }

    @Test
    fun `周日中午有课也先走上课信号`() {
        val pick = decide(
            day = 7,
            minute = 12 * 60,
            signal = ClassSignal(GuessSignalKind.BEFORE_CLASS, 12),
        )
        assertEquals(GuessTarget.KVCX, pick?.target)
        assertEquals(GuessSource.CLASS_START, pick?.source)
    }

    // ---------- 周日 / 周末 ----------

    @Test
    fun `周日中午固定问渠`() {
        listOf(11 * 60, 12 * 60, 12 * 60 + 59).forEach { minute ->
            val pick = decide(day = 7, minute = minute)
            assertEquals(GuessTarget.WENQU, pick?.target)
            assertEquals(GuessSource.SUNDAY_NOON, pick?.source)
        }
    }

    @Test
    fun `周日中午习惯也不能顶掉问渠`() {
        val pick = decide(
            day = 7,
            minute = 12 * 60,
            usages = List(5) { usage(7, 12 * 60, GuessTarget.QIEKJ) },
        )
        assertEquals(GuessTarget.WENQU, pick?.target)
    }

    @Test
    fun `周末没有习惯时固定胖乖`() {
        val pick = decide(day = 6, minute = 15 * 60)
        assertEquals(GuessTarget.QIEKJ, pick?.target)
        assertEquals(GuessSource.WEEKEND_DEFAULT, pick?.source)
    }

    @Test
    fun `周末习惯把先验按概率拉开`() {
        val usages = List(3) { usage(6, 15 * 60, GuessTarget.QZXY) }
        // 权重 胖乖3 / 快趣2 / U净1 / 趣智12 = 18；roll 0 落在胖乖，roll 0.5 落在趣智
        assertEquals(
            GuessTarget.QIEKJ,
            decide(day = 6, minute = 15 * 60, usages = usages, roll = 0.0)?.target,
        )
        val habitual = decide(day = 6, minute = 15 * 60, usages = usages, roll = 0.5)
        assertEquals(GuessTarget.QZXY, habitual?.target)
        assertEquals(GuessSource.HABIT, habitual?.source)
    }

    // ---------- 习惯 ----------

    @Test
    fun `空档三次同槽订单可以出习惯卡`() {
        val usages = List(3) { usage(2, 10 * 60, GuessTarget.UJING) }
        val pick = decide(day = 2, minute = 10 * 60, usages = usages)
        assertEquals(GuessTarget.UJING, pick?.target)
        assertEquals(GuessSource.HABIT, pick?.source)
    }

    @Test
    fun `空档两次同槽订单仍不出卡`() {
        val usages = List(2) { usage(2, 10 * 60, GuessTarget.UJING) }
        assertNull(decide(day = 2, minute = 10 * 60, usages = usages))
    }

    @Test
    fun `习惯把默认时段拉开概率`() {
        val usages = List(3) { usage(2, 7 * 60 + 40, GuessTarget.UJING) }
        // 快趣 10 / U净 12（4*3）= 22：roll 0.4 -> 8.8 落在快趣；roll 0.5 -> 11 落在 U净
        assertEquals(
            GuessTarget.KVCX,
            decide(day = 2, minute = 7 * 60 + 40, usages = usages, roll = 0.4)?.target,
        )
        val habitual = decide(day = 2, minute = 7 * 60 + 40, usages = usages, roll = 0.5)
        assertEquals(GuessTarget.UJING, habitual?.target)
        assertEquals(GuessSource.HABIT, habitual?.source)
    }

    @Test
    fun `跨工作日的同一时段也能累计`() {
        // 周一 / 周三 / 周五各一次，周二这个槽没有直接订单，但 band = 3 已过门槛
        val usages = listOf(
            usage(1, 10 * 60, GuessTarget.UJING),
            usage(3, 10 * 60, GuessTarget.UJING),
            usage(5, 10 * 60, GuessTarget.UJING),
        )
        val pick = decide(day = 2, minute = 10 * 60, usages = usages)
        assertEquals(GuessTarget.UJING, pick?.target)
        assertEquals(GuessSource.HABIT, pick?.source)
    }

    @Test
    fun `六十天以前的订单不参与`() {
        val old = now - 61L * 24 * 60 * 60 * 1000
        val usages = List(3) { usage(2, 10 * 60, GuessTarget.UJING, at = old) }
        assertNull(decide(day = 2, minute = 10 * 60, usages = usages))
    }

    @Test
    fun `习惯权重门槛与上限`() {
        assertNull(GuessRules.habitWeight(2, 0))
        assertEquals(12, GuessRules.habitWeight(3, 0))
        assertEquals(6, GuessRules.habitWeight(1, 2))
        assertEquals(40, GuessRules.habitWeight(20, 0))
        assertEquals(40, GuessRules.habitWeight(10, 10))
    }

    @Test
    fun `非法星期返回空`() {
        assertNull(decide(day = 0, minute = 10 * 60))
        assertNull(decide(day = 8, minute = 10 * 60))
    }

    // ---------- 槽内稳定器 ----------

    @Test
    fun `同一个槽不重复抽样`() {
        var calls = 0
        val stabilizer = GuessPickStabilizer()
        val first = stabilizer.resolve("2026-11-17|15|none") {
            calls++
            GuessPick(GuessTarget.KVCX, GuessSource.SCHEDULE)
        }
        val second = stabilizer.resolve("2026-11-17|15|none") {
            calls++
            GuessPick(GuessTarget.QIEKJ, GuessSource.SCHEDULE)
        }
        assertEquals(first, second)
        assertEquals(1, calls)
    }

    @Test
    fun `换槽才重新抽样，清空后也重新抽样`() {
        var calls = 0
        val stabilizer = GuessPickStabilizer()
        stabilizer.resolve("a") { calls++; null }
        stabilizer.resolve("a") { calls++; GuessPick(GuessTarget.KVCX, GuessSource.SCHEDULE) }
        assertEquals("空结果也要缓存", 1, calls)

        stabilizer.resolve("b") { calls++; GuessPick(GuessTarget.QZXY, GuessSource.SCHEDULE) }
        assertEquals(2, calls)

        stabilizer.reset()
        stabilizer.resolve("a") { calls++; null }
        assertEquals(3, calls)
    }
}
