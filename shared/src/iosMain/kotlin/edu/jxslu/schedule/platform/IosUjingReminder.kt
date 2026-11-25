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
 * 洗衣完成提醒的 iOS 实现（N1，2026-11-25 晚）。
 *
 * 三件事与 [UjingReminder] 一一对应，语义照 `:app` 的 `UjingDoneReminder`：
 * `schedule` 排一条到点通知，`cancel` 撤待发的那条，`revoke` 连已投递的也撤。
 * 区别只有两条，都是 iOS 的机制决定的，不是省事：
 *
 * 1. **到点不能再查订单**。Android 的闹钟落点会先查一次订单详情，只有本次核对看到转入 50
 *    才发通知，还在跑就按新剩余重排；iOS 的本地通知到点由系统直接投递，**没有任何代码被执行**
 *    （后台唤起要靠 `BGTaskScheduler`，系统不保证时刻）。所以这里到点就发，正文写「预计已结束」，
 *    兜底闹钟（按模式总时长排的那枚，常早于真实结束）响得早时不会自我纠正。
 * 2. **一次只挂一条**。标识符带订单号（[UJING_ID_PREFIX] + `orderId`），同一单重排是覆盖；
 *    撤的时候按前缀把待发与已投递两个队列一起扫，不依赖内存里记着哪几个。
 *
 * ## 权限：被拒不抛，降级成 no-op
 *
 * 第一次 `schedule` 时请求 `alert | sound | badge`。用户拒了（或系统里关掉了通知）就
 * **不再请求、也不排**，`schedule` 变 no-op，订单卡与 15 秒轮询照常工作；结论记进
 * [IosNotificationPermission]（进程内，不落盘——权限结论由系统持有，我们只缓存这次进程读到的）。
 * 页面要显示一行说明时读它的 [IosNotificationPermission.hint]。
 *
 * ## 排提醒的两个细节
 *
 * - **`timeInterval` 必须 > 0**：已过期或不足 1 秒的时刻一律按「马上」排（钳到 1 秒）。
 *   这不是精度问题——传 0 或负数 iOS 直接拒绝注册。
 * - **重置落点是常态**：`UjingViewModel` 每次轮询（15 秒）都会带着新剩余时间调一次 `schedule`，
 *   同标识符后一次覆盖前一次，与 Android 的 `PendingIntent` 同语义。
 *
 * ## 前台横幅
 *
 * iOS 默认前台不弹横幅，必须实现 `userNotificationCenter(_:willPresent:withCompletionHandler:)`
 * 并回 `banner | sound`。那个代理挂在 [IosNotificationPresenter] 上，是**进程级强引用**：
 * `UNUserNotificationCenter.delegate` 是 **weak**，写成局部对象会被 ObjC 回收，
 * 回调一次都不会来（与 `WebViewController.ios.kt` 的 `navigationDelegate` 同一个坑）。
 * 为了让它从 App 一起来就在位，[edu.jxslu.schedule.IosServices] 里这个提醒是**非 lazy** 成员。
 *
 * ## 本机没编译过（Windows 无 Kotlin/Native），符号按风险排序
 *
 * | 符号 | 用法 | 不确定点 / 万一不对怎么改 |
 * |---|---|---|
 * | `UNAuthorizationOptionAlert or …Sound or …Badge` | 请求授权 | **最可能编不过的一处**。这三个在 K/N 里是 `UNAuthorizationOptions`（`ULong` 位掩码），`or` 是 `ULong` 的中缀。若报 unresolved / 类型不匹配，改成 `UNAuthorizationOptionAlert.toULong() or …` 或直接写 `7uL`（alert=4, sound=2, badge=1） |
 * | `content.setTitle/setBody/setSound` | 填通知内容 | `UNMutableNotificationContent` 把父类的只读属性重声明成可写，K/N 里属性可能是 `var title` 而不是方法。报 unresolved 就改成 `content.title = …` 赋值 |
 * | `UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(秒, false)` | 相对时间触发器 | `+triggerWithTimeInterval:repeats:` 的 K/N 工厂名与参数标签 |
 * | `getPendingNotificationRequestsWithCompletionHandler` / `getDeliveredNotificationsWithCompletionHandler` | 枚举待发 / 已投递 | 回调参数的具型可能是 `List<*>`（同 `FilePickers.ios.kt` 的 `didPickDocumentsAtURLs`），所以这里按 `List<*>` 收再逐个 `as?` |
 * | `@ObjCSignatureOverride` | 未使用 | 本协议只有一个重名家族（`userNotificationCenter(_:willPresent:…)`），参数个数与类型都不同，不构成 Kotlin 同签名冲突。真报 Conflicting overloads 再补 |
 */
class IosUjingReminder : UjingReminder {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    /**
     * 最近一次 `schedule` 的「订单 + 落点」。
     *
     * 授权回调可能在这次调用之后几十秒才回来（用户在系统弹窗上停着），回来时要按**最新的**
     * 那个落点排，而不是发起请求时的那一个。同一时刻只有一笔 U净 在案订单，所以一个槽位够。
     */
    private var latest: Pair<String, Long>? = null

    /** 授权请求在途：系统弹窗只该出现一次，轮询每 15 秒一次的 `schedule` 不能反复触发。 */
    private var requesting = false

    init {
        // 见文件头「前台横幅」：delegate 是 weak，握在这个长命对象手里的只有那一份进程级单例
        center.delegate = IosNotificationPresenter
    }

