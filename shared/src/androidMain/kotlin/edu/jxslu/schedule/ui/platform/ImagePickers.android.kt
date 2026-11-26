package edu.jxslu.schedule.ui.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import edu.jxslu.schedule.platform.requireAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PickedImage] / [rememberImagePicker] 的 Android 实现（iOS 移植 V4，2026-11-26）：
 * 系统 Photo Picker + `BitmapFactory` 两遍降采样解码。
 *
 * 这是 `:app` 里那两份实现（`ui/toolbox/ToolboxBitmap.kt` 的 `rememberSingleImagePicker` /
 * `readImageBounds`、`ui/common/DecodeSampled.kt` 的 `decodeUriSampled`）的**合并搬移**：
 * 选图与解码原来分散在三个文件里，现在收在一条缝后面，两个工具页不再各拼一遍。
 *
 * ## 三个必须保持的坑（都是真机踩过的）
 *
 * 1. **`inJustDecodeBounds = true` 时 `decodeStream` 必定返回 null**（只量尺寸、不产图）。
 *    所以不能把它的返回值串进 `?:` 判空链——那一串会把「量到了尺寸」误判成「流打不开」，
 *    整条两遍解码永远返回 null（2026-10-12 真机：三个工具全读不出图）。
 *    本文件里流单独取出来判空，`use { decodeStream(...) }` 只当语句用，成败看 `outWidth`。
 * 2. **采样倍率是 2 的幂**（[sampleSizeFor]）：与改造前 `decodeUriSampled` 同一判据，
 *    预览与成品的观感才一致。
 * 3. **解码端钉死 `ARGB_8888`**：10-bit / HDR 照片在 API 34+ 会被解成 `RGBA_F16` /
 *    `RGBA_1010102`，非常规 config 不该漏给下游的出图路径。
 */
internal actual class PickedImage internal constructor(internal val uri: Uri) {

    actual val id: String get() = uri.toString()

    actual suspend fun bounds(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // 见类 KDoc 第 1 条：量尺寸这一遍 decodeStream 返回 null 是常态，不能用它判失败
        val stream = requireAppContext("PickedImage.bounds()").contentResolver
            .openInputStream(uri) ?: return@withContext null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        bounds.outWidth to bounds.outHeight
    }

    actual suspend fun decode(maxLongSidePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        decodeUriSampled(uri, maxLongSidePx)?.asImageBitmap()
    }
}

/**
 * Content Uri 版的降采样解码（两遍都重新开流——`ContentResolver` 的 InputStream 不可 seek）。
 *
 * 与搬移前 `ui/common/DecodeSampled.kt` 的 `decodeUriSampled` 逐行相同，只把 `Context`
 * 参数换成注入点里的那个；**没有**改成先读字节再 `decodeByteArray`，那等于把整份文件
 * 再拷一遍进内存。
 */
private fun decodeUriSampled(uri: Uri, targetLongSidePx: Int): Bitmap? {
    val context = requireAppContext("PickedImage.decode()")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), targetLongSidePx)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

/** 「目标长边 → 采样倍率」判据（2 的幂，直到再降一半就低于目标）。与 `DecodeSampled` 同口径。 */
private fun sampleSizeFor(longSidePx: Int, targetLongSidePx: Int): Int {
    var sample = 1
    while (longSidePx / (sample * 2) >= targetLongSidePx) sample *= 2
    return sample
}

/**
 * 系统 Photo Picker：单选走 `PickVisualMedia()`，多选走 `PickMultipleVisualMedia(maxItems)`。
 *
 * 两条分支各自 `rememberLauncherForActivityResult`：合同类型不同，launcher 也必须是两个。
 * `maxItems` 在拼接页是常量 9，不会来回变；变了也只是换一条分支，不影响已选中的图。
 *
 * Photo Picker 不需要任何存储权限（API 19+ 的系统选择器），所以这里没有权限申请。
 */
@Composable
internal actual fun rememberImagePicker(
    maxItems: Int,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit {
    // 回调可能随重组换新实例，launcher 却只建一次：读最新的那个
    val currentOnPicked = rememberUpdatedState(onPicked)
    val request = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }
    if (maxItems <= 1) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) currentOnPicked.value(listOf(PickedImage(uri)))
        }
        return remember(launcher) { { launcher.launch(request) } }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems.coerceAtLeast(2)),
    ) { uris ->
        if (uris.isNotEmpty()) currentOnPicked.value(uris.map { PickedImage(it) })
    }
    return remember(launcher) { { launcher.launch(request) } }
}
