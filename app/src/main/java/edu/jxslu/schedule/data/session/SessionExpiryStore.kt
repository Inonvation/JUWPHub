package edu.jxslu.schedule.data.session

import android.content.Context

/**
 * 「登录状态已过期」留痕（2026-10-21 用户要求）。
 *
 * 会话失效大多发生在离开页面之后（token / JWT 到期、被平台顶掉、本机凭证被清），
 * 下次进页只剩一张登录表单，用户不知道刚才发生了什么。各功能在**判定失效的那一刻**
 * [mark]，进页时 [consume] 一次并提示；登录成功与用户主动退出走 [clear]。
 *
 * 只存布尔标记，不含账号、密码或 token；与凭证文件互不相干。
 */
class SessionExpiryStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun mark(service: SessionService) {
        synchronized(this) {
            prefs.edit().putBoolean(service.id, true).apply()
        }
    }

    /** 标记还在（账号中心据此显示「已失效」；页面消费后翻回未登录）。 */
    fun isExpired(service: SessionService): Boolean = synchronized(this) {
        prefs.getBoolean(service.id, false)
    }

    /** 取走标记：true = 这次进页该提示一次（同一轮只提示一次）。 */
    fun consume(service: SessionService): Boolean = synchronized(this) {
        if (!prefs.getBoolean(service.id, false)) return false
        prefs.edit().putBoolean(service.id, false).apply()
        true
    }

    /** 登录成功 / 用户主动退出：这不算「过期」，不需要提示。 */
    fun clear(service: SessionService) {
        synchronized(this) {
            prefs.edit().putBoolean(service.id, false).apply()
        }
    }

    companion object {
        /**
         * 进页提示的统一文案（各页与失效当场的提示共用）。
         *
         * 字面量在共享层的 [SESSION_EXPIRED_NOTICE]：`SessionExpiredNotice` 与账号中心页
         * 已搬进 `commonMain`，它们读不到 `:app` 的常量。这里保留同名别名，是为了让
         * `:app` 三个 ViewModel（水控 / U净 / 趣智）的调用点一行都不用改。
         */
        const val NOTICE_TEXT = SESSION_EXPIRED_NOTICE

        private const val FILE = "session_expiry"
    }
}
