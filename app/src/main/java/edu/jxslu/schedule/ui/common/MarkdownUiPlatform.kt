package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.domain.imageRefs

/**
 * Markdown 渲染栈的 Android 平台槽（iOS 移植 S21，2026-11-25；V4 收口 2026-11-25）。
 *
 * 渲染器本体 [MarkdownContent] 与公式排版 [MathText] 已搬进 `shared/commonMain`；
 * 留在 `:app` 的只有**附件图解码**（`BitmapFactory` 降采样 + 按字节计的 LRU，见
 * `AttachmentImage.kt` / `AttachmentThumbnail.kt` / `AttachmentStrip.kt`）——`commonMain`
 * 拿不到位图 API，iOS 侧也还没写。这个文件把那份能力装进共享层的槽里：
 *
 * | 入口 | 用在哪 | 槽 |
 * |---|---|---|
 * | [rememberMarkdownImageUi] | 详情页正文（满宽真图，走共享的 [MarkdownContent]） | [MarkdownImageUi] |
 * | [rememberMarkdownUi] | 列表页正文（固定缩略图档位） | [MarkdownImageUi] |
 * | [rememberMarkdownEditorUi] | 详情页编辑态（附件条 + 工具条 + 选图 + 删文件） | [MarkdownEditorUi] |
 *
 * **原来还有一个 `MarkdownView`**：那是详情页留在 `:app` 时给它们的同名封装（共享层的渲染器
 * 只能换个名字，否则同一个包里两个同名函数）。V4 把两页搬进共享层后它没有调用方了，
 * 已删除——详情页现在直接调 `MarkdownContent` + [rememberMarkdownImageUi]。
 */

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
fun rememberMarkdownImageUi(): MarkdownImageUi = remember {
    MarkdownImageUi(
        fullWidth = { fileName, onClick -> AttachmentImage(fileName = fileName, onClick = onClick) },
        thumbnailRow = { fileNames, onOpen -> AttachmentThumbnailRow(fileNames = fileNames, onOpen = onOpen) },
    )
}

/**
 * 详情页编辑态槽的 Android 装配（V4，2026-11-25）：附件条 + 格式工具条 + 选图 + 删文件。
 *
 * 三件都落在 `Graph.attachmentStore` 的同一个目录上（`filesDir/notes_img/`），所以收成一个
 * [MarkdownEditorUi] 交出去；共享层的详情页只认这个对象，不认 `AttachmentStore`。
 */
@Composable
fun rememberMarkdownEditorUi(): MarkdownEditorUi {
    val context = LocalContext.current
    val store = remember(context) { Graph.attachmentStore(context) }
    return remember(store) {
        MarkdownEditorUi(
            bottomBar = { value, onValueChange, onNotice, onRemoveImage, onOpenImage ->
                // 选图 → 压缩落盘 → 光标处插引用：Photo Picker（零权限），装配原样复用
                val pickImages = rememberImageInserter(
                    value = value,
                    onValueChange = onValueChange,
                    onNotice = onNotice,
                )
                MarkdownEditorBottomBar(
                    value = value,
                    onValueChange = onValueChange,
                    onPickImages = pickImages,
                    onRemoveImage = onRemoveImage,
                    onOpenImage = onOpenImage,
                )
            },
            deleteFiles = { names -> store.delete(names) },
        )
    }
}

/**
 * 编辑器底栏（2026-10-21）：附件条 + 格式工具条，固定在键盘上缘。
 *
 * 放在 `Scaffold(bottomBar = …)` 里，底部取 `safeDrawing`（键盘 + 手势条并集）；
 * 不要再塞回正文的 `verticalScroll`，否则长文写到下半段时工具条在键盘后面（旧版就是这个毛病）。
 *
 * **为什么没跟着 `MarkdownEditor.kt` 搬进共享层**：附件条 [AttachmentStrip] 要按 240px
 * 解码 64dp 缩略图并画「移除」按钮，是平台件（`BitmapFactory`）；工具条本身
 * [MarkdownToolbar] 已在共享层，这里只做拼装。
 */
@Composable
fun MarkdownEditorBottomBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onPickImages: (() -> Unit)?,
    onRemoveImage: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 键盘 + 手势导航条取并集：imePadding + navigationBarsPadding 会叠出双份空白
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            AttachmentStrip(
                fileNames = imageRefs(value.text).toList(),
                onRemove = onRemoveImage,
                onOpen = onOpenImage,
            )
            MarkdownToolbar(
                value = value,
                onValueChange = onValueChange,
                onPickImages = onPickImages,
            )
        }
    }
}
