package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.local.CourseEntity
import edu.jxslu.schedule.data.local.DB_VERSION
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.local.juwDatabasePath
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.DataSelfTestResult
import edu.jxslu.schedule.ui.DataSelfTestStrip
import edu.jxslu.schedule.ui.homework.HomeworkLibraryScreen
import edu.jxslu.schedule.ui.jw.JwHubScreen
import edu.jxslu.schedule.ui.jw.JwScheduleListScreen
import edu.jxslu.schedule.ui.notes.NoteLibraryScreen
import edu.jxslu.schedule.ui.selection.SelectionScreen
import edu.jxslu.schedule.ui.exam.ExamScreen
import edu.jxslu.schedule.ui.score.ScoreScreen
import edu.jxslu.schedule.ui.theme.JuwTheme
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import edu.jxslu.schedule.ui.week.WeekScreen
import edu.jxslu.schedule.ui.week.WeekViewModel
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 侧入口（P3，2026-11-24；M1 起是产品页面）。
 *
 * Swift 侧通过 `MainViewControllerKt.MainViewController()` 拿到这个 UIViewController，
 * 再用 `UIViewControllerRepresentable` 塞进 SwiftUI。这是 Compose Multiplatform 在 iOS 上的
 * 标准接法：Compose 负责画，Swift 只负责窗口与生命周期。
 *
 * ## 现在的页面结构
 *
 * | 区域 | 内容 | 说明 |
 * |---|---|---|
 * | 主体 | [WeekScreen]（共享层同一个页面） | 真实 Room 数据；空库时显示「去导入课表」 |
 * | 底部 | [DbSelfTestStrip] 一行 | 读写探针，**保留是因为它是「数据链路通了」的唯一屏上证据** |
 * | 覆盖层 | [JwLoginScreen] | 点「去导入」进来的教务登录 WebView（WKWebView） |
 *
 * 搬掉的两个旧面板：`WeekGridPreview`（样例网格）与 `LinkValidationScreen`（定点小数对照）。
 * 它们的验收目的都已达成——真网格取代样例网格，小数口径由 2418 条 JVM 单测继续钉住。
 *
 * ## 装配
 *
 * [IosServices] 必须在**任何**共享 ViewModel 构造之前安装，所以放在
 * `ComposeUIViewController` 之前——那是 Swift 调用进来的第一行。
 * 与 Android 侧 `JuwApplication.onCreate` 里的 `Services.install(GraphServices(this))` 对称。
 */
fun MainViewController(): UIViewController {
    val services = IosServices()
    Services.install(services)
    // 首启把作息表与默认学期写进库（Android 在 JuwApplication.ensureDefaults 里做同一件事）
    services.warmUp()
    return ComposeUIViewController { IosRoot(services) }
}

