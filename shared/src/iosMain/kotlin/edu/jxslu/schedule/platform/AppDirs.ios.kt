package edu.jxslu.schedule.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS 侧的应用私有数据目录（iOS 移植 P2，2026-11-24）。
 *
 * 写法和 `data/local/JuwDatabase.ios.kt` 的 `databasePath()` 逐字一致：
 * `NSFileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, create = true)`。
 *
 * **如实记一句实测结论：这个写法在真机上把路径落到了 `Documents/`，不是 Application Support。**
 * 同一个坑 `JuwDatabase.ios.kt` 已经踩过一遍，那里也是这条表达式。两种目录对 DataStore 都能用
 * （都是 App 沙箱内、都可写、卸载即清），所以这一版**不改写法**——改就得先弄清楚
 * iOS 侧到底为什么把 Application Support 解析成 Documents，那要真机 + 文件 App 才验得了，
 * 属于 P3 的事。这里只保证「两端各自稳定、路径能打印出来」。
 *
 * 附带后果（用户可见，别当成 bug 报）：`Documents/` 会进 iCloud 备份，也会出现在「文件」App 里。
 * 一个能从教务重新导入的偏好文件占不了多少配额，暂时接受。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun appDataDir(): String {
    val dir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(dir?.path) { "拿不到 iOS 的私有数据目录" }
}
