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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.domain.ScheduleOverrides
import edu.jxslu.schedule.domain.SemesterConfig
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.compactPosition
import edu.jxslu.schedule.domain.dayLabel
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.PageEmpty
import edu.jxslu.schedule.ui.common.courseColor
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * 教务 → 理论课表 / 实验课表（2026-10-21 二版：日程流）。
 *
 * 与课表 Tab 的**周网格**刻意区分：这里是「某一周 × 按天分段」的日程流——
 * 每天一个日期头 + 该天的课竖排（起止时刻 · 节次 · 课名 · 地点教师 · 周次），
 * 左右滑翻周、默认落在本周；顶栏 ‹ › 步进，非本周给「回到本周」。
 * 数据就是导入写进 Room 的 `courses`，按 [CourseKind] 取其中一张表；
 * 某 (周, 天) 的课走 [ScheduleCalculator.coursesOnDay]（唯一入口，
 * 日期覆盖 / 调休 / 停课只在这一处生效），日期推导用
 * [ScheduleOverrides.dateOf]（domain 版本，不再抄 ui/week 的第三份）。
 *
 * 网页核对仍留给顶栏外链（带当前学期参数）。
 *
 * ## 平台动作在参数里（iOS 移植 S18，2026-11-25）
 *
 * 本文件在共享层，拿不到 `Context` / `Activity`。原来两处
 * `JwImportActivity.startBrowse(...)` / `JwImportActivity.start(...)` 收成可空回调，
 * **null = 这个平台没有这项能力，入口整条不渲染**（口径同 `WeekScreen`）：
 *
 * | 参数 | null 的含义 |
 * |---|---|
 * | [onOpenWebPage] | 平台没有内置浏览窗口 → 顶栏外链图标消失，空态的「在教务网页核对」也消失 |
 * | [onOpenJwImport] | 平台没有导入窗口 → 理论课表空态的「去导入课表」消失 |
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwScheduleListScreen(
    kind: CourseKind,
    onBack: () -> Unit,
    /** 在教务网页核对（Android 走 `JwImportActivity.startBrowse`）。 */
    onOpenWebPage: ((title: String, url: String) -> Unit)? = null,
    /** 去导入课表（Android 走 `JwImportActivity.start`）。 */
    onOpenJwImport: (() -> Unit)? = null,
) {
    // 仓库由装配根提供（Android `GraphServices` / iOS `IosServices`），本页不再摸 `Context`。
    val repo = remember { Services.current.scheduleRepository() }
    // 初值留 null：空列表与「还没读到」是两回事，直接给 emptyList 会先闪一下空态
    val courses by repo.courses.collectAsStateWithLifecycle(initialValue = null)
    val slots by repo.timeSlots.collectAsStateWithLifecycle(initialValue = emptyList())
    val semester by repo.semester.collectAsStateWithLifecycle(initialValue = null)
    val overrides by repo.overrides.collectAsStateWithLifecycle(initialValue = emptyList())
    var term by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        term = runCatching { repo.currentTerm() }.getOrNull()
    }

    val isLab = kind == CourseKind.Lab
    val title = if (isLab) "实验课表" else "理论课表"
    val webUrl = if (isLab) JwUrls.labScheduleUrl(term) else JwUrls.scheduleListUrl(term)
    // 只看这一张表（理论 / 实验）；覆盖计划与表无关，摊到 (周, 天) 后自然只影响命中的天
    val mine = remember(courses, kind) { courses.orEmpty().filter { it.kind == kind } }
    val plan = remember(overrides, semester) { ScheduleOverrides.plan(overrides, semester) }
    // 「本周」只认学期内的周次：学期外给 null（没有可回的周次）。
    // 用裸 weekNumberOf 的话，切到上学期课表后「回到本周」会永远挂着点不掉（同课表页，2026-11-05）
    val todayWeek = remember(semester) {
        ScheduleCalculator.todayWeekOf(semester, AppClock.today())
    }

    // 周次状态：数据就绪前不定值（否则 Pager 先渲染第 1 周再跳本周，与课表页同一坑）。
    // 初始 = 本周（学期缺失退 1）；totalWeeks 缺失按 20 兜底。
    val initialWeek = todayWeek ?: 1
    val totalWeeks = (semester?.totalWeeks ?: 20).coerceAtLeast(1)
    val pagerReady = courses != null
    val pagerState = if (pagerReady) {
        rememberPagerState(initialPage = initialWeek.coerceIn(1, totalWeeks) - 1) { totalWeeks }
    } else {
        null
    }
    // 顶栏周次跟手：滑动中读 currentPage + offset 取最近周；停稳以 settledPage 回写，
    // 两者取值一致时不会抖动（课表页同口径）
    var displayWeek by remember(pagerReady) { mutableStateOf(initialWeek) }
    LaunchedEffect(pagerState) {
        val ps = pagerState ?: return@LaunchedEffect
        snapshotFlow {
            ps.currentPage + if (ps.currentPageOffsetFraction > 0.5f) 1 else 0
        }.collectLatest { nearest ->
            displayWeek = (nearest + 1).coerceIn(1, totalWeeks)
        }
    }
    LaunchedEffect(pagerState) {
        val ps = pagerState ?: return@LaunchedEffect
        snapshotFlow { ps.settledPage }.collectLatest { settled ->
            displayWeek = (settled + 1).coerceIn(1, totalWeeks)
        }
    }
    val pagerScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = pinnedStatusBars(),
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    // 宿主不给浏览窗口就不渲染这一格，不留点了没反应的图标
                    if (onOpenWebPage != null) {
                        IconButton(onClick = { onOpenWebPage(title, webUrl) }) {
                            Icon(AppIcons.ExternalLink, contentDescription = "在教务网页核对$title")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WeekNavRow(
                week = displayWeek,
                todayWeek = todayWeek,
                totalWeeks = totalWeeks,
                monthLabel = ScheduleOverrides.dateOf(semester, displayWeek, 1)
                    ?.let { "${it.monthNumber}月" },
                onPrev = { w -> shiftPager(pagerState, w, totalWeeks, pagerScope) },
                onNext = { w -> shiftPager(pagerState, w, totalWeeks, pagerScope) },
                onBackToThisWeek = { w -> shiftPager(pagerState, w, totalWeeks, pagerScope) },
            )
            when {
                !pagerReady -> {
                    // 学期/课程未就绪：Pager 尚未创建，只给提示（同课表页首帧即终态的纪律）
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "正在读取课表…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
                mine.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // 空态的按钮跟着平台能力走：宿主没给对应动作时不给按钮（不给死入口）
                        val emptyAction: Pair<String, () -> Unit>? = if (isLab) {
                            onOpenWebPage?.let { open -> "在教务网页核对" to { open(title, webUrl) } }
                        } else {
                            onOpenJwImport?.let { run -> "去导入课表" to { run() } }
                        }
                        PageEmpty(
                            title = if (isLab) "这张课表还没有实验课" else "课表里还没有理论课",
                            body = if (isLab) {
                                "有些学期本来就没有实验课；导入课表时会一并抓取实验课表。"
                            } else {
                                "课表默认为空，从教务导入后这里按周列出本学期的理论课。"
                            },
                            actionLabel = emptyAction?.first,
                            onAction = emptyAction?.second,
                        )
                    }
                }
                else -> {
                    val ps = pagerState ?: return@Column
                    HorizontalPager(
                        state = ps,
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top,
                    ) { pageIndex ->
                        WeekDayList(
                            week = pageIndex + 1,
                            courses = mine,
                            slots = slots,
                            semester = semester,
                            plan = plan,
                            todayWeek = todayWeek,
                        )
                    }
                }
            }
        }
    }
}

