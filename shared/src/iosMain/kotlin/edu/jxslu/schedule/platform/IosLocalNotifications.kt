package edu.jxslu.schedule.platform

import edu.jxslu.schedule.domain.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSError
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * 本地通知的四件共用小件（N2，2026-11-26）：授权、排一条、按前缀撤、前台横幅代理。
 *
 * N1（2026-11-25 晚）把这一整套写在 `IosUjingReminder` 里，只服务洗衣完成一类。现在
 * 作业截止与成绩提醒也要用，复制三份会让「权限被拒后不反复追问」这条结论各说各话
 * （三份各自的 `requesting` 标志 = 三个系统弹窗）。这里只搬**与业务无关的那部分**，
 * 文案、标识符前缀、触发时刻一律留给调用方：
 *
 * | 件 | 谁在用 |
 * |---|---|
 * | [ensureAuthorized] | 三类提醒都是同一个入口；同一个进程只弹一次系统授权框 |
 * | [schedule] | 标识符即去重键，同标识符后一次覆盖前一次（与 Android `PendingIntent` 同语义） |
 * | [removePending] / [removeDelivered] | 按标识符前缀扫队列，不靠进程内存记得排过哪几个 |
 * | [installForegroundPresentation] | iOS 默认前台不弹横幅，装代理才有 |
 *
 * ## 标识符前缀各自区分
 *
 * `ujing.<订单号>` / `homework.<作业id>.<提醒点日期>` / `score.<...>`。前缀是**撤销的唯一依据**：
 * `cancel` / `revoke` 这类接口拿不到业务 id（与 Android `AlarmManager` 同形），只能按前缀扫。
 * 所以每一族的标识符必须以自己的前缀开头，**不能**用别人的。
 *
 * ## 权限：被拒不抛，降级成 no-op
 *
 * 第一次需要排提醒时请求 `alert | sound | badge`。用户拒了（或系统里关掉了通知）就
 * **不再请求、也不排**，调用方据此静默降级成「没有提醒」，业务链路照常工作；结论记进
 * [IosNotificationPermission]（进程内，不落盘——权限结论由系统持有，我们只缓存这次进程读到的）。
 *
 * ## 所有符号都已在 CI 上编译过
 *
 * N1 那一版的 `requestAuthorizationWithOptions` / `addNotificationRequest` /
 * `getPendingNotificationRequestsWithCompletionHandler` / `remove…WithIdentifiers` /
 * `triggerWithTimeInterval` 与 `content.setTitle/setBody/setSound` 全部按 CI 编译通过的形状
 * 搬过来，没有改写调用形式。本机（Windows）编不了 iOS，改动后只能靠 CI 验编译。
 */
internal object IosLocalNotifications {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    /** 授权请求在途：系统弹窗只该出现一次，调用方可能每几秒就来一次。 */
    private var requesting = false

    /** 在途请求期间攒下的「授权后要做的事」：授权成功时按加入顺序各跑一次，失败则清空。 */
    private val afterGrant = mutableListOf<() -> Unit>()

    /**
     * 装上「App 开着时也弹横幅」的代理。**启动期必须调一次**。
     *
     * `UNUserNotificationCenter.delegate` 是 **weak**，写成局部对象会被 ObjC 回收，回调一次都不会来
     * （与 `WebViewController.ios.kt` 的 `navigationDelegate` 同一个坑），所以持有者必须是进程级
     * 对象 [presenter]。`IosServices.init` 里调，那里是 `MainViewController` 的第一行。
     */
    fun installForegroundPresentation() {
        center.delegate = presenter
    }

    /**
     * 要排提醒了先过这一道：[onResult] 的布尔是「现在能不能弹」。
     *
     * - 已授权：立刻回 `true`（同步，就在调用栈里）；
     * - 已拒绝：立刻回 `false`，**不再问系统**（已拒过时系统本来也不会再弹，但每次轮询都问一次是白费）；
     * - 还没问过：攒下 [onResult] 并发起一次请求，授权成功后按加入顺序补跑；回调在系统队列上，
     *   不在主线程。
     *
     * 三个调用方（洗衣 / 作业 / 成绩）共用这一个请求在途标志，所以无论谁先触发，
     * 用户只看到一次系统授权框，其余两家的回调都会在授权成功后补跑。
     */
    fun ensureAuthorized(onResult: (Boolean) -> Unit) {
        when (IosNotificationPermission.state.value) {
            IosNotificationPermissionState.Allowed -> onResult(true)
            IosNotificationPermissionState.Denied -> onResult(false)
            IosNotificationPermissionState.Unknown -> {
                afterGrant += { onResult(true) }
                requestOnce()
            }
        }
    }

