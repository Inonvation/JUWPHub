package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.ykt.YktDiagnostics
import edu.jxslu.schedule.domain.SizeText
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSLock
import platform.Foundation.NSLog

/**
 * iOS 侧的日志落盘（B1，2026-11-25）。
 *
 * ## 为什么要有它
 *
 * 2026-11-25 出现过一次「安装后一开就闪退」，当时 iOS 上**没有任何崩溃日志**：
 * `NSLog` 只在连着 Xcode / Console.app 时看得见，用户装完打个包再打开就什么也没有，
 * 只能靠猜。这一层把每一条 [Logger] 调用与未捕获异常一起写进沙箱，用户在
 * 「设置 → 数据与权限 → 权限与存储」里能把整包日志导出来。
 *
 * ## 落点与滚动
 *
 * | 项 | 值 |
 * |---|---|
 * | 目录 | `appDataDir()/logs`（`NSFileManager` 的 Application Support，实测落在沙箱 `Documents/`，见 `AppDirs.ios.kt`） |
 * | 当前文件 | `app.log` |
 * | 单文件上限 | [MAX_FILE_BYTES] = 256 KB |
 * | 文件数 | [MAX_FILES] = 3：`app.log` / `app.log.1` / `app.log.2`，`.2` 最旧 |
 *
 * 滚动是**写满就换**，不是按天：换之前把 `.1 → .2`、`app.log → .1`，最旧的 `.2` 直接删。
 * 于是磁盘占用恒定 ≤ 768 KB（**有界**是这条设计唯一的目的：崩溃循环里一天能写几十兆）。
 *
 * **启动时保留上一次的会话尾巴**：进程内第一次写日志（[writeLineLocked] 的 `initialized`
 * 分支）不截断已有文件。如果上一轮已经写满 256 KB，就在这一下先滚一格，让上次的尾巴落到
 * `app.log.1`，新的 `app.log` 从会话头开始——这正是「崩溃前那几行」最常待的位置。
 *
 * ## 脱敏
 *
 * 每一行在**落盘之前**都过一遍 `data/ykt/YktDiagnostics` 的脱敏（那条实现原本给一卡通
 * 失败诊断用，键名清单里有 password / token / cardno / account / username / sno / phone…）。
 * 崩溃栈按行脱敏，保留换行结构。红线：日志里绝不出现凭证、token、学号、密码、卡号。
 *
 * ## 线程
 *
 * `NSLock` 一把锁包住「初始化 + 滚动 + 写入」。`platformLog` 是同步调用（调用点可能在任何
 * 线程上），所以不能用协程的 Mutex。锁内只做一次 `open/append/close`，没有跨调用的句柄。
 *
 * ## 失败模式
 *
 * 目录建不出来、磁盘满、文件被别的东西占着——一律吞掉（[append] 的 `runCatching`）。
 * 日志**绝不能反过来改变控制流**：为一条写失败的日志把正在跑的业务打断，比没有日志更糟。
 * 控制台那一份（`NSLog`）不依赖这里，Xcode 里照样看得见。
 */
internal object IosLogFile {

    /** 单文件上限 256 KB。 */
    internal const val MAX_FILE_BYTES: Long = 256L * 1024L

    /** 最多 3 个文件（`app.log` + 两个滚动副本）。 */
    internal const val MAX_FILES: Int = 3

    private const val DIR_NAME = "logs"
    private const val FILE_NAME = "app.log"

    /** 单条消息落盘前的上限：一行几万字的日志会把整个文件顶满，也读不出重点。 */
    private const val MAX_MESSAGE_CHARS = 2000

    private val fs: FileSystem get() = platformFileSystem()

    private val lock = NSLock()

    /** 进程内第一次写入了会话头没有；只在锁内读写。 */
    private var initialized = false

    /** 当前 `app.log` 的字节数（锁内维护，省掉每行一次 stat）。 */
    private var currentSize: Long = 0L

    private val formatter: NSDateFormatter by lazy {
        NSDateFormatter().apply {
            // 固定格式 + POSIX locale：用户设备上的佛历 / 民国纪年会把年份写成另一种数字，
            // 日志时间戳就对不上真机现象出现的那一刻了。
            dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
        }
    }

    /** `appDataDir()/logs`。「存储占用」与导出都读它。惰性算一次，别每行日志都问一次 NSFileManager。 */
    private val dirPath: Path by lazy { (appDataDir() + "/" + DIR_NAME).toPath() }

    internal fun dir(): Path = dirPath

    private fun logPath(): Path = dir() / FILE_NAME

