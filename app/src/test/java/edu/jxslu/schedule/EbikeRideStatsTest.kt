package edu.jxslu.schedule

import edu.jxslu.schedule.domain.EbikeRideStats
import edu.jxslu.schedule.domain.KqcxAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 超时费汇总的口径（`.agents/rules/ebike-account.md`，2026-11-05）：
 * **次数数订单、单元数 15 分钟**——一次超时 30 分钟是 1 次超时、3 个单元，
 * 不能用「金额 ÷ 0.8」当次数（用户当场纠正过）。
 */
class EbikeRideStatsTest {

    private fun order(
        feeCents: Long? = 0L,
        dispatchCents: Long? = 0L,
        payCents: Long? = null,
        endAt: String? = "2026-11-05 18:32:11",
        needPay: Boolean? = false,
    ): KqcxAuth.OrderItem = KqcxAuth.OrderItem(
        id = 1L,
        orderNum = "N1",
        carNum = "C1",
        deviceType = 1,
        endAt = endAt,
        totalSeconds = 1_800L,
        payMoneyCents = payCents ?: ((feeCents ?: 0L) + (dispatchCents ?: 0L)),
        orderMoneyCents = feeCents,
        dispatchMoneyCents = dispatchCents,
        needPay = needPay,
    )

    @Test
    fun `超时 30 分钟算 1 次、3 个计费单元`() {
        // 骑行费 2.4 元 = 3 个 15 分钟单元，但只是 1 次骑行
        val summary = EbikeRideStats.summarize(listOf(order(feeCents = 240L)))
        assertEquals(1, summary.finishedOrders)
        assertEquals(1, summary.overtimeOrders)
        assertEquals(240L, summary.overtimeCents)
        assertEquals(3, summary.overtimeUnits)
        assertEquals("1 次超时 · 3 个 15 分钟", EbikeRideStats.caption(summary))
    }

    @Test
    fun `零元单 进行中单 缺金额单都不进超时统计`() {
        val summary = EbikeRideStats.summarize(
            listOf(
                order(feeCents = 80L), // 未开卡 10 分钟：0.8 元起步费，照算
                order(feeCents = 0L), // 卡覆盖全程：不算超时
                order(feeCents = 160L, endAt = null), // 进行中：金额不是最终值
                order(feeCents = null), // 金额缺失：不编 0 元
            ),
        )
        // 已结束的 3 条（进行中那条不计入 finishedOrders）
        assertEquals(3, summary.finishedOrders)
        assertEquals(1, summary.overtimeOrders)
        assertEquals(80L, summary.overtimeCents)
        assertEquals(1, summary.overtimeUnits)
    }

    @Test
    fun `调度费与待支付单分列 不计入超时费`() {
        val summary = EbikeRideStats.summarize(
            listOf(
                order(feeCents = 240L, dispatchCents = 500L),
                order(feeCents = 80L, payCents = 80L, needPay = true),
            ),
        )
        assertEquals(2, summary.overtimeOrders)
        assertEquals(320L, summary.overtimeCents) // 只有骑行费
        assertEquals(500L, summary.dispatchCents)
        assertEquals(80L, summary.unpaidCents)
        assertEquals(4, summary.overtimeUnits) // 3 + 1
    }

    @Test
    fun `折扣券的零头按最近单元四舍五入`() {
        assertEquals(1, EbikeRideStats.unitsOf(72L)) // 9 折后的 0.72 元 → 1 个单元
        assertEquals(1, EbikeRideStats.unitsOf(80L))
        assertEquals(2, EbikeRideStats.unitsOf(120L))
        assertEquals(0, EbikeRideStats.unitsOf(0L))
        assertEquals(0, EbikeRideStats.unitsOf(-1L))
    }

    @Test
    fun `空列表不编造金额`() {
        val summary = EbikeRideStats.summarize(emptyList())
        assertFalse(summary.hasOvertime)
        assertEquals(0L, summary.overtimeCents)
        assertEquals("还没有已结束的订单", EbikeRideStats.caption(summary))
    }

