package edu.jxslu.schedule.platform

import platform.Foundation.NSLog

/**
 * [platformLog] 的 iOS 实现：落到 `NSLog`，级别与 tag 折进同一条前缀。
 *
 * iOS 没有「按级别分流」的等价物：`os_log` 的分级要用 `Logger`（Swift 的 `os.Logger`）
 * 或 C 的 `os_log_with_type`，从 Kotlin/Native 调要过一遍 `os_log` 的 C 接口。用 `NSLog`
 * 是**够用且零胶水**的选择——输出进 Xcode 控制台与 Console.app，级别靠文本前缀区分。
 * 真要接 os_log 分级，改这一个文件即可，调用点不受影响。
 *
 * 本分支未编译验证（需要 macOS）。
 */
internal actual fun platformLog(
    level: LogLevel,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    val prefix = when (level) {
        LogLevel.Debug -> "D"
        LogLevel.Info -> "I"
        LogLevel.Warn -> "W"
        LogLevel.Error -> "E"
    }
    val detail = if (throwable == null) message else "$message (${throwable::class.simpleName}: ${throwable.message})"
    NSLog("%@/%@: %@", prefix, tag, detail)
}
