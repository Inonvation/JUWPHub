package edu.jxslu.schedule.ui.common

/**
 * 系统剪贴板的平台缝（iOS 移植 P2，2026-11-24）。
 *
 * ## 为什么需要它
 *
 * 全仓只有两处在复制纯文本：胖乖 Token（`WaterScreen`）与趣智校园诊断文本
 * （`QzxyDebugScreen`）。写这两个页面的批次要进 `commonMain`，而
 * `ClipboardManager` / `UIPasteboard` 各在各家，所以把「复制/读取一段纯文本」收成一条缝。
 * 调用点只认这两个函数，不认 `Context`，也不需要知道系统版本。
 *
 * ## 两端语义差异（这是这条缝唯一需要记住的东西）
 *
 * | 项 | Android | iOS |
 * |---|---|---|
 * | 实现 | `ClipboardManager.setPrimaryClip` | `UIPasteboard.generalPasteboard.string` |
 * | [label] | 会出现在 Android 13+ 系统的「已复制」浮层里 | **丢弃**：`UIPasteboard` 没有 label 概念，系统浮层只显示 App 名称 |
 * | 读取 | `ClipData.Item.text`；条目不是文本（图片、intent）就返回 null | `UIPasteboard.string`；非文本内容同样返回 null |
 * | 读取的额外代价 | Android 10+ 只有本应用在前台/默认输入法时才能读，否则系统直接给空 | iOS 16+ 首次读取会弹「允许粘贴自…」，用户拒了就是空串 |
 *
 * 空串一律归一成 null：两端都拿不到「一段有内容的文本」时调用方应当走同一条失败分支。
 *
 * ## 为什么不做成 `internal`
 *
 * `:app` 里那两处调用点将来搬进共享层时直接调这两个函数；`internal` 跨模块不可见，
 * 会当场报 `Cannot access`。签名保持 public，KDoc 写清口径。
 *
 * ## shortcut
 *
 * 现在只有无条件读写。Android 13 起系统允许应用自己声明「复制的是敏感内容」从而不把它
 * 写进剪贴板历史，iOS 也没有对应的 `UIPasteboard` 隐私标记——等真要复制学号/证件号时再加
 * 一个 `sensitive: Boolean` 参数，别在这里提前加。
 */
expect fun copyPlainText(label: String, text: String)

/**
 * 读剪贴板里的纯文本；没有文本（空、图片、intent、权限被系统收走）返回 null。
 *
 * 只读 `text` 那一面，**不做 `coerceToText`**：后者会把 `content://` 图片当场解码成一大段
 * 描述文本，既慢又不是调用方想要的。需要「粘贴一段文本」的场景都够用。
 */
expect fun readPlainText(): String?
