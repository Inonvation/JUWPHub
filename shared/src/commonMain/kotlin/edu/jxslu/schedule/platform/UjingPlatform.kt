package edu.jxslu.schedule.platform

/**
 * U净（美的校园洗衣）页面搬进共享层时要的三条平台缝（iOS 移植 S21，2026-11-25）。
 *
 * 页面与 ViewModel 原来各自直接摸 `UjingAlipay` / `UjingDoneReminder` / `BikeLocator`，
 * 那三样都是 Android 专有（支付宝 SDK、`AlarmManager` + 通知渠道、`LocationManager` 权限申请）。
 * 这里只留**页面真正需要的那几件事**，不把三个类的形状抄一遍。
 *
 * | 缝 | 为什么不能搬 | 谁实现 |
 * |---|---|---|
 * | [UjingPay] | `PayTask` 只能在 `Activity` 上拉起 | `:app` 宿主（`SubpageActivity`） |
 * | [UjingReminder] | `AlarmManager` + 通知渠道 | `AppServices.ujingReminder()`（`GraphServices`） |
 * | [UjingLocateOutcome] | 定位权限要 `ActivityResultLauncher` | `:app` 宿主（`SubpageActivity`） |
 *
 * **[UjingPay] 为什么是 ViewModel 构造参数而不是 `AppServices` 成员**：装配根只持有
 * `applicationContext`（见 `GraphServices` 的 KDoc），而支付宝必须拿 `Activity`；
 * 把 Activity 塞进装配根等于给一个长命对象挂短命引用。宿主本来就在组合期握着 Activity，
 * 由它造一个、用完即弃的支付缝更干净（口径同 `PowerBillViewModel(isVpnActive = …)`）。
 */

/** 支付宝支付的四种结局，取值与 `:app` 的 `UjingAlipay.Outcome` 一一对应。 */
enum class UjingPayOutcome {
    /** `resultStatus == 9000`：支付成功。 */
    Success,

    /** `8000`：结果确认中——钱可能已扣，必须查订单状态定论，绝不能按失败报。 */
    Pending,

    /** `6001`：用户取消（含锁屏 / 进程被杀），订单仍在待支付。 */
    Canceled,

    /** 其余状态码（5000 等确定性失败）：按失败报，详情给原文码。 */
    Failed,
}

/**
 * 一次支付的结局：[outcome] 是四态归一，[memo] 是给用户看的原文
 * （失败时是支付宝的 `memo` 或 `resultStatus=…`）。
 */
data class UjingPayResult(val outcome: UjingPayOutcome, val memo: String = "")

/**
 * 拉起支付宝并等结果。
 *
 * 实现方**自己负责切到后台线程**：Android 的 `PayTask.payV2` 是阻塞调用（内部建网络连接），
 * 共享层不替它指定调度器（原来这里写的是 `Dispatchers.IO`，那是 JVM 专有）。
 *
 * `null`（即调用方拿不到这个缝）= 本平台没有支付宝 SDK，订单卡的「去支付 / 重试支付」
 * 据此不渲染成可点按钮，下单链在创建订单**之前**就拦住——机器被锁 2 分钟却没法付是最坏的形状。
 */
fun interface UjingPay {

    suspend fun pay(orderInfo: String): UjingPayResult
}

/**
 * 洗衣完成提醒（闹钟 + 系统通知）。
 *
 * 三个动作与 `:app` 的 `UjingDoneReminder` 逐字对应，语义见那边的 KDoc：
 * - [schedule]：洗涤中按「快照时刻 + 剩余」排精确闹钟，已支付未运行按模式总时长排兜底闹钟；
 * - [cancel]：终结态 / 退出登录时就地撤闹钟；
 * - [revoke]：用户确认取衣（或「知道了」）时**连已发出的通知与免扰队列挂起条一起撤**。
 *
 * `AppServices.ujingReminder()` 返回 `null` = 本平台没有闹钟与通知渠道（iOS 待接），
 * 此时排 / 撤提醒整体跳过——订单卡与轮询照常工作，只是没有「洗好提醒」。
 */
interface UjingReminder {

    fun schedule(orderId: String, triggerAtMillis: Long)

    fun cancel()

    fun revoke()
}

/**
 * 「取一次定位」的结局（U净「添加洗衣房」用）。
 *
 * 只有两态：拿到坐标，或给一句能直接上屏的失败文案。不搬 `ui/ebike` 的
 * `LocateResult`——那个带精度圈、坐标来源、失败分档四件套，全是单车地图页要的，
 * U净 只把经纬度喂给 `stores/near`。
 */
sealed interface UjingLocateOutcome {

    /** 成功。[lat] / [lng] 已是 GCJ-02（`stores/near` 直接吃）。 */
    data class Ok(val lat: Double, val lng: Double) : UjingLocateOutcome

    /** 失败，[message] 直接进提示条（无权限 / 定位关闭 / 等不到读数）。 */
    data class Failed(val message: String) : UjingLocateOutcome
}
