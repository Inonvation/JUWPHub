package edu.jxslu.schedule.ui.platform

/**
 * 「把课表写进系统日历」的结果（iOS 移植 M1，2026-11-25）。
 *
 * ## 为什么要单独一套类型
 *
 * 写入动作本身是平台专有的：Android 走 `CalendarContract` + 运行时权限，iOS 目前不支持。
 * 结果因此必须是一个**两端都能表达**的类型，页面才能照着它选图标与语气。
 * `:app` 的 `CalendarSyncer.CalendarSyncResult` 是 Android 侧的实现细节，Shared 层看不见它，
 * 那个类型原样保留，由宿主在回调里转成本类型。
 *
 * [Unsupported] 与 [NoPermission] 分开：前者是「这个平台永远做不到」，后者是「你能改授权」。
 * 合成一句提示会让人去设置里找一个根本不存在的开关。
 */
sealed interface CalendarSyncOutcome {

    /** 写入成功。[reminderMissing] = 有几条课程没买到「提前提醒」（日历账户不支持）。 */
    data class Synced(val count: Int, val reminderMissing: Int) : CalendarSyncOutcome

    /** 用户点了「删除课程日历」，删掉了 [count] 条。 */
    data class Deleted(val count: Int) : CalendarSyncOutcome

    /** 手机上没有任何可写的日历账户。 */
    data object NoCalendarAccount : CalendarSyncOutcome

    /** 运行时权限没给。 */
    data object NoPermission : CalendarSyncOutcome

    /** 本平台没有系统日历写入能力（iOS）。 */
    data object Unsupported : CalendarSyncOutcome

    /** 写入过程抛异常。 */
    data class Error(val message: String) : CalendarSyncOutcome
}
