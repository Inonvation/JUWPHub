package edu.jxslu.schedule.ui.me.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.StartPage
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.domain.ThemeMode
import edu.jxslu.schedule.domain.ThemePalette
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.ImeAwareModalBottomSheet
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.SettingChoiceRow
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.theme.paletteSwatch
import kotlinx.coroutines.launch

/**
 * 设置 → 应用 → 外观与交互（DESIGN §3.3）。
 *
 * 原「通用设置」的观感与布局、页面与导航两节；功能开关已上提到设置根页
 * （天气 / 快捷方式 / 百宝箱），本页只留外观与导航。主题读写仍走 [MeViewModel]，
 * 改完立即生效。
 *
 * iOS 移植 S17（2026-11-25）搬进 `commonMain`：唯一的平台物是「谁来建 [MeViewModel]」，
 * 改成宿主传入；其余控件与文案一字未改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    /**
     * 「我的」与显示设置的共享 ViewModel，**由宿主创建**（Android `viewModel {}`、
     * iOS `remember {}`）：`commonMain` 里没有 `viewModel()` 这个 Composable，
     * 见 `design/ios-port.md` §11.1。
     */
    viewModel: MeViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 「猜你想用」开关（DESIGN §4.51）：单独一条流，不掺进 displayPrefs 合并模型
    val guessEnabled by viewModel.guessYouWantEnabled.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPaletteSheet by remember { mutableStateOf(false) }
    val showNotice: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals(message, tone = NoticeTone.Info))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观与交互") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(title = "主题与字体") {
                SettingChoiceRow(
                    title = "外观主题",
                    icon = AppIcons.Palette,
                    options = ThemeMode.entries.map { it.label },
                    selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
                    onSelect = { index ->
                        haptics.toggle()
                        viewModel.setThemeMode(ThemeMode.entries[index])
                    },
                )
                SettingItem(
                    title = "主题配色",
                    subtitle = if (state.displayPrefs.dynamicColor) {
                        "关闭动态取色后生效"
                    } else {
                        "内置配色，选中即实时生效"
                    },
                    icon = AppIcons.SwatchBook,
                    value = state.displayPrefs.themePalette.label,
                    enabled = !state.displayPrefs.dynamicColor,
                    onClick = { showPaletteSheet = true },
                )
                SettingSwitchRow(
                    title = "触感反馈",
                    checked = state.displayPrefs.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled,
                    icon = AppIcons.Vibrate,
                )
                SettingSwitchRow(
                    title = "动态取色",
                    subtitle = "跟随壁纸配色（Material You）",
                    checked = state.displayPrefs.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    icon = AppIcons.ColorPicker,
                )
                SettingSwitchRow(
                    title = "固定字体大小",
                    subtitle = "不随系统字体缩放；关闭后跟随系统",
                    checked = state.displayPrefs.fontScaleLocked,
                    onCheckedChange = viewModel::setFontScaleLocked,
                    icon = AppIcons.TextFont,
                )
            }

            SettingsSection(title = "导航与悬浮") {
                SettingSwitchRow(
                    title = "悬浮导航栏",
                    titleTag = "重启生效",
                    subtitle = "底栏半透明，背景图透到屏幕底部",
                    checked = state.displayPrefs.floatingNavBar,
                    onCheckedChange = { value ->
                        viewModel.setFloatingNavBar(value)
                        showNotice("已保存，重启应用后生效")
                    },
                    icon = AppIcons.Blur,
                )
                val startPages = StartPage.visiblePages()
                val effectiveStart = StartPage.effectivePage(state.displayPrefs.startPage)
                SettingChoiceRow(
                    title = "启动页",
                    titleTag = "重启生效",
                    subtitle = "打开应用时先显示这一页",
                    icon = AppIcons.Home01,
                    options = startPages.map { it.label },
                    selectedIndex = startPages.indexOf(effectiveStart),
                    onSelect = { index ->
                        haptics.toggle()
                        viewModel.setStartPage(startPages[index])
                        showNotice("已保存，重启应用后生效")
                    },
                )
            }

            // 首页展示型推荐（DESIGN §3.33 / §4.51）：默认开，关 → 开清空习惯数据。
            // 放「外观与交互」是用户口径（2026-11-17），虽然它不是纯观感项，但同属今日页展示。
            SettingsSection(title = "今日页") {
                SettingSwitchRow(
                    title = "猜你想用",
                    subtitle = "收件箱上方按时段推荐；按真实订单学习习惯，关闭再开启会清空记录",
                    checked = guessEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setGuessYouWantEnabled(enabled)
                        if (enabled) showNotice("已清空使用习惯记录")
                    },
                    icon = AppIcons.Star,
                )
            }
        }
    }

    if (showPaletteSheet) {
        ImeAwareModalBottomSheet(onDismiss = { showPaletteSheet = false }) {
            PalettePickerSheet(
                selected = state.displayPrefs.themePalette,
                onSelect = { palette ->
                    haptics.toggle()
                    viewModel.setThemePalette(palette)
                },
            )
        }
    }
}

/** 主题配色色卡弹层：选中只写偏好不关弹层，方便连续试色。 */
@Composable
private fun PalettePickerSheet(
    selected: ThemePalette,
    onSelect: (ThemePalette) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            "主题配色",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        ThemePalette.entries.forEach { palette ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(palette) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    paletteSwatch(palette).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    palette.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (palette == selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "已选中",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
