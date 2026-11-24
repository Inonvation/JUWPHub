package edu.jxslu.schedule.data.session

/**
 * 有独立登录态、会过期的第三方功能（账号中心「生活服务」那几行）。
 *
 * 2026-11-25 从 `:app` 的 `SessionExpiryStore.kt` 搬进共享层：账号中心页与
 * `SessionExpiredNotice` 都要用它，而这两者现在都在 `commonMain`。
 * 包名不变，`:app` 侧二十多处 `SessionService.Xxx` 一行都不用改。
 *
 * [id] 是落盘键：`SessionExpirySink` 那条缝传的就是它（见 [SessionServiceIds]）。
 * **改 id 等于丢用户的旧标记**，只能加不能改。
 */
enum class SessionService(val id: String) {
    Qiekj("qiekj"),
    Qzxy("qzxy"),
    Ujing("ujing"),
    Kqcx("kqcx"),
    Wenqu("wenqu"),
}

/**
 * 进页提示的统一文案（各页与失效当场的提示共用）。
 *
 * 唯一一份：`:app` 的 `SessionExpiryStore.NOTICE_TEXT` 转指这里，
 * 三个 ViewModel 的失效当场提示也读这里——两处各写一遍就会在改文案时漂移。
 */
const val SESSION_EXPIRED_NOTICE: String = "登录状态已过期，请重新登录"
