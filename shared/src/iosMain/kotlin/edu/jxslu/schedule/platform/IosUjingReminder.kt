package edu.jxslu.schedule.platform

/**
 * 洗衣完成提醒的 iOS 实现（N1，2026-11-25 晚；N2 起复用共用小件）。
 *
 * 三件事与 [UjingReminder] 一一对应，语义照 `:app` 的 `UjingDoneReminder`：
 * `schedule` 排一条到点通知，`cancel` 撤待发的那条，`revoke` 连已投递的也撤。
 * 区别只有两条，都是 iOS 的机制决定的，不是省事：
 *
 * 1. **到点不能再查订单**。Android 的闹钟落点会先查一次订单详情，只有本次核对看到转入 50
 *    才发通知，还在跑就按新剩余重排；iOS 的本地通知到点由系统直接投递，**没有任何代码被执行**
 *    （后台唤起要靠 `BGTaskScheduler`，系统不保证时刻）。所以这里到点就发，正文写「预计已结束」，
 *    兜底闹钟（按模式总时长排的那枚，常早于真实结束）响得早时不会自我纠正。
 * 2. **一次只挂一条**。标识符是 [UJING_ID_PREFIX] + 订单号，同一单重排是覆盖；
 *    撤的时候按前缀把待发与已投递两个队列一起扫，不依赖内存里记着哪几个。
 *
 * 权限、按前缀扫队列、前台横幅代理三件事在 [IosLocalNotifications]（三类提醒共用）；
 * 被拒时的降级口径也写在那边——`schedule` 变 no-op，订单卡与 15 秒轮询照常工作。
 *
 * **重置落点是常态**：`UjingViewModel` 每次轮询（15 秒）都会带着新剩余时间调一次 `schedule`，
 * 同标识符后一次覆盖前一次，与 Android 的 `PendingIntent` 同语义。
 */
class IosUjingReminder : UjingReminder {

    /**
     * 最近一次 `schedule` 的「订单 + 落点」。
     *
     * 授权回调可能在这次调用之后几十秒才回来（用户在系统弹窗上停着），回来时要按**最新的**
     * 那个落点排，而不是发起请求时的那一个。同一时刻只有一笔 U净 在案订单，所以一个槽位够。
     */
    private var latest: Pair<String, Long>? = null

    override fun schedule(orderId: String, triggerAtMillis: Long) {
        latest = orderId to triggerAtMillis
        // 被拒：静默降级成「不排提醒」（不抛、不反复追问、不影响订单卡与轮询）；
        // 还没问过：请求一次，授权回来按**最新**那个落点补排
        IosLocalNotifications.ensureAuthorized { granted ->
            if (granted) latest?.let { (id, triggerAtMillis) -> post(id, triggerAtMillis) }
        }
    }

    override fun cancel() {
        // 清掉「还有一单要排」的记忆：授权回调晚于撤单回来时，不能再把提醒排上去
        latest = null
        IosLocalNotifications.removePending(UJING_ID_PREFIX)
    }

    override fun revoke() {
        cancel()
        IosLocalNotifications.removeDelivered(UJING_ID_PREFIX)
    }

    /** 排一条到点通知；同一订单号后一次覆盖前一次（标识符唯一）。 */
    private fun post(orderId: String, triggerAtMillis: Long) {
        IosLocalNotifications.schedule(
            identifier = UJING_ID_PREFIX + orderId,
            title = TITLE,
            body = BODY,
            triggerAtMillis = triggerAtMillis,
        )
    }

    private companion object {
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
 * 通知标识符前缀：`ujing.<orderId>`。
 *
 * 稳定且**可枚举**是撤销的前提——`cancel()` / `revoke()` 拿不到订单号（接口形参与 Android
 * 的 `AlarmManager` 一致，只有「撤当前这一单」的语义），所以按这个前缀扫两个队列，
 * 而不是靠内存里记着排过哪几个（进程被杀后那点记忆就没了）。
 */
private const val UJING_ID_PREFIX = "ujing."