/** 翻页出口：目标周夹进 1..totalWeeks；state 未就绪静默跳过。 */
private fun shiftPager(state: PagerState?, targetWeek: Int, totalWeeks: Int, scope: kotlinx.coroutines.CoroutineScope) {
    val ps = state ?: return
    val target = targetWeek.coerceIn(1, totalWeeks) - 1
    if (target != ps.currentPage) {
        scope.launch { ps.animateScrollToPage(target) }
    }
}

/** 顶栏下那条周次导航：‹ 第 N 月 · 第 N 周（· 本周）› + 非本周的「回到本周」。 */
@Composable
private fun WeekNavRow(
    week: Int,
    todayWeek: Int?,
    totalWeeks: Int,
    monthLabel: String?,
    onPrev: (Int) -> Unit,
    onNext: (Int) -> Unit,
    onBackToThisWeek: (Int) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onPrev(week - 1) }, enabled = week > 1) {
                Icon(AppIcons.ArrowLeft01, contentDescription = "上一周", modifier = Modifier.size(18.dp))
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (monthLabel != null) {
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurface.copy(alpha = 0.45f),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "第 $week 周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (todayWeek != null && week == todayWeek) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "本周",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            IconButton(onClick = { onNext(week + 1) }, enabled = week < totalWeeks) {
                Icon(AppIcons.ArrowRight01, contentDescription = "下一周", modifier = Modifier.size(18.dp))
            }
        }
        // 非本周时给一条显式回口；本 = 本周时不占位（防高度跳动）
        if (todayWeek != null && week != todayWeek) {
            Text(
                text = "回到本周",
                style = MaterialTheme.typography.labelMedium,
                color = primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "回到本周") { onBackToThisWeek(todayWeek) }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 一周的日程流：7 天（按 [ScheduleOverrides.dateOf] 推日期），每天一段。
 * 今天 = 日期头加主色；停课日给「放假」行；调休日给「补 · 上第 N 周周 X」标记。
 */
@Composable
private fun WeekDayList(
    week: Int,
    courses: List<Course>,
    slots: List<TimeSlot>,
    semester: SemesterConfig?,
    plan: Map<ScheduleOverrides.Slot, ScheduleOverrides.Directive>,
    todayWeek: Int?,
) {
    val today = AppClock.today()
    val todayIsHere = todayWeek != null && week == todayWeek
    // kotlinx-datetime 的 DayOfWeek 以周一为 0，+1 回到「1=周一 … 7=周日」口径（与 domain 同源）
    val todayDay = today.dayOfWeek.ordinal + 1
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..7).forEach { day ->
            val date = ScheduleOverrides.dateOf(semester, week, day)
            val dayCourses = ScheduleCalculator.coursesOnDay(courses, week, day, plan)
            val isToday = todayIsHere && day == todayDay
            val suspended = ScheduleOverrides.isSuspended(plan, week, day)
            val followed = ScheduleOverrides.followedFrom(plan, week, day)
            item(key = "d$day") {
                DayHeader(
                    day = day,
                    date = date,
                    isToday = isToday,
                    count = dayCourses.size,
                    suspended = suspended,
                    followed = followed,
                )
            }
            when {
                dayCourses.isNotEmpty() -> items(dayCourses, key = { "d${day}c${it.id}" }) { course ->
                    ScheduleDayCard(course = course, slots = slots)
                }
                suspended -> item(key = "d${day}s") { SuspendedCard(date) }
                else -> item(key = "d${day}e") { FreeCard() }
            }
        }
    }
}

