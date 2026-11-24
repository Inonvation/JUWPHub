package edu.jxslu.schedule.ui.campus

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.ui.common.ImageSource
import edu.jxslu.schedule.ui.common.ImageViewerDialog
import edu.jxslu.schedule.ui.common.decodeFileSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 校历页图片能力的 Android 实现（iOS 移植 W2，2026-11-25）。
 *
 * 两件事都是 Android 专有，所以留在 `:app`（契约在共享层的 [CampusCalendarImageUi]）：
 * - 解码走 `BitmapFactory` 的降采样（`decodeFileSampled`）——校历图是外部来源、尺寸不可控，
 *   整幅解码一张 3000×4000 就是 48MB；
 * - 全屏查看器是 `ImageViewerDialog`（双指缩放/拖动）。
 *
 * 内置兜底图是打包资源（`res/drawable-nodpi/ic_campus_calendar.webp`），共享层拿不到 `R`，
 * 所以「镜像图没下载到就画内置图」这条判断也在这里。
 */
fun campusCalendarImageUi(context: Context): CampusCalendarImageUi = CampusCalendarImageUi(
    image = { fileName, year, onClick ->
        // 图源：已下载镜像图优先，没有走内置兜底；两者都是本地解码，进页不卡网络。
        // 镜像图是外部来源、尺寸不可控，降采样到页内展示够用的长边（点图看大图时
        // ImageViewerDialog 会按 viewer 的目标宽重新解码一份）
        val localBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = fileName) {
            value = withContext(Dispatchers.IO) {
                Graph.campusCalendar(context).fileFor(fileName)
                    ?.let { decodeFileSampled(File(it), targetLongSidePx = 1600) }
                    ?.asImageBitmap()
            }
        }
        val mirror = localBitmap
        if (mirror != null) {
            CalendarImage(bitmap = mirror, year = year, onClick = onClick)
        } else {
            BuiltinFallbackImage(onClick = onClick)
        }
    },
    viewer = { fileName, visible, onDismiss ->
        if (visible) {
            // 镜像图没下载到（内置兜底状态）也照开——解不到文件时回退打包资源图，
            // 点击放大永远可用（`ImageSource.CampusCalendar` 分支的口径）
            ImageViewerDialog(
                fileName = fileName ?: BUILTIN_IMAGE_KEY,
                source = ImageSource.CampusCalendar,
                onDismiss = onDismiss,
            )
        }
    },
)

/** 已下载的镜像图：满宽按真实比例，圆角卡形态，点开全屏。 */
@Composable
private fun CalendarImage(bitmap: ImageBitmap, year: String, onClick: () -> Unit) {
    val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    Image(
        bitmap = bitmap,
        contentDescription = if (year.isNotBlank()) "$year 学年校历" else "校历",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.4f, 3f))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    )
}

/** 内置兜底图（res/drawable-nodpi/ic_campus_calendar.webp，2026-2027 学年）。 */
@Composable
private fun BuiltinFallbackImage(onClick: () -> Unit) {
    // 打包资源是固定图，比例写死 1375×1020（与 res 里那张一致；改图要同步这行）
    Image(
        painter = painterResource(R.drawable.ic_campus_calendar),
        contentDescription = "2026-2027 学年校历",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1375f / 1020f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    )
}

/** 内置兜底态传给看图弹窗的占位文件名（CampusCalendar 分支解不到文件即回退资源图）。 */
private const val BUILTIN_IMAGE_KEY = "builtin"
