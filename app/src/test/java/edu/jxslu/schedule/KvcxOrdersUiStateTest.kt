package edu.jxslu.schedule

import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.ui.ebike.KvcxOrdersUiState
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 历史订单页的筛选与月份导航（DESIGN §3.9，2026-11-05 用户要求）：
 * 「仅显示有费用」按实付过滤；前后翻页只在**有记录**的月份里走，不跳空格子。
 */
class KvcxOrdersUiStateTest {

    @Test
    fun `仅显示有费用按实付过滤`() {
        val orders = listOf(
            order(no = "A", pay = 80),
            order(no = "B", pay = 0),
            order(no = "C", pay = null),
        )
        val all = KvcxOrdersUiState(orders = orders)
        assertEquals(3, all.visibleOrders.size)

        val paidOnly = all.copy(onlyPaid = true)
        assertEquals(listOf("A"), paidOnly.visibleOrders.map { it.orderNum })
    }

    @Test
    fun `全是没有费用的单时筛选后为空`() {
        val state = KvcxOrdersUiState(
            orders = listOf(order(no = "A", pay = 0)),
            onlyPaid = true,
        )
        assertTrue(state.visibleOrders.isEmpty())
    }

    @Test
    fun `前后翻页只走有记录的月份`() {
        // 记录月份：2026-11、2026-08、2025-12（9、10 月整月空白）
        val state = KvcxOrdersUiState(
            yearMonth = YearMonth.of(2026, 11),
            months = listOf(
                YearMonth.of(2026, 11),
                YearMonth.of(2026, 8),
                YearMonth.of(2025, 12),
            ),
        )
        assertEquals(YearMonth.of(2026, 8), state.olderMonth)
        assertNull(state.newerMonth) // 已经是最新记录

        val older = state.copy(yearMonth = YearMonth.of(2026, 8))
        assertEquals(YearMonth.of(2025, 12), older.olderMonth)
        assertEquals(YearMonth.of(2026, 11), older.newerMonth)

        // 最早一条：再往前没有了
        val oldest = state.copy(yearMonth = YearMonth.of(2025, 12))
        assertNull(oldest.olderMonth)
    }

    private fun order(no: String, pay: Long?, end: String? = "2026-11-01 10:00:00") = KqcxAuth.OrderItem(
        id = null,
        orderNum = no,
        carNum = "123456",
        deviceType = 1,
        endAt = end,
        totalSeconds = 600,
        payMoneyCents = pay,
        orderMoneyCents = 80,
        dispatchMoneyCents = 0,
        needPay = false,
    )
}
