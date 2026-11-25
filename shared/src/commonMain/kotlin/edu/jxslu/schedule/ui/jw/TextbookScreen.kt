package edu.jxslu.schedule.ui.jw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.domain.Textbook
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.PageEmpty
import edu.jxslu.schedule.ui.common.PageScaffold
import kotlinx.coroutines.flow.collect

/**
 * 教务 → 教材（2026-10-20）。
 *
 * 数据已经在 Room 的 `textbooks` 表里，按当前课表学期整批展示；不再用网页占位。
 * 顶栏外链图标打开教务「学生教材确认」壳页，走宿主的浏览窗口。
 *
 * ## 平台动作在参数里（iOS 移植 S18，2026-11-25）
 *
 * | 参数 | null 的含义 |
 * |---|---|
 * | [onOpenWebPage] | 平台没有内置浏览窗口 → 顶栏外链图标消失 |
 * | [onOpenJwImport] | 平台没有导入窗口 → 空态「去导入课表」消失 |
 */
@Composable
fun TextbookScreen(
    onBack: () -> Unit,
    /** 在教务网页核对（Android 走 `JwImportActivity.startBrowse`）。 */
    onOpenWebPage: ((title: String, url: String) -> Unit)? = null,
    /** 去导入课表（Android 走 `JwImportActivity.start`）。 */
    onOpenJwImport: (() -> Unit)? = null,
    /**
     * 本平台有没有「导入课表时顺带抓教材」那条链（Android 的 `TextbookSync`）。
     *
     * false = iOS（2026-11-25 审计 P0）：那边没有任何一方写 `textbooks` 表，数据永远空。
     * 空态如果还说「导入课表后会自动抓取」，用户会照着反复导入——所以文案与出口都要换掉。
     */
    syncAvailable: Boolean = true,
) {
    // 仓库由装配根提供（Android `GraphServices` / iOS `IosServices`），本页不再摸 `Context`。
    val repo = remember { Services.current.scheduleRepository() }
    val snackbar = remember { SnackbarHostState() }
    var term by remember { mutableStateOf<String?>(null) }
    var termLoaded by remember { mutableStateOf(false) }
    val books by produceState<List<Textbook>?>(initialValue = null) {
        repo.textbooksForCurrentTerm.collect { value = it }
    }

    LaunchedEffect(Unit) {
        term = runCatching { repo.currentTerm() }.getOrNull()
        termLoaded = true
    }

    // 按课程名分组、组内保持原顺序；组间按课程名升序（原实现用 `toSortedMap`，
    // 那是 JVM 专有的 `SortedMap`，commonMain 取不到——改成「分组后排序」等价实现）。
    val groups = remember(books) {
        books.orEmpty()
            .groupBy { it.courseName }
            .toList()
            .sortedBy { it.first }
    }
    val empty = books?.isEmpty() == true
    val noTerm = termLoaded && term.isNullOrBlank()

    PageScaffold(
        title = "教材",
        onBack = onBack,
        snackbar = snackbar,
        actions = { JwWebActionButton("教材", JwUrls.TEXTBOOK_QUERY, onOpenWebPage) },
        loading = books == null,
        loadingText = "正在读取教材",
        empty = if (empty) {
            // 两种空态的出口不同：没有学期号要去导入，有学期号则去网页核对。
            // 宿主没给对应动作时不给按钮（不给死入口）。
            // 本平台没有抓取链时两个出口都不给：导入了也不会变（见 [syncAvailable]）。
            val action: Pair<String, () -> Unit>? = when {
                !syncAvailable -> null
                noTerm -> onOpenJwImport?.let { run -> "去导入课表" to { run() } }
                else -> onOpenWebPage?.let { open ->
                    "在教务网页核对" to { open("教材", JwUrls.TEXTBOOK_QUERY) }
                }
            }
            PageEmpty(
                title = "还没有教材数据",
                body = when {
                    !syncAvailable -> "本平台还没有教材抓取，这一页暂时不会有数据。"
                    noTerm -> "当前课表还没有学期号；导入课表后会自动抓取本学期教材。"
                    else -> "导入课表后会自动抓取本学期教材；也可以先去教务网页核对。"
                },
                actionLabel = action?.first,
                onAction = action?.second,
            )
        } else null,
    ) {
        if (!term.isNullOrBlank()) {
            item {
                Text(
                    text = "$term 学期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        items(groups, key = { it.first }) { (course, list) ->
            // 切学期后整份清单会被替换，逐门卡平滑进出（key 是课程名，见上）
            AppCard(modifier = Modifier.animateItem(), contentPadding = PaddingValues(0.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = course,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${list.size} 本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                list.forEach { book ->
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    BookRow(book)
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: Textbook) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        val sub = listOf(book.author, book.press, book.edition, book.isbn)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (book.price.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Row {
                Text(
                    text = "定价 ${book.price}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}
