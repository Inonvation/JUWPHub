package edu.jxslu.schedule.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.jxslu.schedule.data.DefaultData
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.domain.LocalTimeLike
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.ui.common.GridCellStyle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 课表网格预览（iOS 移植 P2 第一屏，2026-11-24）。
 *
 * 为什么要有这个屏：`WeekPage` / `TimeRail` / `GridLayout` 搬进 commonMain 之后，
 * 「能编过」不等于「画得对」——列宽、行高、表头日期、课块文字裁切全是渲染期结论，
 * 只有真机能判。这一屏用样例数据把它们一次摆出来，装到 iPhone 上肉眼比一遍就行。
 *
 * 刻意不接 ViewModel / Room / DataStore：整屏是纯 `@Composable` + 常量数据，
 * 这样它同时是「共享 UI 层能脱离 Android 基础设施独立渲染」的证明。
 *
 * 与 Android 课表页的差异（有意为之，不是漏做）：不能横滑翻周、不能点课开详情、
 * 不能点空白加课、不画「现在」时刻线。这些都要 ViewModel 与时序驱动，属于 P2 后续批次。
 */
@Composable
fun WeekGridPreview(modifier: Modifier = Modifier) {
    val slots = DefaultData.defaultTimeSlots
    val semester = DefaultData.defaultSemester
    val courses = remember { sampleCourses() }
    val visibleDays = remember { listOf(1, 2, 3, 4, 5, 6, 7) }

    // 周次取真实当前周，但夹在 1..16：样例课的周次范围就是 1–16，学期推进到第 17 周以后
    // 若不夹，整张网格会因为没有课而变空——预览屏要的是「看一眼网格画得对不对」，
    // 不是「现在有没有课」。日期表头跟着这个周次走，所以看到的日期可能不是今天。
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val week = (ScheduleCalculator.todayWeekOf(semester, today) ?: 1).coerceIn(1, 16)
    val monthLabel = weekDate(semester, week, 1)?.let { "${it.monthNumber}月" }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            PreviewHeader(week = week)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val layout = remember(maxWidth, maxHeight, visibleDays.size) {
                    buildGridLayout(
                        maxHeight = maxHeight,
                        maxWidth = maxWidth,
                        sectionCount = slots.size,
                        dayCount = visibleDays.size,
                    )
                }
                Row(Modifier.fillMaxSize()) {
                    TimeRail(
                        layout = layout,
                        slots = slots,
                        monthLabel = monthLabel,
                        railFontSp = null,
                        dateFontSp = null,
                    )
                    // 宽度按几何算出来（列宽 × 天数），别用 fillMaxWidth：buildGridLayout 已经把
                    // 时间轴宽度和右缘留白从总宽里扣掉了，再撑满会把最后一列挤出色块位置。
                    Box(
                        Modifier
                            .width(layout.dayWidth * visibleDays.size)
                            .fillMaxHeight(),
                    ) {
                        WeekPage(
                            week = week,
                            layout = layout,
                            visibleDays = visibleDays,
                            allCourses = courses,
                            slots = slots,
                            semester = semester,
                            overrides = emptyList(),
                            isTodayWeek = false,
                            todayDay = 0,
                            // WeekPage 只要一个 State 包着「现在」；这里给个静态快照，
                            // 因为 isTodayWeek=false 时它根本不会被读。
                            now = remember { mutableStateOf(LocalTimeLike.now()) },
                            showNonCurrentWeek = true,
                            cellStyle = GridCellStyle(),
                            showNowLine = false,
                            showGridLines = true,
                            dateFontSp = null,
                            tapBlankToAdd = false,
                            onAddEmpty = { _, _ -> },
                            onOpenCourse = {},
                            onOpenOverrideInfo = { _, _ -> },
                        )
                    }
                }
            }
        }
    }
}

/** 顶部说明条：一眼看出这是样例数据、哪一周，免得被误当成真实课表。 */
@Composable
private fun PreviewHeader(week: Int) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "课表网格预览",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
            Text(
                text = "　第 $week 周",
                fontSize = 13.sp,
                color = onSurface.copy(alpha = 0.55f),
            )
        }
        Text(
            text = "样例数据，用于验证共享网格的渲染；不是你的真实课表",
            fontSize = 11.5.sp,
            color = onSurface.copy(alpha = 0.4f),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        )
    }
}

/**
 * 样例课程：够覆盖网格的各种画法就停手，不是一门完整的教学计划。
 *
 * 覆盖到的形态：同学期多时段（高数 2 段、物理 2 段）、单周课、双周课、实验课（[CourseKind.Lab]）、
 * 只上前半学期的课（1–8 周）、周五与周末的对比（周末留空，跟真实课表一致）。
 */
private fun sampleCourses(): List<Course> = listOf(
    Course(
        id = 1, name = "高等数学A(上)", teacher = "王建国", position = "教学北楼 A302",
        day = 1, startSection = 1, endSection = 2, weeks = (1..16).toSet(), colorIndex = 0,
    ),
    Course(
        id = 2, name = "高等数学A(上)", teacher = "王建国", position = "教学北楼 A302",
        day = 3, startSection = 3, endSection = 4, weeks = (1..16).toSet(), colorIndex = 0,
    ),
    Course(
        id = 3, name = "大学物理B", teacher = "刘敏", position = "教学南楼 B105",
        day = 2, startSection = 3, endSection = 4, weeks = (1..16).toSet(), colorIndex = 1,
    ),
    Course(
        id = 4, name = "大学物理B", teacher = "刘敏", position = "教学南楼 B105",
        day = 4, startSection = 1, endSection = 2, weeks = (1..16).toSet(), colorIndex = 1,
    ),
    // 单周课：只在第 1、3、5… 周出现
    Course(
        id = 5, name = "工程制图", teacher = "张伟", position = "工程训练中心 D401",
        day = 2, startSection = 1, endSection = 2, weeks = (1..15 step 2).toSet(), colorIndex = 2,
    ),
    // 双周课：只在第 2、4、6… 周出现
    Course(
        id = 6, name = "物理实验", teacher = "刘敏", position = "实验楼 F102",
        day = 4, startSection = 7, endSection = 8, weeks = (2..16 step 2).toSet(),
        colorIndex = 1, kind = CourseKind.Lab,
    ),
    Course(
        id = 7, name = "大学英语", teacher = "陈晓红", position = "外语楼 C201",
        day = 1, startSection = 5, endSection = 6, weeks = (1..16).toSet(), colorIndex = 3,
    ),
    Course(
        id = 8, name = "程序设计基础(C)", teacher = "李强", position = "信息楼 E303",
        day = 5, startSection = 3, endSection = 4, weeks = (1..16).toSet(), colorIndex = 4,
    ),
    Course(
        id = 9, name = "体育(羽毛球)", teacher = "赵磊", position = "体育馆",
        day = 3, startSection = 7, endSection = 8, weeks = (1..16).toSet(), colorIndex = 5,
    ),
    // 只上前 8 周：第 9 周起自动从网格消失
    Course(
        id = 10, name = "思想道德与法治", teacher = "孙丽", position = "教学北楼 A201",
        day = 5, startSection = 5, endSection = 6, weeks = (1..8).toSet(), colorIndex = 6,
    ),
)
