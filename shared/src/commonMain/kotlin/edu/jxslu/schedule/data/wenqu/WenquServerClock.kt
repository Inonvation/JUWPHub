package edu.jxslu.schedule.data.wenqu

import edu.jxslu.schedule.domain.WenquRules
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * 问渠服务器的时钟偏移（DESIGN §4.45，2026-10-16）：预报名算「提前量」的校对。
 *
 * 平台写接口会**提前约 10 秒放行**（用户实测：活动写着 12:00 开始报名，11:59:50
 * 就能抢），所以「开始前 15 秒起试发」落在服务器几点几分，得用服务器自己的钟来算。
 * 不依赖 NTP：HTTP 响应自带 `Date` 头，每次请求顺手记一笔偏移（见 [recordFrom]）。
 *
 * 只保留**最近一次**测量；偏移大得离谱（差 10 分钟以上）视为异常样本丢弃——
 * 那种情况下错的是手机时间，不是这一发请求能修的。
 *
 * iOS 移植 P1（2026-11-24）：本对象原本和一个 OkHttp `Interceptor` 在一起。Ktor 没有
 * `Interceptor` 这个概念，所以拦截器删掉，改成调用方（`WenquApi.send`）在拿到响应后
 * 调 [recordFrom]——记账口径完全一样（发前取钟、收后取钟、读 `Date` 头），
 * 且省掉一层包装。
 */
object WenquServerClock {

    /** 合理偏移上限；超过视为样本异常（手机钟本身偏了，先修手机时间）。 */
    const val MAX_ABS_OFFSET_MS: Long = 10 * 60 * 1000L

    /** 服务器 − 手机（毫秒）。没测到过时是 0（按手机钟跑，与旧行为一致）。 */
    @Volatile
    var offsetMs: Long = 0L
        private set

    /** 最近一次测量时刻（手机钟）；0 = 没测到过。 */
    @Volatile
    var measuredAt: Long = 0L
        private set

    fun record(offset: Long, at: Long) {
        if (abs(offset) > MAX_ABS_OFFSET_MS) return
        offsetMs = offset
        measuredAt = at
    }

    /**
     * 从一次请求的响应头记一笔偏移。
     *
     * @param httpDate 响应 `Date` 头的原值（没有就给 null，直接跳过）
     * @param sentAt 请求发出前的手机钟
     * @param receivedAt 响应拿到后的手机钟
     */
    fun recordFrom(httpDate: String?, sentAt: Long, receivedAt: Long) {
        val serverAt = WenquRules.parseHttpDate(httpDate) ?: return
        record(WenquRules.clockOffsetMs(serverAt, sentAt, receivedAt), receivedAt)
    }
}
