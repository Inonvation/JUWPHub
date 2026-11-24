package edu.jxslu.schedule.data.kqcx

import edu.jxslu.schedule.data.local.KqcxOrderDao
import edu.jxslu.schedule.data.local.KqcxOrderEntity
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.KqcxAuth

/**
 * 快趣服务端订单的本地缓存（2026-11-05 用户要求：统计在本地算、缓存自动更新）。
 *
 * 写入方有两处，共用同一张表与同一套业务键（见 [KqcxOrderEntity]）：
 * - 账号页的「统计 / 重算」：全量扫描（[KqcxSessionCore.scanOrderHistory]）结果整批落库；
 * - 历史订单二级页：每翻一个月就把当月订单落库（顺手，不额外发请求）。
 *
 * 读取方只有超时费统计：进页先 [load] 出缓存算一遍（**不联网**），再按需后台刷新。
 * 这样"统计"不再是每次重扫全部月份——同一笔订单无论被拉回多少次都只占一行。
 *
 * iOS 移植 S12（2026-11-24）：从 `:app` 搬进 `:shared`。月份参数与返回值改成
 * `yyyy-MM` 字符串键（共享层没有 `java.time.YearMonth`），语义与原来完全一致：
 * 原实现拿 `YearMonth.toString()` 当 `endAt` 的前缀比，那正是同一个 `yyyy-MM`。
 * `:app` 的 `KqcxOrderCache` 是它的 `YearMonth` 适配壳。
 */
class KqcxOrderStore(private val dao: KqcxOrderDao) {

    /** 把一批订单覆盖写入缓存；返回写入的行数（业务键缺失的行会被丢弃）。 */
    suspend fun store(
        orders: List<KqcxAuth.OrderItem>,
        now: Long = AppClock.nowMillis(),
    ): Int {
        if (orders.isEmpty()) return 0
        val rows = orders.mapNotNull { order -> order.toEntity(now) }
        if (rows.isEmpty()) return 0
        dao.upsert(rows)
        return rows.size
    }

    /** 全部缓存订单（按还车时间倒序，与页面列表一致）。 */
    suspend fun load(): List<KqcxAuth.OrderItem> =
        dao.all()
            .map { it.toDomain() }
            .sortedWith(compareByDescending<KqcxAuth.OrderItem> { it.endAt.orEmpty() })

    suspend fun count(): Int = dao.count()

    /** 最近一次写入时刻；null = 还没缓存过任何订单。 */
    suspend fun lastSyncedAt(): Long? = dao.maxSyncedAt()

    /**
     * 某月 × 车型的缓存订单（按还车时间倒序）。
     * 历史订单页用它「先出缓存」，再按 [edu.jxslu.schedule.domain.KqcxRefreshPolicy] 决定
     * 要不要联网——来回翻月份不会每次都发请求。
     */
    suspend fun ordersFor(
        monthKey: String,
        deviceType: Int,
    ): List<KqcxAuth.OrderItem> {
        val prefix = monthKey.trim()
        return load().filter { order ->
            order.deviceType == deviceType && order.endAt?.trim()?.startsWith(prefix) == true
        }
    }

    /** 退出登录 / 换账号时清空：订单属于某个手机号，不能串号。 */
    suspend fun clear() = dao.clear()

    /**
     * 缓存里出现过订单的月份（`yyyy-MM`，倒序）。
     *
     * 历史订单页的月份选择器只列这些月份（2026-11-05 用户要求「只要显示有记录的年月份」）——
     * 官方 picker 从 2017-01 排到当月，一百多个格子，绝大多数是空的。
     * [deviceType] 非 null 时只看该车型：单车 tab 下不该出现「只骑过电单车」的月份。
     */
    suspend fun monthKeys(deviceType: Int? = null): List<String> =
        dao.all()
            .asSequence()
            .filter { deviceType == null || it.deviceType == deviceType }
            .mapNotNull { entity -> entity.endAt?.trim()?.take(7)?.takeIf(::looksLikeMonthKey) }
            .distinct()
            .sortedDescending()
            .toList()

    /**
     * `yyyy-MM` 形状校验（原实现用 `YearMonth.parse` 兜住脏数据）。
     * 缓存表里的 `endAt` 是服务端原文，历史脏行可能只有 `yyyy` 或带时区尾巴。
     */
    private fun looksLikeMonthKey(raw: String): Boolean =
        raw.length == 7 &&
            raw[4] == '-' &&
            raw.take(4).all { it in '0'..'9' } &&
            raw.substring(5).all { it in '0'..'9' }

    private fun KqcxAuth.OrderItem.toEntity(now: Long): KqcxOrderEntity? {
        val key = businessKey() ?: return null
        return KqcxOrderEntity(
            key = key,
            orderNum = orderNum?.trim()?.takeIf(String::isNotEmpty),
            carNum = carNum?.trim()?.takeIf(String::isNotEmpty),
            deviceType = deviceType,
            endAt = endAt?.trim()?.takeIf(String::isNotEmpty),
            totalSeconds = totalSeconds,
            payMoneyCents = payMoneyCents,
            orderMoneyCents = orderMoneyCents,
            dispatchMoneyCents = dispatchMoneyCents,
            needPay = needPay,
            syncedAt = now,
        )
    }

    private fun KqcxAuth.OrderItem.businessKey(): String? {
        id?.let { return "i:$it" }
        orderNum?.trim()?.takeIf(String::isNotEmpty)?.let { return "n:$it" }
        val car = carNum?.trim().orEmpty()
        val end = endAt?.trim().orEmpty()
        // 两样都没有的订单解析层就已经丢了；这里再兜一层，别写出空键
        if (car.isEmpty() && end.isEmpty()) return null
        return "c:$car:$end"
    }

    private fun KqcxOrderEntity.toDomain(): KqcxAuth.OrderItem = KqcxAuth.OrderItem(
        id = if (key.startsWith("i:")) key.removePrefix("i:").toLongOrNull() else null,
        orderNum = orderNum,
        carNum = carNum,
        deviceType = deviceType,
        endAt = endAt,
        totalSeconds = totalSeconds,
        payMoneyCents = payMoneyCents,
        orderMoneyCents = orderMoneyCents,
        dispatchMoneyCents = dispatchMoneyCents,
        needPay = needPay,
    )
}
