package edu.jxslu.schedule.ui.homework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.Homework
import edu.jxslu.schedule.domain.courseHomeworkOrder
import edu.jxslu.schedule.domain.homeworkDisplayTitle
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.AttachmentImageUi
import edu.jxslu.schedule.ui.common.EmptyHint
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.MarkdownUi
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 某课程的作业列表（DESIGN §3.11）。
 *
 * 排序口径在 domain：未完成在前（逾期 → 今天 → 未来 → 无截止），已完成沉底
 * （最近完成的在前），中间用「已完成 · N」分隔。每条是 [HomeworkItemBlock]
 * （2026-10-21）：标题 + 截止 chip + 完整正文 + 缩略图；点正文进预览，点铅笔直接编辑。
 *
 * 平台动作与 [HomeworkLibraryScreen] 同一组可空参数（iOS 移植 S13，2026-11-25）：
 * `images` / `markdown` / `onOpenLink` / `onHomeworkChanged`，null 的语义见那一页的参数表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkCourseScreen(
    courseName: String,
    onBack: () -> Unit,
    onOpenHomework: (id: Long, startEditing: Boolean) -> Unit,
    /** 附件图能力；null = 本平台没有。 */
    images: AttachmentImageUi? = null,
    /** Markdown 渲染槽；null = 本平台没有。 */
    markdown: MarkdownUi? = null,
    /** 打开正文链接，返回是否成功；null = 链接不可点。 */
    onOpenLink: ((String) -> Boolean)? = null,
    /** 完成状态改动后重排截止提醒。 */
    onHomeworkChanged: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val repo = remember { Services.current.homeworkRepository() }
    val list by repo.observeForCourse(courseName).collectAsStateWithLifecycle(initialValue = null)
    val today = AppClock.today()
    val snackbar = remember { SnackbarHostState() }

    /** 打不开链接时的统一提示（与作业库同一句）。 */
    fun noticeLinkFailed() {
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals("打不开这个链接", tone = NoticeTone.Warning))
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(courseName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    // 新建：进入详情页的「新建」形态（itemId = 0）
                    IconButton(onClick = { onOpenHomework(0L, false) }) {
                        Icon(AppIcons.Add01, contentDescription = "新建作业")
                    }
                },
            )
        },
    ) { padding ->
        val items = list
        when {
            items == null -> LoadingHint("正在读取作业", Modifier.fillMaxSize().padding(padding))

            items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyHint(
                    title = "这门课还没有作业",
                    body = "点右上角「+」记下一条，可以顺手填上截止日期。",
                )
            }

            else -> {
                val ordered = courseHomeworkOrder(items, today)
                val pending = ordered.filter { !it.done }
                val done = ordered.filter { it.done }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(pending, key = { it.id }) { homework ->
                        HomeworkCard(
                            homework = homework,
                            today = today,
                            onOpen = { onOpenHomework(homework.id, false) },
                            onEdit = { onOpenHomework(homework.id, true) },
                            onToggle = { doneState ->
                                scope.launch {
                                    repo.setDone(homework.id, doneState)
                                    onHomeworkChanged?.invoke()
                                    if (doneState) {
                                        val result = snackbar.showNotice(
                                            "已完成「${homeworkDisplayTitle(homework.detail)}」",
                                            actionLabel = "撤销",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            repo.setDone(homework.id, false)
                                            onHomeworkChanged?.invoke()
                                        }
                                    }
                                }
                            },
                            images = images,
                            markdown = markdown,
                            onOpenLink = onOpenLink?.let { open ->
                                { url: String -> if (!open(url)) noticeLinkFailed() }
                            },
                        )
                    }
                    if (done.isNotEmpty()) {
                        item(key = "done-header") { HomeworkDoneHeader(count = done.size) }
                        items(done, key = { it.id }) { homework ->
                            HomeworkCard(
                                homework = homework,
                                today = today,
                                onOpen = { onOpenHomework(homework.id, false) },
                                onEdit = { onOpenHomework(homework.id, true) },
                                onToggle = { doneState ->
                                    scope.launch {
                                        repo.setDone(homework.id, doneState)
                                        onHomeworkChanged?.invoke()
                                    }
                                },
                                images = images,
                                markdown = markdown,
                                onOpenLink = onOpenLink?.let { open ->
                                    { url: String -> if (!open(url)) noticeLinkFailed() }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单条作业的描边卡（课程页是顶层列表，条目各自成卡；组卡展开区走平铺）。 */
@Composable
private fun HomeworkCard(
    homework: Homework,
    today: LocalDate,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    images: AttachmentImageUi?,
    markdown: MarkdownUi?,
    onOpenLink: ((String) -> Unit)?,
) {
    AppCard(contentPadding = PaddingValues(0.dp)) {
        HomeworkItemBlock(
            homework = homework,
            today = today,
            onClick = onOpen,
            onEdit = onEdit,
            onToggle = onToggle,
            markdown = markdown,
            images = images,
            onOpenLink = onOpenLink,
        )
    }
}
