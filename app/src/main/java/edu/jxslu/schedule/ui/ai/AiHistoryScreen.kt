package edu.jxslu.schedule.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.repo.AiApplyHistoryStore
import edu.jxslu.schedule.data.repo.AiApplyRecord
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.showNotice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 「调课记录」页（DESIGN §3.25 / §4.39 / §4.17，2026-10-07）：课表改动的本机留痕，
 * 两个写入口同列——AI 调课（跨会话可撤销，栈式：只能撤最近一条未撤销，见
 * [AiHistoryViewModel]）与调课检测的「合并所选」（**只留痕、不可撤销**，见
 * `AiApplyHistoryStore` 类注释）。入口：AI 调课页顶栏历史图标、调课检测页顶栏历史图标。
 *
 * 列表观感与全 App 一致：`AppCard` + 16dp 横距。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AiHistoryViewModel = viewModel(
        factory = AiHistoryViewModel.Factory(
            Graph.repository(context),
            Graph.aiApplyHistoryStore(context),
        ),
    )
    val records by viewModel.records.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberAppHaptics()

    var confirming by remember { mutableStateOf<AiApplyRecord?>(null) }

    LaunchedEffect(notice) {
        notice?.let {
            if (it.tone == NoticeTone.Success) haptics.success()
            snackbar.showNotice(AppNoticeVisuals(it.text, tone = it.tone))
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调课记录", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "还没有应用记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "在「AI 调课」里应用所选、或在「调课检测」里合并差异后，每次都会记在这里\n" +
                            "AI 调课最近一次可以撤销；教务合并只留痕",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            val aliveIds by viewModel.aliveTimetableIds.collectAsStateWithLifecycle()
            val undoable = remember(records, aliveIds) {
                AiApplyHistoryStore.latestUndoable(records, aliveIds)
            }
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "AI 调课只能撤销最近一次未撤销的应用；教务合并的记录仅留痕",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    records.forEach { record ->
                        AiHistoryRow(
                            record = record,
                            canUndo = record.id == undoable?.id,
                            // 课表已删除的记录永远撤不动（反向写会向不存在的课表塞孤儿行），
                            // 行上标出来，别让用户点了才知道；alive 未知（首帧）时不标。
                            // 只对可撤销的记录判——教务合并本来就撤不动，标「课表已删除」是误导
                            deadTimetable = record.undoable && !record.undone &&
                                aliveIds?.let { record.timetableId !in it } == true,
                            onUndo = { confirming = record },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    confirming?.let { record ->
        // 骨架走 AppConfirmDialog（2026-11-06 统一）。`danger = true` 只把确认键染成 error 色
        // ——撤销会倒回本机已落库的改动，属于「会丢东西」的动作；文案一字未改。
        AppConfirmDialog(
            title = "撤销这次应用？",
            message = "会把这次应用的改动倒回（课表与日期覆盖在同一事务里）。" +
                "应用之后你手动改过的行会跳过，事后如实报条数。",
            confirmLabel = "撤销",
            onConfirm = {
                confirming = null
                viewModel.undoFromUi(record)
            },
            onDismiss = { confirming = null },
            danger = true,
        )
    }
}

/** 一条应用记录：时间 + 来源 + 摘要 + 计数；右侧按状态给「撤销 / 已撤销 / 课表已删除」。
 *  教务合并的记录没有右侧动作（不可撤销）。 */
@Composable
private fun AiHistoryRow(
    record: AiApplyRecord,
    canUndo: Boolean,
    deadTimetable: Boolean,
    onUndo: () -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    timeText(record.id),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.summary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        record.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(sourceLabel(record))
                        append(" · ")
                        // 两种来源的计数口径不同：AI 按行（「已应用 2 处」= 两行），
                        // 检测按「一次调课」的组（合并回执也是按组报）。别混着写
                        if (record.source == AiApplyRecord.SOURCE_DETECT) {
                            append("已合并 ").append(record.appliedGroups).append(" 处变动")
                        } else {
                            append("已应用 ").append(record.applied).append(" 处")
                            if (record.appliedOverrides > 0) {
                                append("（日期覆盖 ").append(record.appliedOverrides).append("）")
                            }
                        }
                        if (record.skipped > 0) append(" · 跳过 ").append(record.skipped)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            when {
                record.undone -> Text(
                    "已撤销",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )

                canUndo -> TextButton(
                    onClick = onUndo,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) { Text("撤销") }

                deadTimetable -> Text(
                    "课表已删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}

/** 来源标签：两个写入口同列展示，一眼分得清哪条是教务合并（不可撤销）。 */
private fun sourceLabel(record: AiApplyRecord): String =
    if (record.source == AiApplyRecord.SOURCE_DETECT) "教务合并" else "AI 调课"

private val SAME_YEAR = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val OTHER_YEAR = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

/** 记录时间：当年省年份，跨年带年份（留痕上限 50 条，跨年是常态不是例外）。 */
private fun timeText(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return if (time.year == java.time.LocalDate.now().year) {
        time.format(SAME_YEAR)
    } else {
        time.format(OTHER_YEAR)
    }
}
