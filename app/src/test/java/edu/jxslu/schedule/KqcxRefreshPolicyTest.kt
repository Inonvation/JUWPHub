package edu.jxslu.schedule

import edu.jxslu.schedule.domain.KqcxRefreshPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快趣请求节流判据（`.agents/rules/ebike-account.md`，2026-11-05 减少请求）：
 * 缓存新鲜就不联网；没有缓存一律算过期（宁可按需拉一次，也不拿空缓存当"没有订单"）。
 */
class KqcxRefreshPolicyTest {

    @Test
    fun `没有缓存一律算过期`() {
        assertFalse(KqcxRefreshPolicy.isFresh(null, nowMillis = 1_000_000L))
    }

    @Test
    fun `保鲜期内算新鲜 超过保鲜期算旧`() {
        val now = 10_000_000L
        assertTrue(KqcxRefreshPolicy.isFresh(now - 60_000L, now))
        assertTrue(KqcxRefreshPolicy.isFresh(now - KqcxRefreshPolicy.ORDERS_TTL_MS, now))
        assertFalse(KqcxRefreshPolicy.isFresh(now - KqcxRefreshPolicy.ORDERS_TTL_MS - 1, now))
    }

    @Test
    fun `时钟被调早按新鲜处理 不把请求打满`() {
        val now = 10_000_000L
        assertTrue(KqcxRefreshPolicy.isFresh(now + 5_000L, now))
    }
}
