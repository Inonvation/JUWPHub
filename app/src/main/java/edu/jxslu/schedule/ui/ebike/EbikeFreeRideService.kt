package edu.jxslu.schedule.ui.ebike

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import edu.jxslu.schedule.domain.EbikeFreeRide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 免费时长常驻倒计时（DESIGN §3.9，2026-09-24 新增）。
 *
 * **它只做一件事：让通知栏上那条倒计时活着。** 到点提醒不归它管——那是
 * `setAlarmClock` 的活（见 `EbikeFreeRideReminder`）。理由：屏幕关闭后 CPU 会挂起，
 * 服务里的协程 `delay` 要等到下一个唤醒点才跑，会迟到几分钟；而 `setAlarmClock`
 * 是系统级精确闹钟，到点唤醒设备，不受 Doze/省电推迟。
 *
 * 刷新策略（2026-10-19 修订）：
 * - **亮屏**：每秒刷新芯片文案与进度条；卡片头部由系统 chronometer 自己走秒。
 *   **秒数优先**：用户 2026-10-19 明确要岛上逐秒走，接受 Live Update 卡片头部的应用图标
 *   跟着每秒重绘（下拉状态栏能看到闪）；别再改成 5 秒一跳。
 * - **灭屏**：**一个 notify 都不发**——通知栏此刻没人看，这是 15 分钟里最大的一块浪费。
 *   协程阻塞在屏幕事件上等（零 CPU），计时到点也由 `withTimeoutOrNull` 兜住；
 * - **重新亮屏**：立刻补刷一次，所以点亮屏幕的瞬间数值就是准的（不会停在一个旧值上）。
 *
 * 生命周期：点「打开微信扫一扫」时启动（此刻 App 在前台，`startForegroundService`
 * 合法）；用户点「结束骑行」、或免费结束后的核对、或本服务自己的兜底延时到点
 * （[STOP_GRACE_MS]）时停止。**不做开机补排**：15 分钟的计时重启后必然过期
 * （用户拍板 2026-09-22）。
 *
 * **收尾只走 [removeNotification]**（2026-11-03）：它先关 [EbikeCountdownPostGate] 再摘通知，
 * 保证最后一次 `notify` 一定排在 `stopForeground` 之前。直接写 `stopForeground` 会在
 * 「tick 的 notify 与摘除在 system_server 里错序」时留下一条没人刷新的 ongoing 通知
 * （还车后灵动岛不消失、倒计时冻住），而且是永久的。
 *
 * 启动方一律用 [start] / [stop]，别自己拼 Intent。
 */
