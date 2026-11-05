package edu.jxslu.schedule.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下拉刷新驻留闸（`RefreshHold.kt` 的 `RefreshHoldGate`）。
 *
 * 重点钉住三件事：后台加载不能点亮、快速刷新也要转满最短驻留、busy 只续命不提前收
 * （2026-11-05 用户报「下拉刷新抽搐回弹」的判据，根因见 `RefreshHoldGate` 的 KDoc）。
 */
class RefreshHoldGateTest {

    private val dwell = 650L

    @Test
    fun `没下拉过时 busy 不点亮`() {
        val gate = RefreshHoldGate(dwell)
        // 进页 / 回层的后台加载：isRefreshing 不能被它点亮，否则 M3 的 onRelease 会吞掉下拉
        assertFalse(gate.settle(busy = true, nowMillis = 0L))
        assertFalse(gate.settle(busy = false, nowMillis = 100L))
    }

    @Test
    fun `下拉后立即结束也要转满最短驻留`() {
        val gate = RefreshHoldGate(dwell)
        gate.begin(nowMillis = 0L)
        // 缓存命中 / 未登录 / 秒失败：busy 一帧都没亮过
        assertTrue(gate.settle(busy = false, nowMillis = 16L))
        assertTrue(gate.settle(busy = false, nowMillis = 600L))
        assertFalse(gate.settle(busy = false, nowMillis = 650L))
    }

    @Test
    fun `刷新途中标志抖动不提前收`() {
        val gate = RefreshHoldGate(dwell)
        gate.begin(nowMillis = 0L)
        // 一次下拉发多个请求：请求之间 loading 归零一帧
        assertTrue(gate.settle(busy = true, nowMillis = 100L))
        assertTrue(gate.settle(busy = false, nowMillis = 200L))
        assertTrue(gate.settle(busy = true, nowMillis = 210L))
        assertTrue(gate.settle(busy = false, nowMillis = 400L))
        assertFalse(gate.settle(busy = false, nowMillis = 650L))
    }

    @Test
    fun `长刷新期间一直转 结束后不再补驻留`() {
        val gate = RefreshHoldGate(dwell)
        gate.begin(nowMillis = 0L)
        // 网络慢：busy 挂了两秒，驻留窗口早就过了，但 busy 期间不能收
        assertTrue(gate.settle(busy = true, nowMillis = 2_000L))
        assertTrue(gate.settle(busy = true, nowMillis = 3_000L))
        // busy 一落就收，不额外再等 650ms
        assertFalse(gate.settle(busy = false, nowMillis = 3_001L))
    }

    @Test
    fun `驻留期内再下拉重新计时`() {
        val gate = RefreshHoldGate(dwell)
        gate.begin(nowMillis = 0L)
        assertTrue(gate.settle(busy = false, nowMillis = 500L))
        gate.begin(nowMillis = 600L)
        assertTrue(gate.settle(busy = false, nowMillis = 1_000L))
        assertFalse(gate.settle(busy = false, nowMillis = 1_250L))
    }

    @Test
    fun `remainingMillis 只报驻留窗口内的剩余`() {
        val gate = RefreshHoldGate(dwell)
        assertEquals(0L, gate.remainingMillis(nowMillis = 0L))
        gate.begin(nowMillis = 1_000L)
        assertEquals(650L, gate.remainingMillis(nowMillis = 1_000L))
        assertEquals(150L, gate.remainingMillis(nowMillis = 1_500L))
        assertEquals(0L, gate.remainingMillis(nowMillis = 1_650L))
        assertEquals(0L, gate.remainingMillis(nowMillis = 9_999L))
    }
}
