package edu.jxslu.schedule.ui.me.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.DefaultData
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.SettingsTag
import edu.jxslu.schedule.ui.common.WheelValueDialog
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.me.MeUiState
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.me.OneShot
import edu.jxslu.schedule.ui.me.TimeSlotEditorCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 设置 → 课表（DESIGN §3.3，2026-11-01 与「课表设置」合并）。
 *
 * 本页只回答「这张课表怎么算周次、怎么排作息」：当前课表 hero + 学期起止 /
 * 总周数 + 作息表。课表管理、导入导出、调课、调课检测、AI 调课都是独立动作，
 * 已上提到设置根页，不再经过本页中转。
 *
 * 学期区支持切换编辑目标（chips）：每张课表各有独立的开学日期/总周数，
 * 选中哪张就编辑哪张；作息区仍固定编辑当前课表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableHubScreen(
    onBack: () -> Unit,
    viewModel: MeViewModel = viewModel(
        factory = MeViewModel.Factory(Graph.repository(LocalContext.current)),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val oneShot by viewModel.oneShot.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var datePickerOpen by remember { mutableStateOf(false) }
    var weeksPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(oneShot) {
        when (val s = oneShot) {
            is OneShot.Message -> {
                snackbar.showNotice(s.text)
                viewModel.consumeOneShot()
            }
            else -> Unit
        }
    }

    // 编辑目标 = 学期区 chips 选中的课表（configTargetId）；无配置时展示默认值兜底
    val semester = state.configSemester
    val startDateText = semester?.startDate ?: DefaultData.defaultSemester.startDate
    val totalWeeks = semester?.totalWeeks ?: DefaultData.defaultSemester.totalWeeks
    val targetName = state.timetables.firstOrNull { it.id == state.configTargetId }?.name.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表") },
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
            TimetableHero(state = state)

            SettingsSection(
                title = if (targetName.isBlank()) "学期" else "学期 · $targetName",
                subtitle = "每张课表各有独立的开学日期与总周数；点课表名切换编辑目标",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.timetables.forEach { timetable ->
                        FilterChip(
                            selected = timetable.id == state.configTargetId,
                            onClick = { viewModel.selectConfigTarget(timetable.id) },
                            label = {
                                Text(
                                    if (timetable.id == state.currentTimetableId) {
                                        "${timetable.name} · 当前"
                                    } else {
                                        timetable.name
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    if (state.configSemester == null) {
                        "该课表还没配过学期，选一次开学日期即自动创建"
                    } else {
                        "该课表第 ${state.configWeek} 周 · 共 ${state.configCourseCount} 门课"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                SettingItem(
                    title = "开学日期",
                    subtitle = "第 1 周周一所在周为开学周",
                    value = startDateText,
                    onClick = { datePickerOpen = true },
                )
                SettingItem(
                    title = "总周数",
                    subtitle = "周次选择器与「第 N 周」的范围",
                    value = "$totalWeeks 周",
                    onClick = { weeksPickerOpen = true },
                )
            }

            // 作息表固定编辑当前课表：标题里点名（2026-10-07 收口）
            TimeSlotEditorCard(
                headerNote = "固定编辑当前课表「${state.timetableName.ifBlank { "—" }}」",
                slots = state.timeSlots,
                onSave = viewModel::saveTimeSlots,
                onReset = viewModel::resetTimeSlots,
            )
        }
    }

    if (datePickerOpen) {
        // rememberDatePickerState 放在 if 块内：每次打开重建，初始选中跟随当前值，
        // 避免上一次取消的选择残留
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(startDateText).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerOpen = false
                        val picked = dateState.selectedDateMillis?.let { millis ->
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        if (picked != null) {
                            // 选完即存：日期由控件保证合法，不再需要「保存」按钮收口
                            viewModel.saveSemester(
                                picked.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                totalWeeks,
                            )
                        }
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (weeksPickerOpen) {
        WheelValueDialog(
            title = "总周数",
            values = (1..30).map { "$it 周" },
            initialIndex = totalWeeks - 1,
            onConfirm = { index ->
                weeksPickerOpen = false
                viewModel.saveSemester(startDateText, index + 1)
            },
            onDismiss = { weeksPickerOpen = false },
        )
    }
}

/**
 * 课表 hub 的 hero 卡：当前课表名 + 学期起止 + 课程数。
 * 视觉 = 今日页焦点卡同语言（主色 8% 底 + 左缘 3dp 竖条 + 14dp 圆角），
 * 提示条消失后仍是「哪张课表在生效」的常驻确认位。
 */
@Composable
private fun TimetableHero(state: MeUiState) {
    val primary = MaterialTheme.colorScheme.primary
    val semester = state.configSemester
    val week = state.configWeek
    // 左缘 3dp 主色竖条 + 内容：外层 Row 装 [竖条, 内容列]，同今日页焦点卡的语言。
    // 竖条上下各缩进 12dp（焦点卡口径），IntrinsicSize.Min 让它跟内容列等高
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(primary.copy(alpha = 0.08f))
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(primary),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.timetableName.ifBlank { "默认课表" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                SettingsTag("当前课表")
            }
            Text(
                text = buildString {
                    if (semester != null) {
                        append(semester.startDate)
                        append(" 起 · 共 ${semester.totalWeeks} 周")
                    } else {
                        append("未配置学期 · 去课表设置填开学日期")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                text = buildString {
                    append("${state.configCourseCount} 门课程")
                    if (week > 0) append(" · 第 $week 周")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
