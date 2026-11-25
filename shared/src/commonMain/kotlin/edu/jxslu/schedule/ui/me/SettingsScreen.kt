package edu.jxslu.schedule.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.LocalBottomBarClearance
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.rememberResumeTick
import edu.jxslu.schedule.ui.icon.AppIcons

/**
 * 设置根页（DESIGN §3.3）：独立窗口里的入口页。
 *
 * 2026-11-01 重排为 7 节；2026-11-06 精简为 **6 节**：外观与功能 → 课表 → 提醒 →
 * 桌面与日历 → 数据与权限 → 关于（原「外观与交互」单行分区并进「功能」，见下方注释）。
 * 根页只做入口，不再有「功能 / 课表 / 提醒与桌面」这类中转 hub；每个入口最多一跳进到叶子页。
 * 支持开发归「关于」。
 *
 * 进页仍会做一次「班级为空就补抓」的 ProfileSync（闸门在 `ProfileSyncRules`，
 * 每天最多一次、失败静默）——左侧栏那行身份（姓名 / 班级）靠它填。
 *
 * ## iOS 移植 S17（2026-11-25）：从 `:app` 搬进共享层
 *
 * 这一页是「我的」那一栏的根，搬过来的目的是让 iOS 也有一份设置入口。搬迁时只换了两件
 * 平台物，其余行与顺序一字未改：
 *
 * - `BuildConfig.VERSION_NAME` → `AppServices.appVersion()`（`:app` 的构建产物，共享层看不见）
 * - `Graph.displayPrefs` / `Graph.profileSync` → `AppServices` 的同名成员
 * - `stringResource(R.string.settings_title)` → 字面量「设置」（本仓只发中文）
 * - `rememberResumeTick` 跟着上移到 `ui/common/ResumeTick.kt`（expect/actual）
 *
 * 二级页入口仍走 [onOpen]：iOS 侧还没有二级页栈，宿主传一句提示（`design/ios-port.md` §11.2 ⑦）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    /** 二级页入口；iOS 侧没有二级页栈，宿主传一句「还在 Android 侧」的提示即可。 */
    onOpen: (SubpageScreen) -> Unit,
    /**
     * 「我的」与显示设置的共享 ViewModel，**由宿主创建**（Android `viewModel {}`、
     * iOS `remember {}`）——`commonMain` 里没有 `viewModel()` 这个 Composable，
     * 见 `design/ios-port.md` §11.1。
     */
    viewModel: MeViewModel,
    /** 从侧栏齿轮进来时给返回箭头（独立窗口，恒有）。 */
    onBack: (() -> Unit)? = null,
    /**
     * 「AI 调课」入口。false = 本平台没有这一页（iOS 精简版），整行不渲染。
     *
     * 与 `onOpen` 的 null 语义不同：这一行是**页面自己**的清单项，不是宿主回调，
     * 所以用开关而不是可空回调（`onOpen` 恒非空，挡不住清单里的一行）。
     */
    showAiSchedule: Boolean = true,
    /** 「百宝箱」开关行。false = 本平台没有百宝箱，整行不渲染。语义同上。 */
    showToolbox: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                        }
                    }
                },
                // 文案与 `app/src/main/res/values/strings.xml` 的 settings_title 一字不差；
                // 共享层没有 R，而本仓只发中文，所以直接写死
                title = { Text("设置") },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingHint("正在读取设置", modifier = Modifier.fillMaxSize())
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                // 悬浮底栏的净空加在滚动内容上（DESIGN §4.22），最后一项顶出胶囊。
                .padding(bottom = LocalBottomBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val prefsStore = remember { Services.current.displayPrefs() }
            val resumeTick = rememberResumeTick()
            val profileClass by remember {
                prefsStore.profileClass
            }.collectAsState(initial = "")
            val pendingChanges by remember { prefsStore.schedulePendingChanges }
                .collectAsStateWithLifecycle(initialValue = null)
            val pendingCount = remember(pendingChanges) { pendingChanges?.groupCount() ?: 0 }
            LaunchedEffect(resumeTick) {
                if (profileClass.isBlank()) {
                    runCatching { Services.current.profileSync().syncOnce() }
                }
            }

            // 2026-11-06 精简：原来「外观与交互」是独立分区，但只有一行、且**行标题与分区
            // 标题一字不差**——同一屏出现两次「外观与交互」。`SettingsSection` 的 title 是
            // 必填项（去掉标题就没法保留分区卡），所以并进下一节，标题改成「外观与功能」：
            // 省一行、去掉一处重复，入口层级不变。
            SettingsSection(title = "外观与功能") {
                SettingItem(
                    title = "外观与交互",
                    subtitle = "主题 · 字体 · 触感 · 导航",
                    icon = AppIcons.Palette,
                    onClick = { onOpen(SubpageScreen.APPEARANCE_SETTINGS) },
                )
                SettingItem(
                    title = "天气",
                    subtitle = "今日页顶栏天气提示 · 数据来自 Open-Meteo",
                    icon = AppIcons.SunCloud01,
                    onClick = { onOpen(SubpageScreen.WEATHER_SETTINGS) },
                )
                SettingItem(
                    title = "快捷方式",
                    subtitle = "编辑今日页快捷入口 · 预设与自定义",
                    icon = AppIcons.Flash,
                    onClick = { onOpen(SubpageScreen.SHORTCUTS) },
                )
                if (showToolbox) {
                    SettingSwitchRow(
                        title = "百宝箱",
                        subtitle = "左侧栏「百宝箱」入口：水印 · 拼接 · 菜鸟批量查",
                        checked = state.displayPrefs.toolboxEnabled,
                        onCheckedChange = viewModel::setToolboxEnabled,
                        icon = AppIcons.Toolbox,
                    )
                }
            }

            SettingsSection(title = "课表") {
                val weekSuffix = if (state.currentWeek > 0) " · 第 ${state.currentWeek} 周" else ""
                SettingItem(
                    title = "课表",
                    subtitle = "当前：${state.timetableName.ifBlank { "—" }}$weekSuffix",
                    icon = AppIcons.CalendarSetting01,
                    onClick = { onOpen(SubpageScreen.TIMETABLE_HUB) },
                )
                SettingItem(
                    title = "调课",
                    subtitle = "把某天的课调到另一天 · 仅本机生效",
                    icon = AppIcons.CalendarSync,
                    onClick = { onOpen(SubpageScreen.COURSE_TWEAK) },
                )
                SettingItem(
                    title = "变动检测",
                    titleTag = if (pendingCount > 0) "${pendingCount}处差异" else null,
                    subtitle = "与教务课表比对差异 · 下学期课表已出也会提醒",
                    icon = AppIcons.Alert02,
                    onClick = { onOpen(SubpageScreen.SCHEDULE_CHANGES) },
                )
                if (showAiSchedule) {
                    SettingItem(
                        title = "AI 调课",
                        subtitle = "适合国庆等大规模调课，需自备 API Key",
                        icon = AppIcons.AiBrain01,
                        onClick = { onOpen(SubpageScreen.AI_SCHEDULE) },
                    )
                }
            }

            SettingsSection(title = "提醒") {
                SettingItem(
                    title = "提醒",
                    subtitle = "上课 / 作业截止通知 · 提前量可调 · 点通知直达",
                    icon = AppIcons.BellRing,
                    onClick = { onOpen(SubpageScreen.REMINDER_SETTINGS) },
                )
                SettingItem(
                    title = "免扰时段",
                    subtitle = "睡眠与午休时段内通知推迟到时段结束补发 · 可自定义",
                    icon = AppIcons.NotificationSnooze02,
                    onClick = { onOpen(SubpageScreen.QUIET_HOURS) },
                )
            }

            SettingsSection(title = "桌面与日历") {
                SettingItem(
                    title = "桌面小组件",
                    subtitle = "课表 · 校园卡电费 · 开水卡 · 一键添加",
                    icon = AppIcons.GridView,
                    onClick = { onOpen(SubpageScreen.WIDGET_SETTINGS) },
                )
                SettingItem(
                    title = "日历同步",
                    subtitle = "课表写入系统日历 · 提前提醒随同步写入",
                    icon = AppIcons.CalendarSetting01,
                    onClick = { onOpen(SubpageScreen.CALENDAR_SETTINGS) },
                )
            }

            SettingsSection(title = "数据与权限") {
                SettingItem(
                    title = "课表管理",
                    subtitle = "新建 · 切换 · 删除课表",
                    icon = AppIcons.GridView,
                    onClick = { onOpen(SubpageScreen.TIMETABLE_MANAGE) },
                )
                SettingItem(
                    title = "导入 / 导出 / 清空",
                    subtitle = "JSON 备份与恢复 · 清空课程",
                    icon = AppIcons.Database,
                    onClick = { onOpen(SubpageScreen.DATA_SETTINGS) },
                )
                SettingItem(
                    title = "权限与存储",
                    subtitle = "通知 · 日历 · 定位 · 缓存 · 诊断日志",
                    icon = AppIcons.Shield01,
                    onClick = { onOpen(SubpageScreen.PERMISSIONS_STORAGE) },
                )
            }

            SettingsSection(title = "关于") {
                SettingItem(
                    title = "关于与更新",
                    subtitle = "版本 v${Services.current.appVersion()} · 检查更新 · 自动检查",
                    icon = AppIcons.InformationCircle,
                    onClick = { onOpen(SubpageScreen.ABOUT_UPDATE) },
                )
                SettingItem(
                    title = "条款与声明",
                    subtitle = "用户须知 · 免责声明 · 充值风险",
                    icon = AppIcons.Agreement01,
                    onClick = { onOpen(SubpageScreen.TERMS_DECLARATIONS) },
                )
                SettingItem(
                    title = "支持开发",
                    subtitle = "扫码赞赏作者 · 完全自愿",
                    icon = AppIcons.Heart,
                    onClick = { onOpen(SubpageScreen.SUPPORT_DEV) },
                )
            }
        }
    }
}
