@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.me.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.interop.LocalUIViewController
import edu.jxslu.schedule.data.local.juwDatabasePath
import edu.jxslu.schedule.domain.SizeText
import edu.jxslu.schedule.platform.IosLogFile
import edu.jxslu.schedule.platform.platformFileSystem
import edu.jxslu.schedule.ui.common.IosShareTarget
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/**
 * 「权限与存储」在 iOS 侧的那一半（B3，2026-11-25）。
 *
 * 页框与两行动作在共享层（`ui/me/hub/PermissionsStorageScreen.kt`）；这里只填 iOS 特有的三件：
 * **存储占用**（数据库 / 缓存 / 日志三行）、**清缓存**（只动缓存目录）、
 * **导出诊断日志**（把 `logs/` 打成一整段文本交给系统分享）。
 *
 * ## 权限块为什么不渲染
 *
 * 共享页的 `permissionSections` 传 `null`。iOS 的授权模型与 Android 不是同一件事：
 * 电池优化白名单、自启动 / 锁后台在 iOS **不存在**；通知是
 * `UNUserNotificationCenter.requestAuthorization`（洗衣 / 作业截止 / 成绩三类提醒共用
 * `platform/IosLocalNotifications` 的那一次请求），日历 / 定位 / 相册各自一套 `Info.plist` 用途描述
 * 与 `PHPhotoLibrary` 授权。按 `design/ios-port.md` §11.2 ④，平台没有的入口整块不渲染，
 * 比造一行「点了没反应」好。
 *
 * ## 三个目录各是什么
 *
 * | 行 | 路径 | 为什么是它 |
 * |---|---|---|
 * | 数据库 | `appDataDir()/juw_schedule.db`（含 `-wal` / `-shm`） | Room 的唯一库，`juwDatabasePath()` 就是它（`data/local/JuwDatabase.ios.kt`） |
 * | 缓存 | 沙箱 `Library/Caches`（`NSCachesDirectory`） | 系统**指定的**可回收目录：`WKWebView` 与 `NSURLSession` 的磁盘缓存在这里，系统内存紧张时自己也会清它 |
 * | 日志 | `appDataDir()/logs` | `platform/IosLogFile` 的落点，三个文件共 ≤ 768 KB |
 *
 * **清缓存只碰第二行**：数据库与日志一个字都不动（任务口径），凭证在 Keychain、
 * 教务会话在 WebKit 的 website data store，也都不在这个目录里 —— 所以清完**不需要重新登录**。
 *
 * ## 哪一项只能真机验
 *
 * 三件事都要真机/模拟器：目录体积随系统写入变化（模拟器上 `Library/Caches` 常常是空的）、
 * 分享面板必须由真实 VC present（`IosShareTarget` 的 popover 锚点也只在 iPad 上才看得出问题）。
 * 本机是 Windows，编不了 iosMain，连编译都留到 macOS 上第一次跑。
 */
@Composable
fun IosPermissionsStorageScreen(onBack: () -> Unit) {
    // present 分享面板要有真实的宿主 VC。**不猜 `UIApplication.keyWindow`**（多场景之后
    // 语义不成立，理由与 `ShareIntents.ios.kt` / `FilePickers.ios.kt` 逐字相同），
    // 从组合里取——CMP 1.7.3 在 `androidx.compose.ui.interop`，与选文件那条缝同一处。
    val host = LocalUIViewController.current
    val storage = remember(host) { iosStorageDiagnostics(host) }
    PermissionsStorageScreen(onBack = onBack, storage = storage, permissionSections = null)
}

/**
 * 「存储与诊断」的 iOS 实现。
 *
 * [host] 是 present 分享面板的宿主 VC（由 [IosPermissionsStorageScreen] 从组合里取）。
 * 体积统计与清缓存都切到 [Dispatchers.Default]：`Library/Caches` 里可能有上千个文件，
 * 别在主线程上走一遍目录树。
 */
internal fun iosStorageDiagnostics(host: UIViewController): StorageDiagnostics = StorageDiagnostics(
    cacheScope = "系统缓存目录里的网页与网络缓存",
    measureCacheBytes = { withContext(Dispatchers.Default) { cacheBytes() } },
    clearCache = { withContext(Dispatchers.Default) { clearCacheDir() } },
    confirmMessage = { bytes ->
        buildString {
            append("将删除系统缓存目录里的网页与网络缓存")
            bytes?.let { append("（共 ").append(SizeText.format(it)).append("）") }
            append("。不影响课表成绩、笔记作业、账号密码，也不删数据库与诊断日志；")
            append("网页缓存会在下次打开教务页面时自动重建。")
        }
    },
    storageLines = { withContext(Dispatchers.Default) { storageLines() } },
    diagnostics = DiagnosticsExport(
        // 条数只数文件，不读内容：进页就要显示，读盘留给导出
        count = { IosLogFile.files().size },
        subtitle = { count ->
            if (count > 0) "$count 个日志文件 · 经系统分享发送" else "没有日志文件"
        },
        export = { exportLogs(host) },
    ),
)

