package edu.jxslu.schedule.data.kqcx

import edu.jxslu.schedule.data.local.KqcxOrderDao
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.KqcxAuth
import java.time.YearMonth

/**
 * 快趣服务端订单缓存的 **Android 兼容壳**（iOS 移植 S12，2026-11-24）。
 *
 * 逻辑与业务键全在共享层 [KqcxOrderStore]；这里只把月份在 `java.time.YearMonth`
 * 与共享层的 `yyyy-MM` 字符串键之间换算——`ui/ebike` 的 `KvcxOrdersViewModel` 直接把
 * `YearMonth` 传进来、把返回的月份列表当 `List<YearMonth>` 用，而那些页面本轮不许改。
 * iOS 侧不经过这个类，直接用 [KqcxOrderStore]。
 *
 * ## 现状（iOS 移植 E2，2026-11-25）：**已经没有调用方**
 *
 * `KvcxViewModel` / `KvcxOrdersViewModel` 已直接吃 [KqcxOrderStore]（月份在页面侧就地换算），
 * `Graph.kqcxOrderCache` 也随之改成 `Graph.kqcxOrderStore`。这一层可以删，
 * **留到搬完那两页的下一轮一起删**；在那之前不要给它加新成员。
 */
class KqcxOrderCache(dao: KqcxOrderDao) {

    private val store = KqcxOrderStore(dao)

    /** 把一批订单覆盖写入缓存；返回写入的行数（业务键缺失的行会被丢弃）。 */
    suspend fun store(
        orders: List<KqcxAuth.OrderItem>,
        now: Long = AppClock.nowMillis(),
    ): Int = store.store(orders, now)

    /** 全部缓存订单（按还车时间倒序，与页面列表一致）。 */
    suspend fun load(): List<KqcxAuth.OrderItem> = store.load()

    suspend fun count(): Int = store.count()

    /** 最近一次写入时刻；null = 还没缓存过任何订单。 */
    suspend fun lastSyncedAt(): Long? = store.lastSyncedAt()

    /** 某月 × 车型的缓存订单（按还车时间倒序）。 */
    suspend fun ordersFor(month: YearMonth, deviceType: Int): List<KqcxAuth.OrderItem> =
        store.ordersFor(month.toString(), deviceType)

    /** 退出登录 / 换账号时清空：订单属于某个手机号，不能串号。 */
    suspend fun clear() = store.clear()

    /** 缓存里出现过订单的月份（倒序）。 */
    suspend fun months(deviceType: Int? = null): List<YearMonth> =
        store.monthKeys(deviceType).mapNotNull { key ->
            runCatching { YearMonth.parse(key) }.getOrNull()
        }
}
