package edu.jxslu.schedule.ui.platform

import androidx.compose.runtime.Composable

/**
 * 「选一个文本文件」与「另存一段文本」的平台缝（iOS 移植 S8，2026-11-25）。
 *
 * ## 为什么要这条缝
 *
 * 课表页的 JSON 导入 / CSV·JSON 导出原先靠 `:app` 递进来的两个回调
 * （`WeekScreenPlatformActions.pickJsonFile` / `saveTextFile`），因为 SAF 的
 * `rememberLauncherForActivityResult` 只有 Android 侧建得出来。代价是 iOS 侧这两个入口
 * **整行不出现**（`design/ios-port.md` §10.3 第 2 条）。这条缝把「选一个 / 存一段」
 * 收成 `expect`，两端各自落实际实现，页面上的入口也就两端都在了。
 *
 * ## 为什么是 `@Composable expect`（而不是普通 `expect fun`）
 *
 * 两端都要求「必须在组合里创建」，这是平台机制的硬约束，不是风格选择：
 *
 * - **Android**：`rememberLauncherForActivityResult(contract, onResult)` 只在组合里注册，
 *   由宿主的 `ActivityResultRegistry` 持有，离开组合时自动注销。没有组合就没有 launcher。
 * - **iOS**：能 present 的 `UIViewController` 也只能从组合里取（`ComposeUIViewController`
 *   提供的 `LocalUIViewController`）；而 `UIDocumentPickerViewController.delegate` 是
 *   **weak**，代理对象必须被强引用住，那个强引用同样只能挂在 `remember` 上。
 *
 * ## 为什么回调是参数、动作是返回值
 *
 * 结果回调必须在**组合期**就定下来（Android 建 launcher 那一刻就固定了 `onResult`；iOS 的
 * 控制器也只在组合里取得到），而「存成什么文件名、按什么 mime 存」要等用户点下去那一刻才知道。
 * 两件事落不到同一个时刻，所以一个函数负责收回调、返回一个动作给点击用。
 *
 * ## 两端的落点
 *
 * | | Android | iOS |
 * |---|---|---|
 * | 选文件 | SAF `OpenDocument`，mime 交给系统挑（`application/json` / `text/plain` / 任意类型兜底）；读文件在 IO 线程 | `UIDocumentPickerViewController(forOpeningContentTypes:)`，`UTTypeJSON` / `UTTypePlainText` |
 * | 另存 | SAF `CreateDocument(mime)`，UTF-8 写回用户选的那个 `content://` | 写进 App 沙箱的 `appDataDir()/exports/`，**不弹面板** |
 * | 用户取消 | 回调 `null` / `false` | 同左 |
 * | 读/写失败 | 回调 `null` / `false` | 同左 |
 *
 * iOS 的「另存」刻意不接 `UIDocumentPickerViewController(forExporting:)`：那一层要多一个代理，
 * 而本机没有 macOS、验不了；写进自己的目录、再配 `Info.plist` 的 `UIFileSharingEnabled` 与
 * `LSSupportsOpeningDocumentsInPlace`，用户在「文件」App 的「我的 iPhone → JUWPHub」里就能看到
 * 并拷走，收益只差「能直接选目录」。
 *
 * ## 回调语义（两端一致，别各自解释）
 *
 * - [rememberTextFilePicker]：`null` = 用户取消**或**读不出来。两种情形调用方都只说一句
 *   「读取文件失败」，所以不细分。
 * - [rememberFileSaver]：[onSaved] 的 `false` = 用户取消**或**写失败，同理。
 *
 * [rememberFileSaver] 的 `mimeType` 在**组合期**固定：Android 的 `CreateDocument` 合同必须在
 * 建 launcher 时就带 mime，所以同一个页面要存两种格式，就得要两个实例（`:app` 一直是这个写法）。
 *
 * ## shortcut
 *
 * 只覆盖「一个文本文件」这一种载荷——够课表导入导出用。要二进制、要多文件，或者要给用户
 * 自选导出目录时再加参数/加函数，别提前把它设计成通用的 document picker。
 */
@Composable
expect fun rememberTextFilePicker(onPicked: (String?) -> Unit): () -> Unit

/**
 * 另存一段 UTF-8 文本；返回的动作参数是「文件名（含扩展名）」与「文本内容」。
 *
 * 两端的落点、`mimeType` 为什么在组合期固定、`false` 的两种含义，见 [rememberTextFilePicker] 的 KDoc。
 */
@Composable
expect fun rememberFileSaver(
    mimeType: String,
    onSaved: (Boolean) -> Unit,
): (fileName: String, text: String) -> Unit