/** 三行存储占用：数据库 / 缓存 / 日志。体积口径与「导出」那一行读的是同一批文件。 */
private fun storageLines(): List<StorageLine> = listOf(
    StorageLine(
        title = "数据库",
        detail = "课表 · 成绩 · 笔记等本地表",
        size = SizeText.format(databaseBytes()),
        icon = AppIcons.Database,
    ),
    StorageLine(
        title = "缓存",
        detail = "网页与网络缓存，清掉会自动重建",
        size = SizeText.format(cacheBytes()),
        icon = AppIcons.File02,
    ),
    StorageLine(
        title = "日志",
        detail = "崩溃与运行日志，导出后仍保留",
        size = SizeText.format(IosLogFile.totalBytes()),
        icon = AppIcons.File01,
    ),
)

/**
 * 把 `logs/` 打成一段文本交给系统分享；`false` = 没发出去。
 *
 * 读盘在 [Dispatchers.Default]，`present` 回到调用方的上下文（主线程）——`UIActivityViewController`
 * 只能在主线程弹。分享面板是异步的：这里返回 `true` 只代表**面板弹出来了**，
 * 用户点没点「存储到文件」不进我们的账（与 `ShareIntents` 的返回值口径一致）。
 *
 * 直接 new 一个 [IosShareTarget] 而不去写全局的 `ShareSettings.target`：那条缝留给将来的
 * Swift 壳一次性注入，这里已经拿得到宿主 VC，改全局状态反而会影响别的页面的分享行为。
 */
private suspend fun exportLogs(host: UIViewController): Boolean {
    val text = withContext(Dispatchers.Default) { runCatching { IosLogFile.exportText() }.getOrNull() }
        ?: return false
    if (text.isBlank()) return false
    return runCatching { IosShareTarget { host }.shareText(text, "诊断日志") }.getOrDefault(false)
}

/** 缓存目录（沙箱 `Library/Caches`）；取不到回 null，调用方按「没有缓存」处理。 */
private fun cachesDir(): Path? = runCatching {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        // 目录本来就该由系统建好；`create = true` 只是让「还没有 Caches」这种极端情况也能返回路径
        create = true,
        error = null,
    )
    url?.path?.toPath()
}.getOrNull()

private fun cacheBytes(): Long = cachesDir()?.let(::dirBytes) ?: 0L

/**
 * 清空缓存目录**里的内容**，保留目录本身（系统与 WebKit 都按路径找它，删掉反而要重建）。
 *
 * `true` = 走完了（个别文件删不掉也只记日志，不把「清了一半」报成失败——那两个状态
 * 在用户眼里都是「清过了」）。真出错（目录读不了）才回 `false`。
 */
private fun clearCacheDir(): Boolean {
    val dir = cachesDir() ?: return false
    val fs = platformFileSystem()
    if (!runCatching { fs.exists(dir) }.getOrDefault(false)) return true
    return runCatching {
        fs.list(dir).forEach { child ->
            // runCatching 逐项兜：WebKit 有时正开着某个缓存文件，删它失败不该拖住其余项
            runCatching { fs.deleteRecursively(child) }
        }
        true
    }.getOrDefault(false)
}

/** 数据库文件本体 + `-wal` / `-shm`（Room 预写日志与共享内存索引，开着库时都存在）。 */
private fun databaseBytes(): Long {
    val base = runCatching { juwDatabasePath() }.getOrNull() ?: return 0L
    return listOf("", "-wal", "-shm", "-journal").sumOf { suffix -> fileBytes((base + suffix).toPath()) }
}

private fun fileBytes(path: Path): Long = runCatching {
    val metadata = platformFileSystem().metadataOrNull(path) ?: return 0L
    if (metadata.isRegularFile) metadata.size ?: 0L else 0L
}.getOrDefault(0L)

/** 目录总字节（不存在的目录回 0）。只看普通文件：目录项在 POSIX 上也有 4 KB 大小，算进去会虚高。 */
private fun dirBytes(dir: Path): Long {
    val fs: FileSystem = platformFileSystem()
    if (!runCatching { fs.exists(dir) }.getOrDefault(false)) return 0L
    return runCatching {
        fs.listRecursively(dir).sumOf { path ->
            val metadata = runCatching { fs.metadataOrNull(path) }.getOrNull()
            if (metadata?.isRegularFile == true) metadata.size ?: 0L else 0L
        }
    }.getOrDefault(0L)
}
