package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import edu.jxslu.schedule.data.local.IosAttachmentStore
import edu.jxslu.schedule.domain.imageRefToken
import edu.jxslu.schedule.domain.imageRefs
import edu.jxslu.schedule.ui.platform.rememberImagePicker
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage

/**
 * 笔记 / 作业的附件图在 iOS 上的渲染与编辑（2026-11-29）。
 *
 * ## 为什么现在才有
 *
 * 共享层的三张页面一直带着三个可空槽（`MarkdownImageUi` / `AttachmentImageUi` /
 * `MarkdownEditorUi`），iOS 三处全传 null：正文退化成纯文本、编辑态没有底栏。
 * 缺的其实是四样平台件——附件目录、位图解码、选图、全屏查看器。前两样
 * （`IosAttachmentStore` + skia）与后两样（`rememberImagePicker` + 本文件的查看器）
 * 现在都齐了，这一批把槽填上。
 *
 * ## 解码与缓存
 *
 * 存的是 JPEG（长边 ≤1600），显示走 skia 惰性解码。缓存**按文件名常驻 24 张**：
 * 一张 1600×1200 的位图约 7MB，24 张已经 170MB 上限——再多就要按屏幕尺寸做降采样，
 * 那是下一步的事（见 [IosAttachmentCache] 的注释）。
 *
 * ## 全屏查看器
 *
 * 用 Compose 的 `Dialog`（不是 `ImageViewerDialog`，那是 Android 的）：黑底、点任意处关闭。
 * 没有缩放手势——百宝箱的预览器也没做，别在一个查看器上引入没法真机验的手势系统。
 */
@Composable
internal fun rememberIosMarkdownImageUi(): MarkdownImageUi {
    val store = remember { IosAttachmentStore() }
    return remember(store) {
        MarkdownImageUi(
            fullWidth = { fileName, onClick ->
                IosAttachmentImage(fileName = fileName, store = store, onClick = onClick)
            },
            thumbnailRow = { fileNames, onOpen ->
                IosAttachmentThumbnailRow(fileNames = fileNames, size = 72.dp, store = store, onOpen = onOpen)
            },
        )
    }
}

@Composable
internal fun rememberIosAttachmentImageUi(): AttachmentImageUi {
    val store = remember { IosAttachmentStore() }
    var viewerFile by remember { mutableStateOf<String?>(null) }
    val ui = remember(store) {
        AttachmentImageUi(
            thumbnailRow = { fileNames, size, onOpen ->
                IosAttachmentThumbnailRow(fileNames = fileNames, size = size, store = store, onOpen = onOpen)
            },
            openViewer = { fileName -> viewerFile = fileName },
        )
    }
    viewerFile?.let { name ->
        IosAttachmentViewer(
            fileName = name,
            store = store,
            onClose = { viewerFile = null },
        )
    }
    return ui
}

@Composable
internal fun rememberIosMarkdownEditorUi(): MarkdownEditorUi {
    val store = remember { IosAttachmentStore() }
    return remember(store) {
        MarkdownEditorUi(
            bottomBar = { value, onValueChange, onNotice, onRemoveImage, onOpenImage ->
                IosMarkdownEditorBar(
                    value = value,
                    onValueChange = onValueChange,
                    store = store,
                    onNotice = onNotice,
                    onRemoveImage = onRemoveImage,
                    onOpenImage = onOpenImage,
                )
            },
            deleteFiles = { names -> store.delete(names) },
        )
    }
}

/** 列表里的正文渲染槽：附件图能力接上之后，这个对象就有意义了。 */
@Composable
internal fun rememberIosMarkdownUi(): MarkdownUi {
    val images = rememberIosMarkdownImageUi()
    return remember(images) {
        MarkdownUi(
            render = { markdown, modifier, onImageClick, onLinkClick ->
                MarkdownContent(
                    markdown = markdown,
                    modifier = modifier,
                    imageStyle = MdImageStyle.Thumbnail,
                    onImageClick = onImageClick,
                    onLinkClick = onLinkClick,
                    images = images,
                )
            },
        )
    }
}

// ---------------------------------------------------------------- 绘制

