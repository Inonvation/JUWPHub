package edu.jxslu.schedule.ui.ebike

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.domain.BarcodeMatrix
import edu.jxslu.schedule.domain.EbikeQr
import edu.jxslu.schedule.domain.GalleryPaths
import edu.jxslu.schedule.ui.common.CodeBitmaps
import edu.jxslu.schedule.ui.common.GallerySaveResult
import edu.jxslu.schedule.ui.common.saveBitmapToGallery
import java.io.File

/**
 * 快趣出码页两条平台缝的 **Android 生产实现**（iOS 移植 P4，2026-11-25）。
 *
 * 搬迁前这两块直接在 `:app` 的 `EbikeViewModel` / `EbikeQrBitmaps` / `KvcxRideSideEffects`
 * 里：媒体库写入走 `MediaStore`、提醒走 `AlarmManager` + 通知渠道——都是 Android 专有件，
 * 共享层只认 `EbikeReminderPort` / `EbikeQrGallery` 两个接口（见 `EbikeCodePlatform.kt`）。
 *
 * 这个对象把接口转接到原有实现，**口径一字未改**：
 *
 * | 接口 | 转接 |
 * |---|---|
 * | [EbikeReminderPort] | `EbikeFreeRideReminder` / `EbikeAutoReturnReminder`（都留在 `:app`） |
 * | [EbikeQrGallery] | 本文件下半段：`CodeBitmaps.render` → `saveBitmapToGallery`（MediaStore / 公共目录直写） |
 *
 * 为什么绑定 `Graph.appContext` 而不是每次传 `Context`：装配根在进程启动时就有一份
 * application context，而这两条缝的调用方（共享层 ViewModel / `AppKvcxSideEffects`）
 * 里没有 `Context` 可传——有的话它们就不在共享层了。
 */
internal object AndroidEbikeCodePlatform : EbikeReminderPort, EbikeQrGallery {

    private val appContext: Context get() = Graph.appContext

    // ---- EbikeReminderPort ----

    override suspend fun startFreeRide(startAtMillis: Long): EbikeReminderOutcome =
        EbikeFreeRideReminder.startRide(appContext, startAtMillis).toShared()

    override suspend fun checkFreeRide(): EbikeReminderOutcome =
        EbikeFreeRideReminder.check(appContext).toShared()

    override suspend fun endFreeRide(): EbikeReminderOutcome =
        EbikeFreeRideReminder.endRide(appContext).toShared()

    override suspend fun reconcileAutoReturn(catchUp: Boolean) {
        EbikeAutoReturnReminder.reconcile(appContext, catchUp = catchUp)
    }

    override suspend fun cancelAutoReturn() {
        EbikeAutoReturnReminder.cancel(appContext)
    }

    /** `:app` 的 Outcome → 共享层的 Outcome（逐项一一对应，改口径时两边一起看）。 */
    private fun EbikeFreeRideReminder.Outcome.toShared(): EbikeReminderOutcome = when (this) {
        EbikeFreeRideReminder.Outcome.Started -> EbikeReminderOutcome.Started
        EbikeFreeRideReminder.Outcome.StartedSilent -> EbikeReminderOutcome.StartedSilent
        EbikeFreeRideReminder.Outcome.StartedNoNotification -> EbikeReminderOutcome.StartedNoNotification
        EbikeFreeRideReminder.Outcome.Ended -> EbikeReminderOutcome.Ended
        EbikeFreeRideReminder.Outcome.Nothing -> EbikeReminderOutcome.Nothing
        is EbikeFreeRideReminder.Outcome.Failed -> EbikeReminderOutcome.Failed(message)
    }

    // ---- EbikeQrGallery ----

    override suspend fun save(
        matrix: BarcodeMatrix,
        dark: Boolean,
        displayName: String,
    ): EbikeQrSaveResult = saveRenderedToGallery(appContext, CodeBitmaps.render(matrix, dark), displayName)

