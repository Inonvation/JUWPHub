package edu.jxslu.schedule.ui.score

import edu.jxslu.schedule.ui.common.copyFileIntoExports
import edu.jxslu.schedule.ui.common.shareFile

/**
 * 盖章成绩单页（`ui/score/TranscriptScreen.kt`）在 iOS 侧的平台动作（iOS 移植 M3，2026-11-26）。
 *
 * 页面那边这三个槽位是**可空回调**（`design/ios-port.md` §11.2 ④），本文件提供能直接填进去的
 * 实现。宿主接线（`IosNavigation.kt` 的 `SubpageScreen.TRANSCRIPT` 分支）：
 *
 * ```kotlin
 * TranscriptScreen(
 *     onBack = pop,
 *     onOpenPdf = null,                              // 见下「为什么没有 onOpenPdf」
 *     onSharePdf = ::iosShareTranscriptPdf,
 *     onSavePdf = ::iosSaveTranscriptPdf,
 *     onOpenHistory = { … },                         // 二级页栈，本来就是纯导航
 *     casAutoFillScript = null,
 * )
 * ```
 *
 * ## 前提：`IosSharePresenter()` 必须已在根组合里调过
 *
 * [iosShareTranscriptPdf] 走 `shareFile`，VC 来自 `IosSharePresenter.kt` 的 `IosShareHost`。
 * 没装 = 分享面板弹不出来（`shareFile` 返回 `false`，而本函数的签名是 `(String) -> Unit`，
 * 没法把失败回给页面：**宁可此时把这个槽位传 `null`**，页面会整行不渲染，好过点了没反应）。
 *
 * ## 为什么没有 onOpenPdf
 *
 * 系统里没有「打开别人沙箱里的 PDF」的公有入口：`UIApplication.openURL(file://…)` 不对沙箱
 * 文件生效，能给用户看的只有两个东西——
 * `QLPreviewController`（QuickLook，要 `QuickLook.framework`，得改 `iosApp/project.yml`）
 * 与一个自建的 `WKWebView` 页面。两者都超出「一个回调」的体量，且本机编不了 iOS。
 * 所以这一格保持 `null`：页面的「打开」按钮不渲染，用户走「分享」→ 系统面板里的
 * 「预览 / 存储到文件」，能力不减。
 */

/**
 * 分享成绩单 PDF：直接调 `ShareIntents.shareFile`，参数是沙箱里的绝对路径。
 *
 * 口径与 Android 那份一致（`ui/score/TranscriptHosts.kt` 的 `ACTION_VIEW` / `ACTION_SEND`）：
 * 文件已经由 `TranscriptStore` 落好，这里只交给系统面板，不改文件。
 */
fun iosShareTranscriptPdf(path: String) {
    shareFile(path, TRANSCRIPT_PDF_MIME, "分享成绩单")
}

/**
 * 另存成绩单 PDF：复制到 `appDataDir()/exports/`，用户在「文件」App →「我的 iPhone」→
 * 「JUWPHub」里能看到、能拷走（落点与理由见 `ui/common/IosExports.kt`）。
 *
 * 回调**必回**（页面的提示全靠它）：写成 [TranscriptSaveOutcome.Saved]，写不成
 * [TranscriptSaveOutcome.Failed]。[TranscriptSaveOutcome.Cancelled] 在这里永远不会出现——
 * 没有弹面板让用户取消的余地。**注意页面把 `Saved` 翻成「已保存到所选位置」**，这句在 iOS 上
 * 稍有偏差：实际落点是 App 自己的 `exports/`，不是用户选的目录。
 *
 * 线程：页面在 `onClick` 里同步调用，所以这里在主线程拷贝一份几百 KB 的 PDF。真机上值得
 * 留意卡顿；要改就得把槽位改成挂起函数，那是共享层签名的事，不在本文件里动。
 */
fun iosSaveTranscriptPdf(
    path: String,
    suggestedName: String,
    onResult: (TranscriptSaveOutcome) -> Unit,
) {
    val saved = copyFileIntoExports(path, suggestedName)
    onResult(
        if (saved != null) {
            TranscriptSaveOutcome.Saved
        } else {
            TranscriptSaveOutcome.Failed("无法写入「文件」App 的 JUWPHub 目录")
        },
    )
}
