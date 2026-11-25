package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.ScoreSync
import edu.jxslu.schedule.domain.AppClock
import kotlinx.coroutines.flow.first

/**
 * 成绩提醒的 iOS 形态（N2，2026-11-26）：**进 App 时查一次，有新成绩就立刻弹一条本地通知**。
 *
 * ## 为什么不是 Android 那套
 *
 * Android 的 `ui/reminder/ScoreAlertReminder` 是「`WorkManager` 周期核对 + 有变更才发通知」，
 * 周期任务还会顺带跑选课 / 调课 / 问渠 / 新学期探测四条链。iOS 没有等价物：`BGTaskScheduler`
 * 由系统决定何时跑、不保证周期（§12.1「后台定时任务」一行），所以**周期检查这一轮不做**。
 *
 * 能做的只有一件事：**用户打开 App 时**（`IosServices.warmUp`）跑一次 [ScoreSync]，
 * 闸门原样复用（开关关着不跑；开着时 `AutoSyncRules` 的 6 小时/半小时-补查闸门决定真抓还是跳过），
 * 抓到变更就立即投一条通知。这条路径**不打扰**——一天开八次 App 也只有闸门放行的那次真抓。
 *
 * ## 三条口径与 Android 一致
 *
 * 1. **首导不是变动**：[ScoreSync.Result.Updated.firstImport] 为真时不发（第一次拿到的全量是基线）。
 * 2. **没变更不发**：`changes` 为空是常态，静默。
 * 3. **提醒开关只管发通知**：关着时连核对都不发起（[DisplayPrefsStore.scoreAlertEnabled]）——
 *    Android 那边关提醒还会走自动导入的 7 天闸门，这条在 iOS 上没有消费者，不搬。
 *
 * ## 隐私：锁屏上只有「门数」
 *
 * 标题写「有 N 门新成绩」，正文不写课程名、不写分数——成绩会显示在锁屏上，比作业更敏感。
 * （Android 那份写「新成绩：课程名」，两端口径不同是刻意的。）
 *
 * ## 没做的
 *
 * - 后台静默抓取后推送：`BGAppRefreshTask` 不保证时刻，做出来的提醒会「有时有有时没有」，
 *   比没有更难解释。
 * - 免扰时段（Android `QuietHoursGate`）：iOS 侧没有拦下-补发的队列，只有 `UNNotificationInterruptionLevel`
 *   分级；本轮不接。
 * - 点击通知直达成绩页：`userInfo` 深链未接（三类通知点开都是回到 App）。
 */
internal class IosScoreReminder(
    private val prefs: DisplayPrefsStore,
    private val sync: ScoreSync,
) {

    /** 冷启动跑一次：闸门在 [ScoreSync.sync] 内部，这里只管「开关 + 有变更才发」。 */
    suspend fun checkOnLaunch() {
        if (!prefs.scoreAlertEnabled.first()) return
        // eager = 冷启动补查：与 Android `JuwApplication` 传 true 同义（闸门阈值减半）
        val result = sync.sync(eager = true)
        if (result !is ScoreSync.Result.Updated) return
        if (result.firstImport || result.changes.isEmpty()) return
        IosLocalNotifications.ensureAuthorized { granted ->
            if (!granted) return@ensureAuthorized
            IosLocalNotifications.schedule(
                identifier = SCORE_NOTIFICATION_ID,
                title = "有 ${result.changes.size} 门新成绩",
                body = BODY,
                // 「一次性定时」在这里就是「马上」：系统要求 timeInterval > 0，1 秒是它接受的最小值
                triggerAtMillis = AppClock.nowMillis() + 1_000L,
            )
        }
    }

    private companion object {
        /**
         * 固定标识符：同一次投递语义是「替换上一条」。不按门数/时间拼标识符——那会让通知栏
         * 里堆一串历史成绩提醒，而这条的作用只是「去看一眼」。
         */
        const val SCORE_NOTIFICATION_ID = "score.latest"

        /** 正文不写课程名与分数（锁屏可见），也不写「点我」这类指令。 */
        const val BODY = "打开 App 查看成绩"
    }
}
