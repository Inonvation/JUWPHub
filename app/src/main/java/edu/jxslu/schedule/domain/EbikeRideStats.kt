package edu.jxslu.schedule.domain

/**
 * 服务端订单的超时费汇总（2026-11-05 用户要求）：把按月拉回来的订单压成
 * 「超时费合计 + 超时次数 + 计费单元数」。
 *
 * **口径**（来自小程序订单详情页 `pages/order/finish` 的字段映射，逐字）：
 * `orderMoney` = 骑行费用（**原价**，时间计费）、`dispatchMoney` = 调度费、
 * `payMoney` = 合计（实付/应付）。详情页行序是「骑行费用 → 骑行卡券减免时长 →
 * 折扣卡券 → 调度费 → 合计」，说明**卡券的减免落在合计上，不落在原价上**。
 *
 * 所以超时费按 [timeFeeCents] 算 = **合计 − 调度费**（= 实付的时间费），
 * **不能用原价 `orderMoney`**（2026-11-05 用户追问：「你不会把小于十五分钟、开了卡本来
 * 是 0 元的订单也算成 0.8 了吧？」——原价在卡覆盖的单上仍是 0.8，按原价合计就会把
 * 免费单算成收费单）。原价缺失或合计缺失时才退回原价。
 *
 * **次数不是金额除以 0.8**（2026-11-05 用户纠正）：一次骑行超时 30 分钟付 2.4 元，
 * 那是 **1 次超时**、**3 个 15 分钟计费单元**。所以 [Summary.overtimeOrders] 数订单、
 * [Summary.overtimeUnits] 数单元，两者互不推导。
 *
 * 计费单元 = 时间费 ÷ 0.8 元（[UNIT_CENTS]，与 [EbikeCardSavings.PER_RIDE_CENTS] 同源），
 * 四舍五入——折扣券会让单笔不是 80 的整数倍。
 *
 * **只统计已结束订单**（`endDate` 非空）：进行中的订单还在计时，金额不是最终值；
 * 金额缺失的订单不进汇总（不编 0 元）。待支付订单的费用已经产生，照常计入，
 * 另由 [Summary.unpaidCents] 单列。
 *
 * 纯 JVM，`EbikeRideStatsTest` 钉住。
 */
object EbikeRideStats {

    /** 一个计费单元的金额（分）：0.8 元 / 15 分钟。 */
    const val UNIT_CENTS: Long = EbikeCardSavings.PER_RIDE_CENTS

    /**
     * 汇总结果。[overtimeOrders] = 有骑行费的订单数（= 超时次数），
     * [overtimeUnits] = 骑行费折成的 15 分钟单元数（含未开卡时的 0.8 元起步费）。
     */
    data class Summary(
        val finishedOrders: Int = 0,
        val overtimeOrders: Int = 0,
        val overtimeCents: Long = 0L,
        val overtimeUnits: Int = 0,
        val dispatchCents: Long = 0L,
        val unpaidCents: Long = 0L,
    ) {
        val hasOvertime: Boolean get() = overtimeOrders > 0
    }

    /**
     * 一笔订单的时间费（分）= 合计 − 调度费；合计缺失时退回原价（见类注释的口径说明）。
     * 卡券覆盖的单在这里是 0，**不会**因为原价是 0.8 元被算成收费。
     */
    fun timeFeeCents(order: KqcxAuth.OrderItem): Long {
        val total = order.payMoneyCents ?: return order.orderMoneyCents ?: 0L
        val dispatch = order.dispatchMoneyCents ?: 0L
        return (total - dispatch).coerceAtLeast(0L)
    }

    fun summarize(orders: List<KqcxAuth.OrderItem>): Summary {
        var finished = 0
        var overtimeOrders = 0
        var overtimeCents = 0L
        var overtimeUnits = 0
        var dispatchCents = 0L
        var unpaidCents = 0L
        for (order in orders) {
            if (order.running) continue
            finished++
            val fee = timeFeeCents(order)
            if (fee > 0) {
                overtimeOrders++
                overtimeCents += fee
                overtimeUnits += unitsOf(fee)
            }
            dispatchCents += order.dispatchMoneyCents ?: 0L
            if (order.needPay == true) unpaidCents += order.payMoneyCents ?: 0L
        }
        return Summary(
            finishedOrders = finished,
            overtimeOrders = overtimeOrders,
            overtimeCents = overtimeCents,
            overtimeUnits = overtimeUnits,
            dispatchCents = dispatchCents,
            unpaidCents = unpaidCents,
        )
    }

    /** 骑行费折成 15 分钟单元数（四舍五入；折扣券导致的零头按最近单元算）。 */
    fun unitsOf(cents: Long): Int =
        if (cents <= 0) 0 else ((cents + UNIT_CENTS / 2) / UNIT_CENTS).toInt()

    fun moneyText(cents: Long): String = "¥%.2f".format(cents / 100.0)

    /** 资产卡里「超时费累计」那一行的说明（唯一出处）。 */
    fun caption(summary: Summary): String = when {
        summary.finishedOrders == 0 -> "还没有已结束的订单"
        !summary.hasOvertime -> "没有超时扣费"
        else -> "${summary.overtimeOrders} 次超时 · ${summary.overtimeUnits} 个 15 分钟"
    }

