package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import edu.jxslu.schedule.domain.ShortcutItem
import edu.jxslu.schedule.domain.ShortcutSettings

/**
 * 快捷方式弹层的 Android 入口（iOS 移植 S23，2026-11-25）。
 *
 * 整张弹层（330 行）已经搬进 `shared/commonMain`，共享版只多一个必填参数 [brandIconFor]：
 * 预设条目的品牌图是 `res/drawable/ic_preset_*.webp`，只有 Android 的 `R` 认得。
 * 这个九参数重载就是装槽的那一层——`ui/life/SideDrawer` 的调用点因此一行都不用改。
 *
 * ## 为什么靠重载而不是改调用点
 *
 * 共享版有十个参数、这里九个，**按参数个数就能唯一确定**，不依赖「默认参数优先级」那类
 * 容易随编译器版本变的规则。这也意味着共享版的那个参数必须保持必填：一旦给它默认值，
 * 九参数调用会同时匹配两份声明，歧义就回来了。
 *
 * iOS 侧要开这张弹层时直接调共享版，`brandIconFor = { null }`（本平台没有这套素材，
 * 预设条目退回矢量图标）。
 */
@Composable
fun ShortcutSheet(
    settings: ShortcutSettings,
    otherExpanded: Boolean,
    onOtherExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onLaunchItem: (ShortcutItem) -> Unit,
    onPinItem: (ShortcutItem) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenCainiao: () -> Unit,
) {
    // 十参数：只有共享层那份匹配，不会递归回本函数
    edu.jxslu.schedule.ui.common.ShortcutSheet(
        settings = settings,
        otherExpanded = otherExpanded,
        onOtherExpandedChange = onOtherExpandedChange,
        onDismiss = onDismiss,
        onLaunchItem = onLaunchItem,
        onPinItem = onPinItem,
        onEdit = onEdit,
        onAdd = onAdd,
        onOpenCainiao = onOpenCainiao,
        brandIconFor = { item -> presetShortcutPainter(item) },
    )
}
