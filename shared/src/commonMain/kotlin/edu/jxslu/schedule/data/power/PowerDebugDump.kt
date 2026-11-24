package edu.jxslu.schedule.data.power

import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.platform.platformFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * debug 包专用：缴费平台响应原文落盘（`filesDir/power_debug/`，2026-10-08）。
 *
 * 目的：`singleFeeitem.sceneinfo 为空 = 未设置寝室` 这条判据没有真实未绑定账号的样本，
 * `sceneBind/add` 也只有「同房间重绑」一次实证——原文留住，首个真实案例一到就能逐字段
 * 核对（照 `ujing_debug/` / `qiekj_debug/` 先例）。
 *
 * 纪律：release 构建 `Graph.powerRepository` 传 null（一个字节都不写）；目录已排除出
 * 两套备份规则（`backup_rules.xml` / `data_extraction_rules.xml`）；每类只留最近
 * [KEEP_PER_NAME] 份。
 *
 * iOS 移植 S14（2026-11-25）：搬进 `:shared` 的 commonMain，三处跟着换——
 * - `java.io.File` → okio 的 [Path] + [platformFileSystem]（commonMain 看不见 `FileSystem.SYSTEM`，
 *   见 `platform/FileSystem.kt`）；目录由装配根给，本类只管写；
 * - `java.time.LocalDateTime.now()` + `DateTimeFormatter` → [AppClock.nowDateTime] + [TextFmt.pad]；
 * - `Dispatchers.IO` → [Dispatchers.Default]（commonMain 没有 IO 调度器；这里只是几百字节的
 *   顺序写，`Default` 的线程池够用，别为了它去加一条平台缝）。
 */
class PowerDebugDump(private val dir: Path) {

    private val fs: FileSystem = platformFileSystem()

    /**
     * 落一份原文；任何 IO 失败都吞掉（留痕不该影响业务链）。
     * 文件 IO 收在 [Dispatchers.Default]：调用方协程可能跑在主线程调度器上
     * （`viewModelScope.launch`），别让建目录/写文件/裁剪那点磁盘活压上去。
     */
    suspend fun dump(name: String, raw: String) {
        withContext(Dispatchers.Default) {
            runCatching {
                fs.createDirectories(dir)
                fs.write(dir / "${name}_${stamp()}.txt") { writeUtf8(raw) }
                trim(name)
            }
        }
    }

    /** 同名文件只留最新 [KEEP_PER_NAME] 份（文件名前缀即时间序）。 */
    private fun trim(name: String) {
        val files = fs.listOrNull(dir) ?: return
        files.filter { it.name.startsWith("${name}_") }
            .sortedByDescending { it.name }
            .drop(KEEP_PER_NAME)
            .forEach { runCatching { fs.delete(it, mustExist = false) } }
    }

    /** `yyyyMMdd_HHmmss_SSS`（本地时区；原 `DateTimeFormatter.ofPattern` 的逐字等价物）。 */
    private fun stamp(): String {
        val now = AppClock.nowDateTime()
        return buildString {
            append(TextFmt.pad(now.year, 4))
            append(TextFmt.pad(now.monthNumber))
            append(TextFmt.pad(now.dayOfMonth))
            append('_')
            append(TextFmt.pad(now.hour))
            append(TextFmt.pad(now.minute))
            append(TextFmt.pad(now.second))
            append('_')
            append(TextFmt.pad(now.nanosecond / 1_000_000, 3))
        }
    }

    companion object {
        const val KEEP_PER_NAME = 20
    }
}