class EbikeFreeRideService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    /** 屏幕事件通道（CONFLATED：只关心最新状态，不关心积压了几个事件）。 */
    private val screenEvents = Channel<Boolean>(Channel.CONFLATED)

    private var screenReceiver: BroadcastReceiver? = null

    /** 屏幕是否亮着。由 [screenReceiver] 在主线程写、tick 协程在读，所以是 volatile。 */
    @Volatile
    private var screenOn = true

    /**
     * 「发通知 ↔ 摘通知」的先后闸（2026-11-03，见 [EbikeCountdownPostGate]）：
     * tick 协程的每一次 notify 与收尾都过它，保证 notify 一定落在摘除之前。
     */
    private val postGate = EbikeCountdownPostGate()

    override fun onCreate() {
        super.onCreate()
        screenOn = (getSystemService(PowerManager::class.java))?.isInteractive ?: true
        registerScreenReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startAt = intent?.getLongExtra(EXTRA_START_AT, 0L) ?: 0L
        if (startAt <= 0L || !EbikeFreeRide.isActive(startAt, System.currentTimeMillis())) {
            // 没有进行中的计时（或已经结束）：常驻通知没有存在理由
            shutdown()
            return START_NOT_STICKY
        }
        if (!enterForeground(startAt)) return START_NOT_STICKY
        startTicking(startAt)
        return START_NOT_STICKY
    }

    /** 进前台并挂上常驻通知；失败（系统限制等）返回 false 并收尾。 */
    private fun enterForeground(startAt: Long): Boolean = runCatching {
        EbikeFreeRideNotifier.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            EbikeFreeRide.NotificationIds.COUNTDOWN,
            EbikeFreeRideNotifier.countdownNotification(this, startAt, System.currentTimeMillis()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        // 通知已经挂上，这一轮才允许 tick 去刷它（见 postGate）
        postGate.open()
        // 记下这一轮 tick 用的起点：换车时它变了，start() 就会重建
        runningStartAt = startAt
    }.onFailure {
        // 通知权限被拒不会抛异常（通知静默不显示，服务照常前台）；
        // 走到这里通常是系统层面的限制，停掉比留一个没有通知的「前台服务」干净
        Log.w(TAG, "startForeground failed", it)
        shutdown()
    }.isSuccess

    /**
     * 亮屏每秒刷新芯片文案与进度、灭屏不刷新的 tick 循环（见类 KDoc）。
     *
     * 到点提醒**不在这里发**——那是 `setAlarmClock` 的活；本协程只负责「刷新通知」
     * 与「到点后把常驻通知摘掉」两件事。
     *
     * **只在芯片文案或进度刻度变了时才真的 notify**（2026-10-19）：正文已是静态说明，
     * 卡片头部由系统 chronometer 走，服务只负责芯片短文案与进度条。点扫一扫后的 5 秒
     * 缓冲期内进度满格不动，那几秒不必重发；灭屏那一支回来时会把键清掉，所以「点亮即准」
     * 照旧（亮屏后第一次一定发）。
     */
    private fun startTicking(startAt: Long) {
        tickJob?.cancel()
        tickJob = scope.launch {
            // 上一轮发出去的内容（null = 还没发过，必须发一次）
            var lastPosted: String? = null
            while (true) {
                val now = System.currentTimeMillis()
                if (!EbikeFreeRide.isActive(startAt, now)) break
                if (screenOn) {
                    val visible = EbikeFreeRide.countdownChipText(startAt, now) +
                        '|' + EbikeFreeRide.progressPercent(startAt, now)
                    if (visible != lastPosted) {
                        // 收尾已经开始（闸已关）：这一发不再发，循环也没有继续的必要
                        if (!postCountdown(startAt, now)) return@launch
                        lastPosted = visible
                    }
                    // 等满一秒，或等屏幕状态变化（灭屏立刻停手，不用等满这一轮）
                    withTimeoutOrNull(TICK_MS) { screenEvents.receive() }
                } else {
                    // 灭屏：不刷任何通知，阻塞等屏幕亮；最长等到免费结束，
                    // 免得整段灭屏时协程醒不过来、错过收尾
                    val remain = EbikeFreeRide.freeEndMillis(startAt) - System.currentTimeMillis()
                    withTimeoutOrNull(remain.coerceAtLeast(1L)) { screenEvents.receive() }
                    // 灭屏期间系统可能把通知收走，重新亮屏后无论内容变没变都补一次
                    lastPosted = null
                }
            }
            // 免费结束：让结束点的闹钟先把提醒发出去，再把常驻通知摘掉。
            // 闹钟真丢了也不会让通知栏一直挂着（提醒由周期核对补发，不依赖这里）。
            delay(STOP_GRACE_MS)
            shutdown()
        }
    }

    private fun registerScreenReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val on = intent.action == Intent.ACTION_SCREEN_ON
                screenOn = on
                screenEvents.trySend(on)
            }
        }
        screenReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // 动态注册只在服务存活期间有效，随服务一起消失；屏幕广播是系统发的，
        // 用 RECEIVER_NOT_EXPORTED 收（API 33+ 强制要求声明可见性）
        runCatching {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { Log.w(TAG, "register screen receiver failed", it) }
    }

    /**
     * 发一次倒计时通知（走 [postGate]，与收尾串行）。
     * 返回 false = 收尾已经开始，这一次**没有发**（见 [EbikeCountdownPostGate]）。
     */
    private fun postCountdown(startAt: Long, now: Long): Boolean =
        postGate.post { EbikeFreeRideNotifier.updateCountdown(this, startAt, now) }

    override fun onDestroy() {
        tickJob?.cancel()
        tickJob = null
        scope.cancel()
        screenReceiver?.let { receiver -> runCatching { unregisterReceiver(receiver) } }
        screenReceiver = null
        runningStartAt = 0L
        // stopService 走的是这条路径（不经过 onStartCommand），通知得在这里摘
        removeNotification()
        super.onDestroy()
    }

    /**
     * 摘掉常驻通知的唯一实现（[shutdown] 与 [onDestroy] 都走它，可重入）。
     *
     * 顺序有讲究：**先关闸**（等在飞的那次 notify 落地、此后不再发）→ 摘前台通知 →
     * 再显式取消一次。第三步不是多余的，理由见 [EbikeFreeRideNotifier.cancelCountdown]：
     * 只 stopForeground 会在「notify 与摘除在 system_server 里错序」时留下永久残通知。
     */
    private fun removeNotification() {
        postGate.seal()
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        EbikeFreeRideNotifier.cancelCountdown(this)
    }

    /** 摘掉常驻通知并结束自己。可重入（[onDestroy] 里的清理是幂等的）。 */
    private fun shutdown() {
        removeNotification()
        stopSelf()
    }

    companion object {
        private const val TAG = "EbikeFreeRideService"
        private const val EXTRA_START_AT = "ebike_free_start_at"

        /**
         * 服务自己的兜底收尾延时：晚于结束点闹钟一点，让闹钟先把「已结束」提醒发出去
         * 再摘常驻通知。闹钟真丢了也不会让通知栏一直挂着。
         */
        private const val STOP_GRACE_MS = 2 * 60_000L

        /**
         * 常驻通知的刷新间隔（1 秒）。**秒数优先**：HyperOS 3 的芯片不显示 chronometer，
         * 只能靠每秒重发 `setShortCriticalText` 让岛上逐秒走；代价是 Live Update 卡片
         * 头部的应用图标每秒重绘（2026-10-19 用户看了 5 秒版后明确「我要看的就是倒计时」，
         * 接受这个闪烁）。每秒一次也是系统允许的上限；灭屏时完全不刷，见类 KDoc。
         */
        private const val TICK_MS = 1_000L

        /**
         * 服务当前 tick 用的起点（进程内；0 = 没在跑）。进程被杀即归零——那正是要的语义。
         *
         * 它是 [start] 的唯一判据，作用有两层：
         * - **换车必须重建**：起点变了（用户又扫了一辆），tick 循环握着的是旧起点，
         *   不重新 `onStartCommand` 就还在刷旧的剩余时间。判据必须是「起点是否相同」，
         *   不能是「服务是否在跑」——2026-09-24 修：早先只看在不在跑，于是上一轮没结束
         *   就换车时倒计时不重置；
         * - 幂等：`check` 每轮都会调 [start]，起点没变时跳过，省掉一次 `startForegroundService`
         *   与 tick 循环的推倒重建。
         */
        @Volatile
        private var runningStartAt = 0L

        /**
         * 起常驻倒计时（起点变了就重建 tick）。返回 false = 起点非法、这一轮被用户划掉过、
         * 或这次没起成（多为后台启动前台服务被系统拒绝，见 `restrictions-bg-start`），不抛异常。
         */
        fun start(context: Context, startAtMillis: Long): Boolean {
            // 用户把这一轮的倒计时划掉了：别再顶回去（官方 Live Updates 口径，
            // 见 EbikeCountdownDismissal）。`check` 每轮都会调这里，这道闸是唯一的拦点。
            if (EbikeCountdownDismissal.isDismissed(startAtMillis)) return false
            if (!EbikeFreeRide.shouldStartCountdown(runningStartAt, startAtMillis)) {
                // 同一起点已经在跑 = 幂等成功；起点非法（≤ 0）= 失败
                return startAtMillis > 0L
            }
            return runCatching {
                context.startForegroundService(
                    Intent(context, EbikeFreeRideService::class.java)
                        .putExtra(EXTRA_START_AT, startAtMillis),
                )
            }.onFailure { Log.w(TAG, "start failed", it) }.isSuccess
        }

        /** 停常驻倒计时（通知随之摘掉）。没在跑时是空操作。 */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, EbikeFreeRideService::class.java))
            }.onFailure { Log.w(TAG, "stop failed", it) }
        }
    }
}

