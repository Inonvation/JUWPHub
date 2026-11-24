package edu.jxslu.schedule.ui.life

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import edu.jxslu.schedule.ui.icon.AppIcons

/**
 * 侧栏的**分组与条目内容**（iOS 移植 U1，2026-11-25）。
 *
 * ## 为什么数据在共享层、壳在宿主
 *
 * `SideDrawerContent`（`:app`）里有 28 个参数、`Graph` 装配、`viewModel()` 与品牌位图资源 id，
 * 那些是宿主的事；而「分三段、每段有哪几行、行上写什么字、右侧挂什么徽标」是**内容**，
 * 两端必须一字不差。这段内容原先是写死在 `SideDrawerContent` 里的几十行 Composable 调用，
 * iOS 要照抄一遍就等于两份真相——所以抽成 [DrawerGroup] / [DrawerEntry] 数据 +
 * [lifeDrawerGroups] 构造器，渲染交给 [DrawerGroups]。
 *
 * ## 段与行的口径（2026-10-21 起）
 *
 * - **生活**：取快递（开快捷面板）· 百宝箱（总开关关掉时整行不渲染）；
 * - **江水生活**：一卡通账号 · 快趣出行 · 胖乖生活 · 趣智校园 · U净 · 问渠，
 *   后五行在 Android 上是官方品牌位图，右侧挂余额 / 空闲数；
 * - **学习**：笔记 · 课件 / 作业；
 * - 收件箱与天气**不进侧栏**：收件箱的唯一入口是今日页收件箱卡，天气页只从今日页顶栏进。
 */

/**
 * 侧栏一行入口的数据。
 *
 * [onClick] 是行本体（图标 + 名称）的落点；[onTrailingClick] 非空时右侧状态另成 48dp 命中区，
 * 点它开入口弹窗——「看状态」与「进页操作」是两个动作（2026-10-21 用户口径）。
 */
data class DrawerEntry(
    val label: String,
    /**
     * 线性图标（`AppIcons`）。与 [brandResId] 并存时**只作兜底**：
     * 平台没有品牌位图（iOS）时画它，不留一个空槽。
     */
    val icon: ImageVector? = null,
    /** 官方品牌位图（`ic_brand_*` 的资源 id）；null = 本平台没有这套素材。 */
    val brandResId: Int? = null,
    /** 右侧状态 / 余额文案；null = 只画箭头。 */
    val trailing: String? = null,
    /** 右侧正在取数（转圈代替文案）。 */
    val trailingLoading: Boolean = false,
    /** 对应服务正在进行中：整行主色描边点亮。 */
    val active: Boolean = false,
    val onClick: () -> Unit,
    val onTrailingClick: (() -> Unit)? = null,
)

/** 侧栏一段：段头 + 若干行。 */
data class DrawerGroup(val title: String, val entries: List<DrawerEntry>)

/**
 * 五个服务品牌位图的资源 id（`ic_brand_*`）。
 *
 * 为什么是一组 `Int`：位图是**宿主模块**的素材（`app/src/main/res/drawable/`），`R` 只有
 * Android 有，跨模块唯一能传的句柄就是资源 id（`ui/common/BrandMark` 这条缝的口径）。
 * 默认全 0 = 本平台没有这套素材，[lifeDrawerGroups] 会退回线性图标。
 */
data class DrawerBrandIds(
    val kqcx: Int = 0,
    val pangguai: Int = 0,
    val qzxy: Int = 0,
    val ujing: Int = 0,
    val wenqu: Int = 0,
)

/**
 * 侧栏三段的内容（顺序即渲染顺序）。
 *
 * 参数全是「值 + 回调」，没有 `Context` / `Graph` / `viewModel()`：数据由宿主先读好
 * （余额、空闲数、工时、进行中标记），这里只决定「哪几行、写什么、点开什么」。
 *
 * @param toolboxEnabled 百宝箱总开关（设置 → 功能）：关掉时整行**不渲染**，不是置灰。
 * @param onOpenShortcutSheet 「取快递」：开快捷面板（`ShortcutSheet`，含菜鸟批量查入口）。
 */
