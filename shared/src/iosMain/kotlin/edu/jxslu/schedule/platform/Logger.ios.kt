package edu.jxslu.schedule.platform

import platform.Foundation.NSLog

/**
 * [platformLog] 的 iOS 实现：控制台走 `NSLog`，同时**落一份盘**（B1，2026-11-25）。
 *
 * iOS 没有「按级别分流」的等价物：`os_log` 的分级要用 `Logger`（Swift 的 `os.Logger`）
 * 或 C 的 `os_log_with_type`，从 Kotlin/Native 调要过一遍 `os_log` 的 C 接口。用 `NSLog`
 * 是**够用且零胶水**的选择——输出进 Xcode 控制台与 Console.app，级别靠文本前缀区分。
 * 真要接 os_log 分级，改这一个文件即可，调用点不受影响。
 *
 * ## 为什么不再只是 `NSLog`（2026-11-25）
 *
 * `NSLog` 只在连着 Xcode / Console.app 时看得见。2026-11-25 那次「安装后一开就闪退」
 * 就是因为这一点只能靠猜：用户装的是自己打的包，闪退之后什么也没留下。现在同一条日志
 * 经 [IosLogFile] 追加进沙箱 `appDataDir()/logs/app.log`（256 KB × 3 滚动），
 * 「设置 → 权限与存储 → 导出诊断日志」能把它整包交出去。
 *
 * 落盘失败**不影响**这一条日志的控制台输出，也不影响调用方（[IosLogFile.append] 内部吞异常）。
 * 脱敏在落盘那一侧做（`YktDiagnostics` 的键名清单），所以这里拿到的仍是原文——
 * 控制台那份是开发者看的，本来就在 Xcode 里。
 *
 * 编译状态：`NSLog` 那半已被 `.github/workflows/ios-compile.yml` 在 macOS 上编译验证
 * （2026-11-24）；落盘那半是新写的，本机（Windows）编不了 iosMain，见任务报告。
 *
 * **不要改回 `NSLog("%@: %@", a, b)` 那种带格式串的写法**：那要把 Kotlin `String` 塞进
 * ObjC 的 vararg，靠桥接规则碰运气；这里先在 Kotlin 侧拼成一个串再传，零桥接面。
 * 也正因为它被当格式串用，`%` 必须先转义成 `%%`——日志里出现一个百分号（URL 编码、
 * 进度文案）就会让 `NSLog` 去解析一个不存在的占位符。
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
    NSLog("$prefix/$tag: $detail".replace("%", "%%"))
    IosLogFile.append(prefix, tag, detail)
}
