package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 笔记 / 作业**详情页编辑态**的平台能力（iOS 移植 V4，2026-11-25）。
 *
 * ## 为什么现在才需要它
 *
 * 列表页只要「看」（`MarkdownUi` + `AttachmentImageUi`），详情页还要「改」：编辑底栏的
 * 附件条要按 64dp 画缩略图并给「移除」按钮（`BitmapFactory` 解码），选图走系统 Photo Picker，
 * 移除后的孤儿文件要按 `filesDir/notes_img/` 删。三件都在 `:app` 的 `AttachmentStore` 上。
 *
 * ## 为什么收成一个对象而不是三个可空参数
 *
 * 它们指的是**同一个目录**：能画附件条就说明这个平台能落附件图，也就该能删；只给一半会得到
 * 「插入的图永远删不掉」或「附件条画着、点删除没反应」。收成对象，调用点就没法只给一半
 * （同 `AttachmentImageUi` 的理由）。
 *
 * ## null 的语义
 *
 * `null` = 本平台没有附件图落盘能力：编辑态**不渲染底栏**（格式工具条与附件条一起消失），
 * 正文照样能写纯 Markdown，插图入口整行不出现——不是「点了没反应」（`design/ios-port.md`
 * §11.2 ④）。iOS 侧要等 `UIImage` 解码 + `PHPickerViewController` 落地才给实现。
 */
class MarkdownEditorUi(
    /**
     * 编辑态底栏：附件条 + 格式工具条，固定在键盘上缘。
     *
     * 选图（系统 Photo Picker → 压缩落盘 → 光标处插引用）在**本槽内部**实现：它要的是
     * 「一个能拉起的 launcher + 一个能写的目录」，两样都归宿主，页面不必知道。
     * [onNotice] 用于「有 N 张图片没能读取」这类失败提示（页面自己有 snackbar）。
     */
    val bottomBar: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        onNotice: (message: String, tone: NoticeTone) -> Unit,
        onRemoveImage: (fileName: String) -> Unit,
        onOpenImage: (fileName: String) -> Unit,
    ) -> Unit,
    /**
     * 删除这些附件文件（不存在即忽略）。
     *
     * 调用方已经算好差集：`本次从正文移除的文件 - 全库仍引用的文件`。所以这里不需要知道
     * 笔记 / 作业的存在，也就不必把两个仓库穿进槽里。
     */
    val deleteFiles: suspend (names: Collection<String>) -> Unit,
)
