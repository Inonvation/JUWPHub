package edu.jxslu.schedule.ui.tile

import edu.jxslu.schedule.SubpageScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 快捷设置磁贴的落点映射（DESIGN §3.32）：纯函数，不碰 Android。 */
class TileTargetsTest {

    @Test
    fun payCodeWithCredentialsOpensCode() {
        val action = TileTargets.action(
            TileKind.PAY_CODE,
            TileSignals(campusCardConfigured = true),
        )
        assertEquals(SubpageScreen.PAY_CODE, action.request.screen)
        assertFalse(action.autoStart)
        assertTrue(action.ready)
    }

    @Test
    fun payCodeWithoutCredentialsOpensSettings() {
        val action = TileTargets.action(
            TileKind.PAY_CODE,
            TileSignals(campusCardConfigured = false),
        )
        assertEquals(SubpageScreen.CAMPUS_CARD_SETTINGS, action.request.screen)
        assertFalse(action.autoStart)
        assertFalse(action.ready)
    }

    @Test
    fun qiekjLoggedInOpensWaterWithAutoStart() {
        val action = TileTargets.action(
            TileKind.QIEKJ_WATER,
            TileSignals(qiekjLoggedIn = true),
        )
        assertEquals(SubpageScreen.WATER, action.request.screen)
        assertTrue(action.autoStart)
        assertTrue(action.ready)
    }

    @Test
    fun qiekjLoggedOutOpensWaterWithoutAutoStart() {
        val action = TileTargets.action(
            TileKind.QIEKJ_WATER,
            TileSignals(qiekjLoggedIn = false),
        )
        assertEquals(SubpageScreen.WATER, action.request.screen)
        assertFalse(action.autoStart)
        assertFalse(action.ready)
    }

    @Test
    fun qzxyLoggedInOpensQzxyWithAutoStart() {
        val action = TileTargets.action(
            TileKind.QZXY_WATER,
            TileSignals(qzxyLoggedIn = true),
        )
        assertEquals(SubpageScreen.QZXY, action.request.screen)
        assertTrue(action.autoStart)
        assertTrue(action.ready)
    }

    @Test
    fun qzxyLoggedOutOpensQzxyWithoutAutoStart() {
        val action = TileTargets.action(
            TileKind.QZXY_WATER,
            TileSignals(qzxyLoggedIn = false),
        )
        assertEquals(SubpageScreen.QZXY, action.request.screen)
        assertFalse(action.autoStart)
        assertFalse(action.ready)
    }
}
