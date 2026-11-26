package edu.jxslu.schedule.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 「从相册 / 文件里选图片」的平台缝（iOS 移植 V4，2026-11-26）。
 *
 * ## 为什么要这条缝
 *
 * 百宝箱的图片水印与拼接要「读一张 / 读几张图」：Android 是系统 Photo Picker
 * （`rememberLauncherForActivityResult` + `PickVisualMedia`，零权限），iOS 是
 * `UIDocumentPickerViewController(forOpeningContentTypes: [UTTypeImage])`。两边机制完全不同，
 * 但调用方要的是同一件事：**拿到若干张图，之后按需读尺寸、按需按目标分辨率解码**。
 *
 * 形状照 `ui/platform/FilePickers.kt`（选文件 / 另存）与 P0 立 `PixelBitmap` 那次：
 * 平台句柄留在 expect class 里，字节与像素只在需要那一刻现读。
 *
 * ## 为什么句柄不是 `ByteArray`
 *
 * 一次最多拼 9 张照片，若在选中那一刻就把 9 份原文件字节搬进内存，等于把「解码内存纪律」
 * 换成了「原始字节内存纪律」（一张手机照片 3–8MB，9 张就是几十 MB，且整屏期间一直压着）。
 * 句柄方式与改造前 Android 的行为一致：**选中时不读像素**，缩略图、成品、行比对各自现取现放。
 *
 * ## 三个成员的口径（两端一致）
 *
 * - [id]：平台侧的稳定标识（Android 是 `content://` 字符串，iOS 是文件 URL 字符串）。
 *   拼接列表靠它去重（同一张选两次只留一份）。
 * - [bounds]：**原图**像素宽高，只读边界不解全图（Android `inJustDecodeBounds`；
 *   iOS 走 skia 的惰性解码，只问宽高）。读不出回 `null`，不抛。
 * - [decode]：按长边上限降采样解码（预览 1200、成品 2600、缩略图 220 三档）。
 *   读不出回 `null`，不抛；**解码失败不许静默**，调用方要给一句提示
 *   （`.agents/rules/toolbox.md`「两遍解码的判空陷阱」）。
 *
 * `internal`：目前只有百宝箱两页在用。等第二个调用方出现、把参数口径谈清楚再放开，
 * 别提前设计成通用相册 API。
 */
internal expect class PickedImage {
    /** 平台侧稳定标识（去重用）。 */
    val id: String

    /** 原图像素宽高；读不出回 null（不抛）。 */
    suspend fun bounds(): Pair<Int, Int>?

    /** 按长边上限解码；读不出回 null（不抛）。 */
    suspend fun decode(maxLongSidePx: Int): ImageBitmap?
}

/**
 * 选图器：返回一个「拉起系统选图面板」的动作，结果经 [onPicked] 回传（**空列表 = 用户取消**）。
 *
 * [maxItems] 为 1 时是单选（水印）；大于 1 时允许多选（拼接，目前上限 9）。
 *
 * 回调在**组合期**就定下来：Android 建 launcher 那一刻就固定了 onResult，iOS 的代理对象
 * （`delegate` 是 weak，必须强引用住）也只能挂在 `remember` 上——与 `FilePickers` 同一条理由。
 */
@Composable
internal expect fun rememberImagePicker(
    maxItems: Int,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit
