package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Markdown 渲染栈的 Android 平台槽（iOS 移植 S21，2026-11-25）。
 *
 * 渲染器本体 [MarkdownContent] 与公式排版 [MathText] 已搬进 `shared/commonMain`；
 * 留在 `:app` 的只有**附件图解码**（`BitmapFactory` 降采样 + 按字节计的 LRU，见
 * `AttachmentImage.kt` / `AttachmentThumbnail.kt`）——`commonMain` 拿不到位图 API，
 * iOS 侧也还没写。这个文件把那份能力装进共享层的槽 [MarkdownImageUi] 里：
 *
 * | 入口 | 用在哪 | 槽 |
 * |---|---|---|
 * | [MarkdownView] | 笔记 / 作业详情页正文（满宽真图） | [MarkdownImageUi] |
 * | [rememberMarkdownUi] | 列表页正文（`MarkdownUi` 槽，固定缩略图档位） | [MarkdownImageUi] |
 *
 * **名字为什么是 `MarkdownView` 而不是共享层的 `MarkdownContent`**：详情页（`NoteDetailScreen`
 * 457 行 / `HomeworkDetailScreen` 741 行）还留在 `:app`，它们直接调 `MarkdownView`；
 * 共享层的渲染器只能换个名字，否则同一个包里两个同名函数。
 */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    imageStyle: MdImageStyle = MdImageStyle.FullWidth,
    onImageClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
) {
    MarkdownContent(
        markdown = markdown,
        modifier = modifier,
        imageStyle = imageStyle,
        onImageClick = onImageClick,
        onLinkClick = onLinkClick,
        images = rememberMarkdownImageUi(),
    )
}

/**
 * 共享层的作业 / 笔记列表只认 `MarkdownUi`；真身 [MarkdownContent] 依赖附件图槽。
 *
 * 图片档位固定 `MdImageStyle.Thumbnail`：这个槽只服务于列表里的正文（72dp、240px 解码）。
 *
 * 不按 `context` 记忆：闭包只用到传入的 modifier 与回调，没有 Activity 引用。
 */
@Composable
fun rememberMarkdownUi(): MarkdownUi {
    val images = rememberMarkdownImageUi()
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

/** 附件图槽的 Android 装配：满宽真图走 [AttachmentImage]，列表档位走 [AttachmentThumbnailRow]。 */
@Composable
private fun rememberMarkdownImageUi(): MarkdownImageUi = remember {
    MarkdownImageUi(
        fullWidth = { fileName, onClick -> AttachmentImage(fileName = fileName, onClick = onClick) },
        thumbnailRow = { fileNames, onOpen -> AttachmentThumbnailRow(fileNames = fileNames, onOpen = onOpen) },
    )
}
