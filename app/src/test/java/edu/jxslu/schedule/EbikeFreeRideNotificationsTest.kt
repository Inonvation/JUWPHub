package edu.jxslu.schedule

import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.ui.ebike.EbikeFreeRideNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 免费时长提醒的排程计划（iOS 本地通知用，2026-11-26）。
 *
 * 钉住两件「写错了只会在真机上悄悄错」的事：
 * 1. **标识符前缀** `freeride.` 与通知四族（`class.` / `homework.` / `ujing.` / `score.`）分开——
 *    撤销是按前缀扫队列的，撞前缀会撤掉别人家的提醒；
 * 2. **时间窗**：只排还没到点的那一条。已过点的重排会让系统立刻再弹一次，或者补一条更晚的提醒。
 */
class EbikeFreeRideNotificationsTest {

    private val start = 1_000_000_000_000L

    private fun minutesAfterStart(n: Long): Long = start + n * 60_000L

    @Test
    fun `标识符前缀与通知四族分开`() {
        assertEquals("freeride.", EbikeFreeRideNotifications.ID_PREFIX)
        assertEquals("freeride.lead", EbikeFreeRideNotifications.LEAD_ID)
        assertEquals("freeride.end", EbikeFreeRideNotifications.END_ID)
        assertTrue(EbikeFreeRideNotifications.LEAD_ID.startsWith(EbikeFreeRideNotifications.ID_PREFIX))
        assertTrue(EbikeFreeRideNotifications.END_ID.startsWith(EbikeFreeRideNotifications.ID_PREFIX))
        // 四族（见 design/ios-port.md §12.14）一个都不能被吃进来
        listOf("class.", "homework.", "ujing.", "score.").forEach { other ->
            assertFalse(
                "freeride 的标识符不该落进 $other 族",
                EbikeFreeRideNotifications.LEAD_ID.startsWith(other) ||
                    EbikeFreeRideNotifications.END_ID.startsWith(other),
            )
        }
    }

    @Test
    fun `起点时排两条且按时间先后`() {
        val planned = EbikeFreeRideNotifications.plan(start, leadMinutes = 3, nowMillis = start)
        assertEquals(2, planned.size)
        // 提前量在前、免费结束在后（同一时刻只有一轮计时在案，顺序影响哪条先覆盖）
        assertEquals(EbikeFreeRideNotifications.LEAD_ID, planned[0].identifier)
        assertEquals(EbikeFreeRideNotifications.END_ID, planned[1].identifier)
        assertEquals(EbikeFreeRide.leadReminderAt(start, 3), planned[0].triggerAtMillis)
        assertEquals(EbikeFreeRide.endReminderAt(start), planned[1].triggerAtMillis)
        assertTrue(planned[0].triggerAtMillis < planned[1].triggerAtMillis)
        assertTrue(planned[0].triggerAtMillis > start)
        // 标题与 Android 侧同一个常量（文案不在这里另写一份）
        assertEquals(EbikeFreeRide.LEAD_TITLE, planned[0].title)
        assertEquals(EbikeFreeRide.END_TITLE, planned[1].title)
        assertEquals(EbikeFreeRide.END_TEXT, planned[1].body)
    }

    @Test
    fun `提前量正文按触发那一刻的剩余时间写`() {
        val leadMinutes = 3
        val planned = EbikeFreeRideNotifications.plan(start, leadMinutes, nowMillis = start)
        val lead = planned.first { it.identifier == EbikeFreeRideNotifications.LEAD_ID }
        // 弹出来那一刻正好剩 3 分钟，不是「排程这一刻的 15 分钟」
        val remainingAtTrigger = EbikeFreeRide.remainingSeconds(start, lead.triggerAtMillis)
        assertEquals(3 * 60, remainingAtTrigger)
        assertEquals(
            EbikeFreeRide.leadText(start, lead.triggerAtMillis),
            lead.body,
        )
        assertTrue(lead.body.contains("3:00"))
    }

    @Test
    fun `提前量已过点就只排结束那一条`() {
        // 起点后第 13 分钟：提前量（第 12 分钟）已经过去，结束（第 15 分钟）还在前头
        val planned = EbikeFreeRideNotifications.plan(start, leadMinutes = 3, nowMillis = minutesAfterStart(13))
        assertEquals(1, planned.size)
        assertEquals(EbikeFreeRideNotifications.END_ID, planned[0].identifier)
    }

    @Test
    fun `两个点都过了就不排`() {
        // 免费结束已经过去（哪怕还在迟到窗口内）：iOS 没有「补发」这一档，重排等于立刻再弹一次
        assertTrue(
            EbikeFreeRideNotifications
                .plan(start, leadMinutes = 3, nowMillis = minutesAfterStart(16))
                .isEmpty(),
        )
        // 迟到窗口内同样不排
        assertTrue(
            EbikeFreeRideNotifications
                .plan(
                    start,
                    leadMinutes = 3,
                    nowMillis = EbikeFreeRide.freeEndMillis(start) + EbikeFreeRide.END_WINDOW_MS / 2,
                )
                .isEmpty(),
        )
    }

    @Test
    fun `没有在案计时就什么都不排`() {
        assertTrue(EbikeFreeRideNotifications.plan(0L, 3, minutesAfterStart(1)).isEmpty())
        assertTrue(EbikeFreeRideNotifications.plan(-1L, 3, minutesAfterStart(1)).isEmpty())
    }

    @Test
    fun `提前量吸附到允许区间后再算触发点`() {
        // 越界的提前量按 `EbikeFreeRide.coerceLead` 吸附：0/9 都落在 1..5 内
        val low = EbikeFreeRideNotifications.plan(start, leadMinutes = 0, nowMillis = start)
        val high = EbikeFreeRideNotifications.plan(start, leadMinutes = 9, nowMillis = start)
        assertEquals(EbikeFreeRide.leadReminderAt(start, 1), low[0].triggerAtMillis)
        assertEquals(EbikeFreeRide.leadReminderAt(start, 5), high[0].triggerAtMillis)
    }

    @Test
    fun `边界时刻不算未来`() {
        // 触发点正好等于 now：不排（`> now` 才是未来），否则系统会按「立即」投递一条
        val planned = EbikeFreeRideNotifications.plan(
            start,
            leadMinutes = 3,
            nowMillis = EbikeFreeRide.leadReminderAt(start, 3),
        )
        assertEquals(listOf(EbikeFreeRideNotifications.END_ID), planned.map { it.identifier })
    }
}