    /** 现有日志文件，**从新到旧**（`app.log` → `app.log.1` → `app.log.2`，不存在的跳过）。 */
    private fun filesLocked(): List<Path> =
        listOf(logPath()) + (1 until MAX_FILES).map { dir() / "$FILE_NAME.$it" }
            .filter { runCatching { fs.exists(it) }.getOrDefault(false) }

    /**
     * 现有日志文件，**从新到旧**。
     *
     * 导出与「有几份日志」都按这个顺序，用户先看到的是刚刚发生的那一段。
     * `NSLock` 不可重入：锁内只调 [filesLocked]，别在这里再套一层。
     */
    internal fun files(): List<Path> {
        lock.lock()
        return try {
            filesLocked()
        } finally {
            lock.unlock()
        }
    }

    /** 日志目录总字节（「存储占用」的日志那一行）。 */
    internal fun totalBytes(): Long {
        lock.lock()
        return try {
            filesLocked().sumOf { runCatching { fs.metadataOrNull(it)?.size ?: 0L }.getOrDefault(0L) }
        } finally {
            lock.unlock()
        }
    }

    /**
     * 记一条（[platformLog] 的唯一落点）。
     *
     * [level] 是 `D` / `I` / `W` / `E` 单字母，[detail] 已由调用方拼好 message 与异常类型。
     */
    fun append(level: String, tag: String, detail: String) {
        // 脱敏是纯计算，放在锁外；时间戳必须在锁内取（NSDateFormatter 不是线程安全的）
        val safe = redact(detail)
        lock.lock()
        try {
            writeLineLocked("${stamp()} $level/$tag: $safe\n")
        } catch (_: Throwable) {
            // 见类注释「失败模式」：写不进去就算了，别把业务带走
        } finally {
            lock.unlock()
        }
    }

    /**
     * 写一段多行文本（崩溃栈）：逐行脱敏、每行带时间戳与 tag，整体按一条记录滚动。
     *
     * 为什么不复用 [append] 拼一个超长单行：栈的价值就在「哪一行调用哪一行」，
     * 挤成一行等于把唯一的诊断信息丢掉。
     */
    fun appendBlock(level: String, tag: String, text: String) {
        if (text.isEmpty()) return
        val safeLines = text.lines().map { redact(it) }
        lock.lock()
        try {
            val block = buildString {
                safeLines.forEach { line ->
                    append(stamp()).append(' ').append(level).append('/').append(tag)
                    append(": ").append(line).append('\n')
                }
            }
            writeLineLocked(block)
        } catch (_: Throwable) {
            // 同上
        } finally {
            lock.unlock()
        }
    }

