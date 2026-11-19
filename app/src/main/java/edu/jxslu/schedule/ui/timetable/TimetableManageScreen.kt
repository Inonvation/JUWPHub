package edu.jxslu.schedule.ui.timetable

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.domain.TermNaming
import edu.jxslu.schedule.domain.Timetable
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.DialogWindowFadeEffect
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.showNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.BookMarked
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Edit02
import me.rerere.hugeicons.stroke.MoreVertical

/**
 * 课表管理页的显示顺序（2026-11-19 用户要求「按学期排序」）。
 *
 * 按学期从新到旧——当前学期就在最上面，不用滚到底去找；**没有学期号的手建表排最后**
 * （它们不属于任何学期，插在中间只会打断时间线）。同 term 的复制表按 `sortOrder` 排。
 *
 * 只影响显示，不改 `sortOrder`：那个值是「新建课表时的相对顺序」，别处也在读。
 */
internal fun sortTimetablesForDisplay(list: List<Timetable>): List<Timetable> =
    list.sortedWith(
        compareByDescending<Timetable> { TermNaming.sortKey(it.term) ?: Int.MIN_VALUE }
            .thenBy { it.sortOrder }
            .thenBy { it.id },
    )

data class ManageUiState(
    val timetables: List<Timetable> = emptyList(),
    val currentId: Long = 0L,
    val defaultSourceId: Long? = null,
    val counts: Map<Long, Int> = emptyMap(),
    val message: String? = null,
    /** 归档进度：null = 没在导入；非 null 时按钮禁用并显示「正在导入 3/6 大二上」。 */
    val archive: ArchiveProgress? = null,
)

/** 学期归档的进度（DESIGN §4.17）：[total] 为 0 表示还在取学期清单。 */
data class ArchiveProgress(val done: Int, val total: Int, val term: String)

class TimetableViewModel(
    private val repo: ScheduleRepository,
    private val app: Application,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val archive = MutableStateFlow<ArchiveProgress?>(null)

    val uiState: StateFlow<ManageUiState> = combine(
        repo.timetables,
        repo.currentTimetableId,
        message,
        archive,
    ) { list, currentId, msg, progress ->
        ManageUiState(
            timetables = sortTimetablesForDisplay(list),
            currentId = currentId,
            defaultSourceId = repo.defaultConfigSourceId(),
            counts = list.associate { it.id to repo.timetableCourseCount(it.id) },
            message = msg,
            archive = progress,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageUiState())

    fun consumeMessage() {
        message.value = null
    }

    fun select(id: Long) {
        viewModelScope.launch {
            repo.setCurrentTimetable(id)
        }
    }

    fun create(name: String) {
        viewModelScope.launch {
            repo.createTimetable(name)
            message.value = "已新建课表「${name.trim()}」"
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            repo.renameTimetable(id, name)
        }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            repo.duplicateTimetable(id)
            message.value = "已复制课表"
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val ok = repo.deleteTimetable(id)
            message.value = if (ok) "已删除课表" else "至少要保留一张课表"
        }
    }

    /** 设为/取消默认配置源。引用型默认：之后新建的课表拷贝该课表当时的设置。 */
    fun setDefaultSource(id: Long?) {
        viewModelScope.launch {
            repo.setDefaultConfigSource(id)
            message.value = if (id == null) "已取消默认配置" else "已设为新建课表的默认配置"
        }
    }

    /**
     * 从教务导入全部学期（DESIGN §4.17，2026-11-19）。
     *
     * [manual] = true：清掉「用户删除过的学期」标记并重建——这是「删了想找回来」的唯一入口。
     * 已有同学期课表仍跳过（历史学期不做覆盖重导）。离开本页会取消本轮（已导入的学期保留）。
     */
    fun archiveAllTerms() {
        if (archive.value != null) return
        archive.value = ArchiveProgress(0, 0, "")
        viewModelScope.launch {
            val result = runCatching {
                Graph.scheduleTermImport(app).archive(manual = true) { done, total, term ->
                    archive.value = ArchiveProgress(done, total, term)
                }
            }.getOrNull()
            archive.value = null
            message.value = when (result) {
                is ScheduleTermImport.Result.Archived -> when {
                    result.createdTerms.isNotEmpty() ->
                        "已导入 ${result.createdTerms.size} 个学期课表（共 ${result.courseCount} 门）"
                    result.failed.isNotEmpty() -> "没有导入成功：${result.failed.first().second}"
                    else -> "没有需要补的学期，课表已是最新"
                }
                is ScheduleTermImport.Result.Failed -> "导入失败：${result.reason}"
                is ScheduleTermImport.Result.InProgress -> "正在导入中，请稍后再试"
                null -> "导入失败，请检查网络与教务登录状态"
            }
        }
    }

    class Factory(private val repo: ScheduleRepository, private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TimetableViewModel(repo, app) as T
    }
}

