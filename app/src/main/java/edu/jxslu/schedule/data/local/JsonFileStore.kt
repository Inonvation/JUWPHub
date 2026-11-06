package edu.jxslu.schedule.data.local

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * `filesDir` 下一份 JSON 的读写（2026-11-06 审计 C2）。
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
 */
class JsonFileStore<T>(
    private val file: File,
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
) {

    /**
     * @param fileName `filesDir` 下的文件名。**改了就是另一份文件**，老数据不会自动搬过去。
     */
    constructor(
        context: Context,
        fileName: String,
        serializer: KSerializer<T>,
        json: Json = DefaultJson,
        tag: String? = null,
    ) : this(File(context.filesDir, fileName), serializer, json, tag)

    /**
     * 只串行化 suspend 版：`withLock` 包住一次完整的「读文件 + 解码」/「编码 + 落盘」，
     * 避免并发读到一个写了一半的文件。
     *
     * **两个 `*Blocking` 版本不碰这把锁**（见各自注释），与四份快照 store 合并前的并发面
     * 完全一致——它们本来就没有互斥，这里加锁反而会把「谁该串行」这件事悄悄改掉。
     */
    private val mutex = Mutex()

    /** 读整份；文件不存在/坏了回 `null`（不抛）。 */
    suspend fun read(): T? = withContext(Dispatchers.IO) { mutex.withLock { readBlocking() } }

    /** 写整份（临时文件 + 改名）；失败回 `false`（不抛）。 */
    suspend fun write(value: T): Boolean =
        withContext(Dispatchers.IO) { mutex.withLock { writeBlocking(value) } }

    /**
     * 同步读，**给「构造期一次性小文件读」这类调用**（`AiApplyHistoryStore` 那类口径）。
     *
     * 不加锁也不切线程：调用方必须已经在 IO 线程上，或者读的是自己构造时那个几百字节的文件。
     * 用 suspend 版能拿到锁，但会把构造期的同步读变成异步——`MutableStateFlow(load())` 那种
     * 初值写法就没法用了，首帧会先看到空表再跳。
     */
    fun readBlocking(): T? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
    }

    /**
     * 同步写，**给「调用方自己已经有一把锁 + 已经在 IO 线程」的场景**（同上）。
     *
     * 与 [write] 唯一的差别是没有外层 Mutex；原子替换与容错完全一致。
     */
    fun writeBlocking(value: T): Boolean {
        val text = runCatching { json.encodeToString(serializer, value) }.getOrNull() ?: return false
        return try {
            // 同目录临时文件：中途被杀（磁盘满 / 进程被杀）只留一个半成品，
            // 正式文件要么是旧的、要么是新的，不会变成截断的半个 JSON。
            val tmp = File(file.parentFile, file.name + TMP_SUFFIX)
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                // rename 覆盖已存在文件在某些文件系统上会失败（Windows 测试环境就是这样）。
                // 删掉再试一次；仍失败就直接写——合并前这几处就是直接 `writeText`，
                // 别因为换了实现反而写不进去。
                runCatching { file.delete() }
                if (!tmp.renameTo(file)) {
                    file.writeText(text)
                    tmp.delete()
                }
            }
            true
        } catch (e: Throwable) {
            // 与合并前的 `runCatching` 同口径（吞 Throwable）：写缓存失败绝不能打断已经
            // 落库的业务。日志本身也包在 runCatching 里——诊断信息不许反过来改变控制流
            // （JVM 单测里 `android.util.Log` 是未 mock 的，直接调会抛）。
            //
            // 顺手删掉半成品 .tmp：writeText 中途失败（磁盘满）会留一个截断文件，
            // 它既不被 [bytes] 统计、也不会被 [delete] 清掉，会永久占着空间
            // （2026-11-06 复核）。
            runCatching { File(file.parentFile, file.name + TMP_SUFFIX).delete() }
            if (tag != null) runCatching { Log.w(tag, "落盘失败 ${file.name}：${e.message}") }
            false
        }
    }

    /** 当前占用字节（缓存统计用）。**调用方负责 IO 线程**。 */
    fun bytes(): Long = file.length()

    /** 删除文件（「清除缓存」用）。缓存删掉即失效，不需要额外状态。残留的 .tmp 一并清。 */
    fun delete() {
        runCatching { file.delete() }
        runCatching { File(file.parentFile, file.name + TMP_SUFFIX).delete() }
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
