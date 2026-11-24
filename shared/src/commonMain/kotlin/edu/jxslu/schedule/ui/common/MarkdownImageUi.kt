package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * 正文附件图的绘制能力（iOS 移植 S21，2026-11-25）。
 *
 * ## 为什么是槽
 *
 * 正文里的图片以 `img:<文件名>` 引用，真图在应用私有目录（`:app` 的 `AttachmentStore`），
 * 解码要走 `android.graphics.BitmapFactory` 的降采样 + 按字节计的 LRU——这套 API `commonMain`
 * 拿不到，iOS 侧也还没写。所以**只把绘制交给宿主**：Markdown 的解析、排版、点击命中全部留在
 * 共享层，两端同一份。
 *
 * **null = 本平台画不出附件图**：图片位置给一行说明（[MarkdownContent] 内部处理），
 * 不静默吞掉——用户得知道"这里有张图，只是这台设备看不到"，否则会以为笔记丢了内容。
 *
 * ## 为什么与 `AttachmentImageUi` 分开
 *
 * 那个槽服务于笔记 / 作业**列表**（只读缩略图 + 全屏查看器，查看器由宿主自己发出）；
 * 这个槽服务于**正文渲染**，多了块级满宽图，也不带查看器状态——点开交给调用方的
 * `onImageClick`。两个槽的生命周期与依赖不同，合成一个会让列表页被迫接受它用不上的满宽图。
 */
class MarkdownImageUi(
    /**
     * 块级满宽图。[onClick] 为 null = 这张图不可点（调用方没给 `onImageClick`）。
     */
    val fullWidth: @Composable (fileName: String, onClick: (() -> Unit)?) -> Unit,
    /**
     * 只读缩略图行：列表正文里连着的几张图合成一行 72dp 方图。
     * 与满宽图是**两种档位**（列表不能按 1080px 解码），不是同一个能力的大小参数。
     * [onOpen] 为 null = 调用方没有查看器，缩略图不可点（与满宽图同口径）。
     */
    val thumbnailRow: @Composable (fileNames: List<String>, onOpen: ((String) -> Unit)?) -> Unit,
)
