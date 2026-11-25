package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.domain.TranscriptEntry
import edu.jxslu.schedule.domain.TranscriptHistory
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * 导出成绩单的落盘（DESIGN §4.25 E4；iOS 移植 2026-11-26 搬进 `shared/commonMain`）。
 *
 * 位置固定 `appDataDir()/transcripts/`（Android 侧就是 `filesDir/transcripts/`，与搬移前
 * **逐字同一个路径**，老用户已导出的文件原地可读，备份规则 / `file_paths.xml` 都不用动）：
 * 成绩单含姓名、学号、证件照与全部成绩，属于隐私文件，默认只进应用私有目录，不写相册、
 * 不写公共下载目录。用户要给别人时走「分享」或「另存」，后者由宿主的 SAF / 文件选择器
 * 决定落到哪（本类不申请任何存储权限，也**不**知道 `content://` 为何物）。
 *
 * 目录**不要**并到 `notes_img/`：那边有 `AttachmentStore.sweep`，会按笔记引用差集删文件
 * （与课表背景图踩过同一个坑，DESIGN §4.21）。
 *
 * 只留最近 [TranscriptHistory.DEFAULT_KEEP] 份：手机存储有限，成绩单又是随时能重导的东西。
 * 清理在每次保存后顺带做，不留后台任务。保留与排序的口径全在 [TranscriptHistory]
 * （纯逻辑、可单测），这里只做目录 I/O。
 *
 * ## 搬进共享层换掉的三样东西
 *
 * 1. **`Context` / `java.io.File` → `appDataDir()` + okio**：iOS 上没有前两者，而
 *    `appDataDir()` 在 Android 侧注入的就是 `filesDir`（见 `platform/AppDirs.kt`）。
 * 2. **`FileProvider.getUriForFile` → 返回路径字符串**：内容 Uri 是 Android 的
 *    「把沙箱文件借给别的 App」机制（authority 还带 `applicationId`），共享层没有对等物。
 *    所以本类只交出**绝对路径**，转 Uri / 打开 / 分享 / 另存全由宿主那一层做
 *    （`design/ios-port.md` §11.2 ④：平台动作用可空回调，`null` = 本平台没这条通道）。
 * 3. **`file.lastModified()` → okio 的 `FileMetadata.lastModifiedAtMillis`**：iOS 的
 *    `NSURLResourceValues` 同样给得上，只是 okio 把它收在同一处。
 *
 * 线程：**所有方法都是同步阻塞的**，调用方负责切线程（页面侧 `withContext(Dispatchers.Default)`）。
 * commonMain 里取不到 `Dispatchers.IO`（它只声明在各平台源集，见 `JsonFileStore` 的类注释）。
 */
