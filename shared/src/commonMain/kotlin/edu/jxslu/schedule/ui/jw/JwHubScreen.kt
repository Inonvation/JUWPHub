package edu.jxslu.schedule.ui.jw

import androidx.compose.foundation.background
import edu.jxslu.schedule.ui.icon.AppIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.repo.ExamSnapshot
import edu.jxslu.schedule.data.repo.ExamSnapshotStore
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.domain.ExamMapper.ExamEntry
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.domain.ScoreCalculator
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.LocalBottomBarClearance
import edu.jxslu.schedule.ui.common.SectionHeader
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberResumeTick
import edu.jxslu.schedule.ui.jwvw.JwImportOutcomeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * 教务 Tab（2026-10-20 新 IA，2026-10-20 第三版；2026-10-21 课表入原生页）。
 *
 * 这一版只保留「总览 + 快捷入口 + 分组卡」：网页核对按钮不再堆在每一行，
 * 已经分别落到成绩、考试、教材、学业、培养方案、实习基地、选课、教学日历
 * 各自的页面顶栏。理论 / 实验课表也先落到本机课表的只读清单（网页核对在页顶栏）。
 * 学籍信息不在这里——按用户口径收进左侧栏的头像区（2026-10-21）。
 *
 * ## 平台动作全在参数里（iOS 移植 S18，2026-11-25）
 *
 * 本文件在共享层，拿不到 `Context` / `Activity`。原来每张卡片内联的
 * `openSubpage(...)` / `JwImportActivity.startBrowse(...)` 收成可空回调：
 * **null = 这个平台没有这项能力，对应入口整行（或整格）不渲染**（口径同 `WeekScreen`）。
 * 给个空 lambda 留一个点了没反应的入口，比没有更糟。
 *
 * | 参数 | null 的含义 |
 * |---|---|
 * | 十二个 `onOpenXxx` | 平台没有这个二级页 → 该行 / 该快捷格消失 |
 * | [onOpenWebPage] | 平台没有内置浏览窗口 → 顶栏「校园门户」图标不出现 |
 *
 * 数据不依赖平台能力：成绩 / 教材 / 课表都读 [Services.current] 给的本地表，
 * 考试场次读 [ExamSnapshotStore] 落盘快照，两端同源。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwHubScreen(
    onOpenTheorySchedule: (() -> Unit)? = null,
    onOpenLabSchedule: (() -> Unit)? = null,
    onOpenScores: (() -> Unit)? = null,
    onOpenExams: (() -> Unit)? = null,
    onOpenTextbooks: (() -> Unit)? = null,
    onOpenScholar: (() -> Unit)? = null,
    onOpenCurriculum: (() -> Unit)? = null,
    onOpenSelection: (() -> Unit)? = null,
    onOpenInternship: (() -> Unit)? = null,
    onOpenTeachingCalendar: (() -> Unit)? = null,
    onOpenTeachingEval: (() -> Unit)? = null,
    onOpenLeaveHub: (() -> Unit)? = null,
    onOpenRepair: (() -> Unit)? = null,
    onOpenCalendar: (() -> Unit)? = null,
    /** 在教务网页核对（Android 走 `JwImportActivity.startBrowse` 那个浏览窗口）。 */
    onOpenWebPage: ((title: String, url: String) -> Unit)? = null,
) {
    val clearance = LocalBottomBarClearance.current
    val stats = rememberJwHubStats()
    val snackbar = remember { SnackbarHostState() }
    // 导入窗口 finish 后结果气泡落回主界面；本 Tab 也是落点之一（导入入口在今日 / 课表 / 成绩 / 考试页）。
    JwImportOutcomeEffect(snackbar)

    // 回调为 null 的入口整条不列：列表本身就是「本平台能做什么」的清单。
    val quickEntries = listOfNotNull(
        onOpenTheorySchedule?.let { QuickEntry("理论课表", stats.theoryShort, AppIcons.Book01, it) },
        onOpenLabSchedule?.let { QuickEntry("实验课表", stats.labShort, AppIcons.Layers01, it) },
        onOpenScores?.let { QuickEntry("成绩查询", stats.scoresShort, AppIcons.Analytics01, it) },
        onOpenExams?.let { QuickEntry("考试安排", stats.examsShort, AppIcons.Calendar01, it) },
    )

    val sections = listOfNotNull(
        HubSection(
            title = "教务服务",
            entries = listOfNotNull(
                onOpenTextbooks?.let { HubEntry("教材", "本学期教材清单", AppIcons.Book01, it) },
                onOpenSelection?.let { HubEntry("选课", "记录 · 轮次", AppIcons.Task01, it) },
                onOpenTeachingCalendar?.let {
                    HubEntry("教学日历", "第 1 周周一 · 每周起止", AppIcons.Calendar01, it)
                },
            ),
        ).takeIf { it.entries.isNotEmpty() },
        HubSection(
            title = "学业与规划",
            entries = listOfNotNull(
                onOpenScholar?.let { HubEntry("学业完成情况", "培养方案达成度", AppIcons.Analytics01, it) },
                onOpenCurriculum?.let { HubEntry("培养方案", "课程设置总表", AppIcons.Book01, it) },
                onOpenInternship?.let { HubEntry("实习基地", "基地清单", AppIcons.Layers01, it) },
            ),
        ).takeIf { it.entries.isNotEmpty() },
        HubSection(
            title = "学工与校历",
            entries = listOfNotNull(
                onOpenTeachingEval?.let {
                    HubEntry("学生评教", "评完解锁成绩", AppIcons.CheckmarkCircle02, it)
                },
                onOpenLeaveHub?.let { HubEntry("请假系统", "请假 · 销假 · 续假", AppIcons.File01, it) },
                onOpenRepair?.let { HubEntry("宿舍报修", "进度 · 审批", AppIcons.Toolbox, it) },
                onOpenCalendar?.let { HubEntry("校历", "学年校历", AppIcons.Calendar01, it) },
            ),
        ).takeIf { it.entries.isNotEmpty() },
    )

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = pinnedStatusBars(),
                title = {
                    Column {
                        Text("教务", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stats.header,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                },
                actions = {
                    // 2026-11-08 用户口径：这一格去**校园门户**（工作台 / 服务 / 应用），
                    // 不是强智教务主页。门户自己会跳统一认证，浏览窗口的自动登录照旧接管。
                    // 宿主不给浏览窗口（iOS 侧还没有）就不渲染这一格，不留点了没反应的图标。
                    if (onOpenWebPage != null) {
                        IconButton(onClick = { onOpenWebPage("校园门户", JwUrls.PORTAL) }) {
                            Icon(AppIcons.ExternalLink, contentDescription = "打开学校智慧校园门户")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 20.dp + clearance,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                SummaryCard(stats)
            }

            if (quickEntries.isNotEmpty()) {
                item(key = "quick") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickEntries.forEach { entry ->
                            QuickTile(entry, Modifier.weight(1f))
                        }
                    }
                }
            }

            sections.forEach { section ->
                item(key = "header-${section.title}") {
                    SectionHeader(
                        title = section.title,
                        trailing = "${section.entries.size} 项",
                        horizontalPadding = 0.dp,
                        topPadding = 8.dp,
                        bottomPadding = 0.dp,
                    )
                }
                item(key = "card-${section.title}") {
                    AppCard(contentPadding = PaddingValues(0.dp)) {
                        section.entries.forEachIndexed { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 48.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                )
                            }
                            HubRow(entry)
                        }
                    }
                }
            }

            item(key = "footer") {
                Text(
                    text = "数据来自教务系统，非学校官方应用；有疑问以教务网页为准。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp),
                )
            }
        }
    }
}