/** 日期头：周X · M/d（今天加主色胶囊），右侧 N 节课 / 调休 / 停课标记。 */
@Composable
private fun DayHeader(
    day: Int,
    date: LocalDate?,
    isToday: Boolean,
    count: Int,
    suspended: Boolean,
    followed: ScheduleOverrides.Slot?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "周${dayLabel(day)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isToday) primary else onSurface.copy(alpha = 0.75f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = date?.let(::monthDay) ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = onSurface.copy(alpha = 0.45f),
        )
        if (isToday) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "今天",
                style = MaterialTheme.typography.labelSmall,
                color = primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(primary.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        val tag = when {
            followed != null -> "补 · 上第${followed.week}周周${dayLabel(followed.day)}"
            suspended -> "放假 · 无课"
            count > 0 -> "$count 节课"
            else -> null
        }
        if (tag != null) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = if (followed != null || suspended) primary else onSurface.copy(alpha = 0.45f),
            )
        }
    }
}

/**
 * 一节课的日程卡：左缘色条 + 起止时刻列 + 课名 / 地点·教师 / 周次。
 * 时刻取 [ScheduleCalculator.courseStartMinutes] / EndMinutes 唯一口径
 * （自定义时间课自动生效）。
 */
@Composable
private fun ScheduleDayCard(course: Course, slots: List<TimeSlot>) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = courseColor(course.colorIndex)
    val start = ScheduleCalculator.courseStartMinutes(slots, course)
    val end = ScheduleCalculator.courseEndMinutes(slots, course)
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            // 左缘色条：与课表网格里同一门课的颜色对应
            Box(
                Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .width(66.dp)
                    .padding(start = 10.dp, top = 11.dp, bottom = 11.dp),
            ) {
                Text(
                    text = start?.let(TextFmt::clock) ?: "--:--",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sectionLabel(course),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                )
                if (end != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = TextFmt.clock(end),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 6.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    compactPosition(course.position).takeIf { it.isNotBlank() }?.let { "@$it" },
                    course.teacher.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val weeks = ScheduleCalculator.formatWeeks(course.weeks)
                if (weeks.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "第 $weeks 周",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurface.copy(alpha = 0.42f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** `3-4节` / `5节`（单节不带尾横线）。 */
private fun sectionLabel(course: Course): String =
    if (course.endSection > course.startSection) {
        "${course.startSection}-${course.endSection}节"
    } else {
        "${course.startSection}节"
    }

/**
 * 日期 → `M/d`（月/日都不补零）。
 *
 * 搬迁前是 `DateTimeFormatter.ofPattern("M/d")`；kotlinx-datetime 没有格式化器，
 * 这里拼两个整数——与 `WeekScreen` 顶部大号日期的口径一致（都不补零）。
 */
private fun monthDay(date: LocalDate): String = "${date.monthNumber}/${date.dayOfMonth}"

/** 无课日期的占位行（保留日期节奏，比整段消失好读）。 */
@Composable
private fun FreeCard() {
    Text(
        text = "无课",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

/** 停课 / 放假日的占位行。 */
@Composable
private fun SuspendedCard(date: LocalDate?) {
    val d = date?.let(::monthDay).orEmpty()
    Text(
        text = if (d.isBlank()) "放假 / 停课" else "$d 放假 / 停课，当天不按课表上课",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}
