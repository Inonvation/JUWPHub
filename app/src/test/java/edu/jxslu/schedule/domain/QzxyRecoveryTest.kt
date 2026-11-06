package edu.jxslu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 开阀失败后的自动修复阶梯：钉住「读到设备状态之后怎么走」，以及弹窗里的
 * 原因文案。执行时序（建链、清除、结算）不在这里，见 `QzxyWaterFlowTest` 与
 * `QzxyViewModel.retryOpenAfterFailure`。
 */
class QzxyRecoveryTest {

    @Test
    fun `空闲态直接重新开阀`() {
        assertEquals(
            QzxyRecovery.Action.OPEN,
            QzxyRecovery.nextAction(QzxyProtocol.STATE_IDLE, recordIsOurs = false, clearedThisRound = false, settledThisRound = false),
        )
    }

    @Test
    fun `待采集且不是自己的记录先清除`() {
        assertEquals(
            QzxyRecovery.Action.CLEAR_RECORD,
            QzxyRecovery.nextAction(
                QzxyProtocol.STATE_FINISHED_UNCOLLECTED,
                recordIsOurs = false,
                clearedThisRound = false,
                settledThisRound = false,
            ),
        )
    }

    @Test
    fun `待采集且是自己的记录先结算_别把还能救的钱清掉`() {
        assertEquals(
            QzxyRecovery.Action.SETTLE_OURS,
            QzxyRecovery.nextAction(
                QzxyProtocol.STATE_FINISHED_UNCOLLECTED,
                recordIsOurs = true,
                clearedThisRound = false,
                settledThisRound = false,
            ),
        )
    }

    @Test
    fun `自己的记录结算失败后转为清除`() {
        assertEquals(
            QzxyRecovery.Action.CLEAR_RECORD,
            QzxyRecovery.nextAction(
                QzxyProtocol.STATE_FINISHED_UNCOLLECTED,
                recordIsOurs = true,
                clearedThisRound = false,
                settledThisRound = true,
            ),
        )
    }

    @Test
    fun `清过一遍仍是待采集就停手_别反复清`() {
        assertEquals(
            QzxyRecovery.Action.STOP,
            QzxyRecovery.nextAction(
                QzxyProtocol.STATE_FINISHED_UNCOLLECTED,
                recordIsOurs = false,
                clearedThisRound = true,
                settledThisRound = false,
            ),
        )
        // 清除优先于结算：清过就不再走结算，避免清完又拿旧随机数去上报
        assertEquals(
            QzxyRecovery.Action.STOP,
            QzxyRecovery.nextAction(
                QzxyProtocol.STATE_FINISHED_UNCOLLECTED,
                recordIsOurs = true,
                clearedThisRound = true,
                settledThisRound = false,
            ),
        )
    }

    @Test
    fun `结算中与正在出水只等不清`() {
        for (state in listOf(
            QzxyProtocol.STATE_SETTLING,
            QzxyProtocol.STATE_IN_ORDER,
            QzxyProtocol.STATE_CARD_CONSUMING,
        )) {
            assertEquals(
                "状态 $state 不该被清除打断",
                QzxyRecovery.Action.WAIT,
                QzxyRecovery.nextAction(state, recordIsOurs = false, clearedThisRound = false, settledThisRound = false),
            )
        }
    }

    @Test
    fun `读不到状态交给下一轮重连`() {
        assertEquals(
            QzxyRecovery.Action.LINK_RETRY,
            QzxyRecovery.nextAction(null, recordIsOurs = false, clearedThisRound = false, settledThisRound = false),
        )
    }

    @Test
    fun `远程控制模式不自动处理`() {
        assertEquals(
            QzxyRecovery.Action.STOP,
            QzxyRecovery.nextAction(QzxyProtocol.STATE_REMOTE_CONTROL, recordIsOurs = false, clearedThisRound = false, settledThisRound = false),
        )
    }

    @Test
    fun `没有可重发订单的失败不弹自动重试`() {
        assertFalse(QzxyRecovery.retryable(QzxyRecovery.Failure.NO_PENDING))
        for (failure in listOf(
            QzxyRecovery.Failure.LINK,
            QzxyRecovery.Failure.DEVICE_BUSY,
            QzxyRecovery.Failure.ORDER,
            QzxyRecovery.Failure.WRITE,
        )) {
            assertTrue(failure.name, QzxyRecovery.retryable(failure))
        }
    }

    @Test
    fun `每条重试原因里都留着上游平台接口变更`() {
        for (failure in listOf(
            QzxyRecovery.Failure.LINK,
            QzxyRecovery.Failure.DEVICE_BUSY,
            QzxyRecovery.Failure.ORDER,
            QzxyRecovery.Failure.WRITE,
        )) {
            val causes = QzxyRecovery.causes(failure)
            assertTrue("$failure 至少 2 条原因", causes.size in 2..3)
            assertTrue(
                "$failure 缺少「上游平台接口变更」",
                causes.any { it.title.contains("上游平台接口变更") },
            )
        }
    }

    @Test
    fun `设备被占用的原因里提醒室友订单可能被终止`() {
        val warning = QzxyRecovery.causes(QzxyRecovery.Failure.DEVICE_BUSY).firstOrNull { it.warning }
        assertTrue("必须有一条警示原因", warning != null)
        assertTrue(warning!!.hint.contains("室友"))
        assertTrue(warning.hint.contains("订单"))
    }
}
