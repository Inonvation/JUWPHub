package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.kqcx.KvcxRideSession
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.RideRecordStore
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.EbikeAutoReturn
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.ui.ebike.AutoReturnResult
import edu.jxslu.schedule.ui.ebike.EbikeFreeRideNotifications
import edu.jxslu.schedule.ui.ebike.EbikeReminderOutcome
import edu.jxslu.schedule.ui.ebike.EbikeReminderPort
import edu.jxslu.schedule.ui.ebike.EbikeRideSignal
import edu.jxslu.schedule.ui.ebike.KvcxReturnSummary
import edu.jxslu.schedule.ui.ebike.runAutoReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 免费时长提醒 + 自动还车的 iOS 实现（2026-11-26；自动还车这一半是 E3 补的）。
 *
 * ## 两半的能力不一样，别混着说
 *
 * | `EbikeReminderPort` | Android | iOS |
 * |---|---|---|
 * | 免费时长提醒（`startFreeRide` / `checkFreeRide` / `endFreeRide`） | `AlarmManager` 精确闹钟 + 前台服务常驻倒计时 | **做**：排两条本地通知（提前量 + 免费结束），到点由系统弹 |
 * | 自动还车（`reconcileAutoReturn` / `cancelAutoReturn`） | 闹钟到点 → `WorkManager` 执行（App 不在也跑） | **降级做**：到点排一条通知 + **App 被唤起时补做**；`supportsAutoReturn = true` 但 `autoReturnRunsInBackground = false` |
 *
 * 自动还车的执行器是共享层的 [runAutoReturn]（2026-11-26 从 `:app` 搬进 `commonMain`），
 * 与 Android 同一份重试时序、同一份坐标选择、同一份 [performReturnBike]。**差的只有"谁在
 * 什么时刻叫醒它"**：Android 有 `setAlarmClock` + `WorkManager`，iOS 只有「下一次 App 在前台」。
 *
 * ## 自动还车：iOS 上到底会发生什么
 *
 * 1. 计时在案 + 开关开 + 账号方式 + 已登录 → 在**触发时刻**（`EbikeAutoReturn.triggerAtMillis`）
 *    排一条本地通知（`autoreturn.due`，标识符固定 = 覆盖式重排）；
 * 2. App 正好在前台 → 类里那个 [armForegroundTimer] 到点直接补做（进程被挂起时协程一起停，
 *    回来接着跑，那时由第 3 条判定）；
 * 3. App 被唤起（进骑行页 / 回前台，`EbikeViewModel` 的那三处 `reconcileAutoReturn`）→
 *    `outcomeOf` 判 `DUE` 就当场还车、判 `MISSED`（已过免费结束后 5 分钟）就只发一条
 *    「没来得及执行」，拖过窗口不再追；
 * 4. 结果（成功 / 每次失败 / 登录失效）都发一条通知，标识符 `autoreturn.result` 覆盖式，
 *    与 Android 的 1014 同语义（不堆一摞）。
 *
 * **不做的**：`BGAppRefreshTask`。系统不保证时刻，且本仓要写 `Info.plist` 的 `BGTaskSchedulerPermittedIdentifiers`
 * 与 `UIBackgroundModes`，而 `iosApp/` 不在这一层能改的范围里——接一半的后台能力比没有更糟
 * （用户会以为它会自己还）。所以这一端的承诺就是「到点提醒 + 下次打开时补做」，界面
 * （`EbikeSettingsSheet` 的说明与骑行页倒计时那行）照这个口径写。
 *
 * ## 与 Android 的三处刻意差异
 *
 * | 项 | Android | 这里 | 为什么 |
 * |---|---|---|---|
 * | 去重键在「已过窗」时 | 不落键（没有别的入队路径） | **落键** | iOS 的 `reconcile` 每次进页都跑，不落键就是每进一次页弹一条「没来得及」 |
 * | 失败原因文案 | `kvcxErrorText(error)`（`:app`，含业务码翻译） | 异常自带的 `message`，没有就写「未知错误」 | 那份翻译在 `:app`；iOS 侧要搬得先把它挪进共享层（本轮没做） |
 * | 结果通知 | 系统通道，后台也弹 | 前台由 `IosLocalNotifications` 的代理弹横幅；后台不弹（App 已被系统挂起） | 通知能力本身的差异 |
 *
 * ## 标识符：`freeride.` / `autoreturn.` 两个前缀
 *
 * 前缀是撤销的唯一依据（[IosLocalNotifications.removePending] 按前缀扫两个队列），
 * 与通知四族（`class.` / `homework.` / `ujing.` / `score.`）分开。
 * `freeride.*` 的具体值来自共享层 [EbikeFreeRideNotifications]（标识符与时间窗的唯一出处）。
 *
 * ## 线程
 *
 * 本类自己不切线程：[IosLocalNotifications] 的每个入口先切主队列，协程侧的 `first()` 读
 * DataStore 也不要求主线程。授权回调来自系统队列，`resume` 由协程库负责接力。
 *
 * ## 编译状态
 *
 * 本机是 Windows，**没有编译验证过**（Kotlin/Native 要 macOS）。用到的 iOS 符号全部来自仓里
 * 已有的两处：`IosLocalNotifications`（CI 编过）与 `IosUjingReminder`（同名同形）；
 * 自动还车这一半没有引入任何新的 iOS API，只多了协程与共享层的类型。
 *
 * @param session 自动还车要用它查在案订单 / 发还车请求。默认从装配根取（`Services.current.kqcxSession()`），
 *   与 `IosServices` 里那份进程级会话是**同一个对象**——iOS 只有一条快趣会话，各建一份会让
 *   token 刷新互相覆盖。传 null 且装配根也没装时，自动还车只排通知、不执行（并在结果里说清）。
 */
