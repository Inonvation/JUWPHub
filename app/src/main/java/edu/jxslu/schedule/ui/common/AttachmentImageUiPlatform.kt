package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 附件图能力的 Android 实现（iOS 移植 S13，2026-11-25）。
 *
 * 共享层的笔记 / 作业页只认 `AttachmentImageUi` 这个槽；缩略图解码
 * （[AttachmentThumbnailRow] → `loadAttachmentBitmap`，`BitmapFactory` + LRU）与全屏查看器
 * （[ImageViewerDialog]）都是 Android 实现，留在 `:app`，由这里装进槽里。
 *
 * 全屏查看器**由本函数自己发出**（`viewer` 状态也在这里）：调用点只需要
 * `val images = rememberAttachmentImageUi()` 再把 `images` 传给页面，
 * 不必各自再记一份「哪张图要放大」的状态，也不会出现「页面有槽、宿主忘了画弹窗」。
 */
@Composable
fun rememberAttachmentImageUi(): AttachmentImageUi {
    var viewer by remember { mutableStateOf<String?>(null) }
    val ui = remember {
        AttachmentImageUi(
            thumbnailRow = { fileNames, size, onOpen ->
                AttachmentThumbnailRow(fileNames = fileNames, size = size, onOpen = onOpen)
            },
            openViewer = { viewer = it },
        )
    }
    viewer?.let { fileName ->
        ImageViewerDialog(fileName = fileName, onDismiss = { viewer = null })
    }
    return ui
}
