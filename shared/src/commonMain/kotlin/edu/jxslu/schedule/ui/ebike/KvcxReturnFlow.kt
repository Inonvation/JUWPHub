package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.data.kqcx.KvcxRideSession
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.KqcxAuth

/**
 * 还车 + 结算口径的**唯一实现**（手动还车 [KvcxRideController.returnBike] 与自动还车
 * （`:app` 的 `runAutoReturn`）共用，结算卡字段与本地记录不能两处各算各的）。
 *
 * 顺序纪律：还车请求成功 → 立刻记 [onOrderEnded]（收计时 / 焚码；本地走时也在这一刻
 * 截停，见 `endedAt`）→ 扣款确认（自动还车等最多 3 次，不能算进本次骑行时长；手动还车
 * 传 `pollSettlement=false`，结果卡先出）→ 组装 [KvcxReturnSummary]。
 * **别把 `onOrderEnded` 挪到轮询之后**——那会把时长多算一截。
 *
 * iOS 移植 E2（2026-11-25）：原来住在 `:app` 的 `EbikeAutoReturnRunner.kt` 里，而唯一的
 * 另一个调用方（[KvcxRideController]）已搬进共享层——把它一起搬过来，否则
 * 手动还车这条主链路留不出共享层。只依赖 [KvcxRideSession] 与 domain 类型，两端都能跑；
 * 自动还车那层（`AlarmManager` / `WorkManager`）仍留在 `:app`，照旧调这里。
 */
suspend fun performReturnBike(
    session: KvcxRideSession,
    before: KqcxAuth.Ride?,
    fetchedAt: Long,
    gcjLat: Double,
    gcjLng: Double,
    locationSource: String = "realtime",
    locationValid: Boolean = true,
    /**
     * true = 等待扣款确认再返回（自动还车要拿准确结果记账）；
     * false = 结果卡先出，调用方在后台查（手动还车的提速路径）。
     */
    pollSettlement: Boolean = true,
    onOrderEnded: suspend () -> Unit,
): KvcxReturnSummary {
    val outcome = session.returnBike(
        gcjLat = gcjLat,
        gcjLng = gcjLng,
        locationSource = locationSource,
        locationValid = locationValid,
        wasLocked = before?.locked,
    )
    // 本地走时在订单结束那一刻截停（2026-10-09 口径，原样搬自 KvcxRideController.returnBike）
    val endedAt = AppClock.nowMillis()
    onOrderEnded()
    // 官方扣款确认节奏：先享后付自动扣，短轮询一次看是否结清（不做支付本身）。
    // 欠费金额一并带回来——结果卡上显示「¥3.50」比"有未结算费用"有用得多
    val payState = if (pollSettlement && outcome.needPay == true) {
        session.confirmUnpaidSettled()
    } else {
        null
    }
    val settling = !pollSettlement && outcome.needPay == true
    return KvcxReturnSummary(
        carNum = before?.carNum,
        durationSeconds = before?.let {
            rideElapsedSeconds(it, fetchedAt, endedAt)
        },
        feeCents = before?.payMoneyCents,
        needPay = outcome.needPay,
        settled = when {
            outcome.needPay == false -> true
            payState is KqcxAuth.UnpayState.Settled -> true
            outcome.needPay == null -> null
            else -> false // Owed 或查询失败：都按"未结清"提示，让用户去核对
        },
        owedCents = (payState as? KqcxAuth.UnpayState.Owed)?.amountCents,
        settling = settling,
        // 结算期间地图还要标注这辆车（2026-10-24）：0,0 是服务端"没有坐标"的占位值，
        // 与写动作的锚点判定同口径，不能当真
        bikeLat = before?.lat?.takeIf { it != 0.0 },
        bikeLng = before?.lng?.takeIf { it != 0.0 },
    )
}
