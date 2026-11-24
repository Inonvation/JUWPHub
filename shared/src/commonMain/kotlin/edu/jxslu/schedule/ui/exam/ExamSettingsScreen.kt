package edu.jxslu.schedule.ui.exam

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.ScoreAlertDefaults
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppCardRow
import edu.jxslu.schedule.ui.common.PageScaffoldColumn
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsNote
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.WheelValueDialog
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.jwvw.JwImportOutcomeEffect
import kotlinx.coroutines.launch

/**
 * 考试设置（2026-10-20）：考试页的提醒设置、自动合并开关与「导入到课表」卡片统一收在这里。
 *
 * 考试列表页只保留列表、刷新与网页核对；设置页负责提醒开关、自动检查间隔、
 * **考试变动自动合并当前课表**（2026-11-03，默认开）和导入入口。
 * 间隔仍与成绩提醒各一份 DataStore 值，互不牵动；自动合并与提醒开关也各自独立
 * （提醒关着也能并表，检查链的闸门是「提醒 || 自动合并」）。
 *
 * ## 平台动作全在参数里（iOS 移植 S10，2026-11-25）
 *
 * 本文件在共享层，拿不到 `Context` / `ActivityResultLauncher` / `JwImportActivity`，
 * 也拿不到后台核对（`ScoreAlertReminder` 是 WorkManager + 通知，Android 专有）。
 * 三件事收成可空回调，**null = 这个平台没有这项能力**：
 *
 * | 参数 | null 的含义 |
 * |---|---|
 * | [onRequestNotifPermission] | 平台没有运行时权限模型 → 开开关时不弹权限框，开关照常生效 |
 * | [onAlertSettingsChanged] | 平台没有后台核对 → 改完开关只落偏好，不重排任务 |
 * | [onOpenImport] | 平台没有导入窗口 → 隐藏「导入考试安排到课表」整卡 |
 *
 * **落偏好这一步始终在页内做**（[Services.current] 的 `DisplayPrefsStore`）：
 * 回调只负责本平台那半截，iOS 侧改完开关也能持久化，重进页面不会跳回默认值。
 */
@Composable
fun ExamSettingsScreen(
    onBack: () -> Unit,
    /** 申请通知权限；拒绝时回调 `onDenied`（页面据此给一条提示）。 */
    onRequestNotifPermission: ((onDenied: () -> Unit) -> Unit)? = null,
    /** 提醒开关 / 间隔改动后，让本平台按新规则重新评估一次后台核对。 */
    onAlertSettingsChanged: (() -> Unit)? = null,
    /** 打开「导入考试安排」窗口（Android 走 `JwImportActivity.start(startAtExam = true)`）。 */
    onOpenImport: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val prefs = remember { Services.current.displayPrefs() }
    val snackbar = remember { SnackbarHostState() }

    val enabled by prefs.examAlertEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoMerge by prefs.examAutoMergeEnabled.collectAsStateWithLifecycle(initialValue = true)
    val intervalHours by prefs.examAlertIntervalHours
        .collectAsStateWithLifecycle(initialValue = ScoreAlertDefaults.INTERVAL_DEFAULT)
    var intervalPickerOpen by remember { mutableStateOf(false) }

    // 导入窗口从本页打开，finish 后回到这里；结果气泡落在本页。
    JwImportOutcomeEffect(snackbar)

    PageScaffoldColumn(
        title = "考试设置",
        onBack = onBack,
        snackbar = snackbar,
    ) {
        SettingsSection(
            title = "考试变动提醒",
            subtitle = "教务发布新考试、或时间/考场调整时通知",
        ) {
            SettingSwitchRow(
                title = "开启提醒",
                subtitle = "首次只记基线，之后有变动才通知",
                checked = enabled,
                onCheckedChange = { want ->
                    scope.launch { prefs.setExamAlertEnabled(want) }
                    if (want) {
                        onRequestNotifPermission?.invoke {
                            scope.launch {
                                snackbar.showNotice(
                                    "未授予通知权限，提醒不会显示；可在系统设置里重新开启",
                                )
                            }
                        }
                        onAlertSettingsChanged?.invoke()
                    }
                },
            )
            SettingItem(
                title = "自动检查间隔",
                subtitle = "与成绩提醒分开设置，互不影响",
                value = ScoreAlertDefaults.intervalLabel(intervalHours),
                enabled = enabled,
                onClick = { intervalPickerOpen = true },
            )
            SettingsNote("提醒需要已保存统一认证账号；点通知回到考试安排页。")
        }

        SettingsSection(
            title = "考试变动自动合并",
            subtitle = "检测到新发布或调整的考试时，自动写进当前课表",
        ) {
            SettingSwitchRow(
                title = "自动合并到当前课表",
                subtitle = "只补/改考试条目，不动普通课程；关掉后仍可手动导入",
                checked = autoMerge,
                onCheckedChange = { want ->
                    scope.launch { prefs.setExamAutoMergeEnabled(want) }
                    // 与提醒开关同一口径：改完按新规则当场评估一次
                    onAlertSettingsChanged?.invoke()
                },
            )
            SettingsNote("自动合并按教务最新考试列表对账：缺的补、时间/考场变了的原地改，" +
                "不删已有考试；与当前课表的学期不同时不动它。")
        }

        // 导入窗口是平台动作：没有它就没有这一卡（不渲染比点了没反应好）
        if (onOpenImport != null) {
            AppCardRow(onClick = onOpenImport) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "导入考试安排到课表",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "打开导入页自动识别考试；确认后才写进课表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Icon(
                    AppIcons.Import,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SettingsNote("导入动作始终需要你在确认弹窗里点一下；自动合并只写考试条目。")
    }

    if (intervalPickerOpen) {
        WheelValueDialog(
            title = "自动检查间隔",
            values = ScoreAlertDefaults.INTERVAL_CHOICES.map { ScoreAlertDefaults.intervalLabel(it) },
            initialIndex = ScoreAlertDefaults.intervalChoiceIndex(intervalHours),
            onConfirm = { index ->
                intervalPickerOpen = false
                scope.launch {
                    prefs.setExamAlertIntervalHours(ScoreAlertDefaults.INTERVAL_CHOICES[index])
                    onAlertSettingsChanged?.invoke()
                }
            },
            onDismiss = { intervalPickerOpen = false },
        )
    }
}
