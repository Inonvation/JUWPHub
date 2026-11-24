package edu.jxslu.schedule.domain

/**
 * 远程用车提醒（2026-10-24 用户要求）：每 [INTERVAL] 次远程开启弹一次提醒，
 * **只提醒、不阻断**——用车流程不因此多一步确认。
 *
 * 计数口径（"远程扫车" = 非扫车身码）只有两条：
 * - 账号方式的「开启」（`KvcxAction.UNLOCK` 两段式建单，车被占住）：地图选车 / 车号面板 /
 *   「重新使用该车」都算；扫车身码（`DIRECT_UNLOCK`，人车同在）不算，
 *   重试开锁 / 解锁继续骑（非新车）不算；
 * - 小程序方式的「打开微信扫一扫」跳转（扫的是 App 按车号生成的码）。
 *
 * 纯 JVM，`EbikeRemoteRideTest` 钉住；弹窗文案唯一出处在这里，页面只排版别手抄。
 */
object EbikeRemoteRide {

    /** 提醒间隔（次数）：每远程开启这么多次提醒一次。 */
    const val INTERVAL = 20

    /**
     * 本次计数后要不要提醒：只在与 [INTERVAL] 的正整数倍上提醒。
     * 传入的是**自增之后**的值（20、40、… 触发；0 / 负数不触发）。
     */
    fun shouldRemind(countAfterIncrement: Int): Boolean =
        countAfterIncrement > 0 && countAfterIncrement % INTERVAL == 0

    /** 提醒弹窗标题。 */
    const val DIALOG_TITLE = "远程开启小提醒"

    /** 提醒弹窗正文（文案唯一出处）。 */
    fun dialogText(count: Int): String =
        "这是第 $count 次远程开启车辆。远程开启会把车提前占住，高峰期其他同学可能找不到车——" +
            "非紧急情况建议走到车边再开车，把机会留给有需要的同学。"
}
