package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * 快捷方式图标：预设条目用宿主给的品牌图，自定义条目按 [ShortcutItem.icon] 的注册表 key 取矢量图，
 * 空/未知 = 链接图标。今日页 chips、设置页列表与快捷方式弹层共用；不按名称哈希取图
 * （同课程配色的教训：撞图比丑更糟）。调用方经 [modifier] 给尺寸（如 `Modifier.size(20.dp)`）。
 *
 * ## 品牌图为什么是参数（S23，2026-11-25）
 *
 * 三张预设品牌图是 `app/src/main/res/drawable/ic_preset_*.webp` 的**位图素材**，`R` 只有 Android 有；
 * 共享层拿不到，也不该为此立一条 `expect/actual`（资源读取不是平台能力，是一份素材）——
 * 口径与 `ui/today/GuessYouWantCard` 的 `brandIcon` 一致。Android 侧在
 * `:app/ui/common/ShortcutUiPlatform.kt` 里用 `painterResource` 装进来。
 *
 * [brandIcon] **不给默认值**，与上面两处同一考虑：宿主必须明确回答「本平台有没有这套素材」。
 * 传 null 不是省略，而是能力声明——本平台没有品牌图，预设条目退回矢量图标
 * （条目名与目标仍完整可用，不藏条目）。
 *
 * @param brandIcon 预设品牌图；null = 本平台没有这套素材，退回矢量图标
 */
@Composable
fun ShortcutIcon(
    item: ShortcutItem,
    brandIcon: Painter?,
    modifier: Modifier = Modifier,
) {
    if (brandIcon != null) {
        Image(
            painter = brandIcon,
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
