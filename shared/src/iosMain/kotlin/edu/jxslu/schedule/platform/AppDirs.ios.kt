package edu.jxslu.schedule.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

/**
 * iOS 侧的应用私有数据目录（iOS 移植 P2，2026-11-24）。
 *
 * ## 主路径
 *
 * 写法和 `data/local/JuwDatabase.ios.kt` 的 `databasePath()` 逐字一致：
 * `NSFileManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, create = true)`。
 *
 * **如实记一句实测结论：这个写法在真机上把路径落到了 `Documents/`，不是 Application Support。**
 * 同一个坑 `JuwDatabase.ios.kt` 已经踩过一遍，那里也是这条表达式。两种目录对 DataStore 都能用
 * （都是 App 沙箱内、都可写、卸载即清），所以主路径**不改写法**——改就得先弄清楚
 * iOS 侧到底为什么把 Application Support 解析成 Documents，那要真机 + 文件 App 才验得了。
 *
 * 附带后果（用户可见，别当成 bug 报）：`Documents/` 会进 iCloud 备份，也会出现在「文件」App 里。
 *
 * ## 2026-11-26：这里原来会**抛异常**，现在不会
 *
 * 上一版是 `requireNotNull(dir?.path)`——`URLForDirectory` 返回 null 就抛
 * `IllegalArgumentException`。后果不是「某处报错」，而是**整条落盘链静默失效**：
 * `IosLogFile.append` 的 `catch (_: Throwable)` 正好把这一下吞掉，于是
 * `Documents/logs/` 从来没被创建过、`app.log` 一份都没有，用户在「文件」App 里翻到的是空目录。
 * 这也让 2026-11-25/26 那两次「装上就闪退」彻底没有线索：唯一能落盘的通道自己先断了。
 *
 * 现在改成**三级降级、永不抛**：
 *
 * | 顺序 | 目录 | 为什么 |
 * |---|---|---|
 * | 1 | `URLForDirectory(ApplicationSupport, create = true)` | 保持原路径不变，已装的用户数据不搬家 |
 * | 2 | `NSSearchPathForDirectoriesInDomains(Document, …)` | 第 1 步在沙箱里失败时的等价私有目录；`Documents/` 由 `UIFileSharingEnabled` 暴露，日志因此能被用户自己取出来 |
 * | 3 | `NSTemporaryDirectory()` | 最后的兜底。写在临时目录里的日志活不过重启，但「有东西可读」好过「什么都没有」 |
 *
 * 三级都拿不到才用 `fallbackLogDir()` 那个常量（见下），**不抛**。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun appDataDir(): String {
    // 1. 原路径：已装机器上的偏好 / 数据库 / 快照都在这儿，能拿到就必须还回它
    val support = runCatching {
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )?.path
    }.getOrNull()
    if (!support.isNullOrBlank()) return support

    // 2. Documents：同一个沙箱，但不是 URLForDirectory 那条容易返回 null 的路
    val documents = runCatching {
        // 位置参数，不写具名参数：这是 ObjC 的 C 函数，Kotlin/Native 侧的参数名不一定叫
        // `directory` / `domainMask` / `expandTilde`，猜错就是编译错误，而本机编不了 iOS。
        NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String
    }.getOrNull()
    if (!documents.isNullOrBlank()) return documents

    // 3. 临时目录：活不过重启，但至少日志写得进去
    val temp = runCatching { NSTemporaryDirectory() }.getOrNull()
    if (!temp.isNullOrBlank()) return temp

    // 三级全空：iOS 上几乎不可能发生。给一个确定的、不会二次抛错的相对路径，
    // 让调用方照常拼路径、照常失败——**不在这里抛**，见上面 KDoc 的 2026-11-26 那段。
    return fallbackLogDir()
}

/**
 * 三级降级全失败时的兜底目录名。
 *
 * 刻意用相对路径：写在当前工作目录下（沙箱内），不猜绝对路径，也不再触发一次
 * `NSFileManager` 调用。这一档存在的意义只是「函数有返回值」，不是「日志一定成功」。
 */
private fun fallbackLogDir(): String = "."
