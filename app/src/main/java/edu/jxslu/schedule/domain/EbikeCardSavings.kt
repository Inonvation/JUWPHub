package edu.jxslu.schedule.domain

/**
 * 骑行卡节省估算（2026-10-24 用户要求，同日按用户口径改为**服务端次数推算**）：
 * 开卡一次给 [CARD_QUOTA] 次前 15 分钟免费额度，资产接口的剩余次数与额度之差就是
 * **已用次数**（服务端计数：开卡前的本机记录、微信里骑的、短时还车不扣次数的单
 * 都天然算对——卡没扣次数就不算用掉）。每用一次按省 [PER_RIDE_CENTS] 计
 * （无卡时 15 分钟内订单收 0.8 元起步费），扣掉开卡价 [CARD_PRICE_CENTS] 即净省。
 *
 * 数据 = 资产快照里的卡券（`KqcxAuth.AssetCoupon`），**本机骑行记录不参与**
 * （它只记 App 内开的车，开卡前的订单还会混入——2026-10-24 用户确认改口径后放弃）。
 *
 * 可用性判据复用 [EbikeRideCard.isUsable]（未过期且次数非 0）；**无限次数卡不参与**
 * （没有"260 次"基准，`unlimited` 即官方 >1e5 口径）；剩余次数缺失的卡同样排除
 * （缺数据不下断言）。没有一张可参与推算的卡时返回 null，调用方不显示区块。
 *
 * 纯 JVM，`EbikeCardSavingsTest` 钉住。这是**估算不是账单**（以快趣为准），
 * 展示区副标题必须把这一点写出来。
 */
object EbikeCardSavings {

    /** 每单节省（分）：无卡时 15 分钟内订单收 0.8 元起步费。 */
    const val PER_RIDE_CENTS = 80L

    /** 开卡价（分）。 */
    const val CARD_PRICE_CENTS = 1880L

    /** 每张骑行卡的开卡免费额度（次，用户口径：260 次 × 前 15 分钟免费）。 */
    const val CARD_QUOTA = 260

    /**
     * 节省统计结果：[usedRides] = 已用次数（额度 − 剩余）；[quotaTotal] = 参与推算的
     * 卡张数 × 每张额度；[ridesToBreakEven] = 距回本还差的次数（0 = 已回本）。
     */
    data class Result(
        val usedRides: Int,
        val quotaTotal: Int,
        val savedCents: Long,
        val netCents: Long,
        val ridesToBreakEven: Int,
    )

    /**
     * 从资产快照的卡券列表推算节省；返回 null = 没有可参与推算的卡
     * （没有可用卡 / 只有无限次数卡 / 次数缺失），调用方据此不显示区块。
     */
    fun computeFromCoupons(
        coupons: List<KqcxAuth.AssetCoupon>,
        nowMillis: Long,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Result? {
        val usable = coupons.filter { coupon ->
            !coupon.unlimited &&
                coupon.remainFrequency != null &&
                EbikeRideCard.isUsable(coupon, nowMillis, zone)
        }
        if (usable.isEmpty()) return null
        val quotaTotal = CARD_QUOTA * usable.size
        val remaining = usable.sumOf { it.remainFrequency ?: 0L }
        // 剩余多于额度是脏数据（官方口径不该出现）：钳 0，不显示负的"已用"
        val usedRides = (quotaTotal.toLong() - remaining).coerceAtLeast(0L)
        val savedCents = usedRides * PER_RIDE_CENTS
        val netCents = savedCents - CARD_PRICE_CENTS
        val toBreakEven = if (netCents < 0) {
            (((-netCents) + PER_RIDE_CENTS - 1) / PER_RIDE_CENTS).toInt()
        } else {
            0
        }
        return Result(usedRides.toInt(), quotaTotal, savedCents, netCents, toBreakEven)
    }
}
