package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import edu.jxslu.schedule.R
import edu.jxslu.schedule.domain.ShortcutItem

/**
 * 快捷方式图标的 Android 接线（iOS 移植 S23，2026-11-25）。
 *
 * 图标注册表（`shortcutIconChoices` / `shortcutIconVector`）与 [ShortcutIcon] 本体已经搬进
 * `shared/commonMain`；这里只留 Android 独有的那半：**三张预设品牌图**。
 * 它们是 `res/drawable/ic_preset_*.webp`，只有 `R` 认得，共享层拿不到。
 *
 * ## 两个出口，各有一个非它不可的理由
 *
 * | 出口 | 谁用 | 为什么不能合并 |
 * |---|---|---|
 * | [presetShortcutIconRes] 资源 id | `ShortcutPinner` 钉桌面（`Icon.createWithResource`） | 系统桌面只要资源 id，拿 Painter 没用 |
 * | [presetShortcutPainter] / [ShortcutIcon] 重载 | 设置页列表、快捷方式弹层 | Compose 只认 Painter |
 *
 * ## 为什么这里还有一个两参数的 [ShortcutIcon]
 *
 * 共享层的 [edu.jxslu.schedule.ui.common.ShortcutIcon] 把品牌图收成必填参数，而
 * `ui/me/ShortcutSettingsScreen` 的调用点写的是 `ShortcutIcon(item, Modifier.size(26.dp))`。
 * 这个两参数重载就是装槽的那一层：`(item, Modifier)` 按**类型**只匹配这一份，
 * 共享层那份的第二参数是 `Painter?`，不构成重载歧义。设置页因此一行都不用改。
 */
private val presetShortcutIcons = listOf(
    R.drawable.ic_preset_pinduoduo,
    R.drawable.ic_preset_taobao,
    R.drawable.ic_preset_cainiao,
)

/** 预设的品牌图资源；非预设返回 null。桌面 Pin 也要用（Compose 图标无法离屏渲染成位图）。 */
fun presetShortcutIconRes(item: ShortcutItem): Int? =
    presetShortcutIcons.getOrNull(item.presetIndex)

/** 预设品牌图的 Painter 版；非预设返回 null（调用点据此退回矢量图标）。 */
@Composable
fun presetShortcutPainter(item: ShortcutItem): Painter? {
    val res = presetShortcutIconRes(item) ?: return null
    return painterResource(res)
}

/** 两参数入口：把品牌图装进共享层的 `ShortcutIcon`，供未改动的旧调用点沿用。 */
@Composable
fun ShortcutIcon(item: ShortcutItem, modifier: Modifier = Modifier) {
    edu.jxslu.schedule.ui.common.ShortcutIcon(
        item = item,
        brandIcon = presetShortcutPainter(item),
        modifier = modifier,
    )
}
