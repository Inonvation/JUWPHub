package edu.jxslu.schedule

import edu.jxslu.schedule.domain.EbikeCardSavings
import edu.jxslu.schedule.domain.KqcxAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 骑行卡节省估算的口径（`.agents/rules/ebike-account.md`，2026-10-24）：
 * 以开卡额度（260 次）与服务端剩余次数之差为"已用次数"，每用一次省 0.8 元、扣开卡 18.8 元。
 */
class EbikeCardSavingsTest {

    private val now = System.currentTimeMillis()

    private fun coupon(
        remainFrequency: Long?,
        endAt: String = "2099-01-01 00:00:00",
    ): KqcxAuth.AssetCoupon = KqcxAuth.AssetCoupon(
        deviceType = 1,
        remainFrequency = remainFrequency,
        freeMinutes = 15L,
        endAt = endAt,
    )

    @Test
    fun `已用次数等于额度减剩余_净省与回本次数向上取整`() {
        // 用了 25 次：25 × 80 分 = 2000，扣开卡 1880 → 净省 120
        val saved = EbikeCardSavings.computeFromCoupons(listOf(coupon(235L)), now)!!
        assertEquals(25, saved.usedRides)
        assertEquals(260, saved.quotaTotal)
        assertEquals(2000L, saved.savedCents)
        assertEquals(120L, saved.netCents)
        assertEquals(0, saved.ridesToBreakEven)

        // 只用了 5 次：400 − 1880 = −1480 → 还差 ceil(1480/80) = 19 次
        val notYet = EbikeCardSavings.computeFromCoupons(listOf(coupon(255L)), now)!!
        assertEquals(5, notYet.usedRides)
        assertEquals(-1480L, notYet.netCents)
        assertEquals(19, notYet.ridesToBreakEven)

        // 新卡一次没用：只有开卡成本，不显示负数
        val fresh = EbikeCardSavings.computeFromCoupons(listOf(coupon(260L)), now)!!
        assertEquals(0, fresh.usedRides)
        assertEquals(-1880L, fresh.netCents)
        assertEquals(24, fresh.ridesToBreakEven) // ceil(1880/80)
    }

    @Test
    fun `多张卡额度与剩余合并`() {
        // 两张同购卡：总额度 520、总剩 300 → 已用 220
        val result = EbikeCardSavings.computeFromCoupons(
            listOf(coupon(150L), coupon(150L)),
            now,
        )!!
        assertEquals(220, result.usedRides)
        assertEquals(520, result.quotaTotal)
    }

    @Test
    fun `无限次数卡与次数缺失的卡不参与`() {
        // 无限次数卡（官方 >1e5）没有"260 次"基准
        assertNull(EbikeCardSavings.computeFromCoupons(listOf(coupon(200_000L)), now))
        assertNull(EbikeCardSavings.computeFromCoupons(listOf(coupon(null)), now))
        // 混合：只有有限次数的可用卡参与推算
        val mixed = EbikeCardSavings.computeFromCoupons(
            listOf(coupon(200_000L), coupon(235L)),
            now,
        )!!
        assertEquals(25, mixed.usedRides)
        assertEquals(260, mixed.quotaTotal)
    }

    @Test
    fun `过期卡不参与推算`() {
        val expired = EbikeCardSavings.computeFromCoupons(
            listOf(coupon(100L, endAt = "2020-01-01 00:00:00")),
            now,
        )
        assertNull(expired)
    }

    @Test
    fun `剩余多于额度的脏数据钳零`() {
        val weird = EbikeCardSavings.computeFromCoupons(listOf(coupon(500L)), now)!!
        assertEquals(0, weird.usedRides)
        assertEquals(0L, weird.savedCents)
    }

    @Test
    fun `文案区分回本前后与未使用`() {
        val saved = EbikeCardSavings.summaryText(
            EbikeCardSavings.computeFromCoupons(listOf(coupon(235L)), now)!!,
        )
        assertTrue(saved.contains("已用 25 次"))
        assertTrue(saved.contains("已净省"))
        val notYet = EbikeCardSavings.summaryText(
            EbikeCardSavings.computeFromCoupons(listOf(coupon(255L)), now)!!,
        )
        assertTrue(notYet.contains("还差 19 次回本"))
        val fresh = EbikeCardSavings.summaryText(
            EbikeCardSavings.computeFromCoupons(listOf(coupon(260L)), now)!!,
        )
        assertTrue(fresh.contains("还没有用过"))
    }
}
