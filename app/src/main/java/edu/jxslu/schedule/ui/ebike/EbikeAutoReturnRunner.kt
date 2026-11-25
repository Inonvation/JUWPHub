package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.data.kqcx.KvcxRideSession
import edu.jxslu.schedule.domain.EbikeAutoReturn
import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.domain.KvcBusinessError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 自动还车的尝试循环与还车结算口径（DESIGN §3.9，2026-10-10）。
 *
 * **为什么单独一层**：`CoroutineWorker` 带着 Android 依赖，JVM 单测跑不了；而「试几次、
 * 隔几秒、哪些失败不重试、每次结果都上报」恰恰是这条链路的责任边界。这里只依赖
 * [KvcxRideSession] 接口与回调，于是可以在单测里用假会话把整条时序钉住
 * （口径同 `KvcxRideController` 的依赖收窄）。
 *
 * **与「写操作零自动重试」的关系**：仓库层的零重试红线（用户手动动作只发一次）不变；
 * 这里的重试是**用户显式开启的自动还车**自己的行为——次数由设置封顶
 * （[EbikeAutoReturn.RETRY_MIN]~[EbikeAutoReturn.RETRY_MAX]），且只在免费时段内跑。
 * 别把它当成"顺手给手动还车也加上重试"的依据。
 */

/** 一次自动还车过程的最终结果。 */
internal sealed interface AutoReturnResult {

    /** 还车成功（订单结束）。[summary] 与手动还车共用同一份结算口径。 */
    data class Returned(val summary: KvcxReturnSummary) : AutoReturnResult

    /** 还车前现查到订单已不在（其他端已结束 / 已还）：按成功口径收干净，不打扰。 */
    data object AlreadyEnded : AutoReturnResult

    /** 未登录（凭证失效）：重试无意义，直接停。 */
    data object NotLoggedIn : AutoReturnResult

    /** 重试用尽或不该重试的失败。[attempts] = 实际尝试次数。 */
    data class Failed(val lastError: Throwable, val attempts: Int) : AutoReturnResult
}

/**
 * 自动还车主循环：最多 [retries] + 1 次尝试，每次失败后隔 [retryDelayMs] 重试。
 *
 * 每一拍都**现查一次在案订单**：一是确认订单还在（其他端还了就不再动作），二是拿车辆
 * 最新上报的坐标。还车坐标由 [EbikeAutoReturn.chooseReturnPoint] 选：先用骑行页留下、
 * 仍在五分钟内的用户定位（与手动还车同源）；没有就退回车辆坐标。遇到 50011
 * （不在还车区域）时换另一个来源再试——车辆位置上报滞后与用户定位漂移都可能是成因。
 *
 * [onOrderEnded] 在还车请求成功、**扣款确认之前**调用（本地走时要在订单结束那一刻截停，
 * 口径见 [performReturnBike]）；[onAttemptFailed] 每次失败都调（每次尝试结果都发通知，
 * 2026-10-10 用户口径）。
 *
 * **取消穿透**：工作被取消时原样抛出，绝不把取消报成"还车失败"并继续重试。
 */
internal suspend fun runAutoReturn(
    session: KvcxRideSession,
    retries: Int,
    onOrderEnded: suspend () -> Unit,
    onAttemptFailed: suspend (
        error: Throwable,
        attempt: Int,
        total: Int,
        state: EbikeAutoReturn.RetryState,
    ) -> Unit,
    retryDelayMs: Long = EbikeAutoReturn.RETRY_INTERVAL_MS,
): AutoReturnResult {
    if (!session.loggedIn.value) return AutoReturnResult.NotLoggedIn
    val total = EbikeAutoReturn.totalAttempts(retries)
    var attempt = 0
    var lastError: Throwable? = null
    // 50011（不在还车区域）时换坐标来源：用户定位 ↔ 车辆坐标。其他错误保持当前来源。
    var preferUser = true
    while (attempt < total) {
        attempt++
        val ride = try {
            session.queryUnderway()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            lastError = error
            val state = retryState(error, attempt, total)
            onAttemptFailed(error, attempt, total, state)
            if (state != EbikeAutoReturn.RetryState.RETRYING) {
                return AutoReturnResult.Failed(error, attempt)
            }
            delay(retryDelayMs)
            continue
        }
        if (ride == null) return AutoReturnResult.AlreadyEnded
        try {
            val lat = ride.lat
            val lng = ride.lng
            val userFix = EbikeUserLocation.fresh()
            val point = EbikeAutoReturn.chooseReturnPoint(
                vehicleLat = lat,
                vehicleLng = lng,
                userLat = userFix?.lat,
                userLng = userFix?.lng,
                userFixAtMillis = userFix?.atMillis,
                nowMillis = System.currentTimeMillis(),
                vehicleLocked = ride.locked,
                preferUser = preferUser,
            )
            val summary = performReturnBike(
                session = session,
                before = ride,
                fetchedAt = System.currentTimeMillis(),
                gcjLat = point.lat,
                gcjLng = point.lng,
                locationSource = point.source,
                locationValid = point.valid,
                onOrderEnded = onOrderEnded,
            )
            return AutoReturnResult.Returned(summary)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            lastError = error
            if (error is KvcBusinessError && error.errorCode == 50011) {
                preferUser = !preferUser
            }
            val state = retryState(error, attempt, total)
            onAttemptFailed(error, attempt, total, state)
            if (state != EbikeAutoReturn.RetryState.RETRYING) {
                return AutoReturnResult.Failed(error, attempt)
            }
            delay(retryDelayMs)
        }
    }
    return AutoReturnResult.Failed(
        lastError ?: IllegalStateException("自动还车未执行"),
        attempt,
    )
}

/** 这一拍失败之后往哪走：不可重试 / 还有额度接着试 / 额度已尽。 */
private fun retryState(
    error: Throwable,
    attempt: Int,
    total: Int,
): EbikeAutoReturn.RetryState = when {
    !EbikeAutoReturn.isRetryable(error) -> EbikeAutoReturn.RetryState.ABORTED
    attempt >= total -> EbikeAutoReturn.RetryState.EXHAUSTED
    else -> EbikeAutoReturn.RetryState.RETRYING
}
