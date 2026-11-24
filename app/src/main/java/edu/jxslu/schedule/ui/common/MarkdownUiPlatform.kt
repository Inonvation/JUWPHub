package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Markdown 渲染槽的 Android 实现（iOS 移植 S13，2026-11-25）。
 *
 * 共享层的作业列表只认 `MarkdownUi`；真身 [MarkdownView] 依赖附件图解码
 * （[AttachmentThumbnailRow]）与公式排版（`MathText`），两者都留在 `:app`，所以在这里装槽。
 *
 * 图片档位固定 `MdImageStyle.Thumbnail`：这个槽只服务于列表里的正文（72dp、240px 解码），
 * 笔记 / 作业详情页仍在 `:app`，自己直接调 [MarkdownView]。
 *
 * 不按 `context` 记忆：闭包只用到传入的 modifier 与回调，没有 Activity 引用。
 */
@Composable
fun rememberMarkdownUi(): MarkdownUi = remember {
    MarkdownUi(
        render = { markdown, modifier, onImageClick, onLinkClick ->
            MarkdownView(
                markdown = markdown,
                modifier = modifier,
                imageStyle = MdImageStyle.Thumbnail,
                onImageClick = onImageClick,
                onLinkClick = onLinkClick,
            )
        },
    )
}
