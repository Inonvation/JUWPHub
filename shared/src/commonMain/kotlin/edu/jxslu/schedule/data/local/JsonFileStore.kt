package edu.jxslu.schedule.data.local

import edu.jxslu.schedule.platform.LogLevel
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import edu.jxslu.schedule.platform.platformLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * `appDataDir()` 下一份 JSON 的读写（2026-11-06 审计 C2；iOS 移植 S7 搬进 `:shared`）。
 *
 * 合并之前同一段逻辑在八处各写一遍，且**每处的容错细节还不完全一样**：有的直接
 * `file.writeText`（写一半被杀 = 文件截断），有的先写临时文件再改名，有的把写失败
 * 吞掉、有的只 `Log.w`。共用一份之后只有两种明确语义：
 *
 * - **读**：文件不存在 / 读不出来 / 解码失败 → `null`。**不抛**——这些文件全是本机缓存
 *   或旁路账本，坏了等于没有，调用方各自决定「当空表」还是「当首跑」。
 * - **写**：先写 `<名字>.tmp` 再改名，**返回 Boolean**。写失败不抛（同上：不能让写缓存
 *   失败打断已经落库的业务），要不要留痕由调用方按自己的口径决定——合并前就是各写各的。
 *
 * 为什么不用 `CourseJsonFormat`（`data/repo/ScheduleBackup.kt` 里那份）：那是给**用户导出、
 * 给兼容工具互导**看的文件，`prettyPrint` + `encodeDefaults` 让文件自解释；这里全是本机缓存，
 * 保持紧凑，且八处的既有文件都是 `Json { ignoreUnknownKeys = true }` 写出来的——换配置会
 * 让已经躺在用户设备上的缓存全部「解码失败」，也就是白丢一次缓存。
 *
 * 文件名一律由调用方传入且**不能改**：改名等于用户丢数据（`AppCacheCleaner` 还按旧名统计）。
 *
 * ## iOS 移植 S7（2026-11-24）换掉的三样东西
 *
 * 1. **不再吃 `Context` / `java.io.File`**：目录来自共享层的 [appDataDir]（Android 侧就是
 *    `context.filesDir`，与搬移前逐字同一个路径，老用户的缓存原地可读、不需要迁移），
 *    读写介质来自 [platformFileSystem]（okio 的 `FileSystem`，两端各有 actual）。
 * 2. **日志**：`android.util.Log` 换成共享层门面 [platformLog]。
 * 3. **调度器**：`Dispatchers.IO` 在 commonMain 里不可见（它只声明在各平台源集，
 *    与 `FileSystem.SYSTEM` 同一个原因，`RideRecordStore` / `WeatherApi` 也都记过这一条），
 *    所以 suspend 版改成 `Dispatchers.Default`。**与 Android 版唯一的行为差异是这一点**：
 *    文件读写跑在 Default 池（与 UI 默认调度同一组线程，而不是 Android 的 IO 池）。
 *    这些文件都是几十 KB、一次一读一整份，Default 池的并行度足够；调用点一行都不用改。
 *    另两个 `*Blocking` 版本本来就不切线程，不受影响。
 *
 * 语义一条都没改：原子替换、失败不抛、`.tmp` 残留清理、`tag == null` 全静默，
 * 以及「同目录临时文件」这个落点。
 */
