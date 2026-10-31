package edu.jxslu.schedule.ui.today

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结束动作 → 完成态停留的帧级判据（`ActiveServiceCard` 的 `rememberDoneLinger`）。
 *
 * 重点钉住「数据消失与完成态亮起必须同一帧」：晚一帧会让外层把服务卡条目先挪到列表下方、
 * 下一帧再挪回顶部，观感就是「已还车」卡从下面窜上来（2026-10-31 用户报障）。
 */
class DoneLingerGateTest {

    @Test
    fun `同帧收尾当帧亮完成态`() {
        // 还车成功的那个组合：ride 清空与 busy 清空被同一帧观察到
        val gate = DoneLingerGate(visible = true)
        assertFalse(gate.frame(visible = true, ending = true))
        assertTrue(gate.frame(visible = false, ending = false))
    }

    @Test
    fun `动作在跑时数据还在不亮完成态`() {
        val gate = DoneLingerGate(visible = true)
        assertFalse(gate.frame(visible = true, ending = false))
        assertFalse(gate.frame(visible = true, ending = true))
        assertFalse(gate.frame(visible = true, ending = true))
    }

    @Test
    fun `数据消失时动作已跑过亮完成态`() {
        val gate = DoneLingerGate(visible = true)
        assertFalse(gate.frame(visible = true, ending = true))
        // 动作收尾与数据消失分两帧到达（还车链路先清 ride、finally 再清 busy）：
        // ride 一没就要亮，不等 busy 也清掉
        assertTrue(gate.frame(visible = false, ending = true))
        // 下一帧 visible 已翻假，完成态保持
        assertTrue(gate.frame(visible = false, ending = false))
    }

    @Test
    fun `没有结束动作的自然收尾不亮完成态`() {
        // 165 秒自动关阀 / 后台自动还车：数据没了但没人点过「结束」
        val gate = DoneLingerGate(visible = true)
        assertFalse(gate.frame(visible = true, ending = false))
        assertFalse(gate.frame(visible = false, ending = false))
    }

    @Test
    fun `宽限期到后数据才消失不亮完成态`() {
        val gate = DoneLingerGate(visible = true)
        assertFalse(gate.frame(visible = true, ending = true))
        // 动作失败 / 被挡，数据还在；6 秒宽限过完，之后的到期清理不算刚结束
        assertFalse(gate.frame(visible = true, ending = false))
        gate.expireEnding()
        assertFalse(gate.frame(visible = false, ending = false))
    }

    @Test
    fun `停留到期后收掉完成态`() {
        val gate = DoneLingerGate(visible = true)
        gate.frame(visible = true, ending = true)
        assertTrue(gate.frame(visible = false, ending = false))
        gate.expireLinger()
        assertFalse(gate.frame(visible = false, ending = false))
    }

    @Test
    fun `停留期间下一单开始立刻收掉`() {
        val gate = DoneLingerGate(visible = true)
        gate.frame(visible = true, ending = true)
        assertTrue(gate.frame(visible = false, ending = false))
        // 用户马上又扫开一辆车：完成态让位给进行中的行，不能盖住新订单
        assertFalse(gate.frame(visible = true, ending = false))
        assertFalse(gate.frame(visible = true, ending = false))
    }

    @Test
    fun `首帧就没有数据时不会误亮完成态`() {
        val gate = DoneLingerGate(visible = false)
        assertFalse(gate.frame(visible = false, ending = false))
        assertFalse(gate.frame(visible = false, ending = true))
        assertFalse(gate.frame(visible = false, ending = false))
    }
}