    override suspend fun deletePending(key: String): Boolean = deleteSaved(appContext, key)

    /**
     * 把一张已渲染的码存进系统相册（`Pictures/JUWPHub`；旧图留在 `Pictures/水贝贝`，不迁移）。
     * API 29+ 走 MediaStore，无需权限；26–28 走公共目录 File 直写（manifest 已声明 legacy 外存权限）。
     * 成功返回 [EbikeQrSaveResult.Saved]（附待焚毁 key，扫完即焚用）；失败返回带原因的 Failed。
     *
     * 落盘本身委托给共用的 `saveBitmapToGallery`（课表导出图片 §4.12 同一条路）；
     * 本对象只保留「待焚毁 key」这段与共享单车绑定的语义。
     */
    private fun saveRenderedToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
    ): EbikeQrSaveResult = when (
        val result = saveBitmapToGallery(
            context = context,
            bitmap = bitmap,
            displayName = displayName,
            format = Bitmap.CompressFormat.JPEG,
            quality = 90,
        )
    ) {
        is GallerySaveResult.Saved -> EbikeQrSaveResult.Saved(
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                EbikeQr.pendingMediaKey(result.location)
            } else {
                EbikeQr.pendingFileKey(result.location)
            },
        )
        is GallerySaveResult.Failed -> EbikeQrSaveResult.Failed(result.message)
    }

    /** 本功能落盘的相盘子目录（与 [saveRenderedToGallery] 同源，删除校验用）。 */
    private const val GALLERY_SUBDIR = GalleryPaths.CURRENT_SUBDIR

    /** 旧目录：旧图仍留在那里，旧 key 删除校验仍要认。 */
    private const val LEGACY_GALLERY_SUBDIR = GalleryPaths.LEGACY_SUBDIR

    /**
     * 按 key 删除一张已保存的二维码（扫完即焚，DESIGN §3.9）。只处理本功能自己
     * 写入的条目：API 29+ 是自己 insert 的 MediaStore uri（owner 删除免权限免确认）；
     * 26–28 是本功能写的公共目录文件。key 目标再做一次包含性校验——key 唯一
     * 写入方是 [saveRenderedToGallery]，校验只防 DataStore 被篡改/损坏后误删别家文件。
     * 返回 true = 已删除；false = 失败/不存在/校验不过（调用方保留 key 下次重试）。
     *
     * 非挂起：`EbikeFreeRideReminder` 的免费结束清理也走它（那里本来就在协程里）。
     */
    fun deleteSaved(context: Context, key: String): Boolean {
        val parsed = EbikeQr.parsePendingKey(key) ?: return false
        return try {
            when (parsed.first) {
                EbikeQr.PendingKind.MediaStore -> if (
                    android.os.Build.VERSION.SDK_INT >= 29
                ) {
                    val uri = android.net.Uri.parse(parsed.second)
                    // 本功能的 MediaStore 条目只会落在 images 集合；host 校验防任意 content uri
                    if (uri.authority != "media" || !uri.path.orEmpty().contains("images")) {
                        false
                    } else {
                        context.contentResolver.delete(uri, null, null) > 0
                    }
                } else {
                    false // 不应出现（29+ 才有 MediaStore key），防御性丢弃
                }
                EbikeQr.PendingKind.FilePath -> {
                    val path = File(parsed.second).canonicalPath
                    val pictures =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val currentDir = File(pictures, GALLERY_SUBDIR).canonicalPath
                    val legacyDir = File(pictures, LEGACY_GALLERY_SUBDIR).canonicalPath
                    // 只删自己目录下的文件（canonicalPath 兼防 ../ 穿越写法）；
                    // 同时认旧目录，旧图留在那里时仍能焚毁。
                    val isCurrent = path.startsWith("$currentDir${File.separatorChar}")
                    val isLegacy = path.startsWith("$legacyDir${File.separatorChar}")
                    if (!isCurrent && !isLegacy) {
                        false
                    } else {
                        File(path).delete()
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
