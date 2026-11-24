package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * 附件图（笔记 / 作业正文里的图片）的平台渲染（iOS 移植 S13，2026-11-25）。
 *
 * ## 为什么由宿主注入
 *
 * 解码走 `android.graphics.BitmapFactory` 的降采样 + LRU 缓存（`:app` 的
 * `loadAttachmentBitmap`），全屏查看器（`ImageViewerDialog`）也是 Android 实现。
 * 两件都拿不到 `commonMain` 能用的位图 API，所以整块交给宿主：**null = 本平台没有附件图
 * 能力**，缩略图行与点图入口整块不渲染（口径同 `WeekScreen` 的可空回调）。
 *
 * ## 为什么收成一个类而不是两个可空参数
 *
 * 两个能力必须同时存在才有意义：只有查看器没缩略图 = 用户点不到图；只有缩略图没查看器 =
 * 点了没反应。收成一个对象，调用点就没法只给一半。
 */
class AttachmentImageUi(
    /** 只读缩略图行；[size] 由调用方给（笔记展开区 44dp、作业正文 72dp）。 */
    val thumbnailRow: @Composable (
        fileNames: List<String>,
        size: Dp,
        onOpen: (String) -> Unit,
    ) -> Unit,
    /** 全屏看图（Android 走 `ImageViewerDialog`）。 */
    val openViewer: (String) -> Unit,
)

/**
 * Markdown 正文的渲染槽（笔记 / 作业列表共用）。
 *
 * `MarkdownView` 依赖附件图解码与公式排版（`MathText`），两者都还在 `:app`。
 * **null = 本平台还没有 Markdown 渲染器**：调用点退化成纯文本（作业正文仍然读得到），
 * 不把整块正文藏掉。
 *
 * 图片档位固定为「列表缩略图」（72dp、240px 解码）：这个槽只服务于列表里的正文，
 * 详情页仍在 `:app`，直接调 `MarkdownView`。
 */
class MarkdownUi(
    /** 渲染一段 Markdown；[modifier] 由调用点给（缩进 / 完成态压暗 / 点击区都在它上面）。 */
    val render: @Composable (
        markdown: String,
        modifier: Modifier,
        onImageClick: (String) -> Unit,
        onLinkClick: (String) -> Unit,
    ) -> Unit,
)
