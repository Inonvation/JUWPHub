@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.score

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.interop.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * 打开沙箱里的 PDF（成绩单），交给系统的文档预览面板（iOS，2026-11-29）。
 *
 * ## 为什么是 `UIDocumentInteractionController` 而不是 `QLPreviewController`
 *
 * 两个都能预览 PDF。这里选前者只有一条理由：**它属于 UIKit**，不需要往 `project.yml`
 * 再加一个 `QuickLook.framework` 的链接行——静态 Kotlin framework 漏一个 `-framework`
 * 是链接期 `Undefined symbols`，而本机编不了 iOS，能少押一个符号就少一次白跑。
 *
 * 代价是预览面板的样式由系统决定（iOS 15 上是一张带完成键的模态页），我们不做自定义。
 *
 * ## 生命周期
 *
 * `delegate` 是 weak，必须在组合里常驻（[remember]）——否则点「打开」那一刻被回收，
 * 系统问「在哪个 VC 里预览」时拿到 nil，表现为点了没反应。`host` 每次调用重新取，
 * 多窗口重建 VC 时不会拿到旧的那一个。
 *
 * ## 未验
 *
 * `UIDocumentInteractionController` 的类方法与 delegate selector 名在 K/N 里没有仓内先例；
 * 签名按 Apple 文档直译。真机上失败了不会崩（`presentPreviewAnimated` 回 false），
 * 但页面会显示「打不开」——那时先对照这两个符号。
 */
@Composable
internal fun rememberIosPdfPreview(): (String) -> Unit {
    val host = LocalUIViewController.current
    val delegate = remember { PdfPreviewDelegate() }
    return remember(host, delegate) {
        { path ->
            delegate.host = host
            val url = NSURL.fileURLWithPath(path)
            val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
            controller.delegate = delegate
            controller.presentPreviewAnimated(true)
        }
    }
}

/**
 * 告诉系统「在哪个 VC 里预览」。
 *
 * 这是 `UIDocumentInteractionControllerDelegateProtocol` 里唯一被我们实现的方法：
 * 其余（结束回调、菜单项）都用系统默认。
 */
private class PdfPreviewDelegate : NSObject(), UIDocumentInteractionControllerDelegateProtocol {

    var host: UIViewController? = null

    override fun documentInteractionControllerViewControllerForPreview(
        controller: UIDocumentInteractionController,
    ): UIViewController = host ?: UIViewController(null, null)
}
