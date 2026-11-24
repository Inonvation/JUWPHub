package edu.jxslu.schedule.ui.jw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.domain.SemesterConfig
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.PageEmpty
import edu.jxslu.schedule.ui.common.PageScaffold
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 教务 → 教学日历（2026-10-20）。
 *
 * 只展示本机课表的学期配置能确定的东西：第 1 周周一、当前周、每周起止日期。
 * 完整的教务周历（备注列等）仍在顶栏外链打开的原始网页里，不在本地重解析。
 *
 * ## 平台动作在参数里（iOS 移植 S18，2026-11-25）
 *
 * | 参数 | null 的含义 |
 * |---|---|
 * | [onOpenWebPage] | 平台没有内置浏览窗口 → 顶栏外链图标消失 |
 * | [onOpenJwImport] | 平台没有导入窗口 → 空态的「去导入课表」消失 |
 */
@Composable
fun TeachingCalendarScreen(
    onBack: () -> Unit,
    /** 在教务网页核对（Android 走 `JwImportActivity.startBrowse`）。 */
    onOpenWebPage: ((title: String, url: String) -> Unit)? = null,
    /** 去导入课表（Android 走 `JwImportActivity.start`）。 */
    onOpenJwImport: (() -> Unit)? = null,
) {
    // 仓库由装配根提供（Android `GraphServices` / iOS `IosServices`），本页不再摸 `Context`。
    val repo = remember { Services.current.scheduleRepository() }
    val snackbar = remember { SnackbarHostState() }
    var config by remember { mutableStateOf<SemesterConfig?>(null) }
    var term by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        config = runCatching { repo.semester.first() }.getOrNull()
        term = runCatching { repo.currentTerm() }.getOrNull()
        loaded = true
    }

    val weeks = remember(config) { config?.let(::weekRanges).orEmpty() }
    val currentWeek = config?.let {
        ScheduleCalculator.weekNumberOf(it, AppClock.today()).takeIf { week ->
            ScheduleCalculator.isWeekInTerm(it, week)
        }
    }
    val webUrl = term?.let { JwUrls.teachingCalendarUrl(it) } ?: JwUrls.JXZL_QUERY

    PageScaffold(
        title = "教学日历",
        onBack = onBack,
        snackbar = snackbar,
        actions = { JwWebActionButton("教学日历", webUrl, onOpenWebPage) },
        loading = !loaded,
        loadingText = "正在读取教学日历",
        empty = if (loaded && config == null) {
            PageEmpty(
                title = "还没有学期配置",
                body = "导入课表后会自动按教务教学日历校准开学日；也可以先手动设置学期。",
                // 宿主没给导入窗口就不给按钮（不给死入口）
                actionLabel = if (onOpenJwImport != null) "去导入课表" else null,
                onAction = onOpenJwImport,
            )
        } else null,
    ) {
        item {
            AppCard {
                Text(
                    text = "本学期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SummaryItem(
                        label = "第 1 周周一",
                        value = config?.startDate?.let {
                            runCatching { monthDay(LocalDate.parse(it)) }.getOrDefault(it)
                        } ?: "—",
                    )
                    SummaryItem(
                        label = "当前周",
                        value = currentWeek?.let { "第 $it 周" } ?: "不在学期内",
                    )
                    SummaryItem(
                        label = "总周数",
                        value = config?.let { "${it.totalWeeks} 周" } ?: "—",
                    )
                }
            }
        }

        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                weeks.forEachIndexed { index, range ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        )
                    }
                    WeekRow(
                        week = range.week,
                        start = monthDay(range.start),
                        end = monthDay(range.end),
                        current = range.week == currentWeek,
                    )
                }
            }
        }

        item {
            Text(
                text = "第 1 周周一来自教务教学日历；完整周历以网页为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

private data class WeekRange(
    val week: Int,
    val start: LocalDate,
    val end: LocalDate,
)

/**
 * 每周起止日期（周一 → 周日）。
 *
 * `plusWeeks` / `plusDays` 在 kotlinx-datetime 里没有同名成员，改用 [DatePeriod] 位移
 * （与 `ScheduleCalculator.startOfWeek` 同一写法）。**没有 `weeks` 这个字段**，
 * 周数一律折成 `days = 7 * n`——日历日相加，跨月跨年由库自己处理。
 */
private fun weekRanges(config: SemesterConfig): List<WeekRange> {
    val start = runCatching {
        ScheduleCalculator.startOfWeek(ScheduleCalculator.parseDate(config.startDate))
    }.getOrNull() ?: return emptyList()
    return (1..config.totalWeeks).map { week ->
        val monday = start.plus(DatePeriod(days = 7 * (week - 1)))
        WeekRange(week = week, start = monday, end = monday.plus(DatePeriod(days = 6)))
    }
}

/**
 * 日期 → `MM-dd`（**补零**，与搬迁前 `DateTimeFormatter.ofPattern("MM-dd")` 同口径；
 * 与课表清单页的 `M/d` 不补零是两套既有文案，别顺手统一）。
 */
private fun monthDay(date: LocalDate): String =
    "${TextFmt.pad(date.monthNumber)}-${TextFmt.pad(date.dayOfMonth)}"

@Composable
private fun SummaryItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WeekRow(week: Int, start: String, end: String, current: Boolean) {
    val background = if (current) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "第 $week 周",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(58.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$start 至 $end",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
        if (current) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "本周",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
