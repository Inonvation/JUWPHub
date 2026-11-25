package edu.jxslu.schedule.data.kqcx

import edu.jxslu.schedule.domain.CarDetailResult
import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.domain.KvcxZones
import kotlinx.coroutines.flow.StateFlow
import java.time.YearMonth

/**
 * 快趣出行会话仓库的 **Android 兼容壳**（iOS 移植 S12，2026-11-24）。
 *
 * ## 它为什么还留在 `:app`
 *
 * 逻辑本体已经整段搬进共享层 [KqcxSessionCore]，这里只做一件搬不动的事：
 * **`java.time.YearMonth` 适配**——订单历史的「月」与全量扫描区间，`ui/ebike` 下的
 * 那一批页面（`KvcxViewModel` / `KvcxOrdersViewModel`）直接把 `YearMonth` 传进来、
 * 把 `OrderHistoryScan.from/to` 读进 `YearMonth?` 字段。本轮不许改那些页面，而
 * `java.time` 进不了 `commonMain`，所以共享层用 `yyyy-MM` 字符串键，这一层负责换算。
 *
 * 「登录状态已过期」的留痕接线**不在这一层**（S23 起）：它在装配根 `Graph.kqcxCore`
 * ——会话状态是 core 的，壳只是它的 `YearMonth` 视图；两端（Android 壳 / iOS 直用 core）
 * 因此共用同一处接线，不会有一边漏写。
 *
 * 除了月份换算，**下面每个方法都是一行转发**：不要在这里加逻辑，否则又变成两份口径。
 * iOS 侧不经过这个类，直接用 [KqcxSessionCore]。
 */
class KqcxSessionRepository(
    private val core: KqcxSessionCore,
) : KvcxRideSession, KvcxZoneSource {

    /** 会话流：UI（hub 登录态、快趣页）统一订阅这条。 */
    override val loggedIn: StateFlow<Boolean> get() = core.loggedIn

    /** 当前登录账号（内存），供页面显示「已登录：138****xxxx」。 */
    val currentMobile: String? get() = core.currentMobile

    // ---------- 登录 / 会话 ----------

    suspend fun login(mobile: String, password: String, remember: Boolean = true) =
        core.login(mobile, password, remember)

    fun logout() = core.logout()

    override suspend fun queryUnderway(): KqcxAuth.Ride? = core.queryUnderway()

    // ---------- 用车动作（B/C 档） ----------

    override suspend fun unlockBike(
        carNum: String,
        gcjLat: Double,
        gcjLng: Double,
        locationSource: String,
        sendUnlock: Boolean,
    ): UnlockOutcome = core.unlockBike(carNum, gcjLat, gcjLng, locationSource, sendUnlock)

    override suspend fun retryUnlock(
        carNum: String,
        gcjLat: Double,
        gcjLng: Double,
        locationSource: String,
    ): UnlockOutcome = core.retryUnlock(carNum, gcjLat, gcjLng, locationSource)

    override suspend fun temporaryLock(
        gcjLat: Double,
        gcjLng: Double,
        locationSource: String,
    ): LockOutcome = core.temporaryLock(gcjLat, gcjLng, locationSource)

    override suspend fun returnBike(
        gcjLat: Double,
        gcjLng: Double,
        locationSource: String,
        locationValid: Boolean,
        wasLocked: Boolean?,
    ): KqcxAuth.EndOutcome.Ended = core.returnBike(gcjLat, gcjLng, locationSource, locationValid, wasLocked)

    override suspend fun queryZones(lat: Double, lng: Double, carNum: String?): KvcxZones? =
        core.queryZones(lat, lng, carNum)

    override suspend fun confirmUnpaidSettled(attempts: Int): KqcxAuth.UnpayState? =
        core.confirmUnpaidSettled(attempts)

    override suspend fun ringFindCar() = core.ringFindCar()

    override suspend fun queryCoupons(): List<KqcxAuth.AssetCoupon> = core.queryCoupons()

    suspend fun queryCarDetail(
        carNum: String,
        centerLat: Double,
        centerLng: Double,
    ): CarDetailResult = core.queryCarDetail(carNum, centerLat, centerLng)

    suspend fun queryAssets(): KqcxAuth.Assets = core.queryAssets()

    // ---------- 订单历史（月份在这里换算成 yyyy-MM 字符串键） ----------

    suspend fun queryOrderHistory(
        year: Int,
        month: Int,
        deviceType: Int,
        page: Int = 1,
        rows: Int = KqcxSessionCore.ORDER_PAGE_ROWS,
    ): List<KqcxAuth.OrderItem> =
        core.queryOrderHistory(year, month, deviceType, page, rows)

    /**
     * 全量订单扫描（只读）：共享层按 `yyyy-MM` 键逐月翻，这里只把 `YearMonth`
     * 换算成键、再把结果换回 `YearMonth`。其余口径见 [KqcxSessionCore.scanOrderHistory]。
     */
    suspend fun scanOrderHistory(
        until: YearMonth = YearMonth.now(),
        since: YearMonth = YearMonth.of(2017, 1),
        maxEmptyMonths: Int = KqcxSessionCore.ORDER_SCAN_EMPTY_MONTHS,
        onProgress: (scannedMonths: Int) -> Unit = {},
    ): OrderHistoryScan {
        val scan = core.scanOrderHistory(
            untilKey = until.toString(),
            sinceKey = since.toString(),
            maxEmptyMonths = maxEmptyMonths,
            onProgress = onProgress,
        )
        return OrderHistoryScan(
            orders = scan.orders,
            // 共享层回的键一定是我们刚传下去或它自己算出来的月份，解析失败只可能是代码 bug；
            // 退回请求区间而不是抛错，别让统计页因为一个格式化问题整页失败。
            from = runCatching { YearMonth.parse(scan.fromKey) }.getOrDefault(until),
            to = runCatching { YearMonth.parse(scan.toKey) }.getOrDefault(until),
            scannedMonths = scan.scannedMonths,
            fetchedRows = scan.fetchedRows,
        )
    }
}

/**
 * 全量订单扫描的结果（[KqcxSessionRepository.scanOrderHistory]）：
 * [orders] 是**去重后**的订单，[from] ~ [to] 是**实际覆盖**的月份区间。
 * 早停时 [from] 会晚于 2017-01，界面必须把区间写出来，别让用户以为这是全部历史。
 *
 * [fetchedRows] / [scannedMonths] 是诊断口径：拉回多少条、查了几个月，
 * 与 [orders] 的差就是被服务端重复返回的量（明细弹窗里写出来，便于判断数据可信度）。
 *
 * 与共享层 [KqcxOrderScan] 一一对应，只差月份类型（见 [KqcxSessionRepository] 类注释）。
 */
data class OrderHistoryScan(
    val orders: List<KqcxAuth.OrderItem>,
    val from: YearMonth,
    val to: YearMonth,
    val scannedMonths: Int = 0,
    val fetchedRows: Int = 0,
)
