package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.domain.EbikeFreeRide

/**
 * 免费时长提醒的**排程计划**（iOS 用：本地通知到点由系统投递，不需要、也没有「到点回调」）。
 *
 * ## 为什么单独抽出来
 *
 * 两件容易写歪的事都在这里、都只写一次：
 *
 * 1. **标识符前缀** `freeride.`——它是撤销的唯一依据（`IosLocalNotifications.removePending`
 *    按前缀扫待发队列），必须与通知四族（`class.` / `homework.` / `ujing.` / `score.`）分开；
 * 2. **时间窗**——只排「还没到点」的那一条。触发时刻已过的提醒要么已经弹过（再排一次会让它
 *    立刻又弹一条），要么这一轮根本没走到（例如设置里刚打开开关时提前量已经过去），
 *    两种情况都不该补一条迟到通知。Android 有「迟到窗口内补发」的口径（闹钟被 ROM 推迟），
 *    iOS 没有这个场景：系统到点就投递。
 *
 * 纯函数 + 纯数据，两端都能在 JVM 单测里钉住（Android 侧目前不消费它，仍然走
 * `EbikeFreeRideReminder` 那套闹钟）。
 */
object EbikeFreeRideNotifications {

    /** 标识符前缀：`freeride.`（见类 KDoc 第 1 条）。 */
    const val ID_PREFIX = "freeride."

    /** 提前量提醒。 */
    const val LEAD_ID = ID_PREFIX + "lead"

    /** 免费结束提醒。 */
    const val END_ID = ID_PREFIX + "end"

    /** 一条待排的通知。 */
    data class Planned(
        val identifier: String,
        val title: String,
        val body: String,
        val triggerAtMillis: Long,
    )

    /**
     * 这一轮还剩哪几条要排（按触发时刻升序，已过点的不在里面）。
     *
     * 提前量那条的正文按**触发那一刻**的剩余时间算（[EbikeFreeRide.leadText] 传
     * `triggerAtMillis` 当「现在」）：到点弹出来正好是同一个数，不会写成「还剩 3 分钟」而实际只剩 40 秒。
     *
     * [startAtMillis] ≤ 0（没有在案计时）时返回空表。
     */
    fun plan(startAtMillis: Long, leadMinutes: Int, nowMillis: Long): List<Planned> {
        if (startAtMillis <= 0L) return emptyList()
        val leadAt = EbikeFreeRide.leadReminderAt(startAtMillis, leadMinutes)
        val endAt = EbikeFreeRide.endReminderAt(startAtMillis)
        val planned = mutableListOf<Planned>()
        if (leadAt > nowMillis) {
            planned += Planned(
                identifier = LEAD_ID,
                title = EbikeFreeRide.LEAD_TITLE,
                body = EbikeFreeRide.leadText(startAtMillis, leadAt),
                triggerAtMillis = leadAt,
            )
        }
        if (endAt > nowMillis) {
            planned += Planned(
                identifier = END_ID,
                title = EbikeFreeRide.END_TITLE,
                body = EbikeFreeRide.END_TEXT,
                triggerAtMillis = endAt,
            )
        }
        return planned
    }
}