/**
 * 课表管理页（DESIGN §4.9）：新建 / 切换 / 重命名 / 复制 / 删除 / 设为默认配置。
 *
 * 排版约定：主操作（切换）是卡内填充按钮，重命名/复制/默认/删除收进「⋮」菜单——
 * 五个平铺文字按钮在窄屏必然溢出，且删除混在平铺行里易误触。
 * 删除保护放在 Repository（至少保留一张；删当前自动切换），这里只负责禁用入口与文案。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableManageScreen(
    onBack: () -> Unit,
    viewModel: TimetableViewModel = viewModel(
        factory = TimetableViewModel.Factory(
            Graph.repository(LocalContext.current),
            LocalContext.current.applicationContext as Application,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var createOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Timetable?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Timetable?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showNotice(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表管理") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "「当前」= 课表页正在使用；「默认配置」= 新建课表时拷贝它的设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            // 学期归档（DESIGN §4.17）：删掉的学期从这里找回来；已导入的学期一律跳过
            TimetableArchiveRow(progress = state.archive, onArchive = viewModel::archiveAllTerms)
            state.timetables.forEach { t ->
                TimetableCard(
                    timetable = t,
                    isCurrent = t.id == state.currentId,
                    isDefaultSource = t.id == state.defaultSourceId,
                    courseCount = state.counts[t.id] ?: 0,
                    canDelete = state.timetables.size > 1,
                    onSwitch = { viewModel.select(t.id) },
                    onRename = {
                        renameText = t.name
                        renameTarget = t
                    },
                    onDuplicate = { viewModel.duplicate(t.id) },
                    onToggleDefault = {
                        viewModel.setDefaultSource(if (t.id == state.defaultSourceId) null else t.id)
                    },
                    onDelete = { deleteTarget = t },
                )
            }
            // 列表尾部的第二新建入口：长列表里比顶栏按钮更快够到
            OutlinedButton(
                onClick = {
                    newName = ""
                    createOpen = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(HugeIcons.Add01, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建课表")
            }
        }
    }

    if (createOpen) {
        NameDialog(
            title = "新建课表",
            label = "课表名称",
            initial = newName,
            onValueChange = { newName = it },
            // 重名校验：建出两张「我的课表」后，切换弹层与管理页都难分彼此
            error = state.timetables
                .firstOrNull { it.name == newName.trim() }?.let { "已有同名课表「${it.name}」，换个名字" },
            onConfirm = {
                viewModel.create(newName)
                createOpen = false
            },
            onDismiss = { createOpen = false },
        )
    }

    renameTarget?.let { t ->
        NameDialog(
            title = "重命名课表",
            label = "课表名称",
            initial = renameText,
            onValueChange = { renameText = it },
            error = state.timetables
                .firstOrNull { it.id != t.id && it.name == renameText.trim() }
                ?.let { "已有同名课表「${it.name}」，换个名字" },
            onConfirm = {
                viewModel.rename(t.id, renameText)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { t ->
        // 纯文案两键 → 公共件（窗口淡入 / 长文滚动 / 危险色只有那一份实现）。
        // 删除课表连带整张表的课程与设置，属真丢数据，故 danger = true。
        AppConfirmDialog(
            title = "删除课表「${t.name}」？",
            message = "将删除该课表的全部课程与设置（不可撤销）。\n" +
                "课程数：${state.counts[t.id] ?: 0}",
            confirmLabel = "删除",
            onConfirm = {
                viewModel.delete(t.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            danger = true,
        )
    }
}

/**
 * 「从教务导入全部学期」入口（DESIGN §4.17，2026-11-19）。
 *
 * 放在课表管理页而不是设置页：导入的产物就是这张列表本身，进度与结果（新增的行）
 * 在同一个视野里，不用两个入口各说一遍。
 */
