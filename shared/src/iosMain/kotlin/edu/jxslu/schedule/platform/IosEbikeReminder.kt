package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.ui.ebike.EbikeFreeRideNotifications
import edu.jxslu.schedule.ui.ebike.EbikeReminderOutcome
import edu.jxslu.schedule.ui.ebike.EbikeReminderPort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 免费时长提醒的 iOS 实现（2026-11-26，补上 `EbikeReminderPort` 缺的那一半）。
 *
 * ## 做了什么、没做什么
 *
 * | `EbikeReminderPort` | iOS |
 * |---|---|
 * | `startFreeRide` / `checkFreeRide` / `endFreeRide` | **做**：排两条本地通知（提前量 + 免费结束），到点由系统弹 |
 * | `reconcileAutoReturn` / `cancelAutoReturn` | **不做**：`supportsAutoReturn = false`，页面把「自动还车」整组藏起来 |
 *
 * 自动还车做不了的理由不是省事：它要**到点执行代码**（查在案订单、算还车坐标、发还车请求、
 * 失败重试）。iOS 的本地通知到点只投递一条通知，没有代码被执行；`BGTaskScheduler` 又不保证
 * 触发时刻（可能晚到几十分钟，那时免费时段早已过去）。拨了没反应的开关比没有更糟，
 * 所以那一半留 Android 独占（`design/ios-port.md` §12.13 已写这条口径）。
 *
 * ## 与 Android 的语义对照
 *
 * | 项 | Android | 这里 |
 * |---|---|---|
 * | 计时起点 | `ebikeRideStartAt`（同一份 DataStore 偏好） | 同一份，照写 |
 * | 提醒通道 | `AlarmManager` 精确闹钟 + 通知渠道 | `UNTimeIntervalNotificationTrigger`（[IosLocalNotifications]） |
 * | 到点动作 | 闹钟 → `check()` → 按需发提醒 | 系统直接投递，**不执行代码** |
 * | 常驻倒计时 | 前台服务通知（Android 16 起是灵动岛） | **没有**：`hasCountdownNotification = false` |
 * | 去重 | `ebikeFreeNotifiedKeys`（带计时起点） | 不要：标识符固定（见下），重排即覆盖 |
 * | 迟到补发 | `check()` 有迟到窗口，闹钟晚了也补 | 不适用：系统到点就弹，没有「晚了」这一档 |
 *
 * ## 标识符：`freeride.lead` / `freeride.end`
 *
 * 前缀 `freeride.` 与通知四族（`class.` / `homework.` / `ujing.` / `score.`）分开——
 * 前缀是撤销的唯一依据（[IosLocalNotifications.removePending] 按前缀扫两个队列）。
 * 两个固定标识符而不是「起点 + 类型」：一次只有一轮计时在案，同标识符后一次覆盖前一次
 * （与 Android 的 `PendingIntent` 同语义，也与 `IosUjingReminder` 同一个手法）。
 *
 * 标识符与「哪几条还该排」的口径**不在本文件**：[EbikeFreeRideNotifications] 是那边的唯一出处
 * （纯函数，JVM 单测钉住）。这里只做「问授权 → 逐条交给系统 → 按前缀撤」。
 *
 * ## 线程
 *
 * 本类自己不切线程：[IosLocalNotifications] 的每个入口先切主队列，协程侧的 `first()` 读
 * DataStore 也不要求主线程。授权回调来自系统队列，`resume` 由协程库负责接力。
 *
 * ## 编译状态
 *
 * 本机是 Windows，**没有编译验证过**（Kotlin/Native 要 macOS）。用到的符号全部来自仓里
 * 已有的两处：`IosLocalNotifications`（CI 编过）与 `IosUjingReminder`（同名同形），
 * 本文件没有引入任何新的 iOS API。
 */
class IosEbikeReminder(private val prefs: DisplayPrefsStore) : EbikeReminderPort {

    /** 没有常驻倒计时：本地通知只在到点弹一次（提示语据此换文案，见 `startFreeRideNotice`）。 */
    override val hasCountdownNotification: Boolean get() = false

    /** 自动还车要「到点执行代码」，iOS 给不出（见类 KDoc 的对照表）。 */
    override val supportsAutoReturn: Boolean get() = false

    override suspend fun startFreeRide(startAtMillis: Long): EbikeReminderOutcome {
        // 换车重新计时：上一轮的「已发」记号和通知栏上那条提醒一并清掉（与 Android 同序）
        prefs.updateEbikeFreeNotifiedKeys { emptySet() }
        IosLocalNotifications.removeDelivered(FREERIDE_ID_PREFIX)
        prefs.setEbikeRideStartAt(startAtMillis)

        if (!prefs.ebikeFreeReminderEnabled.first()) {
            // 开关关着：计时照记（页面上的倒计时条要它），但一条通知都不排
            IosLocalNotifications.removePending(FREERIDE_ID_PREFIX)
            return EbikeReminderOutcome.StartedSilent
        }
        return arm(startAtMillis)
    }

