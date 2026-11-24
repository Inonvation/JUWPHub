package edu.jxslu.schedule.ui.homework

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.Homework
import edu.jxslu.schedule.domain.dueDetailLabel
import edu.jxslu.schedule.domain.imageRefs
import edu.jxslu.schedule.domain.removeImageRef
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.common.AppBackHandler
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppCardDivider
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.AttachmentImageUi
import edu.jxslu.schedule.ui.common.DialogWindowFadeEffect
import edu.jxslu.schedule.ui.common.EmptyHint
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.MarkdownContent
import edu.jxslu.schedule.ui.common.MarkdownEditorField
import edu.jxslu.schedule.ui.common.MarkdownEditorUi
import edu.jxslu.schedule.ui.common.MarkdownImageUi
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * 作业详情/编辑（DESIGN §3.11）：**编辑/预览双态**，2026-10-21 重排。
 *
 * 编辑态 = 满屏写作布局：截止 chips + 完成勾选固定在顶部，正文输入框占满剩余高度
 * （内部滚动），附件条与格式工具条放在 `Scaffold.bottomBar` 并 `imePadding()`——
 * 长文写到下半段时工具条仍在键盘上缘。
 * 预览态 = 截止/完成卡 + Markdown 正文渲染；截止与完成是离散状态，改动即落库，
 * 只有正文需要显式保存。
 * 保存语义与笔记一致：保存 = 落库 + 切回预览 + 「已保存」+ 留在本页；返回时才退出。
 * 删除与「保存并继续新建」收进溢出菜单。
 *
 * ## 平台能力全在参数里（iOS 移植 V4，2026-11-25）
 *
 * 与作业库 / 课程页同一组可空参数（见 `HomeworkLibraryScreen`），另加编辑态那一组
 * （[editor]：附件条底栏 + 选图 + 删文件）。另外两件：
 *
 * | 参数 | null 的含义 |
 * |---|---|
 * | [onOpenLink] | 本平台打不开外链 → 链接不可点 |
 * | [onHomeworkChanged] | 本平台没有截止提醒排程 → 改完只落库，不重排提醒 |
 *
 * `ClassReminder.enqueueCheck`（`AlarmManager` + 通知）与 `Graph.attachmentStore` 都在 `:app`，
 * 共享层拿不到；正文渲染走 [MarkdownContent]，位图绘制交给 [markdownImages]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkDetailScreen(
    courseName: String,
    homeworkId: Long,
    startEditing: Boolean = false,
    onBack: () -> Unit,
    /** 正文附件图的绘制槽；null = 本平台画不出附件图。 */
    markdownImages: MarkdownImageUi? = null,
    /** 附件图能力（缩略图 + 全屏查看器）；null = 本平台没有。 */
    images: AttachmentImageUi? = null,
    /** 编辑态底栏与附件文件删除；null = 本平台没有附件图落盘。 */
    editor: MarkdownEditorUi? = null,
    /** 打开正文链接，返回是否成功；null = 链接不可点。 */
    onOpenLink: ((url: String) -> Boolean)? = null,
    /** 正文 / 截止 / 完成落库后重排截止提醒；null = 本平台没有提醒排程。 */
    onHomeworkChanged: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { Services.current.homeworkRepository() }
    val noteRepo = remember { Services.current.noteRepository() }
    val appScope = remember { Services.current.appScope() }
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberAppHaptics()
    val showNotice: (String, NoticeTone) -> Unit = { message, tone ->
        scope.launch { snackbar.showNotice(AppNoticeVisuals(message, tone = tone)) }
    }

    // 抗重建（优化 2）：转屏 / 进程回收后未保存的编辑必须还在（口径与笔记详情一致）。
    // 截止日期存 epochDay 整数（Long 直接可存 Bundle），NO_DUE = 未设置 —— 不赌 LocalDate 的可保存性。
    // 口径是 kotlinx-datetime 的 `toEpochDays()`：0.8.0 起它返回 Long（不是 Int），所以常量也用 Long。
    var original by remember { mutableStateOf<Homework?>(null) }
    var savedId by rememberSaveable { mutableStateOf(homeworkId) }
    var createdAt by rememberSaveable { mutableStateOf(0L) }
    var detail by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var dueEpochDay by rememberSaveable { mutableStateOf(NO_DUE) }
    val dueDate: LocalDate? = if (dueEpochDay == NO_DUE) null else LocalDate.fromEpochDays(dueEpochDay)
    var done by rememberSaveable { mutableStateOf(false) }
    var loadedFromDb by rememberSaveable { mutableStateOf(false) }
    // 「保存并继续新建」后与路由里的旧 homeworkId 脱钩：进程回收再恢复时不能把旧条目重新灌进来
    var detached by rememberSaveable { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(homeworkId == 0L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 默认态：新建（id=0）直接进编辑；已有作业进预览，列表铅笔入口传 startEditing 直达编辑。
    var editing by rememberSaveable { mutableStateOf(homeworkId == 0L || startEditing) }
    // 「保存并继续新建」后再次聚焦正文：focusTick 变化触发 LaunchedEffect
    var focusTick by remember { mutableStateOf(0) }
    val today = AppClock.today()
    val dueText = dueDate?.let { dueDetailLabel(it, today) } ?: "未设置"

    // 从预览点铅笔进编辑后主动聚焦正文（光标在末尾），省一次点击；
    // loaded 守卫：载入中编辑器还没组合，requestFocus 会抛 FocusRequester not initialized
    val editorFocus = remember { FocusRequester() }
    LaunchedEffect(editing, loaded, focusTick) {
        if (editing && loaded) {
            editorFocus.requestFocus()
            detail = detail.copy(selection = TextRange(detail.text.length))
        }
    }

    LaunchedEffect(homeworkId) {
        if (detached) {
            loaded = true
            return@LaunchedEffect
        }
        if (homeworkId > 0) {
            val item = repo.homework(homeworkId)
            if (item != null) {
                original = item
                if (!loadedFromDb) {
                    savedId = item.id
                    createdAt = item.createdAt
                    detail = TextFieldValue(item.detail)
                    dueEpochDay = item.dueDate?.toEpochDays() ?: NO_DUE
                    done = item.done
                    editing = homeworkId == 0L || startEditing
                }
                loadedFromDb = true
            }
            loaded = true
        }
    }

    // 新作业：写过任何字段才算「有改动」（否则空表单返回时白问一次）。
    // 已有作业：截止/完成改动即落库，脏判断只看正文。
    val dirty = original?.let { base ->
        detail.text != base.detail
    } ?: (detail.text.isNotBlank() || dueDate != null || done)

    /** 保存/删除进行中：不挡的话双击「保存」会对新建作业插两条（original 还没回写，两次 base.id 都是 0）。 */
    var busy by remember { mutableStateOf(false) }

    /**
     * 删掉「本次从正文移除、且全库已无引用」的附件文件（DESIGN §4.20）。
     *
     * 差集在共享层算（`imageRefs` 是 domain 的纯函数），落盘删除交给 [editor]；
     * `null` 说明本平台没有附件目录，跳过即可。
     */
    suspend fun deleteUnreferenced(candidates: Set<String>) {
        val ui = editor ?: return
        val referenced = (noteRepo.allBodies() + repo.allDetails())
            .flatMap { imageRefs(it) }
            .toSet()
        ui.deleteFiles(candidates - referenced)
    }

    /** 保存：正文归库，随后清理「本次移除且已无任何引用」的附件文件（DESIGN §4.20）。 */
    fun save(onSaved: () -> Unit = {}) {
        if (busy) return
        if (detail.text.isBlank()) {
            showNotice("先写点什么", NoticeTone.Warning)
            return
        }
        // savedId/createdAt 参与：恢复后的新作业再次保存要更新同一行，不能再插一条
        val base = original ?: Homework(
            id = savedId,
            courseName = courseName,
            detail = "",
            createdAt = createdAt,
        )
        val newDetail = detail.text
        val due = dueDate
        // 与 repo.save 的 createdAt 戳记条件保持一致：已有行保旧值、新行盖「现在」
        val now = AppClock.nowMillis()
        busy = true
        scope.launch {
            try {
                // 已有作业只写正文：截止/完成可能正在被自动保存，整行 upsert 会把它们覆盖回旧值。
                // 恢复后的「新建后已首存」行（original 为空、savedId > 0）仍走整行写，把本地截止/完成一并落库。
                val persisted = original != null && base.id > 0
                val id = if (persisted) {
                    repo.setDetail(base.id, newDetail)
                    base.id
                } else {
                    repo.save(
                        base.copy(
                            detail = newDetail,
                            dueDate = due,
                            done = done,
                            doneAt = if (done) now else null,
                        ),
                    )
                }
                val removed = imageRefs(base.detail) - imageRefs(newDetail)
                if (removed.isNotEmpty()) {
                    deleteUnreferenced(removed)
                }
                // 回写基线与首存 id/创建时间（与笔记详情同口径）：保存后若留在本页再存，
                // 走更新同一行而不是拿 original==null / savedId==0 再插一条。
                // 已有作业以「保存完成时的 original」为基线，保留期间自动保存的截止/完成。
                val latest = if (persisted) (original ?: base) else base
                original = latest.copy(
                    id = if (base.id > 0) base.id else id,
                    detail = newDetail,
                    dueDate = if (persisted) latest.dueDate else due,
                    done = if (persisted) latest.done else done,
                    doneAt = if (persisted) latest.doneAt else if (done) now else null,
                    createdAt = if (base.id > 0 && base.createdAt > 0) base.createdAt else now,
                    updatedAt = now,
                )
                savedId = original!!.id
                createdAt = original!!.createdAt
                // 作业落库会改变"下一个提醒点"：立即重排（与设置变更同口径）
                onHomeworkChanged?.invoke()
                onSaved()
            } finally {
                busy = false
            }
        }
    }

    fun saveAndPreview() {
        save {
            editing = false
            showNotice("已保存", NoticeTone.Success)
        }
    }

    fun resetForNew() {
        original = null
        savedId = 0L
        createdAt = 0L
        detail = TextFieldValue("")
        dueEpochDay = NO_DUE
        done = false
        editing = true
        detached = true
        focusTick++
    }

    /** 截止日期改动即落库（已有作业）；新建作业先留在本地，随首次保存一起写入。 */
    fun applyDue(newDue: LocalDate?) {
        dueEpochDay = newDue?.toEpochDays() ?: NO_DUE
        val base = original ?: return
        if (base.id <= 0) return
        original = base.copy(dueDate = newDue, updatedAt = AppClock.nowMillis())
        showNotice("截止日期已保存", NoticeTone.Success)
        // 用进程级作用域：改完立刻返回时，写库不会被页面销毁取消
        appScope.launch {
            repo.setDueDate(base.id, newDue)
            onHomeworkChanged?.invoke()
        }
    }

    /** 完成状态改动即落库（定向 UPDATE，不覆盖正文）。 */
    fun applyDone(newDone: Boolean) {
        done = newDone
        val base = original ?: return
        if (base.id <= 0) return
        val now = AppClock.nowMillis()
        original = base.copy(done = newDone, doneAt = if (newDone) now else null, updatedAt = now)
        showNotice(
            if (newDone) "已完成，已自动保存" else "已取消完成，已自动保存",
            NoticeTone.Success,
        )
        appScope.launch {
            repo.setDone(base.id, newDone)
            onHomeworkChanged?.invoke()
        }
    }

    fun deleteHomework() {
        if (busy) return
        val item = original ?: return
        busy = true
        scope.launch {
            try {
                repo.delete(item.id)
                val removed = imageRefs(item.detail)
                if (removed.isNotEmpty()) {
                    deleteUnreferenced(removed)
                }
                onHomeworkChanged?.invoke()
                onBack()
            } finally {
                busy = false
            }
        }
    }

    // 与笔记详情同口径：dirty 已把空表单挡掉，新建写完按返回也要弹确认（防静默丢内容）
    AppBackHandler(enabled = dirty) { showDiscard = true }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = pinnedStatusBars(),
                title = {
                    Column {
                        Text(
                            text = courseName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "作业 · 截止 $dueText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (dirty) showDiscard = true else onBack() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (editing) {
                        IconButton(
                            onClick = {
                                haptics.tap()
                                editing = false
                            },
                        ) {
                            Icon(AppIcons.Eye, contentDescription = "预览")
                        }
                        TextButton(
                            onClick = {
                                haptics.tap()
                                saveAndPreview()
                            },
                            enabled = (dirty || original == null) && !busy,
                        ) {
                            Text("保存")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                haptics.tap()
                                editing = true
                            },
                        ) {
                            Icon(AppIcons.PencilEdit02, contentDescription = "编辑")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AppIcons.MoreVertical, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (editing) "保存并继续新建" else "新建下一条") },
                                onClick = {
                                    menuOpen = false
                                    if (editing) {
                                        save {
                                            resetForNew()
                                            showNotice("已保存，继续新建", NoticeTone.Success)
                                        }
                                    } else {
                                        resetForNew()
                                    }
                                },
                            )
                            if (original != null) {
                                DropdownMenuItem(
                                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuOpen = false
                                        showDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            // null = 本平台没有附件图落盘：底栏整块不渲染（格式工具条与附件条一起消失）
            val bar = editor?.bottomBar
            if (editing && bar != null) {
                bar(
                    detail,
                    { detail = it },
                    showNotice,
                    { name -> detail = TextFieldValue(removeImageRef(detail.text, name)) },
                    { name -> images?.openViewer(name) },
                )
            }
        },
    ) { padding ->
        if (!loaded) {
            LoadingHint("正在读取作业", Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        if (editing) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                HomeworkMetaEditor(
                    dueDate = dueDate,
                    done = done,
                    onDueChange = ::applyDue,
                    onDoneChange = ::applyDone,
                    onCustomDate = { showDatePicker = true },
                )
                MarkdownEditorField(
                    value = detail,
                    onValueChange = { detail = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = "作业要求、要提交的题号…（支持 Markdown 与 \$ 公式）",
                    // 外层 weight(1f) 已经给足高度；这里不再设 min，键盘弹起时不会把底栏顶出去
                    minHeight = 0.dp,
                    focusRequester = editorFocus,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeworkMetaCard(
                    dueDate = dueDate,
                    dueText = dueText,
                    done = done,
                    onDueClick = { showDatePicker = true },
                    onClearDue = { applyDue(null) },
                    onDoneChange = ::applyDone,
                )
                if (detail.text.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 360.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyHint(
                            title = "作业是空的",
                            body = "点右上角铅笔进入编辑。",
                        )
                    }
                } else {
                    // 正文渲染：解析 / 排版 / 点击命中在共享层，位图绘制交给 markdownImages。
                    // onImageClick 为 null = 本平台没有全屏查看器，图不可点（不是点了没反应）。
                    MarkdownContent(
                        markdown = detail.text,
                        modifier = Modifier.fillMaxWidth(),
                        onImageClick = images?.let { ui -> { name: String -> ui.openViewer(name) } },
                        onLinkClick = onOpenLink?.let { open ->
                            { url: String ->
                                if (!open(url)) showNotice("打不开这个链接", NoticeTone.Warning)
                            }
                        },
                        images = markdownImages,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        // M3 DatePicker 的 selectedDateMillis 是「所选日期的 UTC 零点」口径：进出都按 UTC 折算。
        // 这里不能用系统时区——西半球时区下选择/回读会整体差一天。
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (dueDate ?: AppClock.today())
                .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            applyDue(
                                Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC)
                                    .date,
                            )
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showDelete) {
        AppConfirmDialog(
            title = "删除这条作业？",
            message = "删除后无法恢复（详情里的图片会一并清理）。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                showDelete = false
                deleteHomework()
            },
            onDismiss = { showDelete = false },
        )
    }

    if (showDiscard) {
        // 不套 AppConfirmDialog：两个键都不是「取消」——「放弃」丢弃改动返回，「保存」先写盘再返回，
        // 而点外部/返回键只是关窗留在本页。三个出口三种语义，硬套得把「点外部」另塞进 onTapOutside，
        // 后人只改 dismissLabel 就会把「保存」变成「什么都不做」（.agents/rules/ui-common.md：双出口不迁）。
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃未保存的修改？") },
            text = {
                // 弹窗进出场动画：必须在弹窗窗口自己的组合里（内容槽第一行）。
                DialogWindowFadeEffect()
                Text("这条作业有改动还没保存。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        onBack()
                    },
                ) {
                    Text("放弃")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        save { onBack() }
                    },
                ) {
                    Text("保存")
                }
            },
        )
    }
}

/** 编辑态的截止快捷 chips + 完成勾选（固定在正文上方，不随正文滚动）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeworkMetaEditor(
    dueDate: LocalDate?,
    done: Boolean,
    onDueChange: (LocalDate?) -> Unit,
    onDoneChange: (Boolean) -> Unit,
    onCustomDate: () -> Unit,
) {
    val today = AppClock.today()
    // kotlinx 没有 TemporalAdjusters：周日 ordinal 是 6，往回加「模 7 天」就是下一个周日（今天是周日则取今天）。
    val nextSunday = today.plus(DatePeriod(days = (DayOfWeek.SUNDAY.ordinal - today.dayOfWeek.ordinal + 7) % 7))
    val isCustom = dueDate != null &&
        dueDate != today &&
        dueDate != today.plus(DatePeriod(days = 1)) &&
        dueDate != nextSunday
    val haptics = rememberAppHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "截止",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            DueChipButton("今天", dueDate == today) { onDueChange(today) }
            DueChipButton("明天", dueDate == today.plus(DatePeriod(days = 1))) {
                onDueChange(today.plus(DatePeriod(days = 1)))
            }
            DueChipButton("本周日", dueDate == nextSunday) { onDueChange(nextSunday) }
            DueChipButton("自定义", isCustom, onCustomDate)
            DueChipButton("清除", dueDate == null) { onDueChange(null) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = done,
                onCheckedChange = { checked ->
                    haptics.toggle()
                    onDoneChange(checked)
                },
            )
            Text(
                text = "已完成",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}

/** 预览态的截止/完成卡：两个离散状态都可点，改动即落库。 */
@Composable
private fun HomeworkMetaCard(
    dueDate: LocalDate?,
    dueText: String,
    done: Boolean,
    onDueClick: () -> Unit,
    onClearDue: () -> Unit,
    onDoneChange: (Boolean) -> Unit,
) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDueClick() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.Calendar03,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "截止日期",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = dueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (dueDate != null) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onClearDue) { Text("清除") }
            }
        }
        AppCardDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDoneChange(!done) }
                .padding(end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = done,
                onCheckedChange = onDoneChange,
            )
            Text(
                text = "已完成",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

/** 截止快捷 chip：选中态走主色容器，未选中走描边。 */
@Composable
private fun DueChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        },
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(50),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 未设置截止日期在可保存状态里的哨兵值（Int.MIN_VALUE，正常 epochDay 不会取到）。 */
private const val NO_DUE = Long.MIN_VALUE