    override fun schedule(orderId: String, triggerAtMillis: Long) {
        latest = orderId to triggerAtMillis
        when (IosNotificationPermission.state.value) {
            IosNotificationPermissionState.Allowed ->
                postCompleted(orderId, secondsUntil(triggerAtMillis))

            // 权限被拒：静默降级成「不排提醒」（不抛、不反复追问、不影响订单卡与轮询）
            IosNotificationPermissionState.Denied -> Unit

            IosNotificationPermissionState.Unknown -> requestAuthorization()
        }
    }

    override fun cancel() {
        // 清掉「还有一单要排」的记忆：授权回调晚于撤单回来时，不能再把提醒排上去
        latest = null
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val ids = requests.pendingUjingIdentifiers()
            if (ids.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(ids)
        }
    }

    override fun revoke() {
        cancel()
        // 已投递的那条不在待发队列里，要单独清：autoCancel 只在用户点通知本身时生效，
        // 从订单卡「知道了」/ 今日页「我已取走」收摊时，通知栏里那条还杵着（同 Android 的 revoke）。
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.deliveredUjingIdentifiers()
            if (ids.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(ids)
        }
    }

    /**
     * 授权回调。**拒绝不是异常**：`granted = false` 与请求本身失败（[NSError]）都按
     * 「这次进程里不排提醒」处理——排不了提醒不该让下单链报错。
     */
    private fun requestAuthorization() {
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
                // 用户在弹窗上停留期间落点可能已经换过（轮询还在跑），按最新那份排
                latest?.let { (orderId, triggerAtMillis) ->
                    postCompleted(orderId, secondsUntil(triggerAtMillis))
                }
            } else {
                IosNotificationPermission.mark(IosNotificationPermissionState.Denied)
            }
        }
    }

    /** 排一条到点通知；同一订单号后一次覆盖前一次（标识符唯一）。 */
    private fun postCompleted(orderId: String, seconds: Double) {
        val content = UNMutableNotificationContent()
        content.setTitle(TITLE)
        content.setBody(BODY)
        content.setSound(UNNotificationSound.defaultSound())
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, false)
        val request = UNNotificationRequest.requestWithIdentifier(
            UJING_ID_PREFIX + orderId,
            content,
            trigger,
        )
        // 回执（NSError）只可能是标识符重复或内容非法，两者都是我们的编程错误；
        // 提醒不发出去也不该让调用方崩，所以不做回调、只由系统日志留痕。
        center.addNotificationRequest(request, null)
    }

    private companion object {
        const val TAG = "IosUjingReminder"

        /** 与 Android 侧同一条标题（`UjingDoneReminder.postNotification`）。 */
        const val TITLE = "衣服洗好了"

        /**
         * 正文不带设备名：`schedule(orderId, triggerAtMillis)` 这条缝没有设备名，
         * iOS 到点又不执行代码（查不到订单）。「预计」是必要的限定——兜底落点常早于真实结束。
         */
        const val BODY = "洗衣预计已结束，记得去取衣服。"
    }
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

/** 待发队列里的 U净 标识符。入参是可空的 `List<*>?`（ObjC 的 NSArray 没做非空标注，2026-11-25 CI 实测）。 */
private fun List<*>?.pendingUjingIdentifiers(): List<String> =
    orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }
        .filter { it.startsWith(UJING_ID_PREFIX) }

/** 已投递队列里的 U净 标识符（同一个请求标识，挂在 `UNNotification.request` 上）。入参同上，可空。 */
private fun List<*>?.deliveredUjingIdentifiers(): List<String> =
    orEmpty().mapNotNull { (it as? UNNotification)?.request?.identifier }
        .filter { it.startsWith(UJING_ID_PREFIX) }

/**
 * 通知标识符前缀：`ujing.<orderId>`。
 *
 * 稳定且**可枚举**是撤销的前提——`cancel()` / `revoke()` 拿不到订单号（接口形参与 Android
 * 的 `AlarmManager` 一致，只有「撤当前这一单」的语义），所以按这个前缀扫两个队列，
 * 而不是靠内存里记着排过哪几个（进程被杀后那点记忆就没了）。
 */
private const val UJING_ID_PREFIX = "ujing."

/**
 * 前台横幅代理。**必须是进程级强引用**：`UNUserNotificationCenter.delegate` 是 weak，
 * 局部对象会被回收，`willPresentNotification` 一次都不会来（iOS 默认前台静默）。
 *
 * 只实现这一个方法：负责「App 开着时也弹横幅并响一声」。点通知进入 App 的落点这一版不做
 * （通知没有带 `userInfo`，点开就是回到 App，没有要跳的页面）。
 */
private object IosNotificationPresenter : NSObject(), UNUserNotificationCenterDelegateProtocol {

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

/** 权限结论的三态（进程内）。 */
enum class IosNotificationPermissionState {

    /** 还没向系统请求过，或请求还在等用户回答：下一次 `schedule` 会发一次请求。 */
    Unknown,

    /** 已授权：`schedule` 会真的排一条。 */
    Allowed,

    /** 用户拒绝 / 通知在系统里被关掉：`schedule` 静默不排，也不反复追问。 */
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
     * 文案写在这里而不是页面里：只有本端知道「拒了之后 `schedule` 是 no-op」这件事，
     * 页面拿不到第二个信息源。
     */
    val hint: String?
        get() = when (_state.value) {
            IosNotificationPermissionState.Denied ->
                "通知未开启：洗衣完成时不会提醒，可在 设置 › 通知 › JUWPHub 打开"

            else -> null
        }

    internal fun mark(state: IosNotificationPermissionState) {
        _state.value = state
    }
}
