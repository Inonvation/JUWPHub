package edu.jxslu.schedule.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import edu.jxslu.schedule.platform.requireAppContext

/**
 * [copyPlainText] / [readPlainText] 的 Android 实现（iOS 移植 P2，2026-11-24）。
 *
 * 与 `:app/.../ui/common/Clipboard.kt` 原来那 6 行**行为逐字一致**，唯一的变化是 Context 不
 * 再从参数传：走 `platform/AppDirs.android.kt` 里那一个 `initAppDirs(context)` 注入点。
 * **不要在这里再开第二个注入点**——那样会出现「一个地方注入了、另一个没注入」的半死状态。
 *
 * label 保留：Android 13+ 的系统「已复制」浮层就显示它。
 */
actual fun copyPlainText(label: String, text: String) {
    requireAppContext("copyPlainText()")
        .getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * 读剪贴板的纯文本。
 *
 * 三种「读不到」都返回 null，与 iOS 侧的降级口径对齐（见 `Clipboard.kt` 的差异表）：
 * 没有 `primaryClip`（Android 10+ 本应用不在前台时系统就这么给）、条目数为 0、
 * 条目不是文本（图片 / intent / uri 的 `text` 是 null）。
 *
 * 刻意不用 `coerceToText(context)`：它会把 `content://` 图片解析成一长串描述并可能阻塞在
 * Provider 查询上，而这条缝的契约就是「一段能直接粘贴的字符串」。
 */
actual fun readPlainText(): String? {
    val clip = requireAppContext("readPlainText()")
        .getSystemService(ClipboardManager::class.java)
        ?.primaryClip
        ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).text?.toString()?.takeIf { it.isNotEmpty() }
}
