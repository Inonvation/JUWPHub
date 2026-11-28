package edu.jxslu.schedule.ui.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import edu.jxslu.schedule.data.local.IosAiImageStore
import edu.jxslu.schedule.ui.platform.rememberImagePicker
import org.jetbrains.skia.Image as SkiaImage

/**
 * AI 调课的图片附件 UI（iOS，2026-11-29）。
 *
 * 补的是 `AiChatScreen(imageUi = null)` 那一格：输入区的「截图 / 相册」入口、缩略图与全屏看图。
 * 三样都建立在 [IosAiImageStore] 上（与 `AiChatViewModel(imageStore = …)` 同一个实例）。
 *
 * ## 选图
 *
 * 用 `rememberImagePicker`（与百宝箱 / 笔记附件同一条缝）：选中的 `PickedImage` 逐张交给
 * 页面回调（`AiChatViewModel.addImage`），落盘与压缩在 store 里。**最多 4 张**由 store 的
 * `maxImages` 与 VM 的双重闸门保证。
 *
 * ## 粘图为什么没有
 *
 * `pasteImages` 传 null：iOS 的剪贴板读图要 `UIPasteboard.image` + `UIImage` 解码，
 * 而 Kotlin/Native 里那组符号没有先例（相册那条走的是文件 URL）。入口不渲染，
 * 不是「点了没反应」——升级条件：出现第二个需要剪贴板图片的功能时再补。
 */
@Composable
internal fun rememberIosAiImageUi(
    store: IosAiImageStore,
    onAddImage: (Any) -> Unit,
): AiImageUi {
    var viewerFile by remember { mutableStateOf<String?>(null) }
    val latestAdd by rememberUpdatedState(onAddImage)
    val picker = rememberImagePicker(maxItems = store.maxImages) { picked ->
        picked.forEach { image -> latestAdd(image) }
    }

    viewerFile?.let { name ->
        IosAiViewer(fileName = name, store = store, onClose = { viewerFile = null })
    }

    return remember(store, picker) {
        AiImageUi(
            store = store,
            thumb = { fileName, modifier, contentDescription ->
                IosAiThumb(fileName = fileName, store = store, modifier = modifier, description = contentDescription)
            },
            openViewer = { fileName -> viewerFile = fileName },
            pickImages = { picker() },
            pasteImages = null,
        )
    }
}

@Composable
private fun IosAiThumb(
    fileName: String,
    store: IosAiImageStore,
    modifier: Modifier,
    description: String?,
) {
    val bitmap = rememberAiBitmap(fileName, store)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = description,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun IosAiViewer(
    fileName: String,
    store: IosAiImageStore,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = rememberAiBitmap(fileName, store)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text("图片读不出来", color = Color.White)
            }
        }
    }
}

@Composable
private fun rememberAiBitmap(fileName: String, store: IosAiImageStore): ImageBitmap? =
    remember(fileName) { IosAiImageCache.image(fileName, store) }

/** 会话内最多 4 张、单张 ≤1600px，常驻缓存不会过界（4 × ~7MB）。 */
private object IosAiImageCache {

    private val decoded = LinkedHashMap<String, ImageBitmap?>()

    fun image(fileName: String, store: IosAiImageStore): ImageBitmap? {
        if (decoded.containsKey(fileName)) return decoded[fileName]
        val bitmap = store.read(fileName)?.let { bytes ->
            runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
        decoded[fileName] = bitmap
        return bitmap
    }
}