fun lifeDrawerGroups(
    toolboxEnabled: Boolean,
    serviceActive: ServiceActiveFlags = ServiceActiveFlags(),
    brands: DrawerBrandIds = DrawerBrandIds(),
    waterBalance: String? = null,
    qzxyBalance: String? = null,
    ujingFreeText: String? = null,
    ujingFreeLoading: Boolean = false,
    wenquHours: String? = null,
    onOpenShortcutSheet: () -> Unit,
    onOpenToolbox: () -> Unit,
    onOpenCampusAccount: () -> Unit,
    onOpenRide: () -> Unit,
    onOpenWater: () -> Unit,
    onOpenWaterSheet: () -> Unit,
    onOpenQzxy: () -> Unit,
    onOpenQzxySheet: () -> Unit,
    onOpenUjing: () -> Unit,
    onOpenUjingSheet: () -> Unit,
    onOpenWenqu: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenHomework: () -> Unit,
): List<DrawerGroup> = listOf(
    DrawerGroup(
        title = "生活",
        entries = buildList {
            add(
                DrawerEntry(
                    label = "取快递",
                    icon = AppIcons.Package01,
                    onClick = onOpenShortcutSheet,
                ),
            )
            // 2026-10-22 顶栏「+」删除后，百宝箱入口只剩侧栏这一行，总开关必须继续生效
            if (toolboxEnabled) {
                add(DrawerEntry(label = "百宝箱", icon = AppIcons.Toolbox, onClick = onOpenToolbox))
            }
        },
    ),
    DrawerGroup(
        title = "江水生活",
        entries = listOf(
            // 一卡通账号（2026-11-21 用户口径）：「江水生活」首行、快趣卡片之上。
            // 图标用线性信用卡、不用水宝宝位图——品牌位图留给五张服务卡，
            // 账号行与「账号与登录」页的「一卡通 · 电费」共用同一颗。
            DrawerEntry(
                label = "一卡通账号",
                icon = AppIcons.CreditCard,
                onClick = onOpenCampusAccount,
            ),
            // 五行走**官方品牌图标**（2026-10-21 用户口径）：位图自带彩色底，
            // 视觉体量比线性图标大，槽位取 24dp（线性 22dp）保持行内平衡。
            // 行本体直接进二级页，右侧状态 / 余额才开入口弹窗。
            DrawerEntry(
                label = "快趣出行",
                icon = AppIcons.ScooterElectric,
                brandResId = brands.kqcx.ifZeroNull(),
                active = serviceActive.ride,
                onClick = onOpenRide,
            ),
            DrawerEntry(
                label = "胖乖生活",
                icon = AppIcons.Droplet,
                brandResId = brands.pangguai.ifZeroNull(),
                trailing = waterBalance,
                active = serviceActive.water,
                onClick = onOpenWater,
                onTrailingClick = onOpenWaterSheet,
            ),
            DrawerEntry(
                label = "趣智校园",
                icon = AppIcons.ShowerHead,
                brandResId = brands.qzxy.ifZeroNull(),
                trailing = qzxyBalance,
                active = serviceActive.qzxy,
                onClick = onOpenQzxy,
                onTrailingClick = onOpenQzxySheet,
            ),
            DrawerEntry(
                label = "U净",
                icon = AppIcons.WashingMachine,
                brandResId = brands.ujing.ifZeroNull(),
                trailing = ujingFreeText,
                trailingLoading = ujingFreeLoading,
                active = serviceActive.ujing,
                onClick = onOpenUjing,
                onTrailingClick = onOpenUjingSheet,
            ),
            DrawerEntry(
                label = "问渠 · 志愿服务",
                icon = AppIcons.HeartHandshake,
                brandResId = brands.wenqu.ifZeroNull(),
                trailing = wenquHours,
                onClick = onOpenWenqu,
            ),
        ),
    ),
    DrawerGroup(
        title = "学习",
        entries = listOf(
            DrawerEntry(label = "笔记 · 课件", icon = AppIcons.Book01, onClick = onOpenNotes),
            DrawerEntry(label = "作业", icon = AppIcons.Task01, onClick = onOpenHomework),
        ),
    ),
)

/** 0 是「本平台没有这张位图」的哨兵：`painterResource(0)` 会崩，不能传给 [DrawerRow]。 */
private fun Int.ifZeroNull(): Int? = takeIf { it != 0 }

/** 把 [lifeDrawerGroups] 的产物画出来：一段一张段头，一行一个 [DrawerRow]。 */
@Composable
fun DrawerGroups(groups: List<DrawerGroup>) {
    groups.forEach { group ->
        DrawerSection(group.title) {
            group.entries.forEach { entry ->
                DrawerRow(
                    label = entry.label,
                    icon = entry.icon,
                    brandIcon = entry.brandResId,
                    trailing = entry.trailing,
                    trailingLoading = entry.trailingLoading,
                    active = entry.active,
                    onClick = entry.onClick,
                    onTrailingClick = entry.onTrailingClick,
                )
            }
        }
    }
}
