package edu.jxslu.schedule.ui.me.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.domain.QuietWindow
import edu.jxslu.schedule.domain.QuietWindows
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.ImeAwareModalBottomSheet
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsNote
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.TimeWheelDialog
import kotlinx.coroutines.launch

/**
 * 免扰时段管理页（DESIGN §3.7，2026-10-07）：预置夜间休息 / 午休两个时段，
 * 可编辑、可停用、可删除、可再加。时段内的系统通知不立即发出，推迟到时段
 * 结束后补发（队列与补发见 `ui/reminder/QuietHoursGate`）。
 *
 * 配置存 DataStore（`quiet_hours_json` / `quiet_hours_enabled`，缺键回预置两时段）；
 * 停用的窗口保留在列表里，用户不用每次重建。上课等「时刻型」提醒若补发时事件
 * 已过期（>10 分钟）会静默丢弃，页面说明里写清。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietHoursScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Graph.repository(context) }
    val scope = rememberCoroutineScope()

    // null = DataStore 首帧未到（先渲染占位，避免「预置时段闪一下又消失」）
    val enabled by repo.quietHoursEnabled.collectAsStateWithLifecycle(initialValue = true)
    val windowsState by repo.quietHoursWindows.collectAsStateWithLifecycle(initialValue = null)

    var editing by remember { mutableStateOf<QuietWindow?>(null) }
    var deleting by remember { mutableStateOf<QuietWindow?>(null) }

    fun persist(next: List<QuietWindow>) {
        scope.launch { repo.setQuietHoursWindows(next) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("免扰时段", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 列表未载入时禁用：载入前保存会以空表为底，把 DataStore 里
                    // 全部既有时段静默覆盖成一条新时段
                    IconButton(
                        onClick = {
                            editing = QuietWindow(id = "", label = "", startMinute = 9 * 60, endMinute = 12 * 60)
                        },
                        enabled = windowsState != null,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "添加时段")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSection(title = "总开关", subtitle = "关闭后所有通知回到立即发送，时段配置保留") {
                    SettingSwitchRow(
                        title = "免扰时段",
                        subtitle = "时段内的提醒推迟到时段结束后发出",
                        checked = enabled,
                        onCheckedChange = { value -> scope.launch { repo.setQuietHoursEnabled(value) } },
                    )
                }
            }
            item {
                SettingsSection(
                    title = "时段",
                    subtitle = "预置两个可改；可添加更多（支持跨零点，如 22:00 到次日 07:00）",
                ) {
                    val windows = windowsState
                    if (windows == null) {
                        Text(
                            "正在读取…",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        windows.forEach { window ->
                            WindowRow(
                                window = window,
                                onToggle = { value ->
                                    persist(windows.map {
                                        if (it.id == window.id) it.copy(enabled = value) else it
                                    })
                                },
                                onClick = { editing = window },
                                onDelete = if (windows.size > 1) {
                                    { deleting = window }
                                } else null,
                            )
                        }
                    }
                }
            }
            item {
                SettingsNote("时段内通知推迟到结束后发出；已过点的提醒不补发，收件箱不受影响")
            }
        }
    }

    editing?.let { current ->
        WindowEditSheet(
            initial = current,
            isNew = current.id.isEmpty(),
            onSave = { saved ->
                editing = null
                val next = if (saved.id.isEmpty()) {
                    windowsState.orEmpty() + saved.copy(id = QuietWindows.newId())
                } else {
                    windowsState.orEmpty().map { if (it.id == saved.id) saved else it }
                }
                persist(next)
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { target ->
        AppConfirmDialog(
            title = "删除这个时段？",
            message = "「${target.label}」将从列表里移除；预置时段删除后也可重新添加。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                deleting = null
                persist(windowsState.orEmpty().filterNot { it.id == target.id })
            },
            onDismiss = { deleting = null },
        )
    }
}

/** 一个时段行：名称 + 时间范围 + 行内启停开关；点行进编辑，可删除（保底留一个）。 */
@Composable
private fun WindowRow(
    window: QuietWindow,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    window.label.ifBlank { "未命名时段" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    windowText(window),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            androidx.compose.material3.Switch(
                checked = window.enabled,
                onCheckedChange = onToggle,
            )
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 「22:00 – 次日 07:00」样式的时间范围（跨零点自动带「次日」）。 */
private fun windowText(window: QuietWindow): String {
    val start = QuietWindows.clockText(window.startMinute)
    val end = QuietWindows.clockText(window.endMinute)
    val endText = if (window.endMinute <= window.startMinute) "次日 $end" else end
    return "$start – $endText" + if (window.enabled) "" else " · 已停用"
}

/**
 * 添加 / 编辑时段：名称 + 起止两个滚轮时间选择（「时:分」双滚轮，滚着点确定取当下值）。
 *
 * ## 为什么是底部弹层而不是 `AlertDialog`（2026-11-06 改）
 *
 * 这个表单有输入框，而 `AlertDialog` 是**居中的对话框窗口**，不是给输入设计的：
 *
 * 1. 软键盘弹起时窗口被顶起，可用高度只剩一条缝，而 `AlertDialog` 的内容槽**没有滚动**——
 *    两个时间行和「保存」直接被挤出可视区，用户只能按返回键退出（用户报的现象）；
 * 2. 对话框窗口层的进出场（缩放 + 淡入）与键盘动画是两个系统在抢同一个窗口位置，
 *    观感是弹窗被键盘顶得抖一下。
 *
 * 弹层走 [ImeAwareModalBottomSheet]，两件事它已经内建、这里不要自己拼：
 * - `skipPartiallyExpanded = true`：键盘弹起后 M3 会造出半高锚点并 `animateTo` 过去，
 *   底部按钮被键盘盖住（DESIGN §4.19 定位过同一现象，本容器已钉死）；
 * - 退场「先收键盘、键盘收完再滑走」两段（并行收会因每帧重锚点把退场动画反复重启，
 *   定位过程见 `ui/common/SheetDismissIme.kt` 文件头）。
 *
 * 内容里**不要再加 `Modifier.imePadding()`**：M3 的 `ModalBottomSheet` 已经给容器套了一层
 * `imePadding()`（SheetDismissIme.kt 文件头「容器的父 Box」那段），再套一层等于把内容
 * 顶高两次——键盘弹起时表单会整体跳到半屏之上，收键盘后又滑回去。
 *
 * ## 为什么按钮出口也走 `pendingDismiss`
 *
 * 「保存 / 取消」直接调 `onDismiss()` 会把弹层从组合里瞬间抽掉，退场动画根本来不及播
 * （表现是"啪"地消失，2026-09-30 车号面板报过同款）。`pendingDismiss` 让这两条出口与
 * 点遮罩走同一条序列：播完退场 → 落 `onDismiss` → 再执行保存。保存动作放在 `after` 里
 * 还有一个必要原因：**保存会写 DataStore 并触发列表重组**，先关弹层再落数据，避免
 * 「列表已经变了、弹层还挂着旧值」的中间帧。
 *
 * ## 为什么内层时间滚轮仍是 dialog
 *
 * [TimeWheelDialog] 是 `ui/common` 的共享组件（其它页面也在用），这里是**二次弹层**：
 * 弹层里再叠一层 sheet 会有两套拖拽锚点与两个遮罩，且外层弹层的 `confirmValueChange`
 * 会去询问「能不能 Hidden」，两层互相牵连。一次性的小选择器用 dialog 更稳，也保住了
 * 「滚轮居中、点确定取当下值」的既有手感。打开滚轮前先 `clearFocus()` 收键盘，
 * 否则键盘会把居中的滚轮顶起来（这一步用的是**弹层窗口**的 focus manager——
 * 写在弹层内容里才是这一份，写在弹层外拿到的是页面窗口那份，收不动，见 SheetDismissIme.kt）。
 */
@Composable
private fun WindowEditSheet(
    initial: QuietWindow,
    isNew: Boolean,
    onSave: (QuietWindow) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(initial.label) }
    var startMinute by remember { mutableStateOf(initial.startMinute) }
    var endMinute by remember { mutableStateOf(initial.endMinute) }
    // null = 未在选时间；"start" / "end" = 当前在编辑哪一端
    var picking by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // 内容请求的「带动画关闭」：非 null 时弹层先播退场，跑完再执行它（见上方 KDoc）
    var pendingDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }

    ImeAwareModalBottomSheet(onDismiss = onDismiss, pendingDismiss = pendingDismiss) {
        // 弹窗窗口的 focus manager：必须在弹层内容里取（外面取到的是页面窗口那份）
        val focusManager = LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 小屏 + 大字号下三个字段 + 按钮会超过弹层可用高度，必须能滚
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isNew) "添加时段" else "编辑时段",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("名称（如 夜间休息）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TimeRow(
                label = "开始",
                text = QuietWindows.clockText(startMinute),
                onClick = {
                    // 先收键盘再开滚轮：键盘会把居中的 dialog 顶起来
                    focusManager.clearFocus()
                    picking = "start"
                },
            )
            TimeRow(
                label = "结束",
                text = QuietWindows.clockText(endMinute),
                onClick = {
                    focusManager.clearFocus()
                    picking = "end"
                },
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { pendingDismiss = {} }) { Text("取消") }
                TextButton(
                    onClick = {
                        if (!QuietWindows.validate(startMinute, endMinute)) {
                            // 校验不过就**不关**弹层：错误行贴在两个时间行下方，用户改完再点保存
                            error = "开始与结束不能相同"
                            return@TextButton
                        }
                        pendingDismiss = {
                            onSave(
                                initial.copy(
                                    id = if (isNew) "" else initial.id,
                                    label = label.trim().ifBlank { "自定义时段" },
                                    startMinute = startMinute,
                                    endMinute = endMinute,
                                ),
                            )
                        }
                    },
                ) { Text("保存") }
            }
        }
    }

    picking?.let { which ->
        TimeWheelDialog(
            title = if (which == "start") "开始时间" else "结束时间",
            initial = QuietWindows.clockText(if (which == "start") startMinute else endMinute),
            onConfirm = { value ->
                val minute = QuietWindows.minutesFromClockText(value)
                if (which == "start") startMinute = minute else endMinute = minute
                error = null
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun TimeRow(label: String, text: String, onClick: () -> Unit) {
    SettingItem(
        title = label,
        subtitle = "点按选择时间",
        onClick = onClick,
        showArrow = false,
        value = text,
    )
}
