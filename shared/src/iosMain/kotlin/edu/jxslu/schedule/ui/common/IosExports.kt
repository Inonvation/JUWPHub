package edu.jxslu.schedule.ui.common

import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * 「导出到用户看得见的地方」的 iOS 落点：`<appDataDir>/exports/`（iOS 移植 M3，2026-11-26）。
 *
 * ## 为什么是这里
 *
 * `appDataDir()` 在 iOS 上解析到沙箱 `Documents/`（见 `platform/AppDirs.ios.kt` 的实测记录），
 * 而 `Info.plist` 开着 `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace`，
 * 所以这一层就是「文件」App →「我的 iPhone」→「JUWPHub」里能直接看到、能拷走的那层。
 * 这条能力已经跑通：`ui/platform/FilePickers.ios.kt` 的 `writeExport` 就写同一目录。
 *
 * **刻意不用 `UIDocumentPickerViewController(forExporting:)`**：那要多一个代理对象（delegate 是
 * weak，得挂住）、多一条 present 路径，本机编不了 iOS，验不了任何 UI 行为；换来的只是
 * 「用户能选存到哪个目录」。落自己的目录 + 文件 App 共享，能力覆盖了「另存」这一步。
 *
 * ## 为什么不清理旧文件
 *
 * 「分享用的临时图」在 Android 走 `cacheDir`（`ScheduleShareStore` 会裁到最近 5 份），iOS 这里
 * **不裁**：`exports/` 里还有用户从设置页导出的 CSV / JSON，按修改时间裁会把用户自己的导出删掉。
 * 同名覆盖即可——课表图片与成绩单的文件名都带周次 / 学期，重复导出自然覆盖同一份。
 *
 * ## 文件名
 *
 * 只取最后一段、去掉分隔符：调用方给的名字来自页面（`成绩单-2026-2027-1.pdf`），但写盘前
 * 必须先过这道闸，否则 `../` 就能把文件写到 `Documents/` 外面去。
 */

/** 导出目录（`<appDataDir>/exports`）。目录不一定存在，写之前 `createDirectories`。 */
internal fun exportsDirectory(): Path = (appDataDir() + "/exports").toPath()

/**
 * 把 [bytes] 写成 `<exports>/<fileName>`，返回绝对路径；写不进去（目录建不了、磁盘满、
 * 名字非法）返回 null，**不抛**——调用方只按「没保存成」提示。
 */
internal fun writeExportedFile(fileName: String, bytes: ByteArray): String? = runCatching {
    val dir = exportsDirectory()
    val fs = platformFileSystem()
    fs.createDirectories(dir)
    val target = dir / exportFileName(fileName)
    // 整份覆盖写：导出的 PDF / PNG 每次都是重新生成的，没有追加语义
    fs.write(target) { write(bytes) }
    target.toString()
}.getOrNull()

/**
 * 把 [sourcePath] 那份文件复制到 `<exports>/<fileName>`，返回副本的绝对路径；失败返回 null。
 *
 * 用于「另存」：原件留在 `appDataDir()/transcripts/`（成绩单的正式落点，见 `TranscriptStore`），
 * 这里只多做一份放在用户翻得到的地方。读到一半失败就当没保存，不留下半份文件。
 */
internal fun copyFileIntoExports(sourcePath: String, fileName: String): String? = runCatching {
    val bytes = platformFileSystem().read(sourcePath.toPath()) { readByteArray() }
    writeExportedFile(fileName, bytes)
}.getOrNull()

/**
 * 落盘用的文件名：只留最后一段（`/` 与 `\` 都算分隔符），空 / `.` / `..` 一律换成 [FALLBACK_NAME]。
 *
 * 不在这里补扩展名：调用方给的名字已经带 `.pdf` / `.png`，补第二次会变成 `x.pdf.pdf`。
 */
private fun exportFileName(name: String): String {
    val trimmed = name.replace('\\', '/').substringAfterLast('/').trim()
    return if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") FALLBACK_NAME else trimmed
}

/** [exportFileName] 的兜底名：真实调用方不会走到这里，只为「不写出一个非法路径」。 */
private const val FALLBACK_NAME = "export"
