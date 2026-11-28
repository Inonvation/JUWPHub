package edu.jxslu.schedule.data.local

import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import edu.jxslu.schedule.ui.platform.PickedImage
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

/**
 * 笔记 / 作业的图片附件存储（iOS，2026-11-29）。
 *
 * ## 与 Android `AttachmentStore` 的对齐点
 *
 * | 项 | Android | 这里 |
 * |---|---|---|
 * | 落点 | `filesDir/notes_img/` | `appDataDir()/notes_img/`（同一个私有沙箱） |
 * | 正文引用 | `![](img:文件名)` | 同（`domain/imageRefToken`） |
 * | 压缩 | 长边 ≤1600、JPEG 质量 0.86（`ImageImport`） | `UIImage.downscaledToLongSide(1600)` + `UIImageJPEGRepresentation(0.86)` |
 * | 名字 | 时间戳 + 随机后缀 | 同形状（`img_<毫秒>_<四位随机>.jpg`） |
 * | 删除 | 正文差集 + 冷启动扫孤儿 | 正文差集（`MarkdownEditorUi.deleteFiles`）；孤儿扫描**没做**，见 shortcut |
 *
 * ## 为什么复用课表背景图那套 UIImage 工具
 *
 * `decodeBitmap` / `downscaledToLongSide` / `UIImageJPEGRepresentation` 已经在
 * `ui/week/IosScheduleBackgroundImport.kt` 的真机路径上用过（长边钳制 + 降质量重编码），
 * 再写一份 CGContext 版本只会多一套没法验证的绘图代码。
 *
 * ## shortcut
 *
 * 没有「冷启动扫孤儿」：删笔记 / 删作业时共享层已经把「本次移除 - 全库仍引用」的差集交给
 * `deleteFiles`，日常不涨垃圾；只有「进程在插图与保存之间被杀」会留一两个孤儿文件。
 * 升级条件：iPhone 上出现「备份体积明显大于笔记体积」，那时在 `IosServices.warmUp` 里扫一次差集。
 */
internal class IosAttachmentStore {

    private val fs = platformFileSystem()

    private val dir: Path = (appDataDir() + "/" + DIR_NAME).toPath()

    fun exists(fileName: String): Boolean = runCatching { fs.exists(dir / fileName) }.getOrDefault(false)

    /** 读整份字节；读不出来回 null（调用方画空，不抛）。 */
    fun read(fileName: String): ByteArray? = runCatching {
        fs.read(dir / fileName) { readByteArray() }
    }.getOrNull()

    fun write(fileName: String, bytes: ByteArray): Boolean = runCatching {
        fs.createDirectories(dir)
        fs.write(dir / fileName) { write(bytes) }
        true
    }.getOrDefault(false)

    /** 删这些文件（不存在即忽略）。与 Android 同口径：只删差集，不删还在被引用的。 */
    fun delete(fileNames: Collection<String>) {
        fileNames.forEach { name -> runCatching { fs.delete(dir / name, mustExist = false) } }
    }

    /**
     * 选中的图 → 压缩落盘，返回文件名；失败回 null（调用方统计并提示一次）。
     *
     * 解码走 `NSData` + `UIImage`（选图器给的是文件 URL），与课表背景图同一条链。
     */
    suspend fun importPicked(picked: PickedImage): String? {
        val encoded = IosImageCompress.jpegBytes(
            picked = picked,
            maxSide = MAX_SIDE,
            quality = JPEG_QUALITY,
            maxBytes = MAX_BYTES,
        ) ?: return null
        val name = newName()
        return if (write(name, encoded)) name else null
    }

    private fun newName(): String = "img_${AppClock.nowMillis()}_${Random.nextInt(1000, 9999)}.jpg"

    private companion object {
        const val DIR_NAME = "notes_img"

        /** 与 Android `ImageImport` 的长边上限一致。 */
        const val MAX_SIDE = 1600

        /** JPEG 质量（与 Android `JPEG_QUALITY` 同值）。 */
        const val JPEG_QUALITY = 0.86
    }
}