@Composable
private fun IosRoot(services: IosServices) {
    JuwTheme {
        var jwOpen by remember { mutableStateOf(false) }
        var page by remember { mutableStateOf(IosPage.Week) }
        // 教务 Tab 的一层子页（理论 / 实验课表清单，S18）。iOS 没有 `SubpageActivity` 那套
        // 栈，这一版只要一层：非 null = 正在看那张表，返回键清回 null。
        var jwList by remember { mutableStateOf<CourseKind?>(null) }
        // 详情页（笔记 / 作业）还在 :app，iOS 点条目时给一句确定的话，不留「点了没反应」
        var hint by remember { mutableStateOf<String?>(null) }
        if (jwOpen) {
            JwLoginScreen(services = services, onClose = { jwOpen = false })
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (page) {
                        // ViewModel 挂在组合上即可：iOS 侧没有 Android 的配置变更重建，
                        // `remember` 的生命周期就等于这一屏的生命周期。
                        IosPage.Week -> {
                            val viewModel = remember { WeekViewModel(services.scheduleRepository()) }
                            WeekScreen(
                                viewModel = viewModel,
                                // 空态的「去导入课表」→ 教务登录页（M2 第一步：先把登录页跑起来）
                                onOpenJwImport = { jwOpen = true },
                                // 其余平台动作在 iOS 侧都还没有实现，留空即可——
                                // 传 null / 空 lambda 的入口本来就不渲染（见 WeekScreen 头部参数表）。
                            )
                        }
                        // 教务 Tab（S18 搬进共享层）：概览数字读本地表（Room），两端同源。
                        //
                        // 十二个二级页回调里只接五个：
                        // - 成绩 / 考试 / 选课在 iOS 侧**已经是能进的页**（[IosPage] 里各有一项）；
                        // - 理论 / 实验课表是 [JwScheduleListScreen]（本次一起搬进共享层），
                        //   落到本 Tab 的一层子页（[jwList]）里，返回键回汇总页。
                        // 其余七个（教材、学业、培养方案、实习基地、教学日历、评教、请假、报修、校历）
                        // 在 iOS 侧还没有路由，传 null = 整行不渲染（`design/ios-port.md` §11.2 ④：
                        // 给空 lambda 留死入口比没有更糟）。顶栏「校园门户」同理：iOS 还没有通用
                        // 浏览窗口，传 null 后图标不出现。
                        IosPage.Jw -> {
                            val kind = jwList
                            if (kind != null) {
                                JwScheduleListScreen(kind = kind, onBack = { jwList = null })
                            } else {
                                JwHubScreen(
                                    onOpenTheorySchedule = { jwList = CourseKind.Theory },
                                    onOpenLabSchedule = { jwList = CourseKind.Lab },
                                    onOpenScores = { page = IosPage.Score },
                                    onOpenExams = { page = IosPage.Exam },
                                    onOpenSelection = { page = IosPage.Selection },
                                )
                            }
                        }
                        // 笔记库（S13 搬进共享层）：仓库是本地 Room 表，iOS 直接可读。
                        // `images` 传 null = 本平台还没有附件图解码，缩略图整行不出现；
                        // 详情页仍在 :app，条目点开先给提示（见 [hint]）。
                        IosPage.Notes -> NoteLibraryScreen(
                            onBack = { page = IosPage.Week },
                            onOpenNote = { _, _, _ ->
                                hint = "笔记详情页还在 Android 侧，下一批搬过来"
                            },
                        )
                        // 作业库（S13）：同上。`markdown` 传 null 时正文退化成纯文本，
                        // 不会因为「还没有渲染器」就把作业内容藏掉。
                        IosPage.Homework -> HomeworkLibraryScreen(
                            onBack = { page = IosPage.Week },
                            onOpenHomework = { _, _, _ ->
                                hint = "作业详情页还在 Android 侧，下一批搬过来"
                            },
                        )
                        // 选课（S13）：iOS 没有教务直取链（`IosServices.selectionSync()` 返回 null），
                        // 轮次卡会说「本平台还没有教务直取能力」；已同步过的记录仍可读。
                        // 二级页（选课中心 / 预选清单 / 已选课程）在 iOS 侧还没有路由，全部传 null，
                        // 对应入口整行不渲染。
                        IosPage.Selection -> SelectionScreen(onBack = { page = IosPage.Week })
                        // 成绩（S10 搬进共享层）：读的是本地 Room 表，iOS 直接可用。
                        // 四个平台动作全传 null——「从教务导入」「成绩设置」「盖章成绩单」
                        // 三个入口在 iOS 侧还没有对应实现，对应行整行不渲染。
                        IosPage.Score -> ScoreScreen(onBack = { page = IosPage.Week })
                        // 考试（S10）：同上。考试数据来自教务同步，iOS 已有 IosJwReadSession，
                        // 顶栏的「刷新」走 ExamSync，导入页登录后即可拉到。
                        IosPage.Exam -> ExamScreen(onBack = { page = IosPage.Week })
                    }
                }
                hint?.let { text ->
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IosPage.entries.forEach { entry ->
                        TextButton(
                            onClick = {
                                hint = null
                                // 点底栏 = 回到该 Tab 的根页（教务 Tab 正在看课表清单时退回汇总页）
                                jwList = null
                                page = entry
                            },
                        ) { Text(entry.label) }
                    }
                }
                DbSelfTestStrip()
            }
        }
    }
}

/**
 * iOS 侧目前能进的一级页面（S13 起有笔记 / 作业 / 选课；S18 起有教务）。
 *
 * 为什么用枚举而不是导航库：iOS 侧还没有二级页栈（`SubpageActivity` 那一套是 Android 的），
 * 这一版只要能切页就够验证「共享页面在 iOS 上真的画得出来」。等详情页搬完再换真正的路由。
 */
