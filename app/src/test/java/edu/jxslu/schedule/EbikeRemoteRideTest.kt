package edu.jxslu.schedule

import edu.jxslu.schedule.domain.EbikeRemoteRide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 远程用车提醒的判定与文案（`.agents/rules/ebike-account.md`，2026-10-24）。 */
class EbikeRemoteRideTest {

    @Test
    fun `只在二十的正整数倍提醒`() {
        assertFalse(EbikeRemoteRide.shouldRemind(0))
        assertFalse(EbikeRemoteRide.shouldRemind(-5))
        assertFalse(EbikeRemoteRide.shouldRemind(1))
        assertFalse(EbikeRemoteRide.shouldRemind(19))
        assertTrue(EbikeRemoteRide.shouldRemind(20))
        assertFalse(EbikeRemoteRide.shouldRemind(21))
        assertFalse(EbikeRemoteRide.shouldRemind(39))
        assertTrue(EbikeRemoteRide.shouldRemind(40))
    }

    @Test
    fun `文案带次数与劝导要点`() {
        val text = EbikeRemoteRide.dialogText(20)
        assertTrue(text.contains("20"))
        assertTrue(text.contains("走到车边"))
        assertTrue(text.contains("留给有需要的同学"))
        assertEquals("远程开启小提醒", EbikeRemoteRide.DIALOG_TITLE)
    }
}