    override suspend fun checkFreeRide(): EbikeReminderOutcome {
        val startAt = prefs.ebikeRideStartAt.first()
        val now = AppClock.nowMillis()
        val active = EbikeFreeRide.isActive(startAt, now)
        val endDue = EbikeFreeRide.isEndDue(startAt, now)

        // 没有在案计时，或计时与迟到窗口都已过：把排着的提醒撤干净（幂等）
        if (startAt <= 0L || (!active && !endDue)) {
            // **收之前复核一次起点**（与 Android 同一纪律）：并发的 `startFreeRide`
            // 可能刚写进新起点，拿开头读到的空状态去撤会把新一轮的提醒一起撤掉
            if (prefs.ebikeRideStartAt.first() != startAt) return EbikeReminderOutcome.Nothing
            IosLocalNotifications.removePending(FREERIDE_ID_PREFIX)
            if (startAt > 0L) {
                prefs.setEbikeRideStartAt(0L)
                prefs.updateEbikeFreeNotifiedKeys { emptySet() }
            }
            return EbikeReminderOutcome.Nothing
        }

        if (!prefs.ebikeFreeReminderEnabled.first()) {
            IosLocalNotifications.removePending(FREERIDE_ID_PREFIX)
            return EbikeReminderOutcome.Nothing
        }

        // 计时在案、提醒开着：按当前起点把还没到点的那几条覆盖一遍（幂等）。
        // 不回报 Started——[checkFreeRide] 是「进页 / 回前台」的静默核对，
        // 每进一次页弹一句「提醒已生效」是噪音（Android 那侧同样返回 Nothing）。
        arm(startAt)
        return EbikeReminderOutcome.Nothing
    }

    override suspend fun endFreeRide(): EbikeReminderOutcome {
        prefs.setEbikeRideStartAt(0L)
        prefs.updateEbikeFreeNotifiedKeys { emptySet() }
        IosLocalNotifications.removePending(FREERIDE_ID_PREFIX)
        // 已经弹过的那条要单独撤：`autoCancel` 只在用户点通知本身时生效，
        // 手动结束骑行时通知栏里那条还杵着（与 Android `revoke` 同口径）
        IosLocalNotifications.removeDelivered(FREERIDE_ID_PREFIX)
        return EbikeReminderOutcome.Ended
    }

    /** 自动还车：本平台没有这项能力（[supportsAutoReturn] = false），页面也不会调到这里。 */
    override suspend fun reconcileAutoReturn(catchUp: Boolean) = Unit

    /** 同上；清理由 [endFreeRide] 自己完成。 */
    override suspend fun cancelAutoReturn() = Unit

    /**
     * 排这一轮还没到点的提醒。
     *
     * 授权结论先问一次：被拒就什么都不排（也不反复追问，见 [IosLocalNotifications.ensureAuthorized]），
     * 调用方据此把结果变成「通知被关闭，提醒发不出来」。
     */
    private suspend fun arm(startAtMillis: Long): EbikeReminderOutcome {
        if (!awaitAuthorized()) return EbikeReminderOutcome.StartedNoNotification

        val leadMinutes = EbikeFreeRide.coerceLead(prefs.ebikeFreeLeadMinutes.first())
        // 哪些还该排（标识符 + 触发时刻 + 正文）由共享层的纯函数决定，见
        // `ui/ebike/EbikeFreeRideNotifications`：前缀与时间窗只有那一份
        EbikeFreeRideNotifications
            .plan(startAtMillis, leadMinutes, AppClock.nowMillis())
            .forEach { planned ->
                IosLocalNotifications.schedule(
                    identifier = planned.identifier,
                    title = planned.title,
                    body = planned.body,
                    triggerAtMillis = planned.triggerAtMillis,
                )
            }
        return EbikeReminderOutcome.Started
    }

    /**
     * 等一次授权结论。
     *
     * 已授权 / 已拒绝时 [IosLocalNotifications.ensureAuthorized] 是同步回调（就在调用栈里），
     * `suspendCancellableCoroutine` 的 `resume` 会照常把它接回协程；还没问过时回调来自
     * 系统队列，`resume` 在哪个线程都合法。协程已被取消就别再 resume。
     */
    private suspend fun awaitAuthorized(): Boolean = suspendCancellableCoroutine { continuation ->
        IosLocalNotifications.ensureAuthorized { granted ->
            if (continuation.isActive) continuation.resume(granted)
        }
    }

    private companion object {
        /**
         * 撤销用的标识符前缀：`freeride.`。
         *
         * 值来自共享层（[EbikeFreeRideNotifications.ID_PREFIX]）——标识符本身在那边拼，
         * 这里只按同一个前缀扫队列；两边写死两份迟早漂移（撤销就撤不干净了）。
         */
        const val FREERIDE_ID_PREFIX = EbikeFreeRideNotifications.ID_PREFIX
    }
}
