package edu.jxslu.schedule

import edu.jxslu.schedule.ui.common.LaunchGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 二级页防连点的分槽语义（2026-11-05，`.agents/rules/ujing.md`）：
 * 同一页连点只开一个窗口，**跨页连续开窗不许吞**——后者是 U净
 * 「侧栏扫一扫进页 → 识别成功 → 跳下单页」能走通的前提。
 *
 * 每个用例用各自的 key：LaunchGate 是进程级单例，状态跨用例保留。
 */
class LaunchGateTest {

    @Test
    fun `同一页在窗口期内只放行第一次`() {
        val key = "same-page:UJING"
        assertTrue(LaunchGate.allow(key, now = 0))
        assertFalse(LaunchGate.allow(key, now = 499))
        assertTrue(LaunchGate.allow(key, now = 500))
    }

    @Test
    fun `不同页面互不吞掉`() {
        val first = "cross-page:UJING"
        val second = "cross-page:UJING_ORDER"
        assertTrue(LaunchGate.allow(first, now = 0))
        assertTrue(LaunchGate.allow(second, now = 100))
        assertFalse(LaunchGate.allow(second, now = 599))
        assertTrue(LaunchGate.allow(first, now = 700))
    }
}
