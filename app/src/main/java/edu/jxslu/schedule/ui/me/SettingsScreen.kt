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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.BuildConfig
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.LocalBottomBarClearance
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.rememberResumeTick
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Agreement01
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.BellRing
import me.rerere.hugeicons.stroke.CalendarSetting01
import me.rerere.hugeicons.stroke.CalendarSync
import me.rerere.hugeicons.stroke.Database
import me.rerere.hugeicons.stroke.Flash
import me.rerere.hugeicons.stroke.GridView
import me.rerere.hugeicons.stroke.Heart
import me.rerere.hugeicons.stroke.InformationCircle
import me.rerere.hugeicons.stroke.NotificationSnooze02
import me.rerere.hugeicons.stroke.Palette
import me.rerere.hugeicons.stroke.Shield01
import me.rerere.hugeicons.stroke.SunCloud01
import me.rerere.hugeicons.stroke.Toolbox

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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpen: (SubpageScreen) -> Unit,
    /** 从侧栏齿轮进来时给返回箭头（独立窗口，恒有）。 */
    onBack: (() -> Unit)? = null,
    viewModel: MeViewModel = viewModel(
        factory = MeViewModel.Factory(Graph.repository(LocalContext.current)),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                        }
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
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
            val prefsStore = remember { Graph.displayPrefs(context) }
            val resumeTick = rememberResumeTick()
            val profileClass by remember {
                prefsStore.profileClass
            }.collectAsState(initial = "")
            val pendingChanges by remember { prefsStore.schedulePendingChanges }
                .collectAsStateWithLifecycle(initialValue = null)
            val pendingCount = remember(pendingChanges) { pendingChanges?.groupCount() ?: 0 }
            LaunchedEffect(resumeTick) {
                if (profileClass.isBlank()) {
                    runCatching { Graph.profileSync(context).syncOnce() }
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
                    icon = HugeIcons.Palette,
                    onClick = { onOpen(SubpageScreen.APPEARANCE_SETTINGS) },
                )
                SettingItem(
                    title = "天气",
                    subtitle = "今日页顶栏天气提示 · 数据来自 Open-Meteo",
                    icon = HugeIcons.SunCloud01,
                    onClick = { onOpen(SubpageScreen.WEATHER_SETTINGS) },
                )
                SettingItem(
                    title = "快捷方式",
                    subtitle = "编辑今日页快捷入口 · 预设与自定义",
                    icon = HugeIcons.Flash,
                    onClick = { onOpen(SubpageScreen.SHORTCUTS) },
                )
                SettingSwitchRow(
                    title = "百宝箱",
                    subtitle = "左侧栏「百宝箱」入口：水印 · 拼接 · 菜鸟批量查",
                    checked = state.displayPrefs.toolboxEnabled,
                    onCheckedChange = viewModel::setToolboxEnabled,
                    icon = HugeIcons.Toolbox,
                )
            }

            SettingsSection(title = "课表") {
                val weekSuffix = if (state.currentWeek > 0) " · 第 ${state.currentWeek} 周" else ""
                SettingItem(
                    title = "课表",
                    subtitle = "当前：${state.timetableName.ifBlank { "—" }}$weekSuffix",
                    icon = HugeIcons.CalendarSetting01,
                    onClick = { onOpen(SubpageScreen.TIMETABLE_HUB) },
                )
                SettingItem(
                    title = "调课",
                    subtitle = "把某天的课调到另一天 · 仅本机生效",
                    icon = HugeIcons.CalendarSync,
                    onClick = { onOpen(SubpageScreen.COURSE_TWEAK) },
                )
                SettingItem(
                    title = "调课检测",
                    titleTag = if (pendingCount > 0) "${pendingCount}处差异" else null,
                    subtitle = "与教务最新课表比对，发现变动提醒并可选合并",
                    icon = HugeIcons.Alert02,
                    onClick = { onOpen(SubpageScreen.SCHEDULE_CHANGES) },
                )
                SettingItem(
                    title = "AI 调课",
                    subtitle = "适合国庆等大规模调课，需自备 API Key",
                    icon = HugeIcons.AiBrain01,
                    onClick = { onOpen(SubpageScreen.AI_SCHEDULE) },
                )
            }

            SettingsSection(title = "提醒") {
                SettingItem(
                    title = "提醒",
                    subtitle = "上课 / 作业截止通知 · 提前量可调 · 点通知直达",
                    icon = HugeIcons.BellRing,
                    onClick = { onOpen(SubpageScreen.REMINDER_SETTINGS) },
                )
                SettingItem(
                    title = "免扰时段",
                    subtitle = "睡眠与午休时段内通知推迟到时段结束补发 · 可自定义",
                    icon = HugeIcons.NotificationSnooze02,
                    onClick = { onOpen(SubpageScreen.QUIET_HOURS) },
                )
            }

            SettingsSection(title = "桌面与日历") {
                SettingItem(
                    title = "桌面小组件",
                    subtitle = "课表 · 校园卡电费 · 开水卡 · 一键添加",
                    icon = HugeIcons.GridView,
                    onClick = { onOpen(SubpageScreen.WIDGET_SETTINGS) },
                )
                SettingItem(
                    title = "日历同步",
                    subtitle = "课表写入系统日历 · 提前提醒随同步写入",
                    icon = HugeIcons.CalendarSetting01,
                    onClick = { onOpen(SubpageScreen.CALENDAR_SETTINGS) },
                )
            }

            SettingsSection(title = "数据与权限") {
                SettingItem(
                    title = "课表管理",
                    subtitle = "新建 · 切换 · 删除课表",
                    icon = HugeIcons.GridView,
                    onClick = { onOpen(SubpageScreen.TIMETABLE_MANAGE) },
                )
                SettingItem(
                    title = "导入 / 导出 / 清空",
                    subtitle = "JSON 备份与恢复 · 清空课程",
                    icon = HugeIcons.Database,
                    onClick = { onOpen(SubpageScreen.DATA_SETTINGS) },
                )
                SettingItem(
                    title = "权限与存储",
                    subtitle = "通知 · 日历 · 定位 · 缓存 · 诊断日志",
                    icon = HugeIcons.Shield01,
                    onClick = { onOpen(SubpageScreen.PERMISSIONS_STORAGE) },
                )
            }

            SettingsSection(title = "关于") {
                SettingItem(
                    title = "关于与更新",
                    subtitle = "版本 v${BuildConfig.VERSION_NAME} · 检查更新 · 自动检查",
                    icon = HugeIcons.InformationCircle,
                    onClick = { onOpen(SubpageScreen.ABOUT_UPDATE) },
                )
                SettingItem(
                    title = "条款与声明",
                    subtitle = "用户须知 · 免责声明 · 充值风险",
                    icon = HugeIcons.Agreement01,
                    onClick = { onOpen(SubpageScreen.TERMS_DECLARATIONS) },
                )
                SettingItem(
                    title = "支持开发",
                    subtitle = "扫码赞赏作者 · 完全自愿",
                    icon = HugeIcons.Heart,
                    onClick = { onOpen(SubpageScreen.SUPPORT_DEV) },
                )
            }
        }
    }
}
