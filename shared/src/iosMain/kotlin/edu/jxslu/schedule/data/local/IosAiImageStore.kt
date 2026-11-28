package edu.jxslu.schedule.data.local

import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import edu.jxslu.schedule.ui.ai.AiImageAttachments
import edu.jxslu.schedule.ui.platform.PickedImage
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

/**
 * AI 会话的图片附件存储（iOS，2026-11-29；对齐 `:app` 的 `AiImageStore`）。
 *
 * | 项 | Android | 这里 |
 * |---|---|---|
 * | 落点 | `filesDir/ai_img/` | `appDataDir()/ai_img/` |
 * | 压缩 | 长边 1600、质量 80、≤400KB | `IosImageCompress` 同参数（超限降质量重编） |
 * | 张数 | `MAX_IMAGES = 4` | 同 |
 * | 生命周期 | 会话级：新会话 / 页面销毁 / 冷启动一起清 | 同（`clear()` 在 VM 的 `onCleared` 里调） |
 * | 交给客户端的元素 | `java.io.File` | **绝对路径字符串**；编码器在 `IosServices` 里注入 |
 *
 * `payload` 返回路径而不是字节：编码器（`AiScheduleClient` 的 `imageEncoder`）只需要读一次，
 * 把 `ByteArray` 一直摊在 `attachments` 状态里只会让内存跟着张数涨。
 */
internal class IosAiImageStore : AiImageAttachments {

    private val fs = platformFileSystem()

    private val dir: Path = (appDataDir() + "/" + DIR_NAME).toPath()

    override val maxImages: Int = MAX_IMAGES

    override suspend fun import(source: Any): String? {
        val picked = source as? PickedImage ?: return null
        val encoded = IosImageCompress.jpegBytes(
            picked = picked,
            maxSide = MAX_SIDE,
            quality = JPEG_QUALITY,
            maxBytes = MAX_BYTES,
        ) ?: return null
        val name = newName()
        val ok = runCatching {
            fs.createDirectories(dir)
            fs.write(dir / name) { write(encoded) }
            true
        }.getOrDefault(false)
        return if (ok) name else null
    }

    override suspend fun delete(fileName: String) {
        runCatching { fs.delete(dir / fileName, mustExist = false) }
    }

    override suspend fun clear() {
        runCatching { fs.deleteRecursively(dir, mustExist = false) }
    }

    override fun payload(fileName: String): Any? {
        val path = dir / fileName
        return if (runCatching { fs.exists(path) }.getOrDefault(false)) path.toString() else null
    }

    /** 缩略图 / 全屏看图读字节用；读不出来回 null（画空，不抛）。 */
    fun read(fileName: String): ByteArray? = runCatching {
        fs.read(dir / fileName) { readByteArray() }
    }.getOrNull()

    private fun newName(): String = "ai_${AppClock.nowMillis()}_${Random.nextInt(1000, 9999)}.jpg"

    private companion object {
        const val DIR_NAME = "ai_img"

        /** 与 Android `AiImageStore.MAX_IMAGES` 同值：每张图都是 token 大户。 */
        const val MAX_IMAGES = 4

        const val MAX_SIDE = 1600

        /** JPEG 质量 80（Android 是整数 80，这里 0..1）。 */
        const val JPEG_QUALITY = 0.80

        /** 单张字节上限（`AiImageStore.MAX_BYTES` 同值）。 */
        const val MAX_BYTES = 400_000L
    }
}
