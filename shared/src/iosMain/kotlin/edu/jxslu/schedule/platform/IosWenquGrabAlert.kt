package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.wenqu.WenquActivity
import edu.jxslu.schedule.domain.AppClock

/**
 * 问渠蹲点**命中那一刻**的 iOS 提醒：一条本机通知（2026-11-26，E3）。
 *
 * ## 为什么不是震动 / 提示音
 *
 * Android 那一档是 `ReminderVibration.vibrate` + `ReminderChime.play`（`.agents/rules/wenqu-alerts.md`
 * 「蹲点模式」：命中要震一下 + 响一声，用户可能没盯着屏幕）。iOS 没有等价的两件小事可以
 * 直接用（振动要走 `AudioServicesPlaySystemSound`，提示音要自己带音频文件并占住音频会话），
 * 而**本地通知本来就同时带声音与横幅**，还是系统统一入口（不响也不会怪 App）。
 *
 * 前提是用户给了通知权限：没给就静默（与其它四族同一降级口径，结论记在
 * [IosNotificationPermission]，页面可以拿它的 `hint` 说一句）。
 *
 * ## 为什么不做「后台蹲点」
 *
 * 蹲点这条路本来就只有前台版本：`WenquGrabViewModel` 的探测协程跟着页面走，页面没了就停
 * （`.agents/rules/wenqu-alerts.md`：不想把蹲点接后台，需要无人值守的场景走预报名）。
 * 所以这条通知**只在 App 前台时**可能触发——它的用途是「人可能在刷别的页 / 屏幕没盯着」，
 * 不是「App 关着也替你盯」。
 *
 * ## 标识符
 *
 * `wenqu.grab.<活动id>`：同一条活动重复命中只留最后一条（本条按设计只命中一次，
 * 这条口径是为了进程被杀 / 页面重进之后不堆）。前缀 `wenqu.grab.` 与通知四族
 * （`class.` / `homework.` / `ujing.` / `score.`）以及骑行两族（`freeride.` / `autoreturn.`）
 * 都不重叠——前缀是撤销的唯一依据。
 *
 * ## 接线（壳层要做的唯一一件事）
 *
 * ```kotlin
 * WenquGrabScreen(
 *     onBack = pop,
 *     viewModel = vm,
 *     onHitAlert = { activity -> iosWenquGrabHitAlert(activity) },
 * )
 * ```
 *
 * 不传就等于「命中只弹确认框」（`onHitAlert` 的 null 语义），不会崩、也不会没反应。
 *
 * ## 编译状态
 *
 * 本机是 Windows，**没有编译验证过**（Kotlin/Native 要 macOS）。用到的符号全部来自
 * [IosLocalNotifications]（CI 编过）与共享层的 `WenquActivity` / `AppClock`，
 * 本文件没有引入任何新的 iOS API。
 *
 * @param activity 命中的那条活动；null = 拿不到活动（只有一句「命中」）。
 */
fun iosWenquGrabHitAlert(activity: WenquActivity?) {
    val title = activity?.title?.trim().orEmpty()
    IosLocalNotifications.ensureAuthorized { granted ->
        // 被拒就什么都不做：通知发不出去，硬排只会被系统丢掉
        if (!granted) return@ensureAuthorized
        IosLocalNotifications.schedule(
            identifier = WENQU_HIT_ID_PREFIX + (activity?.id ?: 0L),
            title = if (title.isEmpty()) WENQU_HIT_TITLE else "$WENQU_HIT_TITLE：$title",
            body = WENQU_HIT_BODY,
            // 立刻投递：`UNTimeIntervalNotificationTrigger` 只接受 > 0，`schedule` 内部
            // 已过期的一律钳到 1 秒（系统接受的最小值）
            triggerAtMillis = AppClock.nowMillis(),
        )
    }
}

/**
 * 命中通知的标题前缀（后面接活动名）。
 *
 * 与「后台盯的活动可以报名了」那条（Android 1016，标题「可以报名了：<活动名>」）用语刻意不同：
 * 那条是后台周期任务事后发现的，这条是蹲点当场命中的，用户在哪个场景该做什么不一样。
 */
private const val WENQU_HIT_TITLE = "蹲点命中"

/**
 * 正文：**只说该做的动作**（点开 App 的确认框报名），不重复活动信息——标题里已经有了。
 *
 * 不写「已为你报名」：蹲点这条路**永远只弹确认框**、不自动提交
 * （`.agents/rules/wenqu-alerts.md`「蹲点不自动提交」），自动提交只属于用户显式开启的预报名。
 */
private const val WENQU_HIT_BODY = "名额空出或报名已开放，回 App 点「确认报名」"

/** 标识符前缀（见文件头「标识符」一节）。 */
private const val WENQU_HIT_ID_PREFIX = "wenqu.grab."
