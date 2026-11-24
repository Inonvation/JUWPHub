package edu.jxslu.schedule.data.session

/**
 * 「登录状态已过期」留痕的**共享缝**（iOS 移植 S15，2026-11-24）。
 *
 * ## 为什么是接口而不是把 `SessionExpiryStore` 搬过来
 *
 * `:app` 的 `SessionExpiryStore` 介质是 `SharedPreferences` + `Context`（Android 专有），
 * 搬不动；但**口径**两端一致：失效当场写一条标记、登录成功 / 主动退出清掉、进页消费一次。
 * 搬进 `:shared` 的仓库（问渠 / U净）与账号中心页只需要这四件事，于是收成一条缝，
 * 各端装配根各给一个实现（Android = 包一层现有 store，iOS 待接）。
 *
 * ## id 口径
 *
 * 传 **String** 而不是枚举：这条缝先于 `SessionService` 上移就定了形状，
 * 现在三个共享层仓库（问渠 / U净 / 快骑的回调）都已按字符串接好；改成枚举只会让
 * 三处调用点各动一遍而不改任何行为。取值与 [SessionService.id] 逐字一致，
 * 见 [SessionServiceIds]。**改一处必须两处一起改**——对不上时 `:app` 的适配器会直接抛错
 * （`error("未知的会话服务 id")`），不会静默丢标记。
 */
interface SessionExpirySink {

    /** 记一条「已过期」（只存布尔，不含账号 / token）。 */
    fun mark(serviceId: String)

    /** 登录成功 / 用户主动退出：这不算过期，清掉标记。 */
    fun clear(serviceId: String)

    /** 标记还在（账号中心据此显示「已失效」；页面消费后翻回未登录）。 */
    fun isExpired(serviceId: String): Boolean

    /**
     * 取走标记：true = 这次进页该提示一次。
     *
     * 与 [isExpired] 分开是为了「同一轮只提示一次」：进页读一次决定要不要弹，
     * 弹完必须清掉，否则用户每次回到这一页都会再被提示一遍（2026-10-21 用户要求）。
     */
    fun consume(serviceId: String): Boolean
}

/**
 * [SessionExpirySink] 用的服务 id，逐字对齐 [SessionService.id]。
 *
 * 只列**共享层仓库真正会写的**那几条；账号中心页直接用枚举的 `id`，
 * 不需要经过这里——枚举已经上移到本模块，页面不必再走字符串。
 */
object SessionServiceIds {
    const val WENQU = "wenqu"
    const val UJING = "ujing"
}