private enum class IosPage(val label: String) {
    Week("课表"),
    // 教务 Tab 的入口汇总页；与 Android 底栏同序（课表之后就是教务）。
    Jw("教务"),
    Notes("笔记"),
    Homework("作业"),
    Selection("选课"),
    Score("成绩"),
    Exam("考试"),
}

/**
 * 教务导入页（M2，2026-11-25）：WKWebView 登录 → 自动抓取并落库。
 *
 * ## 流程
 *
 * 1. `WKWebView` 打开统一身份认证（[JwUrls.ENTRY]），用户自己填账号密码 —— iOS 没有凭证
 *    保险箱（`EncryptedSharedPreferences` 是 Android 专有），**不做自动填表**，也不存密码。
 * 2. 页面落在教务域（[JwUrls.isJwHost]）就说明 CAS 回跳完成、Cookie 已经进
 *    `WKWebsiteDataStore`：这时跑一次 `ScheduleTermImport.archive(manual = true)`，
 *    它用 `IosJwReadSession` 读 Cookie 发 HTTP，走的是**与 Android 同一条共享抓取链**。
 * 3. 结果写成顶栏一行字；返回课表页即可看到，课表是 Room 的流，不需要手动刷新。
 *
 * ## 为什么只抓一次
 *
 * `archive` 自己会遍历教务下拉里的全部学期，重复触发只是白撞教务（强智是单会话，
 * 撞多了会被顶掉）。所以用 `importing` 闸门；失败后闸门放开，下一次页面加载会自然重试。
 *
 * ## 这一版还没有的
 *
 * 没有成绩 / 考试 / 教材 / 选课的单页导入——那些在 Android 侧是 `JwImportScreen` 里
 * 的多个入口，要逐页搬。当前只打通「课表导入」这一条主链路。
 */
@Composable
private fun JwLoginScreen(services: IosServices, onClose: () -> Unit) {
    val controller = remember { WebViewController() }
    val scope = rememberCoroutineScope()
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    // 回调挂在**控制器**上：控制器比组合活得久（视图销毁后仍在），装一次即可。
    // 内层读的 `importing` / `status` 是 remember 的 State 委托，读到的是当前值。
    remember(controller) {
        controller.onPageFinished = { url ->
            currentUrl = url
            error = null
            if (!importing && JwUrls.isJwHost(url)) {
                importing = true
                status = "已登录，正在从教务导入课表…"
                scope.launch {
                    val failure = services.scheduleTermImport()
                        .archive(manual = true) as? ScheduleTermImport.Result.Failed
                    status = if (failure == null) {
                        "导入完成，返回课表即可看到"
                    } else {
                        "导入失败：${failure.reason}"
                    }
                    importing = false
                }
            }
        }
        controller.onError = { message -> error = message }
        controller
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("返回课表") }
            Text(
                // 有状态就说状态：导入中 / 导入结果比 URL 有用得多
                text = error ?: status ?: currentUrl ?: JwUrls.ENTRY,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    error != null -> MaterialTheme.colorScheme.error
                    status != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            WebContentView(
                controller = controller,
                url = JwUrls.ENTRY,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 数据库自检（P2 验收，2026-11-24）：真实写入一门探针课 → 按 id 读回 → 删掉 → 再数一遍。
 *
 * 界面与状态机在 `ui/DataSelfTestStrip.kt`（commonMain），本机门禁能编；这里只留平台相关的
 * 十几行。探针用固定 `timetableId = 999999` 且写完即删，不会污染真实数据。
 */
@Composable
private fun DbSelfTestStrip() {
    DataSelfTestStrip(
        probe = {
            val dao = juwDatabase().courseDao()
            val probe = CourseEntity(
                timetableId = 999_999,
                name = "iOS 数据自检",
                teacher = "-",
                position = "-",
                day = 1,
                startSection = 1,
                endSection = 1,
                weeksCsv = "1",
            )
            val id = dao.upsert(probe)
            val readBack = dao.getById(id)
            dao.delete(probe.copy(id = id))
            val left = dao.getForTimetable(999_999).size
            if (readBack?.name == probe.name && left == 0) {
                DataSelfTestResult(ok = true, text = "DB v$DB_VERSION · 写入并读回 1 门 · 清理后剩 $left 门")
            } else {
                DataSelfTestResult(ok = false, text = "读写不一致：读回「${readBack?.name}」，残留 $left 门")
            }
        },
        footnote = juwDatabasePath(),
        emptyHint = "数据库自检：进行中…",
    )
}
