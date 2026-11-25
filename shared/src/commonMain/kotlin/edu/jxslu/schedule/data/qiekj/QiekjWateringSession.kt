package edu.jxslu.schedule.data.qiekj

import kotlinx.serialization.Serializable

/**
 * 一次「正在出水」的本机会话（2026-10-21）。
 *
 * 胖乖官方**没有**可靠的跨会话「进行中」状态：`sync` 的 `workStatus` / `status`
 * 会黏住十几分钟（life-power.md 实测定论），只有设备回报 `status==5 && amount>0`
 * 才是真结束。所以本地只记「这次开阀的时刻与设备」，作为今日页服务卡 / 侧栏高亮的
 * 展示依据——真正的结算仍以流程回报的账单为准，本记录不参与任何金额判断。
 *
 * ## 为什么这个类型在共享层（iOS 移植 V2，2026-11-25）
 *
 * 它原先和 `QiekjWateringStore`（`SharedPreferences` + `StateFlow`）一起躺在 `:app`：
 * 当时唯一的读者是 `:app` 的今日页服务卡与侧栏。今日页服务卡搬进 `shared/commonMain`
 * 之后，卡片本体（开水行 / 完成态 / 进度条）要在共享层读这几个字段，而类型在 `:app`
 * 就是「共享层 import 宿主模块」——编不过。
 *
 * **只搬数据类，不搬 store**：store 的介质是 Android 的 `SharedPreferences`，换介质等于
 * 让老用户设备上正在出水的那条记录读不出来；两端各留自己的介质（Android 仍是
 * `QiekjWateringStore`，iOS 是 `appDataDir()` 下的 JSON），读侧收成
 * [QiekjWateringSessions.watering] 一条缝。包名保持 `edu.jxslu.schedule.data.qiekj`
 * 不变，`:app` 那些 import 一个字都不用改。
 */
@Serializable
data class QiekjWateringSession(
    /** 服务端确认出水（流程首次进入 Working）的时刻。 */
    val startedAtMillis: Long,
    val deviceName: String,
    val goodsId: String?,
) {
    /**
     * 超过这个时长仍没结算 = 流程已经不在了（进程被杀 / 页面被关导致协程取消）。
     * 服务端 165 秒会自动关阀，再留一段结算余量；此后本地记录视为过期，
     * 读的时候直接清掉——不给用户留一条永不消失的「出水中」。
     */
    fun isStale(nowMillis: Long): Boolean = nowMillis - startedAtMillis >= STALE_MILLIS

    /**
     * 是否已过服务端自动关阀时刻（[AUTO_CLOSE_SECONDS]，2026-11-06）。
     *
     * 到点后出水事实已经结束（结算可能仍在跑，账单由流程回报），**展示层不该再写
     * 「出水中」**。此前展示只认 [isStale]（5 分钟），流程不在本窗口时（页面被关 /
     * 协程被取消 / VM 重建）没人清 store，水停了卡片还挂到 5 分钟——用户报的
     * 「开水后今日页仍显示出水中」就是这个。数据层的 5 分钟过期仍作兜底。
     */
    fun isPastAutoClose(nowMillis: Long): Boolean =
        nowMillis - startedAtMillis >= AUTO_CLOSE_SECONDS * 1_000L

    /**
     * 出水进度 0..1（2026-10-22，今日页服务卡用）：按服务端 [AUTO_CLOSE_SECONDS] 秒
     * 自动关阀兜底，超出按 1 封顶。与开水页 `WaterViewModel.AUTO_SETTLE_SECONDS` 同一口径。
     */
    fun progressFraction(nowMillis: Long): Float {
        val total = AUTO_CLOSE_SECONDS * 1000L
        val elapsed = (nowMillis - startedAtMillis).coerceAtLeast(0L)
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }

    companion object {
        /** 服务端出水自动关阀的兜底秒数（`WaterViewModel` 的自动结算同一口径）。 */
        const val AUTO_CLOSE_SECONDS = 165

        /** 165 秒服务端超时关阀 + 结算/轮询余量。 */
        const val STALE_MILLIS: Long = 5 * 60 * 1000L
    }
}
