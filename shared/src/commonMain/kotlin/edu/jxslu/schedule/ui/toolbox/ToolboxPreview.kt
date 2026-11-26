package edu.jxslu.schedule.ui.toolbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import edu.jxslu.schedule.ui.common.AppCard

/**
 * 工具页的预览区（水印 / 拼接共用，iOS 移植 V4，2026-11-26 搬进共享层）：
 * 有图显图（等比完整显示、**点开全屏看大图**），无图给「点一下选图」的占位。
 *
 * 与改造前的 `ui/toolbox/ToolboxBitmap.kt` 同一形态，只把位图类型换成 Compose 的
 * [ImageBitmap]。全屏看图的那套（黑底 + 双指缩放 + 点空白关闭）也随之下沉——
 * 改造前它调的是 `:app` 的 `ImageViewerDialog(bitmap: Bitmap)`，那个重载吃
 * `android.graphics.Bitmap`，共享层用不了；:app 那份留给笔记 / 校历 / AI 截图四处，
 * 不合并（它们要按文件名解码，口径不同）。
 */
@Composable
internal fun ToolboxPreview(
    image: ImageBitmap?,
    placeholder: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 有图时点开全屏，无图时去选图
    var viewing by remember { mutableStateOf(false) }
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            if (image == null) onPick() else viewing = true
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (image == null) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            } else {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "点图放大",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                )
            }
        }
    }
    if (viewing && image != null) {
        ToolboxImageViewer(image = image, onDismiss = { viewing = false })
    }
}

/**
 * 全屏看内存里的位图：黑底 + 双指缩放 / 拖动 + 点空白关闭 + 右上关闭键。
 *
 * 与 `:app` 的 `ImageViewerDialog` 同一个交互口径（缩放夹在 1..5 倍、缩回 1 倍时清偏移），
 * 这里只认 [ImageBitmap]，因此两端都在。
 */
@Composable
private fun ToolboxImageViewer(image: ImageBitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformable: TransformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + offsetChange
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                // 点空白关闭（缩放态下的拖动由 transformable 消费，不会误触）
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .transformable(transformable),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
