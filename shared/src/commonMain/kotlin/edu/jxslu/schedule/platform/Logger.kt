package edu.jxslu.schedule.platform

/**
 * 跨平台日志门面（iOS 移植 P1，2026-11-24）。
 *
 * `android.util.Log` 是 Android 专有，iOS 上没有；而全仓有 179 处 `Log.*` 调用散在 52 个文件里。
 * 这里先立缝，**按需迁移**：文件搬进 `:shared` 时顺手换成 [Logger]，没搬的继续用
 * `android.util.Log`——不做一次性全量扫，那是纯churn。
 *
 * ## 级别选择的既有口径（保持原样）
 *
 * 全仓现状是 `w` 占绝大多数（123/179），`d` 26、`i` 26、`e` 4。别按「教科书级别」重排，
 * 换实现就换实现，级别照抄原代码。
 *
 * ## 为什么不在这里做「release 静音」
 *
 * 原计划（P-1 第 7 项）想让 Logger 在 release 整体静音。但**先没做**：现状的日志大多在
 * `BuildConfig.DEBUG` 分支里（17 处），而且在 release 里静音会让 `Log.w` 记的那些
 * 「某源不可达，换下一个」的线索引错都消失，出问题时反而更难查。要做的话得先决定
 * 哪些级别在 release 保留，那时再改 `platformLog` 的 actual——**不需要动任何调用点**，
 * 这是这层缝的价值。
 */
object Logger {

    fun d(tag: String, message: String) = platformLog(LogLevel.Debug, tag, message, null)

    fun d(tag: String, message: String, throwable: Throwable?) =
        platformLog(LogLevel.Debug, tag, message, throwable)

    fun i(tag: String, message: String) = platformLog(LogLevel.Info, tag, message, null)

    fun i(tag: String, message: String, throwable: Throwable?) =
        platformLog(LogLevel.Info, tag, message, throwable)

    fun w(tag: String, message: String) = platformLog(LogLevel.Warn, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable?) =
        platformLog(LogLevel.Warn, tag, message, throwable)

    fun e(tag: String, message: String) = platformLog(LogLevel.Error, tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable?) =
        platformLog(LogLevel.Error, tag, message, throwable)
}

/** 与 `android.util.Log` 的四个常用级别一一对应。 */
internal enum class LogLevel { Debug, Info, Warn, Error }

/**
 * 平台落点：Android 走 `android.util.Log`，iOS 走 `NSLog`。
 *
 * `throwable` 只取类型与 message（不打印栈）：Android 侧原本就是 `Log.w(tag, msg, e)`
 * 打完整栈，而 iOS 的 `NSLog` 打 Kotlin 栈没有意义（栈是 Kotlin/Native 的符号）。
 * 真要排查时两边都该看 crash 上报，不该靠日志行。
 */
internal expect fun platformLog(
    level: LogLevel,
    tag: String,
    message: String,
    throwable: Throwable?,
)
