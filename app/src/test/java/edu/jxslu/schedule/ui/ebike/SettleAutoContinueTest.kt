package edu.jxslu.schedule.ui.ebike

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 结算卡「继续找车」的倒计时（`RidePanels.settleContinueSecondsLeft`）。
 *
 * 结算卡在还车成功 8 秒后自动收尾（2026-10-31 用户要求）：按钮上的秒数要向上取整、
 * 到点归零；离开页面再回来按 `returnSummaryAt` 的真实经过时间算，不重新计时。
 */
class SettleAutoContinueTest {

    @Test
    fun `没有截止时刻不显示倒计时`() {
        assertEquals(0, settleContinueSecondsLeft(deadlineAt = 0L, now = 1_000L))
    }

    @Test
    fun `整八秒显示八`() {
        assertEquals(8, settleContinueSecondsLeft(deadlineAt = 9_000L, now = 1_000L))
    }

    @Test
    fun `不足一秒向上取整`() {
        // 剩 1ms 也算 1 秒，到最后一秒数字才消失
        assertEquals(1, settleContinueSecondsLeft(deadlineAt = 1_001L, now = 1_000L))
        assertEquals(8, settleContinueSecondsLeft(deadlineAt = 8_001L, now = 1L))
    }

    @Test
    fun `到点归零`() {
        assertEquals(0, settleContinueSecondsLeft(deadlineAt = 8_000L, now = 8_000L))
        assertEquals(0, settleContinueSecondsLeft(deadlineAt = 8_000L, now = 9_000L))
    }

    @Test
    fun `离开页面很久再回来直接归零`() {
        // 还车时刻 1000，8 秒后截止；十分钟后才进骑行页
        val deadline = 1_000L + SettleAutoContinueMillis
        assertEquals(0, settleContinueSecondsLeft(deadline, now = 601_000L))
    }

    @Test
    fun `逐秒递减到一`() {
        val deadline = 1_000L + SettleAutoContinueMillis
        assertEquals(8, settleContinueSecondsLeft(deadline, now = 1_000L))
        assertEquals(7, settleContinueSecondsLeft(deadline, now = 2_000L))
        assertEquals(2, settleContinueSecondsLeft(deadline, now = 7_000L))
        assertEquals(1, settleContinueSecondsLeft(deadline, now = 8_000L))
    }
}