class TranscriptStore(
    /**
     * 落盘目录的**父**目录，缺省 [appDataDir]。
     *
     * 只有单测会传（临时目录）；生产调用方一律用缺省值，与 `JsonFileStore` 同口径。
     */
    dir: String = appDataDir(),
    private val fs: FileSystem = platformFileSystem(),
) {

    /** 成绩单目录：`<appDataDir>/transcripts`。改名等于用户已导出的文件集体消失。 */
    private val dirPath: Path = dir.toPath() / DIR_NAME

    /**
     * 写入一份成绩单，返回其**绝对路径**。
     *
     * 先写 `.part` 再改名：中途失败（磁盘满、进程被杀）只会留下一个 `.part`，
     * 不会让一个截断的 PDF 占着正式文件名被用户当成可用文件打开。
     */
    fun save(bytes: ByteArray, fileName: String): String {
        fs.createDirectories(dirPath)
        val target = dirPath / fileName
        val temp = dirPath / (fileName + TranscriptHistory.PARTIAL_SUFFIX)
        fs.write(temp) { write(bytes) }
        // 改名失败（某些文件系统拒绝「改名覆盖已存在文件」）先删目标再试一次，
        // 仍失败就直接写目标：仍要让用户拿到文件（与搬移前 `File.renameTo` 的兜底同口径）
        if (!moveInto(temp, target)) {
            fs.write(target) { write(bytes) }
            runCatching { fs.delete(temp, mustExist = false) }
        }
        prune()
        return target.toString()
    }

    /**
     * 目录里现有的成绩单，最新在前（「最近导出」列表的数据源）。
     *
     * 顺手清掉写盘半成品（`.part`）：那是崩溃/被杀留下的残骸，留在目录里既不完整、
     * 又会占掉保留名额，还可能被用户从文件管理器里当成可用文件打开。
     */
    fun recent(): List<TranscriptEntry> {
        val children = fs.listOrNull(dirPath) ?: return emptyList()
        val entries = ArrayList<TranscriptEntry>(children.size)
        children.forEach { path ->
            val name = path.name
            when {
                TranscriptHistory.isPartialName(name) ->
                    runCatching { fs.delete(path, mustExist = false) }

                TranscriptHistory.isEntryName(name) -> {
                    val metadata = runCatching { fs.metadataOrNull(path) }.getOrNull()
                    if (metadata?.isRegularFile != true) return@forEach
                    entries += TranscriptEntry(
                        name = name,
                        sizeBytes = metadata.size ?: 0L,
                        // 取不到修改时刻按 0 算：那只会把它排到最后，不会挡住用户打开它
                        modifiedAtMillis = metadata.lastModifiedAtMillis ?: 0L,
                    )
                }
            }
        }
        return TranscriptHistory.sortNewestFirst(entries)
    }

    /** 删一份。[name] 只接受纯文件名（来自 [recent]），带路径分隔符的一律拒绝。 */
    fun delete(name: String): Boolean {
        if (!TranscriptHistory.isSafeName(name)) return false
        return runCatching { fs.delete(dirPath / name, mustExist = false) }.isSuccess
    }

    /**
     * 取一份仍存在的文件路径；不在就返回 `null`。
     *
     * 列表与动作之间有时间差（新导出一份会触发保留策略删旧的），所以动作前必须复核，
     * 否则打开/分享会把一个不存在的文件交给阅读器，用户看到的是系统报错。
     */
    fun existingPath(name: String): String? {
        if (!TranscriptHistory.isSafeName(name)) return null
        val path = dirPath / name
        val metadata = runCatching { fs.metadataOrNull(path) }.getOrNull()
        return if (metadata?.isRegularFile == true) path.toString() else null
    }

    /** 清空。返回实际删掉的份数。 */
    fun deleteAll(): Int = recent().count { delete(it.name) }

    /** 保留最新的 [keep] 份，其余删除。名单由 [TranscriptHistory.filesToPrune] 给。 */
    fun prune(keep: Int = TranscriptHistory.DEFAULT_KEEP) {
        val names = TranscriptHistory.filesToPrune(recent(), keep).toSet()
        if (names.isEmpty()) return
        names.forEach { name ->
            // 名单来自 recent()，已经过 isSafeName 之外的后缀判定；这里再走一次 isSafeName
            // 只为「拼接路径」这一件事：带分隔符的名字不许出目录
            if (TranscriptHistory.isSafeName(name)) {
                runCatching { fs.delete(dirPath / name, mustExist = false) }
            }
        }
    }

    /** 改名，失败时先删目标再试一次。返回是否成功（都不抛）。 */
    private fun moveInto(from: Path, to: Path): Boolean {
        if (runCatching { fs.atomicMove(from, to) }.isSuccess) return true
        runCatching { fs.delete(to, mustExist = false) }
        return runCatching { fs.atomicMove(from, to) }.isSuccess
    }

    private companion object {
        /**
         * 与 `res/xml/file_paths.xml`、`backup_rules.xml`、`data_extraction_rules.xml`
         * 里的 `transcripts/` 是同一份约定：改名要四处一起改（备份排除只认字面路径）。
         */
        const val DIR_NAME = "transcripts"
    }
}