/**
 * 「发通知 ↔ 摘通知」的先后闸（2026-11-03）。
 *
 * 问题的形状：倒计时通知每秒被 `NotificationManager.notify` 用同一 id 顶一次，摘除走
 * `stopForeground(STOP_FOREGROUND_REMOVE)`。两条都是**异步**投给 system_server 的请求，
 * 执行顺序只由到达顺序决定；而 tick 协程（`Dispatchers.Default`）与收尾（主线程
 * `onDestroy`）不在同一线程上。tick 已经进了 notify、主线程这时在收尾，摘除先执行、
 * notify 后落地——于是留下一条**没有任何人再刷新的 ongoing 通知**：芯片文案冻在最后一次
 * 的秒数（用户看到的是「倒计时不动」），又是 ongoing 划不掉（「残留」）。这不是观感问题，
 * 一旦发生就是永久的（没有服务再管那条通知）。
 *
 * 所以两边串行：每次发送都在 [post] 的锁内完成（**返回时 notify 已经交出去**），收尾先
 * [seal]（等正在发的那一次落地、此后一律不再发）再摘除。不变量只有一条：
 * **seal 返回之后，不允许再有任何一次发送**（`EbikeCountdownPostGateTest` 钉住）。
 */
internal class EbikeCountdownPostGate {

    private val lock = Any()
    private var sealed = false

    /** 重新开闸（进前台挂上通知之后调用）。 */
    fun open() {
        synchronized(lock) { sealed = false }
    }

    /** 闸开着就执行一次发送并返回 true；已关闸返回 false、**不执行**。 */
    fun post(block: () -> Unit): Boolean = synchronized(lock) {
        if (sealed) {
            false
        } else {
            block()
            true
        }
    }

    /** 关闸（幂等）。返回时：在飞的那一次发送已经落地，之后的 [post] 一律不执行。 */
    fun seal() {
        synchronized(lock) { sealed = true }
    }
}