class JsonFileStore<T>(
    /**
     * `appDataDir()` 下的文件名。**改了就是另一份文件**，老数据不会自动搬过去。
     */
    private val fileName: String,
    private val serializer: KSerializer<T>,
    private val json: Json = DefaultJson,
    /**
     * 写盘失败时打日志用的 tag；`null` = 完全静默。
     *
     * 合并前八处的口径本来就不一致：五份缓存默默吞掉，`AiApplyHistoryStore` 与
     * `QuietHoursQueue` 各 `Log.w` 一句。保留这个参数是为了**别把已有的留痕弄丢**——
     * 「重启后记录消失」这类无头案全靠这一行日志。
     */
    private val tag: String? = null,
    /**
     * 落盘目录，缺省 [appDataDir]。
     *
     * **只有单测会传**（临时目录，避免依赖注入过的 `Context`；这条也顺带让
     * 「写不进去」这类失败路径可测）。生产调用方一律用缺省值：多传一个目录就等于
     * 多一份「缓存写到别处」的可能，而 `AppCacheCleaner` 只认 `filesDir`。
     */
    dir: String = appDataDir(),
) {

    private val fs: FileSystem = platformFileSystem()

    /** 正式文件与临时文件都在同一个目录里：改名不能跨文件系统。 */
    private val dirPath: Path = dir.toPath()
    private val path: Path = dirPath / fileName
    private val tmpPath: Path = dirPath / (fileName + TMP_SUFFIX)

    /**
     * 只串行化 suspend 版：`withLock` 包住一次完整的「读文件 + 解码」/「编码 + 落盘」，
     * 避免并发读到一个写了一半的文件。
     *
     * **两个 `*Blocking` 版本不碰这把锁**（见各自注释），与四份快照 store 合并前的并发面
     * 完全一致——它们本来就没有互斥，这里加锁反而会把「谁该串行」这件事悄悄改掉。
     */
    private val mutex = Mutex()

    /**
     * 读整份；文件不存在/坏了回 `null`（不抛）。
     *
     * 调度器是 `Dispatchers.Default`，不是 Android 版的 `Dispatchers.IO`（理由见类注释第 3 条）。
     */
    suspend fun read(): T? = withContext(Dispatchers.Default) { mutex.withLock { readBlocking() } }

    /** 写整份（临时文件 + 改名）；失败回 `false`（不抛）。调度器同上。 */
    suspend fun write(value: T): Boolean =
        withContext(Dispatchers.Default) { mutex.withLock { writeBlocking(value) } }

    /**
     * 同步读，**给「构造期一次性小文件读」这类调用**（`AiApplyHistoryStore` 那类口径）。
     *
     * 不加锁也不切线程：调用方必须已经在 IO 线程上，或者读的是自己构造时那个几百字节的文件。
     * 用 suspend 版能拿到锁，但会把构造期的同步读变成异步——`MutableStateFlow(load())` 那种
     * 初值写法就没法用了，首帧会先看到空表再跳。
     */
    fun readBlocking(): T? {
        if (!fs.exists(path)) return null
        return runCatching {
            json.decodeFromString(serializer, fs.read(path) { readUtf8() })
        }.getOrNull()
    }

    /**
     * 同步写，**给「调用方自己已经有一把锁 + 已经在 IO 线程」的场景**（同上）。
     *
     * 与 [write] 唯一的差别是没有外层 Mutex；原子替换与容错完全一致。
     */
    fun writeBlocking(value: T): Boolean {
        val text = runCatching { json.encodeToString(serializer, value) }.getOrNull() ?: return false
        return try {
            // 目录不存在时 okio 的 write 会抛「parent 不存在」；建目录失败也走下面的
            // false 分支——**不因为多了这一步就把「失败回 false」改成抛**。
            fs.createDirectories(dirPath)
            // 同目录临时文件：中途被杀（磁盘满 / 进程被杀）只留一个半成品，
            // 正式文件要么是旧的、要么是新的，不会变成截断的半个 JSON。
            fs.write(tmpPath) { writeUtf8(text) }
            if (runCatching { fs.atomicMove(tmpPath, path) }.isFailure) {
                fallbackWrite(tmpPath, path, text)
            }
            true
        } catch (e: Throwable) {
            // 与合并前的 `runCatching` 同口径（吞 Throwable）：写缓存失败绝不能打断已经
            // 落库的业务。日志本身也包在 runCatching 里——诊断信息不许反过来改变控制流。
            //
            // 顺手删掉半成品 .tmp：write 中途失败（磁盘满）会留一个截断文件，
            // 它既不被 [bytes] 统计、也不会被 [delete] 清掉，会永久占着空间
            // （2026-11-06 复核）。
            runCatching { fs.delete(tmpPath, mustExist = false) }
            tag?.let { warnTag ->
                runCatching { platformLog(LogLevel.Warn, warnTag, "落盘失败 $fileName：${e.message}", null) }
            }
            false
        }
    }

    /**
     * 改名失败的兜底（合并前那三行的原样语义）：某些文件系统拒绝「改名覆盖已存在文件」
     * （Windows 测试环境上的 `File.renameTo` 就是这样）——先删目标再试一次，仍失败就直接写。
     * 合并前这几处就是直接 `writeText`，别因为换了实现反而写不进去。
     *
     * 兜底本身再失败会抛，由 [writeBlocking] 的外层接住（结论仍是 `false`）。
     */
    private fun fallbackWrite(tmp: Path, target: Path, text: String) {
        runCatching { fs.delete(target, mustExist = false) }
        if (runCatching { fs.atomicMove(tmp, target) }.isSuccess) return
        fs.write(target) { writeUtf8(text) }
        runCatching { fs.delete(tmp, mustExist = false) }
    }

    /** 当前占用字节（缓存统计用）。**调用方负责 IO 线程**。 */
    fun bytes(): Long = fs.metadataOrNull(path)?.size ?: 0L

    /** 删除文件（「清除缓存」用）。缓存删掉即失效，不需要额外状态。残留的 .tmp 一并清。 */
    fun delete() {
        runCatching { fs.delete(path, mustExist = false) }
        runCatching { fs.delete(tmpPath, mustExist = false) }
    }

    companion object {
        /**
         * 这八份文件合并前用的就是这一份配置，别换成 `CourseJsonFormat`（理由见类注释）。
         * `ignoreUnknownKeys` 是硬要求：加字段后老文件还能读，减字段时新代码读老文件也不炸。
         */
        val DefaultJson: Json = Json { ignoreUnknownKeys = true }

        /** 临时文件后缀（与 `ScheduleShareStore` 的 `.part` 同思路，只是这里是同目录 JSON）。 */
        private const val TMP_SUFFIX = ".tmp"
    }
}
