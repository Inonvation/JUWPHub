package edu.jxslu.schedule.data.session

/**
 * 「登录状态已过期」留痕的**共享缝**（iOS 移植 S15，2026-11-24）。
 *
 * ## 为什么是接口而不是把 `SessionExpiryStore` 搬过来
 *
 * `:app` 的 `SessionExpiryStore` 介质是 `SharedPreferences` + `Context`（Android 专有），
 * 搬不动；但**口径**两端一致：失效当场写一条标记、登录成功 / 主动退出清掉、进页消费一次。
 * 搬进 `:shared` 的仓库（问渠 / U净）只需要这三件事，于是收成一条缝，
 * 各端装配根各给一个实现（Android = 包一层现有 store，iOS 待接）。
 *
 * ## id 口径
 *
 * 传 **String** 而不是枚举：`SessionService` 枚举还留在 `:app`（它的文件在并行流禁改清单里），
 * commonMain 看不见它。取值与那个枚举的 `id` 逐字一致，见 [SessionServiceIds]。
 * **改一处必须两处一起改**——对不上时 `:app` 的适配器会直接抛错（`error("未知的会话服务 id")`），
 * 不会静默丢标记。
 */
interface SessionExpirySink {

    /** 记一条「已过期」（只存布尔，不含账号 / token）。 */
    fun mark(serviceId: String)

    /** 登录成功 / 用户主动退出：这不算过期，清掉标记。 */
    fun clear(serviceId: String)
}

/**
 * [SessionExpirySink] 用的服务 id，逐字对齐 `:app` 的 `SessionService.id`。
 *
 * 只列**已经被共享层用到**的那几条；其余（趣智 / 快趣）还留在 `:app`，它们的页面没搬，
 * 不需要在这里出现——**用到再加**，别提前抄一份全表。
 */
object SessionServiceIds {
    const val WENQU = "wenqu"
    const val UJING = "ujing"

    /** 胖乖生活：开水页（`ui/water`）进页消费过期标记时用（iOS 移植 T2）。 */
    const val QIEKJ = "qiekj"
}

/**
 * 「登录状态已过期，请重新登录」的进页提示文案（iOS 移植 T2）。
 *
 * 逐字对齐 `:app` 的 `SessionExpiryStore.NOTICE_TEXT`——同一句话在两个模块各有一份，
 * **改一处必须两处一起改**。共享层看不见那个类（`SessionExpiryStore` 带 `Context`），
 * 而开水页搬进共享层后要原样显示这句话，所以在这里留一份常量。
 */
const val SESSION_EXPIRED_NOTICE = "登录状态已过期，请重新登录"
