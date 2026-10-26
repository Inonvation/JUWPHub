package edu.jxslu.schedule.data.qiekj

import android.util.Log
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.domain.QiekjAutoSignInRules
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/**
 * 胖乖「打开软件自动签到」的执行器。
 *
 * 每次主窗口回到前台调用一次；只做签到，不碰任务/领奖。三道闸：
 * 开关默认关、未登录不跑、同一天只跑一次（成功后才写日期，失败下次打开还能重试）。
 * [Mutex] 防同一进程内多次 onResume 并发触发。
 *
 * [onFreshSignIn] 只在「本次真签到成功」（服务端确认新签，非幂等已签）时回调，
 * 由调用方（MainActivity）发成功提醒；「今天已签」与失败都不回调。
 */
object QiekjAutoSignInRunner {
    private val mutex = Mutex()

    suspend fun runIfDue(
        prefs: DisplayPrefsStore,
        repo: QiekjRepository,
        today: String = LocalDate.now().toString(),
        onFreshSignIn: (QiekjDailySignInResult) -> Unit = {},
    ) {
        if (!mutex.tryLock()) return
        try {
            val enabled = prefs.qiekjAutoSignIn.first()
            val lastRunDate = prefs.qiekjAutoSignInLastDate.first()
            if (!QiekjAutoSignInRules.shouldRun(enabled, repo.loggedIn.value, lastRunDate, today)) return
            runCatching { repo.runDailySignIn(includeBalance = false) }
                .onSuccess { result ->
                    prefs.setQiekjAutoSignInLastDate(today)
                    if (result.freshSignIn) onFreshSignIn(result)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (error is TokenExpiredException) repo.markExpired()
                    Log.w("QiekjAutoSignIn", "auto sign-in failed", error)
                }
        } finally {
            mutex.unlock()
        }
    }
}
