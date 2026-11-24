package edu.jxslu.schedule.ui.common

/**
 * 系统分享面板的平台缝（iOS 移植 P2，2026-11-24）。
 *
 * ## 为什么需要它
 *
 * 现在有 6 处在分享：成绩单 PDF（`TranscriptActions`）、课表导出图片（`ScheduleShareScreen`）、
 * 百宝箱图片（`ToolboxCommon`）。Android 侧是一段 `ACTION_SEND` + `createChooser`，
 * iOS 侧是 `UIActivityViewController`。写这批页面之前先把「发一段文本 / 发一个文件」收成
 * 两个函数，页面搬进 `commonMain` 时不用改结构。
 *
 * ## 三端的形状（Android 不需要注入，iOS 必须注入）
 *
 * | 端 | 谁能提供「往哪 present」 | 实现 |
 * |---|---|---|
 * | Android | `Context.startActivity` 自带宿主，不需要宿主窗口句柄 | 走 `platform/AppDirs.android.kt` 那一个 Context 注入点，`ACTION_SEND` + `FLAG_ACTIVITY_NEW_TASK` |
 * | iOS | **拿不到**。`UIActivityViewController` 必须由一个真实的 `UIViewController` present | 由 Swift 壳实现 [ShareTarget] 塞进 [ShareSettings]，或用 `ShareIntents.ios.kt` 里那个现成的 `IosShareTarget` |
 *
 * iOS 侧**刻意不去猜 present 路径**（`UIApplication.keyWindow.rootViewController` 那套在
 * iOS 13 多场景之后本来就不成立，Compose 的 `ComposeUIViewController` 又是被谁包在
 * `UINavigationController` 里、没有定论）。宁可留一个明确的注入点，让将来的 Swift 壳
 * 一次性把正确的 VC 递进来。
 *
 * ## 返回值语义
 *
 * `true` = 已经把面板交给系统；`false` = 没有可用目标（没装任何可分享的 App、iOS 侧还没注入
 * [ShareTarget]）。**两端都不是「用户真的发出去了」**——分享面板是异步的，发没发出去不进
 * 我们的账。调用方拿 false 给提示，拿 true 就当用户自己会处理。
 *
 * ## shortcut
 *
 * 现在只支持「纯文本」与「一个文件」两种载荷，够上面三处用。
 * 需要多文件、需要带标题/主题（`EXTRA_SUBJECT`）时再加参数——别提前设计成一个载荷 builder。
 */
interface ShareTarget {
    /** 分享一段纯文本。 */
    fun shareText(text: String, chooserTitle: String): Boolean

    /**
     * 分享一个文件。[fileRef] 是**平台自己的文件句柄**：Android 是 `content://`（必须是已授
     * 读权限的 Provider uri），iOS 是文件路径。两端都从「保存/导出」那一侧拿到这个字符串，
     * 见 `GallerySaveOutcome.Saved.location`。
     */
    fun shareFile(fileRef: String, mime: String, chooserTitle: String): Boolean
}

/**
 * [ShareTarget] 的注入点，形状照 `ui/common/Haptics.kt` 的 `HapticsSettings`：
 * **进程级可变持有 + 启动时注入**，不进 CompositionLocal。
 *
 * 写方：iOS 壳（`iosApp` 的 Swift 入口，P3）。Android 侧**永远不用写它**——Android 的 actual
 * 直接走 Context，不读这里。读方：`ShareIntents.ios.kt` 的两个 actual。
 *
 * 为什么不用 CompositionLocal：分享只在点击那一刻发生，不需要触发重组；而且 iOS 的入口
 * 在 Swift 里，塞进 Composition 要绕一圈 Compose 契约。
 */
object ShareSettings {
    var target: ShareTarget? = null
}

/**
 * 用系统分享面板发一段纯文本；返回 false = 没有可用目标。
 *
 * [chooserTitle] 只有 Android 用（`createChooser` 的标题）；iOS 的 `UIActivityViewController`
 * 没有标题位，这一端**丢弃**。
 */
expect fun sharePlainText(text: String, chooserTitle: String): Boolean

/**
 * 用系统分享面板发一个文件；返回 false = 没有可用目标或引用不可用。
 *
 * [fileRef] 的口径见 [ShareTarget.shareFile]：Android 只接受 `content://`，传绝对路径会
 * 直接返回 false（`FileUriExposedException` 是必崩的，不能在平台缝里赌）。
 * [mime] 两端都用：Android 决定 `type`（没有匹配 App 就没有目标），iOS 只用来给活动项
 * 一个提示（`UIActivityViewController` 自己按扩展名判断，不吃 mime）。
 */
expect fun shareFile(fileRef: String, mime: String, chooserTitle: String): Boolean
