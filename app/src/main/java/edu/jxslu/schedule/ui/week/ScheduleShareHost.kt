package edu.jxslu.schedule.ui.week

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.domain.BgScale
import edu.jxslu.schedule.domain.ScheduleBackground
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.GallerySaveResult
import edu.jxslu.schedule.ui.common.saveBitmapToGallery
import edu.jxslu.schedule.ui.common.shareFile
import edu.jxslu.schedule.ui.common.shareUriOf
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 课表导出图片（DESIGN §4.12）的 **Android 宿主**（iOS 移植 M2，2026-11-26）。
 *
 * ## 为什么这个文件里有一个和共享层同名的 `ScheduleShareScreen`
 *
 * 页面本身在 `shared/commonMain/.../ui/week/ScheduleShareScreen.kt`，签名是
 * `ScheduleShareScreen(viewModel, onBack, initialWeek, loadBackground, onSaveToGallery, onShareImage)`
 * ——`WeekViewModel` 必填、三件平台事是可空槽位（`design/ios-port.md` §11.2 ①④）。
 *
 * 而 `SubpageActivity` 的 `SubpageScreen.SCHEDULE_SHARE` 分支正在被另一条分支改，本轮不碰它
 * （那里的 `ScheduleShareScreen(onBack = …, initialWeek = …)` 一字不动）。所以这里放一个**同包
 * 同名的双参重载**当装配点：它建 ViewModel、把三件平台事填成 Android 实现，再调共享层那个。
 * 两个重载靠 `viewModel` 这个必填参数区分（共享层那个选不中两参调用），调用方零改动。
 *
 * 等 `SubpageActivity` 的冲突解掉，可以把本文件的函数并入那一行、删掉这层转发——
 * 转发里没有任何业务逻辑，删它不影响行为。
 *
 * ## 三件平台事各是什么
 *
 * | 槽位 | Android 实现 | 为什么不能进共享层 |
 * |---|---|---|
 * | `loadBackground` | `loadScheduleBackground`（`BitmapFactory`，目标尺寸按 `DisplayMetrics` 长边算） | 解码是 `android.graphics`；iOS 要另一套图像 API |
 * | `onSaveToGallery` | `saveBitmapToGallery`（`MediaStore` / 公共目录直写） | 相册写入是平台能力（iOS 是 `PHPhotoLibrary`） |
 * | `onShareImage` | PNG 编码 → `ScheduleShareStore` 落 `cacheDir/schedule_share` → `FileProvider` → `ACTION_SEND` | `content://` 的 authority 带 applicationId，只能在宿主拼；落盘那一段已搬进共享层 |
 *
 * 截图（`GraphicsLayer.toImageBitmap()`）留在共享层：两端都可用，交出去的是一张 `ImageBitmap`。
 *
 * `context` 一律用 `LocalContext` 拿到的那个（二级页窗口的 Activity），**不要**换成
 * `applicationContext`：`shareFile` → `startActivityOutsideApp` 靠 `as? SubpageActivity`
 * 抑制外部应用返回时的右推入过渡，换成 application context 就哑了（用户可感知的观感回退）。
 */
@Composable
fun ScheduleShareScreen(
    onBack: () -> Unit,
    initialWeek: Int? = null,
) {
    val context = LocalContext.current
    // 与课表页同一份 ViewModel 口径：共享层没有 `viewModel()`（它的 KMP 变体要 Compose 1.9），
    // 所以由宿主建——`viewModel {}` 让旋转（配置变更）不重建 VM 与它的仓库订阅。
    val viewModel: WeekViewModel = viewModel { WeekViewModel(Graph.repository(context)) }

    // 这里调的是共享层的六参重载（`viewModel` 必填），不是本函数自己。
    ScheduleShareScreen(
        viewModel = viewModel,
        onBack = onBack,
        initialWeek = initialWeek,
        loadBackground = { fileName, scale, blur ->
            // 目标长边与搬迁前逐字节一致：平铺按原图像素铺，其余按设备屏幕长边压
            val targetLongSidePx = if (scale == BgScale.Tile) {
                ScheduleBackground.decodeLongSide(blur)
            } else {
                ScheduleBackground.targetLongSide(screenLongSidePx(context), blur)
            }
            // cache = false：板上的解码目标与整屏渲染不同，进单槽缓存会把整屏那张挤掉
            loadScheduleBackground(context, fileName, targetLongSidePx, cache = false)
        },
        onSaveToGallery = { image, displayName ->
            withContext(Dispatchers.IO) {
                when (
                    val result = saveBitmapToGallery(
                        context = context,
                        bitmap = image.asAndroidBitmap(),
                        displayName = displayName,
                        format = Bitmap.CompressFormat.PNG,
                        quality = 100,
                    )
                ) {
                    is GallerySaveResult.Saved -> GallerySaveOutcome.Saved(result.location)
                    is GallerySaveResult.Failed -> GallerySaveOutcome.Failed(result.message)
                }
            }
        },
        onShareImage = { image, displayName ->
            withContext(Dispatchers.IO) {
                val bytes = runCatching { pngBytes(image) }.getOrNull()
                // 落盘走共享层的 ScheduleShareStore（目录仍是 cacheDir/schedule_share，
                // 与 res/xml/file_paths.xml 的 <cache-path> 根对齐）；content:// 只能在宿主拼。
                val uri = bytes?.let {
                    runCatching {
                        val store = Graph.scheduleShareStore(context)
                        val path = store.save(it, displayName)
                        shareUriOf(context, path)
                    }.getOrNull()
                }
                when {
                    bytes == null || uri == null -> "分享失败：无法写出图片"
                    // 走 :app 的 shareFile：它认得出二级页窗口，从分享面板返回时不会重放
                    // 一次右推入过渡（共享层那条 actual 只拿得到 applicationContext，
                    // 见 ui/common/ShareIntents.android.kt 文件头的 shortcut）。
                    shareFile(context, uri, "image/png", "分享课表图片") -> null
                    else -> "没有可用的分享目标"
                }
            }
        },
    )
}

/** 位图 → PNG 字节（原来内联在页内，随「保存 / 分享」一起搬到宿主）。 */
private fun pngBytes(image: ImageBitmap): ByteArray =
    ByteArrayOutputStream().use { out ->
        image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
