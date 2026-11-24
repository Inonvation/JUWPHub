package edu.jxslu.schedule.ui.common

import platform.UIKit.UIPasteboard

/**
 * [copyPlainText] / [readPlainText] 的 iOS 实现（iOS 移植 P2，2026-11-24）：`UIPasteboard`。
 *
 * 两处**有意的降级**，都写在这里备查：
 *
 * 1. **[label] 直接丢**。`UIPasteboard` 没有 label 概念，既没有「已复制」浮层也没有剪贴板
 *    历史条目的名字。Android 侧留着它是因为系统浮层要用；iOS 侧丢掉它对用户没有可见损失
 *    （系统浮层显示的是 App 名称），所以不加等价参数。
 * 2. **读取会触发系统授权**。iOS 16 起，读别的 App 写进来的内容会先弹「允许粘贴自…」，
 *    用户拒绝时 `string` 给 null。这条缝没法绕（`UIPasteboard.detectPatterns` 之类只能
 *    看「有没有链接/数字」，拿不到内容）。写入方向不需要任何授权。
 *
 * 线程：`UIPasteboard` 必须在主线程用。Compose 在 iOS 上本来就在主线程跑，
 * 这两个函数也只从点击回调里调，不额外切线程（切了反而会因为跨线程崩溃）。
 *
 * 编译状态：本机是 Windows，**这段没有编译验证过**（Kotlin/Native 要 macOS）。
 * 用到的只有 `UIPasteboard.generalPasteboard.string` 这一个属性，符号风险见返回报告。
 */
actual fun copyPlainText(label: String, text: String) {
    // label 在 iOS 上没有落点，见文件头第 1 条降级
    UIPasteboard.generalPasteboard.string = text
}

/** 读纯文本；用户拒绝粘贴授权、或剪贴板里不是文本时返回 null（与 Android 侧同一条失败分支）。 */
actual fun readPlainText(): String? =
    UIPasteboard.generalPasteboard.string?.takeIf { it.isNotEmpty() }