private data class HubEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private data class HubSection(
    val title: String,
    val entries: List<HubEntry>,
)

private data class QuickEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun SummaryCard(stats: JwHubStats) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "学业概览",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stats.scoreTerm,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Metric("成绩", stats.scores, Modifier.weight(1f))
            Metric("考试", stats.exams, Modifier.weight(1f))
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
        Row(Modifier.fillMaxWidth()) {
            Metric("教材", stats.textbooks, Modifier.weight(1f))
            Metric("课表", stats.schedule, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickTile(entry: QuickEntry, modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier.heightIn(min = 78.dp),
        onClick = entry.onClick,
        onClickLabel = entry.title,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HubRow(entry: HubEntry) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = entry.title) {
                haptics.tap()
                entry.onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp),
        )
    }
}

private data class JwHubStats(
    val header: String,
    val scoreTerm: String,
    val scores: String,
    val scoresShort: String,
    val exams: String,
    val examsShort: String,
    val textbooks: String,
    val schedule: String,
    val theoryShort: String,
    val labShort: String,
    /**
     * 考试快照是否已读回。false 时 [exams] / [examsShort] 是占位符「…」而不是
     * 「暂无考试」——快照是磁盘文件，异步读，读到之前不能下「没有考试」的结论
     * （2026-11-06 审计 2-B）。
     */
    val examLoaded: Boolean = true,
)

