package edu.jxslu.schedule.domain

/**
 * 快趣请求的「多久算旧」判据（2026-11-05 用户要求减少请求）。
 *
 * 账号页与历史订单页的数据都先出缓存，只有缓存过期才联网——过期判据统一放这里，
 * 免得两个 ViewModel 各写一份、以后改一个忘一个。
 *
 * 纯 JVM，`KqcxRefreshPolicyTest` 钉住。
 */
object KqcxRefreshPolicy {

    /** 订单类数据（最近两个月 / 某个月的列表）的保鲜期：半小时。 */
    const val ORDERS_TTL_MS: Long = 30L * 60 * 1000

    /**
     * 缓存是否还新鲜：**没有缓存（[lastSyncMillis] 为 null）一律算过期**——
     * 宁可按需拉一次，也不要拿空缓存当"没有订单"。
     */
    fun isFresh(lastSyncMillis: Long?, nowMillis: Long, ttlMillis: Long = ORDERS_TTL_MS): Boolean {
        if (lastSyncMillis == null) return false
        val age = nowMillis - lastSyncMillis
        // 时钟被调早（age 为负）时按新鲜处理，避免用户改系统时间就把请求打满
        if (age < 0) return true
        return age <= ttlMillis
    }
}
