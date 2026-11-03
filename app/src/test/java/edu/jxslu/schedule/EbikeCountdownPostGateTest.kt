package edu.jxslu.schedule

import edu.jxslu.schedule.ui.ebike.EbikeCountdownPostGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 常驻倒计时「发通知 ↔ 摘通知」的先后闸（2026-11-03）。
 *
 * 钉住的不变量只有一条：**`seal` 返回之后，不允许再有任何一次发送**——它保证
 * `stopForeground`（摘通知）一定排在最后一次 `notify` 之后，否则会留下一条没人刷新的
 * ongoing 通知（芯片文案冻住 + 划不掉，即用户报的「还车后灵动岛不消失、倒计时不动」）。
 */
class EbikeCountdownPostGateTest {

    @Test
    fun `闸开着时照常发送`() {
        val gate = EbikeCountdownPostGate()
        gate.open()
        var calls = 0
        assertTrue(gate.post { calls++ })
        assertEquals(1, calls)
    }

    @Test
    fun `关闸之后一律不再发送`() {
        val gate = EbikeCountdownPostGate()
        gate.open()
        var calls = 0
        gate.seal()
        // 关闸 = 收尾已开始：再发就会在摘除之后落地
        assertFalse(gate.post { calls++ })
        assertEquals(0, calls)
        // 幂等
        gate.seal()
        assertFalse(gate.post { calls++ })
        assertEquals(0, calls)
    }

    @Test
    fun `重新开闸后恢复发送`() {
        // 进前台挂上通知之后会重新开闸（同一个服务实例换车重建 tick）
        val gate = EbikeCountdownPostGate()
        gate.seal()
        var calls = 0
        assertFalse(gate.post { calls++ })
        gate.open()
        assertTrue(gate.post { calls++ })
        assertEquals(1, calls)
    }

    @Test
    fun `关闸返回时在飞的那一发已落地且之后不再有发送`() {
        val gate = EbikeCountdownPostGate()
        gate.open()
        val sent = AtomicInteger(0)
        val inFlight = AtomicBoolean(false)
        val start = CountDownLatch(1)

        val posters = (1..4).map {
            Thread {
                start.await()
                repeat(200) {
                    gate.post {
                        inFlight.set(true)
                        sent.incrementAndGet()
                        Thread.yield() // 把「正在发」的窗口撑开，方便关闸撞进去
                        inFlight.set(false)
                    }
                }
            }
        }
        posters.forEach { it.start() }
        start.countDown()

        // 等它们真跑起来再关闸（只要求关闸那一刻有在飞的发送，次数不参与断言）
        val deadline = System.currentTimeMillis() + 5_000L
        while (sent.get() < 20 && System.currentTimeMillis() < deadline) Thread.yield()

        gate.seal()
        val sentAtSeal = sent.get()
        // seal 返回 = 已拿到锁 = 没有任何一次发送还在执行
        assertFalse("关闸返回时仍有发送在飞", inFlight.get())

        posters.forEach { it.join() }
        // 关闸之后一次都不许再发（否则那一发会排在摘除之后落地，留下残通知）
        assertEquals("关闸之后又发了通知", sentAtSeal, sent.get())
    }
}
