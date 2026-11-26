package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.RideRecordStore
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.domain.EbikeUseMode
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.ebike.KvcxReturnSummary
import edu.jxslu.schedule.ui.ebike.KvcxSideEffects
import edu.jxslu.schedule.ui.ebike.startFreeRideNotice
import kotlinx.coroutines.flow.first

/**
 * [KvcxSideEffects] 的 **iOS 实现**（2026-11-26，E3）。
 *
 * ## 它解决的是什么
 *
 * iOS 的 `KvcxRideController` 此前一直传 `effects = null`（`IosServices.kvcxRideImpl`）：开锁 / 锁车 /
 * 还车照常，但**账号方式开锁时没人写 `ebikeRideStartAt`**——于是免费时长倒计时那一条起不来，
 * 自动还车也永远判 `startAt <= 0` 直接撤排程。换句话说：提醒的那一套在 iOS 上「排得出来、
 * 但永远不会被触发」，因为计时起点根本没写。
 *
 * 这一份实现把那一半补齐，**口径与 Android 的 `AppKvcxSideEffects` 逐条对齐**（字段与顺序全照它），
 * 差的只有平台手段：
 *
 * | 动作 | Android | 这里 |
 * |---|---|---|
 * | `startRideTimer` | 通知栏倒计时 + 两个精确闹钟 | 两条本地通知（[IosEbikeReminder.startFreeRide]），**没有常驻倒计时** |
 * | `ensureReminderEnabled` | 读 / 写同一个 DataStore 键 | 同左（键与默认值都是共享层那份） |
 * | `scheduleAutoReturn` | `AlarmManager` + `WorkManager` | 到点一条通知 + App 被唤起时补做（[IosEbikeReminder.reconcileAutoReturn]） |
 * | `endRideAndBurn` | 停前台服务、焚相册里的码、撤闹钟 | 收计时 + 撤通知；**焚码跳过**（`EbikeQrGallery.canDelete` = false，本来也不记待焚 key） |
 * | `onOrderGoneExternally` | 同一份判据 `EbikeFreeRide.shouldEndTimerWhenOrderGone` | 同左 |
 * | `recordRide` | `Graph.rideRecordStore` | 同一张 Room 表（`Services.current.database()`） |
 *
 * ## 谁用它（**壳层还差这一行**）
 *
 * `IosServices.kvcxRideImpl` 的 `effects = null` 改成 `effects = IosKvcxSideEffects` 就全通了。
 * 本文件不改壳文件（那几条由另一条流独占），所以现在**这份实现没有任何调用方**——
 * 也就是说：iOS 的自动还车 / 免费提醒要等那一行接上才会真的开始计时。
 *
 * ## 为什么是 `object` 而不是每页一份
 *
 * `IosEbikeReminder` 里有一个进程级的前台计时器槽位（自动还车到点那一刻用它补做）。
 * 每页各建一份就有好几个槽位、同一轮骑行可能被补还两次；`effect` 在 Android 侧也是进程级单例
 * （`AppKvcxSideEffects`），两端同一个形态。
 *
 * ## 编译状态
 *
 * 本机是 Windows，**没有编译验证过**（Kotlin/Native 要 macOS）。用到的类型全部来自共享层
 * （`KvcxSideEffects` / `KvcxReturnSummary` / `EbikeFreeRide` / `RideRecordStore`）与本模块的
 * [IosEbikeReminder]，没有新的 iOS 符号。
 */
internal object IosKvcxSideEffects : KvcxSideEffects {

    /**
     * 偏好与提醒实现都是**第一次用到时才建**：`Services.current` 在装配根 install 之前读会抛
     * （`AppServices` 的刻意设计），而本对象可能在常量初始化阶段就被引用到。
     */
    private val prefs: DisplayPrefsStore by lazy { Services.current.displayPrefs() }

    /** 进程内唯一的那一份提醒实现（见类 KDoc「为什么是 object」）。 */
    val reminder: IosEbikeReminder by lazy { IosEbikeReminder(prefs) }

    override suspend fun startRideTimer(): Pair<String, NoticeTone> =
        startFreeRideNotice(reminder, AppClock.nowMillis())

    override suspend fun ensureReminderEnabled(): Boolean {
        val on = runCatching { prefs.ebikeFreeReminderEnabled.first() }.getOrDefault(true)
        if (on) return false
        runCatching { prefs.setEbikeFreeReminderEnabled(true) }
        return true
    }

    override suspend fun scheduleAutoReturn() {
        // 上一轮的结果通知先撤掉（「已自动还车」不能与新一轮骑行并存），再按设置收口：
        // 排到点通知 / 到点补做 / 开关关着时撤销（与 Android 的 `AppKvcxSideEffects` 同序）
        reminder.cancelAutoReturn()
        reminder.reconcileAutoReturn()
    }

    override suspend fun endRideAndBurn() {
        // 收计时起点 + 撤免费提醒（待发与已投递）+ 撤自动还车排程，一处收口
        reminder.endFreeRide()
        // 相册里的码不焚：iOS 没有按 key 删除相册资产的能力（`EbikeQrGallery.canDelete` = false），
        // 共享层的 `burnPendingCodes` 因此也不会记待焚 key——不是漏做
    }

    override suspend fun onOrderGoneExternally(): Boolean {
        val mode = runCatching { prefs.ebikeUseMode.first() }.getOrElse { EbikeUseMode.Default }
        val startAt = runCatching { prefs.ebikeRideStartAt.first() }.getOrDefault(0L)
        val end = EbikeFreeRide.shouldEndTimerWhenOrderGone(
            inAppRide = mode.isAccount,
            startAtMillis = startAt,
            nowMillis = AppClock.nowMillis(),
        )
        if (!end) return false
        // 与手动还车走同一条收尾（清计时 / 撤提醒 / 撤自动还车），不另写一份
        endRideAndBurn()
        return true
    }

    override suspend fun recordRide(summary: KvcxReturnSummary) {
        val carNum = summary.carNum ?: return
        runCatching {
            RideRecordStore(Services.current.database().rideRecordDao()).add(
                carNum = carNum,
                endAt = AppClock.nowMillis(),
                durationSeconds = summary.durationSeconds ?: 0L,
                feeCents = summary.feeCents,
                // 拿不到结算结果按未结清记：宁可多提醒一次去核对，也不把不确定写成"已结清"
                settled = summary.settled ?: (summary.needPay == false),
            )
        }
    }

    override suspend fun recordRemoteRide(): Int = runCatching {
        prefs.incrementEbikeRemoteRideCount()
    }.getOrDefault(0)
}