@Composable
private fun TimetableArchiveRow(progress: ArchiveProgress?, onArchive: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "从教务导入全部学期",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    progress == null -> "每个学期各建一张课表（大一上…大四下），已导入的学期会跳过；离开本页会中断，已完成的保留"
                    progress.total == 0 -> "正在读取教务学期清单…"
                    else -> "正在导入 ${progress.done}/${progress.total}：${progress.term}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onArchive,
                enabled = progress == null,
                modifier = Modifier.height(34.dp),
            ) {
                Text(if (progress == null) "开始导入" else "导入中…", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 单张课表卡：名称 + 徽标 + 课程数；主操作「设为当前」，其余操作收进「⋮」菜单。 */@Composable
private fun TimetableCard(
    timetable: Timetable,
    isCurrent: Boolean,
    isDefaultSource: Boolean,
    courseCount: Int,
    canDelete: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timetable.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$courseCount 门课",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (isCurrent || isDefaultSource) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isCurrent) Badge("当前", MaterialTheme.colorScheme.primary)
                    if (isDefaultSource) Badge("默认配置", MaterialTheme.colorScheme.secondary)
                }
            }
            // 学期号（2026-11-19）：归档建出的「大二下」要对得上教务的 2026-2027-2，
            // 否则同一学年两张表在列表里分不清
            timetable.term?.takeIf { it.isNotBlank() }?.let { term ->
                Text(
                    term,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Text(
                        "使用中",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    FilledTonalButton(
                        onClick = {
                            haptics.tap()
                            onSwitch()
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp, vertical = 4.dp,
                        ),
                        modifier = Modifier.padding(vertical = 6.dp).height(34.dp),
                    ) { Text("设为当前", style = MaterialTheme.typography.labelMedium) }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(
                        onClick = {
                            haptics.tap()
                            menuOpen = true
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            HugeIcons.MoreVertical,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(HugeIcons.Edit02, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("复制") },
                            leadingIcon = { Icon(HugeIcons.Copy01, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isDefaultSource) "取消默认" else "设为默认") },
                            leadingIcon = {
                                Icon(HugeIcons.BookMarked, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuOpen = false
                                onToggleDefault()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    HugeIcons.Delete02,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            // 只有一张时禁用删除：Repository 也会拒绝，这里提前禁掉入口
                            enabled = canDelete,
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * 新建 / 重命名课表的输入弹窗。
 *
 * **不迁到 `ui/common/ConfirmDialog.kt`**：那个公共件只提供「纯文案」与「自定义正文」两种
 * 确认框，正文槽里的内容会被套进 `verticalScroll`——输入框放进滚动容器后，聚焦时键盘
 * 弹出、光标定位都不可靠（同 `ImeAwareModalBottomSheet` 那套问题的缩小版）。含输入框的
 * 弹窗要自己的键盘/焦点口径，故保留自建 `AlertDialog`。
 */
@Composable
private fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** 非 null 时禁止确认并显示错误（如同名课表已存在）。 */
    error: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // 窗口淡入必须放在弹窗自己的内容槽里（见 ui/common/DialogWindowFade.kt）：
            // 本弹窗含输入框、不走 AppConfirmDialog，但进出场动画与滚动容器无关，仍然要挂。
            DialogWindowFadeEffect()
            OutlinedTextField(
                value = initial,
                onValueChange = onValueChange,
                label = { Text(label) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = initial.isNotBlank() && error == null, onClick = onConfirm) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