    /**
     * 排一条到点通知（**不做权限判断**，调用方先用 [ensureAuthorized] 拿到结论）。
     *
     * `triggerAtMillis` 已过期或不足 1 秒一律按「马上」排（钳到 1 秒）：`UNTimeIntervalNotificationTrigger`
     * 要求 `timeInterval > 0`，传 0 或负数 iOS 直接拒绝注册。
     */
    fun schedule(identifier: String, title: String, body: String, triggerAtMillis: Long) {
        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(body)
        content.setSound(UNNotificationSound.defaultSound())
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(secondsUntil(triggerAtMillis), false)
        val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)
        // 回执（NSError）只可能是标识符重复或内容非法，两者都是我们的编程错误；
        // 提醒不发出去也不该让调用方崩，所以不做回调、只由系统日志留痕。
        center.addNotificationRequest(request, null)
    }

    /**
     * 撤掉以 [prefix] 开头的**待发**通知；[then] 在扫完撤完之后调用（用于「先清空再重排」）。
     *
     * 为什么按前缀扫而不是记在内存里：进程被杀后那点记忆就没了，而系统队列还在。
     */
    fun removePending(prefix: String, then: () -> Unit = {}) {
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val ids = requests.identifierPrefixMatches(prefix)
            if (ids.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(ids)
            then()
        }
    }

    /**
     * 再撤掉以 [prefix] 开头的**已投递**通知。
     *
     * 已投递的那条不在待发队列里，要单独清：`autoCancel` 只在用户点通知本身时生效，
     * 业务侧确认收摊时通知栏里那条还杵着（同 Android 的 `revoke`）。
     */
    fun removeDelivered(prefix: String) {
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.deliveredIdentifierPrefixMatches(prefix)
            if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
    }

    /** 授权回调。**拒绝不是异常**：`granted = false` 与请求本身失败（[NSError]）都按「这次进程里不排提醒」处理。 */
    private fun requestOnce() {
        if (requesting) return
        requesting = true
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, error ->
            requesting = false
            if (error != null) {
                Logger.w(TAG, "请求通知权限失败：${error.localizedDescription}")
            }
            if (granted && error == null) {
                IosNotificationPermission.mark(IosNotificationPermissionState.Allowed)
                val pending = afterGrant.toList()
                afterGrant.clear()
                pending.forEach { it() }
            } else {
                IosNotificationPermission.mark(IosNotificationPermissionState.Denied)
                afterGrant.clear()
            }
        }
    }

    private const val TAG = "IosLocalNotifications"
}

/**
 * 触发时刻 → 相对秒数。
 *
 * **已过期或不足 1 秒一律按「马上」**：`UNTimeIntervalNotificationTrigger` 要求 `timeInterval > 0`，
 * 传 0 或负数会注册失败（不是精度问题）。1 秒是系统接受的最小值。
 */
private fun secondsUntil(triggerAtMillis: Long): Double {
    val remaining = triggerAtMillis - AppClock.nowMillis()
    return if (remaining < 1_000L) 1.0 else remaining / 1_000.0
}

/**
 * 待发队列里以 [prefix] 开头的标识符。
 *
 * 入参是可空的 `List<*>?`（ObjC 的 NSArray 没做非空标注，2026-11-25 CI 实测），逐个 `as?` 收。
 */
private fun List<*>?.identifierPrefixMatches(prefix: String): List<String> =
    orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }
        .filter { it.startsWith(prefix) }

/** 已投递队列里以 [prefix] 开头的标识符（同一个请求标识，挂在 `UNNotification.request` 上）。入参同上，可空。 */
private fun List<*>?.deliveredIdentifierPrefixMatches(prefix: String): List<String> =
    orEmpty().mapNotNull { (it as? UNNotification)?.request?.identifier }
        .filter { it.startsWith(prefix) }

/**
 * 前台横幅代理。**必须是进程级强引用**：`UNUserNotificationCenter.delegate` 是 weak，
 * 局部对象会被回收，`willPresentNotification` 一次都不会来（iOS 默认前台静默）。
 *
 * 只实现这一个方法：负责「App 开着时也弹横幅并响一声」。点通知进入 App 的落点这一版不做
 * （三类通知都没有带 `userInfo`，点开就是回到 App，没有要跳的页面）。
 *
 * ## 为什么是 `class` + 手工单例，而不是 `object`
 *
 * 2026-11-25 第二轮 CI 实测：`private object` 同时继承 `NSObject` 与 `UNUserNotificationCenterDelegateProtocol`
 * 会在**后端代码生成**阶段炸——`Allocation of Obj-C class … should have been lowered`。
 * Obj-C 子类的实例必须由 ObjC runtime 分配（`alloc/init`），而 Kotlin 的 `object` 是静态单例，
 * 两条路对不上。改成普通 `class`、由 [presenter] 持一份就是了。
 */
private class IosNotificationPresenter : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound,
        )
    }
}

/** 那个唯一实例（进程级）。见 [IosNotificationPresenter] 的 KDoc。 */
private val presenter: IosNotificationPresenter by lazy { IosNotificationPresenter() }

/** 权限结论的三态（进程内）。 */
enum class IosNotificationPermissionState {

    /** 还没向系统请求过，或请求还在等用户回答：下一次要排提醒时会发一次请求。 */
    Unknown,

    /** 已授权：排提醒会真的排上。 */
    Allowed,

    /** 用户拒绝 / 通知在系统里被关掉：排提醒静默不排，也不反复追问。 */
    Denied,
}

/**
 * 通知权限的进程内结论，页面可以直接读来显示一行说明。
 *
 * 为什么不落盘：权限的真身由系统持有（`UNAuthorizationStatus`），我们只是把这次进程里
 * 「请求过、结论是拒」这件事记下来，避免每次轮询都去问一遍。冷启动重新问一次是免费的
 * （已拒过时 `requestAuthorization` 立刻回 `false`，不再弹窗）。
 */
object IosNotificationPermission {

    private val _state = MutableStateFlow(IosNotificationPermissionState.Unknown)

    val state: StateFlow<IosNotificationPermissionState> = _state

    /**
     * 给页面用的一行说明；`null` = 不需要解释（已授权，或还没问过）。
     *
     * 文案不点功能名：同一个结论对洗衣 / 作业 / 成绩三类都成立，写死一类会让另外两类看着像「没这功能」。
     */
    val hint: String?
        get() = when (_state.value) {
            IosNotificationPermissionState.Denied ->
                "通知未开启：提醒不会弹出，可在 设置 › 通知 › JUWPHub 打开"

            else -> null
        }

    internal fun mark(state: IosNotificationPermissionState) {
        _state.value = state
    }
}