    /**
     * 把 [files] 拼成一段可直接贴进聊天窗口的纯文本（「导出诊断日志」的载荷）。
     *
     * [maxChars] 是硬上限：三个文件满量是 768 KB，整份塞给 `UIActivityViewController`
     * 既慢又可能被系统拒。截断时在开头写明「只导出了前 N 字」，别让用户以为日志就那么点。
     * 顺序是**新的在后**（老文件先写），读到末尾就是崩溃前的那几行。
     *
     * 全程持锁：`NSDateFormatter` 不是线程安全的，而日志写入在别的线程上也会调 [stamp]。
     * 导出最多几百毫秒，期间落盘的那几条会等一等——这比让两个线程同时用同一个
     * formatter 崩掉好（`NSLock` 不可重入，所以锁内一律走 `*Locked` 的私有版本）。
     */
    internal fun exportText(maxChars: Int = 300_000): String {
        lock.lock()
        return try {
            buildString {
                val paths = filesLocked()
                append("诊断日志导出\n")
                append("时间: ").append(stamp()).append('\n')
                append("目录: ").append(dir()).append('\n')
                append("文件: ")
                    .append(
                        paths.joinToString("、") { it.name + "（" + sizeLabel(it) + "）" }
                            .ifEmpty { "无" },
                    )
                    .append("\n\n")
                paths.asReversed().forEach { path ->
                    val body = runCatching { fs.read(path) { readUtf8() } }.getOrNull()
                        ?: return@forEach
                    if (length >= maxChars) return@forEach
                    append("---- ").append(path.name).append(" ----\n")
                    val room = maxChars - length
                    if (body.length <= room) {
                        append(body)
                    } else {
                        append(body.take(room))
                        append("\n…（后面还有 ").append(body.length - room).append(" 字没导出）\n")
                    }
                    append('\n')
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private fun sizeLabel(path: Path): String =
        SizeText.format(runCatching { fs.metadataOrNull(path)?.size ?: 0L }.getOrDefault(0L))

    private fun stamp(): String = formatter.stringFromDate(NSDate())

    /**
     * 脱敏：键值形态（`token=…` / `"password":"…"`）整值抹掉，空白压成单空格。
     *
     * 脱敏本身抛了宁可把这一行换成一句占位符，也不原样落盘——红线是「日志里不出现凭证」，
     * 不是「日志一定完整」。
     */
    private fun redact(text: String): String {
        val clean = runCatching { YktDiagnostics.redactBody(text) }.getOrElse { "<脱敏失败>" }
        return if (clean.length <= MAX_MESSAGE_CHARS) clean else clean.take(MAX_MESSAGE_CHARS) + "…"
    }

    /** 会话头：一条明确的分界线，之后才是本次启动产生的日志。 */
    private fun sessionHeader(): String = "===== session ${stamp()} =====\n"

    private fun writeLineLocked(line: String) {
        if (!initialized) {
            fs.createDirectories(dir())
            currentSize = runCatching { fs.metadataOrNull(logPath())?.size ?: 0L }.getOrDefault(0L)
            // 上一轮已经写满：先滚一格，把上一次的尾巴留住（见类注释）
            if (currentSize >= MAX_FILE_BYTES) rollLocked()
            initialized = true
            writeRawLocked(sessionHeader())
        }
        if (currentSize + line.encodeToByteArray().size > MAX_FILE_BYTES) rollLocked()
        writeRawLocked(line)
    }

    private fun writeRawLocked(text: String) {
        fs.appendingSink(logPath()).buffer().use { it.writeUtf8(text) }
        currentSize += text.encodeToByteArray().size
    }

    /**
     * 滚动一格：删最旧的，其余依次改名，`app.log` 让位。
     *
     * 改名用 `atomicMove`（POSIX `rename`），失败只是滚动没做成——[runCatching] 兜住，
     * 下一次写满再试。**不做「改名失败就清空当前文件」**：那会把刚写的证据丢掉。
     */
    private fun rollLocked() {
        val dir = dir()
        runCatching { fs.delete(dir / "$FILE_NAME.${MAX_FILES - 1}", mustExist = false) }
        for (index in (MAX_FILES - 2) downTo 1) {
            val from = dir / "$FILE_NAME.$index"
            if (runCatching { fs.exists(from) }.getOrDefault(false)) {
                runCatching { fs.atomicMove(from, dir / "$FILE_NAME.${index + 1}") }
            }
        }
        runCatching {
            if (fs.exists(logPath())) fs.atomicMove(logPath(), dir / "$FILE_NAME.1")
        }
        currentSize = 0L
    }
}

/** 崩溃钩子的安装状态；`setUnhandledExceptionHook` 只能有一个，装两次会把上一次顶掉。 */
private var crashHookInstalled = false

/**
 * 装**未捕获异常钩子**：Kotlin 侧没人接的异常在终止进程前先落一份栈到 `logs/app.log`。
 *
 * ## 为什么需要它
 *
 * [IosServices] 的 `CoroutineExceptionHandler` 只接得住协程作用域里的异常。UIKit 回调
 * （WebView 的 delegate、通知代理、Compose 的组合期）里抛出来的 Kotlin 异常走的是
 * 「进程终止」这条路——Kotlin/Native 上没有 JVM 那种 `Thread.setDefaultUncaughtExceptionHandler`，
 * 对应的唯一入口就是 `kotlin.native.setUnhandledExceptionHook`。
 *
 * ## 语义
 *
 * 钩子里**只做落盘**，不吞异常：钩子返回之后进程照旧终止（Kotlin/Native 的约定）。
 * 同时把栈打到 `NSLog`，Xcode 里那份控制台输出不因为装了钩子而消失。
 *
 * `setUnhandledExceptionHook` 是 `ExperimentalNativeApi`；若将来这条符号在目标版本上不可用，
 * 删掉 [installCrashHook] 的调用处即可（只有 `IosServices.init` 一处），日志落盘不受影响。
 */
@OptIn(kotlin.ExperimentalNativeApi::class)
internal fun installCrashHook() {
    if (crashHookInstalled) return
    crashHookInstalled = true
    kotlin.native.setUnhandledExceptionHook { error ->
        val stack = runCatching { error.stackTraceToString() }.getOrElse { error.toString() }
        // 控制台那一份：`%` 要转义，Kotlin 的字符串是当格式串传进去的（见 Logger.ios.kt 的 KDoc）
        runCatching { NSLog("E/uncaught: ${stack.replace("%", "%%")}") }
        runCatching { IosLogFile.appendBlock("E", "uncaught", stack) }
    }
}
