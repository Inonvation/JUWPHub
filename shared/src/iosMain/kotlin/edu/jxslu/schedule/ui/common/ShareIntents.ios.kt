@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * [sharePlainText] / [shareFile] 的 iOS 实现（iOS 移植 P2，2026-11-24）：`UIActivityViewController`。
 *
 * ## 为什么这两个 actual 只是转发
 *
 * `UIActivityViewController` 必须由一个**真实的、当前在屏上的** `UIViewController` present。
 * 共享层拿不到它，而常见的两条取法都不能用：
 * - `UIApplication.keyWindow.rootViewController` 在 iOS 13 多场景（`UIScene`）之后语义不成立，
 *   在 `ComposeUIViewController` 被别的容器包着时拿到的往往是包皮而不是能 present 的那一层；
 * - 猜「`rootViewController.presentedViewController` 链顶」在多窗口下会 present 到别的场景去。
 *
 * 所以这里只做一件事：按两条来源找 VC，**都没有就返回 false**（调用方按「分享不可用」处理），
 * 绝不退回某个猜出来的 VC：
 *
 * 1. [ShareSettings.target]——Swift 壳自己注入的（`ShareSettings.shared.target = IosShareTarget { vc }`）。
 *    壳最清楚从哪个 VC 弹，所以它优先。
 * 2. `IosShareHost.target()`——根组合里 `IosSharePresenter()` 装进来的当前宿主 VC
 *    （`IosSharePresenter.kt`，那是「从组合里取 VC」的唯一实现）。
 *
 * [IosShareTarget] 是这条缝留给宿主的那一半：宿主只负责回答「从哪个 VC 弹」，UIKit 的那些
 * 细节（activity items、iPad 的 popover 锚点、present 调用）留在这里，公共代码里
 * 一行 UIKit 都不出现。
 *
 * ## 两端语义差异
 *
 * | 项 | Android | iOS |
 * |---|---|---|
 * | 选择器 | `createChooser`，标题=[chooserTitle] | `UIActivityViewController`，**没有标题位**，[chooserTitle] 被丢弃 |
 * | MIME | 决定 `type`，没有匹配 App 就没有目标 | 不吃 mime：活动项是 `NSURL`，系统按扩展名判断，[mime] 只用于调用方传参一致性 |
 * | 返回值 | 拉起成功 = true | present 成功 = true；**不等于用户发出去了** |
 *
 * ## shortcut
 *
 * iPad 上 `UIActivityViewController` 必须以 popover 呈现，不给锚点会崩（`NSInvalidArgumentException`），
 * 所以 [IosShareTarget] 里硬塞了一个「宿主视图中心」的锚点。等真机点验过 iPad 分屏/旋转后，
 * 如果弹层位置明显难看，再让壳把 `sourceRect` 一起递进来。
 *
 * 编译状态：本机是 Windows，**这段没有编译验证过**（Kotlin/Native 要 macOS）。
 * 用到的 UIKit 符号清单与不确定项见返回报告；真要改，只改本文件。
 */
actual fun sharePlainText(text: String, chooserTitle: String): Boolean =
    resolveShareTarget()?.shareText(text, chooserTitle) ?: false

/** 见 [sharePlainText] 的类 KDoc；[fileRef] 是**文件路径**（不是 `content://`，iOS 没有这个概念）。 */
actual fun shareFile(fileRef: String, mime: String, chooserTitle: String): Boolean =
    resolveShareTarget()?.shareFile(fileRef, mime, chooserTitle) ?: false

/**
 * 分享出口的两条来源，按优先级：[ShareSettings.target]（壳注入）→ `IosShareHost`（根组合装的）。
 * 见文件头的 KDoc。
 */
private fun resolveShareTarget(): ShareTarget? = ShareSettings.target ?: IosShareHost.target()

/**
 * 现成的 [ShareTarget] 实现：把 UIKit 那一半写死在 Kotlin/Native 里，只把「从哪个 VC 弹」
 * 留给构造方（构造参数是一个返回当前 VC 的闭包，每次分享时现取，不要缓存）。
 *
 * 两个构造方：Swift 壳（`ShareSettings.target`）与根组合里的 `IosSharePresenter()`
 * （`IosSharePresenter.kt`）。两者都只是回答「哪个 VC」，不重复实现本类。
 *
 * 线程：`UIActivityViewController` 必须在主线程 present。Compose 在 iOS 上跑在主线程，
 * 这里不切线程——切过去反而会因为跨线程碰 UIKit 崩。
 */
class IosShareTarget(
    private val presentingViewController: () -> UIViewController?,
) : ShareTarget {

    /** 分享纯文本：文本本身就是一个合法的活动项。 */
    override fun shareText(text: String, chooserTitle: String): Boolean = present(listOf(text))

    /**
     * 分享文件：[fileRef] 按路径构 `NSURL`（`file://`）。
     *
     * 注意这里**不做存在性检查**：不存在的路径进到面板里会变成灰项，由系统报；提前判一次
     * 只会让「刚写完还没来得及 flush」的调用方偶尔拿到 false。
     */
    override fun shareFile(fileRef: String, mime: String, chooserTitle: String): Boolean =
        present(listOf(NSURL.fileURLWithPath(fileRef)))

    private fun present(items: List<Any>): Boolean {
        // 壳还没注入、或当时确实没有可 present 的 VC：返回 false，不猜
        val presenter = presentingViewController() ?: return false
        val controller = UIActivityViewController(items, null)
        // iPad 走 popover：不给 sourceView/sourceRect 会直接抛异常（见类 KDoc 的 shortcut）
        controller.popoverPresentationController?.let { popover ->
            val anchor = presenter.view
            popover.sourceView = anchor
            anchor?.bounds?.useContents {
                popover.sourceRect = CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
            }
        }
        presenter.presentViewController(controller, animated = true, completion = null)
        return true
    }
}
