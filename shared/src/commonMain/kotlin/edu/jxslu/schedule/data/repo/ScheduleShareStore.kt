package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.platform.platformFileSystem
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * 一次性图片的**分享**落盘（DESIGN §4.12 课表导出 / §4.47 百宝箱共用一套机制）。
 *
 * 位置 = 调用方给的目录 + `dirName`（课表导出 = [DIR_NAME]，百宝箱 = [DIR_TOOLBOX]，
 * FileProvider 各配一个根）：
 * - 分享用的图是一次性的——系统分享面板把内容 Uri 读完就没用了，落 cache 既不进
 *   云备份（只有 filesDir 进备份，也省掉往两份备份排除 xml 里再登记一条），
 *   也不污染私有目录；
 * - 「保存到相册」**不走这里**——那条路直接进 MediaStore（`ui/common/BitmapSave`）。
 *
 * 目录只留最近 [KEEP] 份，保存后顺带清理，不留后台任务。
 *
 * ## iOS 移植 M2（2026-11-26）搬进 `commonMain`
 *
 * 原来吃 `Context` + `java.io.File`，分享出口还要 `FileProvider.getUriForFile`。搬完后
 * **只保留「往一个目录写字节、裁旧文件」这一段**，两件平台事回到宿主：
 *
 * | 原来 | 现在 |
 * |---|---|
 * | `File(context.cacheDir, dirName)` | 目录由调用方给绝对路径（[dir]）。Android 宿主传 `cacheDir/<dirName>`，Android 侧路径与 FileProvider 的 `res/xml/file_paths.xml` 一字未变 |
 * | `File.writeBytes` / `listFiles` | okio 的 [FileSystem]（[platformFileSystem]，两端各有 actual） |
 * | `uriOf(file)` | **删掉**。`content://` 的 authority 是 `${applicationId}.fileprovider`，debug / release 两个包名不同，Authority 只能在 `:app` 拼（`ShareIntents.android.kt` 的文件头也这么写）；iOS 那边分享面板吃的是文件路径，不需要 Uri |
 *
 * 返回值也从 `File` 改成**绝对路径字符串**：`File` / `Path` 都是平台类型，而宿主拿它做的事
 * 本来就只是「转成自己那一端的分享引用」（Android 转 `content://`、iOS 直接传路径）。
 *
 * 语义一条没改：先写 `.part` 再改名、只留最近 [KEEP] 份、保存后顺手清残骸。
 */
class ScheduleShareStore(
    /**
     * 落盘目录的**绝对路径**（目录不必预先存在）。
     *
     * 为什么不是「目录名 + 一条取 cacheDir 的缝」：`platform/AppDirs` 只回答「私有数据在哪」
     * （Android = `filesDir`），而分享用的图**不能**进 `filesDir`——那份目录进云备份，
     * 一次性图片跟着上传是白白浪费用户流量。所以目录由宿主给，Android 侧继续用 `cacheDir`。
     */
    dir: String,
    private val fs: FileSystem = platformFileSystem(),
) {

    private val dirPath: Path = dir.toPath()

    /**
     * 写入一份待分享的图片，返回落盘的**绝对路径**。
     *
     * 先写 `.part` 再改名：中途失败（磁盘满、进程被杀）只留一个半成品，
     * 不会让截断的文件占着正式名被分享出去。
     *
     * 写不进去会抛（磁盘满 / 没权限），由调用方决定怎么提示——与搬移前的 `File.writeBytes`
     * 同口径（调用点本来就是 `runCatching` 包着）。
     */
    fun save(bytes: ByteArray, fileName: String): String {
        fs.createDirectories(dirPath)
        val target = dirPath / fileName
        val temp = dirPath / (fileName + PART_SUFFIX)
        fs.write(temp) { write(bytes) }
        if (!replace(temp, target)) {
            // 改名彻底失败时留个明确结果：与 `JsonFileStore.fallbackWrite` 一样直接写正式文件，
            // 已写的字节不会丢，只多一次写盘。
            fs.write(target) { write(bytes) }
            runCatching { fs.delete(temp, mustExist = false) }
        }
        prune()
        return target.toString()
    }

    /**
     * 只留最近 [keep] 份，其余删除；顺手清掉写盘半成品（`.part`）。
     * 崩溃/被杀留下的残骸既不完整，又会占掉保留名额。
     */
    fun prune(keep: Int = KEEP) {
        // 目录还不存在（首次保存前后崩掉）时没有东西可裁，不是错误。
        val files = fs.listOrNull(dirPath) ?: return
        val present = files.filter { entry ->
            fs.metadataOrNull(entry)?.isRegularFile == true
        }
        present.filter { it.name.endsWith(PART_SUFFIX) }
            .forEach { runCatching { fs.delete(it, mustExist = false) } }
        present.filterNot { it.name.endsWith(PART_SUFFIX) }
            .sortedByDescending { fs.metadataOrNull(it)?.lastModifiedAtMillis ?: 0L }
            .drop(keep)
            .forEach { runCatching { fs.delete(it, mustExist = false) } }
    }

    /**
     * 把临时文件换成正式名；已存在同名文件时先删再改名。
     *
     * 某些文件系统（包括 Windows 上的 `renameTo`）拒绝「改名覆盖已存在文件」，
     * 所以先删目标再试一次——返回 false 表示两次都没成，由 [save] 直接写正式文件兜底。
     */
    private fun replace(temp: Path, target: Path): Boolean {
        if (runCatching { fs.atomicMove(temp, target) }.isSuccess) return true
        runCatching { fs.delete(target, mustExist = false) }
        if (runCatching { fs.atomicMove(temp, target) }.isSuccess) return true
        return false
    }

    companion object {
        /** 课表导出的分享目录（DESIGN §4.12）。 */
        const val DIR_NAME = "schedule_share"

        /** 百宝箱分享出口（DESIGN §4.47）：与课表导出各占一个目录、各自裁到 5 份。 */
        const val DIR_TOOLBOX = "toolbox_share"

        private const val KEEP = 5
        private const val PART_SUFFIX = ".part"
    }
}
