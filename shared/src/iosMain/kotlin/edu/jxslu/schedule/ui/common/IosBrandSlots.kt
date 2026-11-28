package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.domain.ShortcutItem
import edu.jxslu.schedule.ui.life.DrawerBrandIds
import edu.jxslu.schedule.ui.today.ServiceBrands

/*
 * iOS 侧四处「品牌位图槽位」的取值表（2026-11-27）：侧栏五行的 id、今日页进行中服务卡四行的
 * id、今日页猜你想用的 Painter、快捷方式预设的 Painter。
 *
 * ## 为什么在 `ui/common` 而不是各自的宿主文件里
 *
 * 这四处的宿主（`IosDrawer.kt`、`MainViewController.kt`、`IosNavigation.kt`）这一轮不归本次
 * 改动所有，而取值本身必须与 `IosBrandAssets` 的表**同源**——让宿主人手写五个 id 或一个
 * `when`，等于把同一张表抄三份，抄错一个就是「快趣那行显示胖乖的图」。
 * 所以这里把「槽位 → id」的映射收一处，宿主只加一个实参。
 *
 * 放在 `ui/common` 是因为它横跨 `ui/life`（侧栏）、`ui/today`（进行中服务卡 / 猜你想用）与
 * `ui/me`（快捷方式）三个包，落到任何一个包里都要被另外几个反向依赖。这里只有映射，
 * 没有一行 UI。
 *
 * 四者的语义都与 Android 侧一致：**给不出就退回共享层的既有降级**（侧栏退回线性图标、
 * 进行中行不画图标位、猜你想用不画图标位、预设条目退回矢量图标），不是「以后补」的占位。
 */

/**
 * 侧栏「江水生活」五行的品牌位图 id。
 *
 * 用法（`IosDrawer.kt` 里 `lifeServiceRows(` 那一句）：加 `brands = iosDrawerBrandIds`。
 * [DrawerBrandIds] 的默认值 0 表示「本平台没有这套素材」，这里五个槽全部填上。
 */
internal val iosDrawerBrandIds: DrawerBrandIds = DrawerBrandIds(
    kqcx = IosBrandAssets.KQCX,
    pangguai = IosBrandAssets.PANG_GUAI,
    qzxy = IosBrandAssets.QZXY,
    ujing = IosBrandAssets.UJING,
    wenqu = IosBrandAssets.WENQU,
)

/**
 * 今日页「进行中服务卡」四行的品牌位图 id（开水 / U净 / 趣智 / 快趣行里的免费计时行）。
 *
 * 用法（`MainViewController.kt` 的 `ActiveServiceCard(`）：加 `brands = iosServiceBrands`。
 * 快趣骑行那条「行本体」由宿主的 `ActiveServiceSlot` 自带（`IosTodayKvcx`），不读这里。
 */
internal val iosServiceBrands: ServiceBrands = ServiceBrands(
    kqcx = IosBrandAssets.KQCX,
    pangguai = IosBrandAssets.PANG_GUAI,
    qzxy = IosBrandAssets.QZXY,
    ujing = IosBrandAssets.UJING,
)

/**
 * 今日页「猜你想用」的品牌图。
 *
 * 用法（`MainViewController.kt` 的 `TodayScreen(`）：加
 * `brandIcon = { target -> iosGuessBrandPainter(target) }`。
 *
 * `GuessTarget` 与品牌图**按语义配对**，不按枚举顺序：QIEKJ 是胖乖（开水）、KVCX 是快趣
 * （单车），两个名字与中文名对不上，最容易在这里对调。
 *
 * `when` 不给 `else`：`GuessTarget` 新增服务时会在此编不过，那是有意的——逼着补一张图，
 * 而不是静默少一个图标位。
 */
@Composable
internal fun iosGuessBrandPainter(target: GuessTarget): Painter? {
    val id = when (target) {
        GuessTarget.QIEKJ -> IosBrandAssets.PANG_GUAI
        GuessTarget.KVCX -> IosBrandAssets.KQCX
        GuessTarget.QZXY -> IosBrandAssets.QZXY
        GuessTarget.UJING -> IosBrandAssets.UJING
        GuessTarget.WENQU -> IosBrandAssets.WENQU
    }
    return IosBrandAssets.painter(id)
}

/**
 * 快捷方式预设条目的品牌图。
 *
 * 用法（`IosNavigation.kt` 的 `ShortcutSettingsScreen(`）：把 `brandIcon = { null }` 换成
 * `brandIcon = { item -> iosPresetBrandPainter(item) }`；`ui/common/ShortcutSheet` 那份
 * `brandIcon` 同理。
 *
 * `presetIndex` 的取值是 0=拼多多 1=淘宝 2=菜鸟（`domain/Shortcuts.kt` 定死）；自定义条目是
 * -1，回 null 走矢量图，与 Android 的 `presetShortcutPainter` 同一条降级。
 */
@Composable
internal fun iosPresetBrandPainter(item: ShortcutItem): Painter? {
    val id = when (item.presetIndex) {
        0 -> IosBrandAssets.PRESET_PINDUODUO
        1 -> IosBrandAssets.PRESET_TAOBAO
        2 -> IosBrandAssets.PRESET_CAINIAO
        // 自定义条目（-1）与未知值：没有品牌图，退回矢量图标
        else -> return null
    }
    return IosBrandAssets.painter(id)
}
