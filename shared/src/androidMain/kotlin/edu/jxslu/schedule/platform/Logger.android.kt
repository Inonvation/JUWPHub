package edu.jxslu.schedule.platform

import android.util.Log

/**
 * [platformLog] 的 Android 实现：直接落到 `android.util.Log`，与迁移前逐字等价
 * （含 throwable —— `Log.w(tag, msg, e)` 照旧打完整栈）。
 */
internal actual fun platformLog(
    level: LogLevel,
    tag: String,
    message: String,
    throwable: Throwable?,
) {
    when (level) {
        LogLevel.Debug -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
        LogLevel.Info -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
        LogLevel.Warn -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        LogLevel.Error -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
