package edu.jxslu.schedule

import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.ebike.EbikeReminderOutcome
import edu.jxslu.schedule.ui.ebike.EbikeReminderPort
import edu.jxslu.schedule.ui.ebike.startFreeRideNotice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「开始计时」那句提示语的映射（`startFreeRideNotice`，2026-11-26 起按平台能力分叉）。
 *
 * 钉住的分叉：**没有常驻倒计时的那一端（iOS）不许说「通知栏已显示倒计时」**——
 * 用户会去通知栏找一条永远不存在的卡片。有倒计时的那一端（Android）文案一字未改。
 */
class EbikeReminderNoticeTest {

    private class FakePort(
        private val outcome: EbikeReminderOutcome,
        override val hasCountdownNotification: Boolean = true,
    ) : EbikeReminderPort {
        override suspend fun startFreeRide(startAtMillis: Long): EbikeReminderOutcome = outcome

        override suspend fun checkFreeRide(): EbikeReminderOutcome = EbikeReminderOutcome.Nothing

        override suspend fun endFreeRide(): EbikeReminderOutcome = EbikeReminderOutcome.Ended

        override suspend fun reconcileAutoReturn(catchUp: Boolean) = Unit

        override suspend fun cancelAutoReturn() = Unit
    }

    private fun notice(port: EbikeReminderPort?): Pair<String, NoticeTone> =
        runBlocking { startFreeRideNotice(port, startAtMillis = 0L) }

    @Test
    fun `有常驻倒计时的一端照旧说通知栏已显示倒计时`() {
        val (text, tone) = notice(FakePort(EbikeReminderOutcome.Started, hasCountdownNotification = true))
        assertEquals("已开始计时，通知栏已显示倒计时", text)
        assertEquals(NoticeTone.Success, tone)
    }

    @Test
    fun `没有常驻倒计时的一端只承诺到点提醒`() {
        val (text, tone) = notice(FakePort(EbikeReminderOutcome.Started, hasCountdownNotification = false))
        assertEquals("已开始计时，免费时长结束时会提醒你", text)
        assertEquals(NoticeTone.Success, tone)
        assertTrue("不该提通知栏倒计时", !text.contains("倒计时"))
    }

    @Test
    fun `通知被关掉时不承诺任何提醒`() {
        val (text, tone) = notice(FakePort(EbikeReminderOutcome.StartedNoNotification))
        assertEquals("通知被关闭，提醒发不出来，请到系统设置打开", text)
        assertEquals(NoticeTone.Warning, tone)
    }

    @Test
    fun `提醒开关关着时说明计时照走`() {
        val (text, tone) = notice(FakePort(EbikeReminderOutcome.StartedSilent))
        assertEquals("已开始计时（免费时长提醒未开启）", text)
        assertEquals(NoticeTone.Info, tone)
    }

    @Test
    fun `排程失败要说清失败原因`() {
        val (text, tone) = notice(FakePort(EbikeReminderOutcome.Failed("权限被拒")))
        assertEquals("已开始计时；提醒排程失败：权限被拒", text)
        assertEquals(NoticeTone.Warning, tone)
    }

    @Test
    fun `没有提醒能力的平台只报已开始计时`() {
        val (text, tone) = notice(null)
        assertEquals("已开始计时", text)
        assertEquals(NoticeTone.Info, tone)
    }
}
