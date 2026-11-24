package edu.jxslu.schedule.data.kqcx

/**
 * 「上一次进程退出时还有在案骑行」的落盘提示（2026-11-18）。
 *
 * 只服务一个判据：今日页「进行中服务」卡的**位置**。冷启动时
 * [edu.jxslu.schedule.ui.ebike.KvcxRideController.state] 里的 `ride` 只能等第一次
 * `queryUnderwayOrder` 回包才知道；在那之前位置判据若按「没有骑行」处理，卡片先排在
 * 作业卡之后，回包一到再挪到课程区之前——用户看到的就是「状态卡从今日页底部弹到顶部」。
 *
 * 所以控制器把「有骑行」这件事按进程落盘，下一次冷启动先拿它当位置提示：
 * 提示为真时服务卡条目从第一帧就留在顶部（行内容仍然等回包；没骑行时整条零高度、
 * 不可见）。第一次成功查询落地后提示即作废（`KvcxRideController.rideHint`）。
 *
 * 只存一枚布尔，不含车号 / 订单号；凭证仍在 `secure_kqcx` 里。
 *
 * iOS 移植 S12（2026-11-24）：**收成接口**。实现是 `:app` 的
 * `KvcxRideHintPrefsStore`（普通 SharedPreferences，与 `QzxyWateringStore` /
 * `UjingOrderStore` 同口径）；iOS 将来用 NSUserDefaults 写一份，共享层不关心。
 * 没有把整类搬过来，是因为它唯一的消费方 `KvcxRideController` 还在 `:app` 的
 * `ui/ebike`，搬过去也只是一层转调。
 */
interface KvcxRideHintStore {

    /** 上一次进程退出时是否还有在案骑行。 */
    val hadRide: Boolean

    /** 有骑行 → true、无骑行 → false。同值跳写由实现方负责（每次查询都会走到这里）。 */
    fun setHadRide(active: Boolean)
}