class IosEbikeReminder(
    private val prefs: DisplayPrefsStore,
    private val session: KvcxRideSession? = null,
) : EbikeReminderPort {

    /** 没有常驻倒计时：本地通知只在到点弹一次（提示语据此换文案，见 `startFreeRideNotice`）。 */
    override val hasCountdownNotification: Boolean get() = false

    /** 自动还车有，但**只承诺「到点提醒 + 下次打开 App 时补做」**（见类 KDoc）。 */
    override val supportsAutoReturn: Boolean get() = true

    /** iOS 没有「App 不在前台也能跑」的等价物：界面据此把话说全（见 `EbikeViewModel.autoReturnInBackground`）。 */
    override val autoReturnRunsInBackground: Boolean get() = false

    override suspend fun startFreeRide(startAtMillis: Long): EbikeReminderOutcome {
        // 换车重新计时：上一轮的「已发」记号和通知栏上那条提醒一并清掉（与 Android 同序）
        prefs.updateEbikeFreeNotifiedKeys { emptySet() }
        IosLocalNotifications.removeDelivered(FREERIDE_ID_PREFIX)
        prefs.setEbikeRideStartAt(startAtMillis)
        // 计时起点变了 = 新一轮骑行：自动还车那一条按新起点重排（Android 在开锁成功的
        // 副作用里做同一件事）。这里不 catchUp——刚起计时不可能已经到触发点。
        reconcileAutoReturn(catchUp = false)

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
        // 订单结束：自动还车再排着也没有对象可还（Android 的 `endRideAndBurn` 同一步）
        cancelAutoReturn()
        return EbikeReminderOutcome.Ended
    }

    /**
     * 自动还车的「重算 + 补做」入口（进骑行页 / 账号页 / 开锁 / 设置变更，Android 四处同口径）。
     *
     * 三分走向见 `EbikeAutoReturn.outcomeOf`：
     * - `NOT_YET`：排到点通知 + 起一个前台计时器（App 正好开着时那一刻就还）；
     * - `DUE`：**当场补做**（[catchUp] 为 false 时只是不追补，设置变更不该立刻还车）；
     * - `MISSED`：只发一条「没来得及执行」并落去重键（见类 KDoc 的差异表）。
     */
    override suspend fun reconcileAutoReturn(catchUp: Boolean) {
        val enabled = prefs.ebikeAutoReturnEnabled.first()
        val startAt = prefs.ebikeRideStartAt.first()
        // 与 Android 同一组闸门：开关 / 账号方式 / 有在案计时。登录态在会话里判（runAutoReturn 自己看）
        val account = prefs.ebikeUseMode.first().isAccount
        if (!enabled || !account || startAt <= 0L) {
            cancelAutoReturn()
            return
        }
        // 这一轮已经有结论（成功 / 重试用尽 / 已过窗）：不再排、不再跑
        if (prefs.ebikeAutoReturnDoneKey.first() == EbikeAutoReturn.doneKey(startAt)) {
            IosLocalNotifications.removePending(AUTO_RETURN_ID_PREFIX)
            return
        }

        val lead = prefs.ebikeAutoReturnLeadSeconds.first()
        val now = AppClock.nowMillis()
        when (EbikeAutoReturn.outcomeOf(startAt, lead, now)) {
            EbikeAutoReturn.Outcome.NOT_YET -> {
                val triggerAt = EbikeAutoReturn.triggerAtMillis(startAt, lead)
                armDueNotice(triggerAt)
                armForegroundTimer(triggerAt)
            }

            EbikeAutoReturn.Outcome.DUE -> {
                // 到点了但开关是刚刚才打开的（catchUp = false）：只是不追补，
                // 已排着的那条通知也不撤（到点该提醒的已经提醒过了）
                if (!catchUp) return
                performCatchUp(startAt, lead)
            }

            EbikeAutoReturn.Outcome.MISSED -> {
                // 过窗：这一轮一次都不会跑了，用户可能正在被计费——必须出声
                // （与 Android 同一句话、同一个判断依据，见 EbikeAutoReturn.MISSED_TEXT）
                IosLocalNotifications.removePending(AUTO_RETURN_ID_PREFIX)
                notifyResult(AUTO_RETURN_MISSED_ID, EbikeAutoReturn.TITLE_MISSED, EbikeAutoReturn.MISSED_TEXT)
                // **这里与 Android 不同**：落去重键。iOS 的 reconcile 每次进页都跑，
                // 不落键就是每进一次页弹一条「没来得及」（见类 KDoc 的差异表）
                prefs.setEbikeAutoReturnDoneKey(EbikeAutoReturn.doneKey(startAt))
            }
        }
    }

    /** 撤自动还车：待发的到点通知 + 已经投递的结果通知，一起清（关开关 / 退出 / 计时结束）。 */
    override suspend fun cancelAutoReturn() {
        foregroundTimer?.cancel()
        foregroundTimer = null
        IosLocalNotifications.removePending(AUTO_RETURN_ID_PREFIX)
        IosLocalNotifications.removeDelivered(AUTO_RETURN_ID_PREFIX)
    }

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
     * 排「该还车了」那条到点通知（标识符固定 = 覆盖式重排，与免费提醒同一手法）。
     *
     * 先问授权：没有通知权限时这一条也排不上，静默降级（自动还车的执行本身不依赖通知）。
     */
    private suspend fun armDueNotice(triggerAtMillis: Long) {
        if (!awaitAuthorized()) return
        IosLocalNotifications.schedule(
            identifier = AUTO_RETURN_DUE_ID,
            title = EbikeAutoReturn.TITLE_DUE,
            body = EbikeAutoReturn.dueText(),
            triggerAtMillis = triggerAtMillis,
        )
    }

    /**
     * App 正好开着时，到触发点直接还车（不必等用户退页再进）。
     *
     * **只是尽力而为**：进程被系统挂起时协程一起停，恢复时会晚一点跑；那时 [reconcileAutoReturn]
     * 用 `outcomeOf` 判 `DUE` / `MISSED`，该还的还、该喊的喊。所以这里不需要复杂的补偿逻辑。
     * 只留一个槽位（[foregroundTimer]）：页面进出会反复调 reconcile，多留几个只会重复还车。
     */
    private fun armForegroundTimer(triggerAtMillis: Long) {
        foregroundTimer?.cancel()
        val remaining = triggerAtMillis - AppClock.nowMillis()
        // 至少等 1 秒：早到的几毫秒不该变成紧密循环
        val wait = if (remaining > 0L) maxOf(remaining, 1_000L) else 1_000L
        foregroundTimer = timerScope.launch {
            delay(wait)
            runCatching { reconcileAutoReturn(catchUp = true) }
        }
    }

    /**
     * 真的还这一单：共享层的 [runAutoReturn]（与 Android 同一份重试时序与坐标选择）。
     *
     * 善后顺序与 Android 的 `EbikeAutoReturnWorker.run` 对齐：
     * 成功 → 清计时 / 撤免费提醒 / 通知页面 / 发结果通知；
     * 订单已不在 → 清计时、不打扰；未登录 → 通知并停；失败 → 每次尝试都已发过通知。
     * **去重键放最后**（早退的分支不落键，与 Android 同一纪律）。
     */
    private suspend fun performCatchUp(startAtMillis: Long, leadSeconds: Int) {
        val rideSession = sessionOrNull()
        if (rideSession == null) {
            // 装配根没装 / 拿不到会话：不能装作还过车。清掉排程，把话说明白
            IosLocalNotifications.removePending(AUTO_RETURN_ID_PREFIX)
            notifyResult(
                AUTO_RETURN_RESULT_ID,
                EbikeAutoReturn.TITLE_FAILURE,
                EbikeAutoReturn.NOT_LOGGED_IN_TEXT,
            )
            return
        }
        val key = EbikeAutoReturn.doneKey(startAtMillis)
        val retries = prefs.ebikeAutoReturnRetries.first()
        // 闹钟 / 通知那一层已经到点：先撤掉，免得残留一条不会再响的排程
        IosLocalNotifications.removePending(AUTO_RETURN_ID_PREFIX)

        val result = runAutoReturn(
            session = rideSession,
            retries = retries,
            onOrderEnded = { clearTimerAfterReturn() },
            onAttemptFailed = { error, attempt, total, state ->
                notifyResult(
                    AUTO_RETURN_RESULT_ID,
                    EbikeAutoReturn.TITLE_FAILURE,
                    EbikeAutoReturn.failureText(errorText(error), attempt, total, state),
                )
            },
        )
        when (result) {
            is AutoReturnResult.Returned -> {
                // 本机骑行记录要留痕（与 Android `AppKvcxSideEffects.recordRide` 同口径）：
                // 自动还掉的那一单不记，账号页的「骑行记录」就会静默漏掉它
                recordRide(result.summary)
                EbikeRideSignal.notifyEnded("已自动还车，订单已结束")
                notifyResult(
                    AUTO_RETURN_RESULT_ID,
                    EbikeAutoReturn.TITLE_SUCCESS,
                    EbikeAutoReturn.successText(
                        result.summary.durationText,
                        result.summary.feeText,
                    ),
                )
            }

            // 订单已不在（其他端还了 / 已结束）：收计时、不打扰——还车的动作不是我们做的
            AutoReturnResult.AlreadyEnded -> {
                clearTimerAfterReturn()
                EbikeRideSignal.notifyEnded("订单已在其他端结束")
            }

            AutoReturnResult.NotLoggedIn -> notifyResult(
                AUTO_RETURN_RESULT_ID,
                EbikeAutoReturn.TITLE_FAILURE,
                EbikeAutoReturn.NOT_LOGGED_IN_TEXT,
            )

            is AutoReturnResult.Failed -> Unit // 每次失败都已在 onAttemptFailed 里通知过
        }
        prefs.setEbikeAutoReturnDoneKey(key)
    }

    /**
     * 订单结束那一刻的本地善后：清计时起点、撤免费提醒（待发 + 已投递）、撤自动还车排程。
     *
     * 对应 Android 的 `AppKvcxSideEffects.endRideAndBurn`（那边还要停前台服务、焚相册里的码；
     * iOS 没有常驻服务，相册也删不了——`EbikeQrGallery.canDelete` 是 false，本来就不记待焚 key）。
     * 由 [performReturnBike] 在**还车请求成功之后、扣款确认之前**调起（本地走时在这一刻截停）。
     */
    private suspend fun clearTimerAfterReturn() {
        prefs.setEbikeRideStartAt(0L)
        prefs.updateEbikeFreeNotifiedKeys { emptySet() }
        IosLocalNotifications.removePending(FREERIDE_ID_PREFIX)
        IosLocalNotifications.removeDelivered(FREERIDE_ID_PREFIX)
        IosLocalNotifications.removePending(AUTO_RETURN_ID_PREFIX)
    }

    /** 发一条结果类通知（标识符覆盖式：第 1 次失败的会被第 2 次的结果顶掉，不堆一摞）。 */
    private fun notifyResult(identifier: String, title: String, body: String) {
        IosLocalNotifications.schedule(
            identifier = identifier,
            title = title,
            body = body,
            // 立刻投递：`UNTimeIntervalNotificationTrigger` 只接受 > 0，`schedule` 内部
            // 已过期的一律钳到 1 秒（系统接受的最小值）
            triggerAtMillis = AppClock.nowMillis(),
        )
    }

    /**
     * 失败原因的用户可读文本。
     *
     * Android 用 `:app` 的 `kvcxErrorText`（业务码 → 中文）；那份还在 `:app`，iOS 这一侧
     * 先退回异常自带的 `message`（快趣的 `KvcBusinessError` 已经带平台原文），空则「未知错误」。
     * **别在这里再写一份业务码翻译**——要搬就把 `kvcxErrorText` 搬进共享层，一处改两端。
     */
    private fun errorText(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "未知错误"

    /**
     * 记一次本机骑行记录（iOS 侧唯一调用点：自动还车成功那一刻）。
     *
     * 与 Android 的 `AppKvcxSideEffects.recordRide` 逐字段同口径（含「拿不到结算结果按未结清记」
     * ——宁可多提醒去核对，也不把不确定写成"已结清"）。失败静默：记账坏掉不该让还车结果
     * 反过来变成失败（那辆车已经还了）。
     *
     * 库从装配根取（`Services.current.database()`，两端同一份 Room 库）；`carNum` 拿不到时
     * 没有可记的键，直接跳过。
     */
    private suspend fun recordRide(summary: KvcxReturnSummary) {
        val carNum = summary.carNum ?: return
        runCatching {
            RideRecordStore(Services.current.database().rideRecordDao()).add(
                carNum = carNum,
                endAt = AppClock.nowMillis(),
                durationSeconds = summary.durationSeconds ?: 0L,
                feeCents = summary.feeCents,
                settled = summary.settled ?: (summary.needPay == false),
            )
        }
    }

    /** 自动还车的会话：优先用构造参数，否则从装配根取（`IosServices` 那份进程级会话）。 */
    private fun sessionOrNull(): KvcxRideSession? =
        session ?: runCatching { Services.current.kqcxSession() }.getOrNull()

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

    private var foregroundTimer: Job? = null

    private companion object {
        /**
         * 撤销用的标识符前缀：`freeride.`。
         *
         * 值来自共享层（[EbikeFreeRideNotifications.ID_PREFIX]）——标识符本身在那边拼，
         * 这里只按同一个前缀扫队列；两边写死两份迟早漂移（撤销就撤不干净了）。
         */
        const val FREERIDE_ID_PREFIX = EbikeFreeRideNotifications.ID_PREFIX

        /**
         * 自动还车那一族的前缀：`autoreturn.`（到点提醒 / 结果 / 过窗三条共用）。
         *
         * 与 `freeride.` 分开是刻意的：用户关掉免费提醒时不该顺手撤掉自动还车那条，
         * 反过来也一样（两套开关独立，见 `.agents/rules/ebike.md`）。
         */
        const val AUTO_RETURN_ID_PREFIX = "autoreturn."

        /** 到点「该还车了」：一轮只有一条，标识符固定即覆盖式重排。 */
        const val AUTO_RETURN_DUE_ID = AUTO_RETURN_ID_PREFIX + "due"

        /** 还车结果（成功 / 失败）：同 id 覆盖，不堆一摞（与 Android 的 1014 同语义）。 */
        const val AUTO_RETURN_RESULT_ID = AUTO_RETURN_ID_PREFIX + "result"

        /** 过窗「没来得及执行」：一轮最多一条。 */
        const val AUTO_RETURN_MISSED_ID = AUTO_RETURN_ID_PREFIX + "missed"

        /**
         * 前台计时器的作用域：进程级、`Dispatchers.Default`。
         *
         * 为什么不等宽：`armForegroundTimer` 由页面构造期 / 回前台触发，把它挂在
         * ViewModel 的 `viewModelScope` 上会随页面销毁而取消（用户切到别页就等于放弃还车），
         * 而这本来就是「进程活着就尽量还」的一件事。单槽位（[foregroundTimer]）保证不叠加。
         */
        val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
