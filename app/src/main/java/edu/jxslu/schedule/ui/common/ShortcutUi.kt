package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import edu.jxslu.schedule.R
import edu.jxslu.schedule.domain.ShortcutItem
import edu.jxslu.schedule.ui.icon.AppIcons

/**
 * 自定义快捷方式的图标注册表：key 持久化进 [ShortcutItem.icon]（序列化稳定值），
 * 换图标库/改映射不影响已存数据。顺序即编辑表单里的展示顺序。
 */
val shortcutIconChoices: List<Pair<String, ImageVector>> = listOf(
    "link" to AppIcons.Link01,
    "flash" to AppIcons.Flash,
    "rocket" to AppIcons.Rocket,
    "qr" to AppIcons.QrCode01,
    "package" to AppIcons.Package01,
    "star" to AppIcons.Star,
    "home" to AppIcons.Home01,
    "school" to AppIcons.School01,
    "book" to AppIcons.Book01,
    "clock" to AppIcons.Clock01,
    "globe" to AppIcons.Globe02,
    "cart" to AppIcons.ShoppingCart02,
    "wallet" to AppIcons.Wallet01,
    "phone" to AppIcons.SmartPhone01,
)

/** 图标 key → ImageVector；未知 key（换版本后的脏数据）退回链接图标。 */
fun shortcutIconVector(key: String): ImageVector =
    shortcutIconChoices.firstOrNull { it.first == key }?.second ?: AppIcons.Link01

/** 预设条目的品牌图标资源；下标即 [ShortcutItem.presetIndex]（0=拼多多 1=淘宝 2=菜鸟）。 */
private val presetShortcutIcons = listOf(
    R.drawable.ic_preset_pinduoduo,
    R.drawable.ic_preset_taobao,
    R.drawable.ic_preset_cainiao,
)

/** 预设的品牌图资源；非预设返回 null。桌面 Pin 也要用（Compose 图标无法离屏渲染成位图）。 */
fun presetShortcutIconRes(item: ShortcutItem): Int? =
    presetShortcutIcons.getOrNull(item.presetIndex)

/**
 * 快捷方式图标：预设固定用品牌图（webp/自绘矢量），自定义条目按 [ShortcutItem.icon]
 * 的注册表 key 取图，空/未知 = 链接图标。今日页 chips 与设置页共用；
 * 不按名称哈希取图（同课程配色的教训：撞图比丑更糟）。
 * 调用方经 [modifier] 给尺寸（如 Modifier.size(20.dp)），两种分支同尺寸。
 */
@Composable
fun ShortcutIcon(item: ShortcutItem, modifier: Modifier = Modifier) {
    val res = presetShortcutIcons.getOrNull(item.presetIndex)
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Icon(
            shortcutIconVector(item.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
    }
}