    @Test
    fun `扫描诊断行写明重复条数`() {
        // 2026-11-05 实测：服务端把同一批订单重复返回，不去重时次数被放大到 1477
        val line = EbikeRideStats.scanLine(scannedMonths = 13, fetchedRows = 1477, uniqueOrders = 42)
        assertTrue(line != null && line.contains("已查 13 个月"))
        assertTrue(line!!.contains("1435 条是重复订单"))
        assertTrue(line.contains("去重后 42 条"))
        assertNull(EbikeRideStats.scanLine(scannedMonths = 0, fetchedRows = 0, uniqueOrders = 0))
    }

    @Test
    fun `按月分布按还车时间分组 最新的在前`() {
        val orders = listOf(
            order(feeCents = 240L, endAt = "2026-11-03 10:00:00"),
            order(feeCents = 80L, endAt = "2026-11-01 10:00:00"),
            order(feeCents = 160L, endAt = "2026-10-20 10:00:00"),
            order(feeCents = 80L, endAt = null), // 进行中：不进分布
            order(feeCents = 0L, endAt = "2026-09-01 10:00:00"), // 零元：不算超时
        )
        assertEquals(
            listOf("2026-11 · 2 次 · ¥3.20", "2026-10 · 1 次 · ¥1.60"),
            EbikeRideStats.monthlyLines(orders),
        )
        // 月份识别不出就整条跳过，不编月份
        assertEquals(emptyList<String>(), EbikeRideStats.monthlyLines(listOf(order(endAt = "坏时间"))))
        // 区间覆盖全部订单（含零元单），不只是有超时费的那些
        assertEquals("2026-09" to "2026-11", EbikeRideStats.monthRange(orders))
    }

    @Test
    fun `骑行卡覆盖的零元单按实付 0 元计 不按原价 0 8 元`() {
        // 原价 0.8 元、卡覆盖后实付 0（2026-11-05 用户追问的场景）：不能算成 1 次超时
        val cardCovered = order(feeCents = 80L, payCents = 0L)
        val paid = order(feeCents = 80L, payCents = 80L)
        val summary = EbikeRideStats.summarize(listOf(cardCovered, paid))
        assertEquals(2, summary.finishedOrders)
        assertEquals(1, summary.overtimeOrders)
        assertEquals(80L, summary.overtimeCents)
        // 合计已含调度费，扣掉后不重复计
        assertEquals(80L, EbikeRideStats.timeFeeCents(order(feeCents = 80L, dispatchCents = 500L)))
        assertEquals(0L, EbikeRideStats.timeFeeCents(order(feeCents = 80L, payCents = 0L)))
    }

    @Test
    fun `覆盖月份与月均换算`() {
        val orders = listOf(
            order(feeCents = 80L, endAt = "2026-11-03 10:00:00"),
            order(feeCents = 80L, endAt = "2026-10-03 10:00:00"),
            order(feeCents = 160L, endAt = "2026-10-04 10:00:00"),
            order(feeCents = 0L, endAt = "2026-10-05 10:00:00"), // 零元单只算覆盖月份，不算超时
        )
        // 2 个月、3 次超时、¥3.20 → 月均 1.5 次、约 ¥1.60
        assertEquals("覆盖 2 个月：月均 1.5 次超时、约 ¥1.60。", EbikeRideStats.coverageLine(orders))
        assertNull(EbikeRideStats.coverageLine(emptyList()))
    }

    @Test
    fun `明细说明带区间与口径`() {
        val summary = EbikeRideStats.summarize(
            listOf(order(feeCents = 240L, dispatchCents = 500L, payCents = 740L, needPay = true)),
        )
        val lines = EbikeRideStats.detailLines(summary, "2025-01", "2026-11")
        assertTrue(lines.first().contains("2025-01 ~ 2026-11"))
        assertTrue(lines.any { it.contains("1 次骑行超时") && it.contains("3 个 15 分钟") })
        assertTrue(lines.any { it.contains("调度费 ¥5.00") })
        assertTrue(lines.any { it.contains("待支付 ¥7.40") })
    }
}