/**
 * 从现有仓库拼出首页数字，不新建数据层：
 * - 成绩取**最近有数据的学期**，不拿空的本学期充数；
 * - 考试读 `ExamSnapshotStore`（考试提醒链写的快照）；
 * - 教材取当前课表学期；
 * - 课表取当前周次与小节合计。
 */
@Composable
private fun rememberJwHubStats(): JwHubStats {
    // 仓库由装配根提供（Android `GraphServices` / iOS `IosServices`），本页不再摸 `Context`。
    val repo = remember { Services.current.scheduleRepository() }
    val scoreRepo = remember { Services.current.scoreRepository() }
    val resumeTick = rememberResumeTick()

    val semester by repo.semester.collectAsStateWithLifecycle(initialValue = null)
    val courses by repo.courses.collectAsStateWithLifecycle(initialValue = emptyList())
    val scoresByTerm by scoreRepo.observeAllGroupedByTerm()
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val textbookTitles by repo.textbookTitlesByCourse
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    var term by remember { mutableStateOf<String?>(null) }
    /**
     * 考试快照（应用私有目录里的 JSON）——**读盘必须离开主线程**（2026-11-06 审计 2-B）。
     *
     * 原实现是 `remember(resumeTick) { ExamSnapshotStore(context).load() }`：组合期同步读盘，
     * 而且 `resumeTick` 每变一次（每次回到本页）就再读一次。
     * 改成 LaunchedEffect + 后台调度器之后首帧没有值，所以另配 [examSnapshotLoaded]：
     * 读到之前「考试」两处显示占位符「…」，**不显示**「暂无考试」——后者是结论，
     * 先显示再跳成「3 场」就是闪一下（这是不能为了干净引入的那种闪烁）。
     *
     * 调度器是 `Dispatchers.Default` 而不是 `Dispatchers.IO`：后者只声明在 JVM 源集，
     * `commonMain` 看不见（同 `JsonFileStore` 的处置）。这里读的是几百字节的小文件。
     */
    var examSnapshot by remember { mutableStateOf<ExamSnapshot.Snapshot?>(null) }
    var examSnapshotLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick) {
        term = runCatching { repo.currentTerm() }.getOrNull()
        examSnapshot = withContext(Dispatchers.Default) {
            runCatching { ExamSnapshotStore().load() }.getOrNull()
        }
        examSnapshotLoaded = true
    }

    return remember(
        semester, courses, scoresByTerm, textbookTitles, term, examSnapshot, examSnapshotLoaded,
    ) {
        val today = AppClock.today()
        val latestScoreTerm = scoresByTerm.entries
            .filter { it.value.isNotEmpty() }
            .maxByOrNull { it.key }
            ?.key
        val summary = latestScoreTerm?.let { t ->
            ScoreCalculator.summarize(t, scoresByTerm[t].orEmpty())
        }
        val scores = when {
            summary == null || summary.courseCount == 0 -> "暂无成绩"
            else -> buildString {
                append("${summary.courseCount} 门")
                // 原为 `"%.1f".format(it)`（String.format 是 JVM 专有）：走 TextFmt 的定点实现，
                // 与 Java `%.1f` 逐位一致（末位恰为 5 时也不会分叉，见 formatFixed 的 KDoc）。
                summary.weightedAverage?.let { append(" · 加权 ${TextFmt.decimals(it, 1)}") }
            }
        }
        val scoresShort = summary?.takeIf { it.courseCount > 0 }
            ?.let { "${it.courseCount} 门" }
            ?: "暂无"

        val examStat = examStatText(examSnapshot?.exams.orEmpty(), today, examSnapshotLoaded)

        val bookCount = textbookTitles.values.sumOf { it.size }
        val textbooks = if (bookCount == 0) "暂无教材" else "$bookCount 本"

        val week = semester?.let { cfg ->
            ScheduleCalculator.weekNumberOf(cfg, today).takeIf { it in 1..cfg.totalWeeks }
        }
        val sections = week
            ?.let { ScheduleCalculator.coursesInWeek(courses, it) }
            ?.sumOf { it.endSection - it.startSection + 1 }
            ?: 0
        val schedule = when {
            courses.isEmpty() -> "课表为空"
            week == null -> "共 ${courses.size} 门课"
            sections == 0 -> "第 $week 周无课"
            else -> "第 $week 周 · $sections 节"
        }
        val header = when {
            term.isNullOrBlank() -> "未配置学期"
            week == null -> term.orEmpty()
            else -> "第 $week 周 · $term"
        }

        JwHubStats(
            header = header,
            scoreTerm = latestScoreTerm ?: "最近学期",
            scores = scores,
            scoresShort = scoresShort,
            exams = examStat.summary,
            examsShort = examStat.short,
            textbooks = textbooks,
            schedule = schedule,
            // 快捷格副标题：理论 / 实验各自的课次数（不是「门」，一门课可能分多块）
            theoryShort = courses.count { it.kind == CourseKind.Theory }
                .takeIf { it > 0 }?.let { "$it 条" } ?: "暂无",
            labShort = courses.count { it.kind == CourseKind.Lab }
                .takeIf { it > 0 }?.let { "$it 条" } ?: "暂无",
            examLoaded = examSnapshotLoaded,
        )
    }
}