@Composable
private fun IosAttachmentImage(
    fileName: String,
    store: IosAttachmentStore,
    onClick: (() -> Unit)?,
) {
    val bitmap = rememberAttachmentBitmap(fileName, store) ?: return
    Image(
        bitmap = bitmap,
        contentDescription = fileName,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
private fun IosAttachmentThumbnailRow(
    fileNames: List<String>,
    size: Dp,
    store: IosAttachmentStore,
    onOpen: ((String) -> Unit)?,
) {
    if (fileNames.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fileNames.forEach { name ->
            val bitmap = rememberAttachmentBitmap(name, store) ?: return@forEach
            Image(
                bitmap = bitmap,
                contentDescription = name,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onOpen != null) Modifier.clickable { onOpen(name) } else Modifier),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun IosAttachmentViewer(
    fileName: String,
    store: IosAttachmentStore,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = rememberAttachmentBitmap(fileName, store)
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

/** 按文件名取解码后的位图；读盘 + 解码走 [IosAttachmentCache]，同一张只解一次。 */
@Composable
private fun rememberAttachmentBitmap(fileName: String, store: IosAttachmentStore): ImageBitmap? =
    remember(fileName) { IosAttachmentCache.image(fileName, store) }

// ---------------------------------------------------------------- 编辑底栏

@Composable
private fun IosMarkdownEditorBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    store: IosAttachmentStore,
    onNotice: (String, NoticeTone) -> Unit,
    onRemoveImage: (String) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 选图回调在组合期就固定下来（launcher 只建一次），用 rememberUpdatedState 拿最新正文
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    val picker = rememberImagePicker(maxItems = MAX_PICK) { picked ->
        if (picked.isEmpty()) return@rememberImagePicker
        scope.launch {
            val names = mutableListOf<String>()
            var failed = 0
            picked.forEach { image ->
                val name = store.importPicked(image)
                if (name == null) failed += 1 else names += name
            }
            if (names.isNotEmpty()) {
                val current = latestValue
                val refs = names.joinToString(separator = "\n") { imageRefToken(it) }
                val insertAt = current.selection.end.coerceIn(0, current.text.length)
                val text = buildString {
                    append(current.text.substring(0, insertAt))
                    if (insertAt > 0 && current.text[insertAt - 1] != '\n') append('\n')
                    append(refs)
                    append('\n')
                    append(current.text.substring(insertAt))
                }
                val cursor = insertAt + refs.length + 2
                latestChange(current.copy(text = text, selection = TextRange(cursor)))
            }
            if (failed > 0) onNotice("有 $failed 张图片没能读取", NoticeTone.Warning)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            val names = imageRefs(value.text).toList()
            if (names.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    names.forEach { name ->
                        Box(contentAlignment = Alignment.TopEnd) {
                            val bitmap = rememberAttachmentBitmap(name, store)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = name,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenImage(name) },
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            // 移除：只从正文删引用，文件由页面算差集后交 deleteFiles。
                            // 22dp 触控区 + 半透明底：56dp 的图右下角放 20dp 纯文字点不准（HIG 最小 44）。
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                    .clickable { onRemoveImage(name) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "×",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
            MarkdownToolbar(
                value = value,
                onValueChange = onValueChange,
                onPickImages = { picker() },
            )
        }
    }
}

/** 一次最多插几张（与 Android 的选图上限一致）。 */
private const val MAX_PICK = 9

/**
 * 附件图解码缓存：**按文件名常驻 24 张**。
 *
 * 为什么不做 LRU 淘汰：笔记正文一屏最多十几张图，24 张足够覆盖一次翻页；再往上就要按
 * 显示尺寸降采样（现在整幅解出后由 `Image` 缩放），那是另一件事。
 *
 * 为什么只缓存 `ImageBitmap` 不缓存原始字节：字节只在解码那一刻有用，留着只会多占内存。
 */
private object IosAttachmentCache {

    private const val MAX_ENTRIES = 24

    private val decoded = LinkedHashMap<String, ImageBitmap?>()

    fun image(fileName: String, store: IosAttachmentStore): ImageBitmap? {
        decoded[fileName]?.let { return it }
        if (decoded.containsKey(fileName)) return null
        val bitmap = store.read(fileName)?.let { bytes ->
            runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
        decoded[fileName] = bitmap
        while (decoded.size > MAX_ENTRIES) {
            val oldest = decoded.keys.firstOrNull() ?: break
            decoded.remove(oldest)
        }
        return bitmap
    }
}