    /**
     * 扫描诊断行（明细弹窗用）：查了几个月、拉回多少条、去重后多少条。
     * 拉回量明显大于去重量 = 服务端把同一批订单重复返回了（2026-11-05 实测踩到过，
     * 不去重会把金额与次数放大到上千），用户据此判断这份统计可不可信。
     */
    fun scanLine(scannedMonths: Int, fetchedRows: Int, uniqueOrders: Int): String? {
        if (scannedMonths <= 0) return null
        val duplicated = fetchedRows - uniqueOrders
        return buildString {
            append("已查 $scannedMonths 个月，拉回 $fetchedRows 条记录")
            if (duplicated > 0) append("（其中 $duplicated 条是重复订单，已去重）")
            append("，去重后 $uniqueOrders 条。")
        }
    }

    /**
     * 按月的超时费分布（明细弹窗用，2026-11-05 用户要求「核实一下」）：把订单按
     * `endAt` 的年月分组，**最新的在前**，最多 [limit] 行。
     *
     * 为什么要有它：总数是一个孤零零的数字，用户没法判断对错；按月拆开之后，
     * 「2026-11 有 30 次超时」这种一眼就能和记忆对上（或对不上）。
     * 进行中与零骑行费的单不进分布（与 [summarize] 同口径）。
     */
    fun monthlyLines(orders: List<KqcxAuth.OrderItem>, limit: Int = 6): List<String> {
        val buckets = HashMap<String, LongArray>() // [次数, 金额（分）]
        for (order in orders) {
            if (order.running) continue
            val fee = timeFeeCents(order)
            if (fee <= 0) continue
            val month = monthKey(order.endAt) ?: continue
            val slot = buckets.getOrPut(month) { LongArray(2) }
            slot[0] += 1
            slot[1] += fee
        }
        return buckets.entries
            .sortedByDescending { it.key }
            .take(limit)
            .map { (month, slot) -> "$month · ${slot[0]} 次 · ${moneyText(slot[1])}" }
    }

    /**
     * 缓存覆盖的月份区间（`yyyy-MM` 原文，闭区间）；没有可识别月份时返回 null。
     * 统计改成本地算之后，界面靠它说明「这份数字覆盖到哪」（缓存可能只到最近几个月）。
     */
    fun monthRange(orders: List<KqcxAuth.OrderItem>): Pair<String, String>? {
        val months = orders.mapNotNull { monthKey(it.endAt) }
        if (months.isEmpty()) return null
        return months.min() to months.max()
    }

    /**
     * 覆盖月份与月均（明细弹窗的核对行，2026-11-05 用户实测后追加）：
     * 「总数 ¥1245」这种量级光看数字没法判断，换算成**每月多少单、多少钱**才和记忆对得上。
     * 覆盖月份数按 `endAt` 里出现过的不同月份算（有断档也照实计）。
     */
    fun coverageLine(orders: List<KqcxAuth.OrderItem>): String? {
        val months = orders.asSequence()
            .filter { !it.running }
            .mapNotNull { monthKey(it.endAt) }
            .distinct()
            .count()
        if (months <= 0) return null
        val summary = summarize(orders)
        val perMonthOrders = summary.overtimeOrders.toDouble() / months
        val perMonthCents = summary.overtimeCents.toDouble() / months
        return "覆盖 %d 个月：月均 %.1f 次超时、约 %s。".format(
            months,
            perMonthOrders,
            moneyText(perMonthCents.toLong()),
        )
    }

    /** `endAt` 的 `yyyy-MM` 前缀；格式不认识就返回 null（不编月份）。 */
    private fun monthKey(endAt: String?): String? {
        val text = endAt?.trim().orEmpty()
        if (text.length < 7) return null
        val head = text.substring(0, 7)
        val ok = head[4] == '-' && head[0].isDigit() && head[1].isDigit() &&
            head[2].isDigit() && head[3].isDigit() && head[5].isDigit() && head[6].isDigit()
        return head.takeIf { ok }
    }

    /**
     * 明细弹窗的逐行说明（唯一出处）：把口径写清楚——次数数订单、单元数 15 分钟、
     * 超时费 = 合计扣除调度费（实付时间费，卡券覆盖的单按 0 计），待支付单单独一行。
     */
    fun detailLines(summary: Summary, from: String, to: String): List<String> = buildList {
        add("统计区间 $from ~ $to，共 ${summary.finishedOrders} 条已结束订单。")
        add("超时费 ${moneyText(summary.overtimeCents)}：${summary.overtimeOrders} 次骑行超时，" +
            "折合 ${summary.overtimeUnits} 个 15 分钟计费单元（每单元 0.8 元）。")
        if (summary.dispatchCents > 0) {
            add("另有调度费 ${moneyText(summary.dispatchCents)}，不计入超时费。")
        }
        if (summary.unpaidCents > 0) {
            add("其中待支付 ${moneyText(summary.unpaidCents)}，在快趣小程序里付清即可。")
        }
        add("金额按「合计 − 调度费」算（即实付的时间费）：骑行卡覆盖的单实付 0 元，按 0 计，不按原价算。")
    }
}
