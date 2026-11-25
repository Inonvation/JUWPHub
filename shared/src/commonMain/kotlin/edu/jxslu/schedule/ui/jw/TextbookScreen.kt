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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.repo.TextbookSync
import edu.jxslu.schedule.domain.Textbook
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.PageEmpty
import edu.jxslu.schedule.ui.common.PageRefreshAction
import edu.jxslu.schedule.ui.common.PageScaffold
import edu.jxslu.schedule.ui.common.showNotice
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
 *
 * ## 抓取链从哪来（iOS 移植 W3，2026-11-25）
 *
 * 原来「本平台有没有教材抓取」由调用方传一个布尔参数，而它的唯一读者是空态文案——
 * 同一个事实有两份表达，迟早对不上（iOS 传 false 之后那段文案就成了永久承诺）。
 * 现在只认 `AppServices.textbookSync()` 的可空性：非空 = 真的能抓，null = 本平台没有这条链。
 *
 * 两个触发点，闸门各不同：
 * 1. **进页补抓**：该学期一条教材都没有时走 [TextbookSync.backfillIfMissing]，闸门在里面
 *    （抓过这个学期 / 6 小时内试过都跳过，零网络）。只跑一次（`autoTried`），
 *    否则每次重组都会再打一次教务。
 * 2. **顶栏「重新导入」**：走 [TextbookSync.syncForTerm]，无闸门——用户手动点的这一次
 *    必须真抓，闸门只该管自动那次（口径同学业完成情况 / 培养方案）。
 *
 * 失败原因原样透出（`没取到教材数据，教务返回 HTTP 500` / `教务会话已失效，请重新登录一次`），
 * 不换成一句「导入失败」：教材丢过就是因为会话被别处登录顶掉，用户需要知道是登录还是数据问题。
 */
@Composable
fun TextbookScreen(
    onBack: () -> Unit,
    /** 在教务网页核对（Android 走 `JwImportActivity.startBrowse`）。 */
    onOpenWebPage: ((title: String, url: String) -> Unit)? = null,
    /** 去导入课表（Android 走 `JwImportActivity.start`）。 */
    onOpenJwImport: (() -> Unit)? = null,
) {
    // 仓库与抓取链都由装配根提供（Android `GraphServices` / iOS `IosServices`）。
    val repo = remember { Services.current.scheduleRepository() }
    val sync = remember { Services.current.textbookSync() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var term by remember { mutableStateOf<String?>(null) }
    var termLoaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var autoTried by remember { mutableStateOf(false) }
    /** 手动抓取失败的原因；空态里显示出来，免得用户只看到一句「还没有教材数据」。 */
    var lastError by remember { mutableStateOf<String?>(null) }
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
    val syncAvailable = sync != null

    /**
     * 抓一次教材。
     *
     * [gated] = 走补抓闸门（进页自动那次）；false = 用户手动点的，必须真抓。
     * [silent] = 自动那次不弹结果气泡，原因仍会留在空态里。
     */
    fun runSync(gated: Boolean, silent: Boolean) {
        if (busy) return
        val service = sync ?: return
        busy = true
        scope.launch {
            val result = try {
                if (gated) service.backfillIfMissing() else service.syncForTerm(term)
            } finally {
                busy = false
            }
            when (result) {
                is TextbookSync.Result.Updated -> {
                    lastError = null
                    // 0 条不是失败：征订窗口关闭 / 本学期没教材时接口就回空，界面说清事实即可
                    snackbar.showNotice(
                        if (result.books == 0) {
                            "已抓取 ${result.term}：教务这学期没有教材记录"
                        } else {
                            "已更新：${result.term} 共 ${result.books} 本教材"
                        },
                    )
                }
                // 自动那次 = 闸门没过（这学期抓过 / 6 小时内试过），无事可报；
                // 手动那次 = 没有学期号，得说清往哪儿走
                TextbookSync.Result.Skipped -> if (!silent && !gated) {
                    val reason = "当前课表还没有学期号，先导入课表再抓教材"
                    lastError = reason
                    snackbar.showNotice(reason)
                }
                TextbookSync.Result.InProgress -> if (!silent) {
                    snackbar.showNotice("正在抓取教材，请稍后再试")
                }
                is TextbookSync.Result.Failed -> {
                    lastError = result.reason
                    if (!silent) snackbar.showNotice(result.reason)
                }
            }
        }
    }

    // 进页补抓一次：闸门在 `backfillIfMissing` 内部判，抓过 / 刚试过时是零网络的空跑。
    // 只跑一次（autoTried），否则每次重组都会再打一次教务。
    LaunchedEffect(books, termLoaded) {
        if (!autoTried && syncAvailable && termLoaded && !noTerm && books?.isEmpty() == true) {
            autoTried = true
            runSync(gated = true, silent = true)
        }
    }

    PageScaffold(
        title = "教材",
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            JwWebActionButton("教材", JwUrls.TEXTBOOK_QUERY, onOpenWebPage)
            if (syncAvailable) {
                PageRefreshAction(
                    busy = busy,
                    onClick = { runSync(gated = false, silent = false) },
                    label = "重新导入",
                )
            }
        },
        // 首帧（Room 未发射）与「空态下手动抓取」都算加载：后者不该在抓取期间继续挂着
        // 「还没有教材数据」，那会让人以为按了没反应（口径同学业完成情况 / 培养方案）
        loading = books == null || (busy && empty),
        loadingText = if (books == null) "正在读取教材" else "正在从教务获取教材…",
        empty = if (empty) {
            // 三种空态的出口不同：没有学期号要去导入；有学期号且本平台能抓就当场抓一次；
            // 抓不动时退回「在教务网页核对」。宿主没给对应动作时不给按钮（不给死入口）。
            val action: Pair<String, () -> Unit>? = when {
                noTerm -> onOpenJwImport?.let { run -> "去导入课表" to { run() } }
                syncAvailable -> "重新导入" to { runSync(gated = false, silent = false) }
                else -> onOpenWebPage?.let { open ->
                    "在教务网页核对" to { open("教材", JwUrls.TEXTBOOK_QUERY) }
                }
            }
            PageEmpty(
                title = "还没有教材数据",
                body = when {
                    !syncAvailable -> "本平台还没有教材抓取，这一页暂时不会有数据。"
                    noTerm -> "当前课表还没有学期号；导入课表后会自动抓取本学期教材。"
                    // 抓过一次但没抓到：原因原样给出（哪一步失败、要不要重新登录）
                    lastError != null -> lastError.orEmpty()
                    else -> "点「重新导入」从教务抓一次；本学期没征订教材时这里也会是空的。"
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