/** 教务页「考试」两处文案：概览卡的「考试」指标 + 快捷格副标题。 */
data class ExamStatText(val summary: String, val short: String)

/**
 * 考试场次口径（2026-11-19）：**本学期总场次**，与考试页表头「共 N 场」同一份数
 * （[ExamSnapshotStore] 那份快照，两边同源）。
 *
 * 旧实现数的是「还没考的场次」：11-17 那场考完之后 9 场变 8 场，用户报成
 * 「教务已经 9 场，卡片还是 8 场，重启也没用」——数据其实是新的，口径在掉数。
 * 「最近」只取待考里最早的一场（含当天）；全都考完时保留总场次并标「均已结束」。
 *
 * **public 而不是 internal**（iOS 移植 S18）：`:app` 的 `ExamStatTextTest` 仍在 JVM 侧
 * 钉着这段口径，跨模块 `internal` 不可见。日期入参也从 `java.time.LocalDate` 换成了
 * `kotlinx.datetime.LocalDate`，比较仍按 `yyyy-MM-dd` 的字典序（该格式下等价于按日比较）。
 */
fun examStatText(
    exams: List<ExamEntry>,
    today: LocalDate,
    loaded: Boolean,
): ExamStatText {
    // 快照是磁盘文件、异步读：读到之前不下「没有考试」的结论（届时会闪一下）
    if (!loaded) return ExamStatText("…", "…")
    val dated = exams.filter { it.date.isNotBlank() }
    if (dated.isEmpty()) return ExamStatText("暂无考试", "暂无")
    val todayText = today.toString()
    val next = dated.filter { it.date >= todayText }.minByOrNull { it.date }
    val summary = if (next != null) {
        "共 ${dated.size} 场 · 最近 ${next.date.drop(5)}"
    } else {
        "共 ${dated.size} 场 · 均已结束"
    }
    return ExamStatText(summary, "${dated.size} 场")
}
