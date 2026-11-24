package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.DataSelfTestResult
import edu.jxslu.schedule.ui.DataSelfTestStrip
import edu.jxslu.schedule.ui.common.AppBackHandler
import edu.jxslu.schedule.ui.jw.JwHubScreen
import edu.jxslu.schedule.ui.me.DisplaySettingsContent
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.me.SettingsScreen
import edu.jxslu.schedule.ui.theme.JuwTheme
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import edu.jxslu.schedule.ui.week.WeekScreen
import edu.jxslu.schedule.ui.week.WeekViewModel
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

@Composable
private fun IosRoot(services: IosServices) {
    JuwTheme {
        var jwOpen by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(IosTab.Week) }
        // 二级页栈：Android 是 SubpageActivity 的任务栈，iOS 用一份列表顶上（见 IosNavigation.kt）
        val stack = remember { mutableStateListOf<IosRoute>() }
        var drawerOpen by remember { mutableStateOf(false) }
        // 「我的」与课表显示设置共用同一个 VM（Android 侧是 Activity 作用域，两边等价）。
        // iOS 没有配置变更重建，`remember` 的生命周期就是这一屏；见 design/ios-port.md §11.2 ①。
        val meViewModel = remember { MeViewModel(services.scheduleRepository()) }
        val top = stack.lastOrNull()

        // iOS 没有系统返回手势：二级页栈非空时把「返回」接到这个缝上（Android 是实体返回键）
        AppBackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

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
                    if (top != null) {
                        IosRouteContent(
                            route = top,
                            services = services,
                            meViewModel = meViewModel,
                            push = { stack.add(it) },
                            pop = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                        )
                    } else {
                        when (tab) {
                            IosTab.Week -> {
                                val viewModel = remember { WeekViewModel(services.scheduleRepository()) }
                                WeekScreen(
                                    viewModel = viewModel,
                                    // 空态的「去导入课表」→ 教务登录页（WKWebView 登录后自动抓取落库）
                                    onOpenJwImport = { jwOpen = true },
                                    // 课程长按菜单里的「笔记 / 作业」→ 对应课程的分组页
                                    onOpenCourseNotes = { course ->
                                        stack.add(IosRoute(SubpageScreen.NOTES_COURSE, course.name))
                                    },
                                    onOpenCourseHomework = { course ->
                                        stack.add(IosRoute(SubpageScreen.HOMEWORK_COURSE, course.name))
                                    },
                                    // 显示设置面板（S17 搬进共享层）：顶栏那只眼睛从此在 iOS 上也有入口。
                                    // 面板里的「背景」整组不出现——iOS 还没有 UIImage 解码实现，
                                    // 装配根的 scheduleBackgroundFiles() 返回 null。
                                    displaySettings = { headerMinDp, dismiss ->
                                        IosDisplaySettingsOverlay(
                                            viewModel = meViewModel,
                                            headerMinDp = headerMinDp,
                                            onDismiss = dismiss,
                                        )
                                    },
                                    // 其余平台动作（时间表管理 / 调课 / 分享 / 日历同步 / 文件选择）
                                    // 在 iOS 侧还没有实现，留默认值——对应入口本来就不渲染。
                                )
                            }

                            // 教务 Tab（S18 搬进共享层）：概览数字读本地表（Room），两端同源。
                            // 七个入口接真页面；学业 / 培养方案 / 实习基地 / 评教 / 请假 / 报修 /
                            // 校历的页面还在 `:app`，传 null = 整行不渲染（§11.2 ④）。
                            IosTab.Jw -> JwHubScreen(
                                onOpenTheorySchedule = {
                                    stack.add(IosRoute(SubpageScreen.THEORY_SCHEDULE))
                                },
                                onOpenLabSchedule = {
                                    stack.add(IosRoute(SubpageScreen.LAB_SCHEDULE))
                                },
                                onOpenScores = { stack.add(IosRoute(SubpageScreen.SCORES)) },
                                onOpenExams = { stack.add(IosRoute(SubpageScreen.EXAMS)) },
                                onOpenTextbooks = { stack.add(IosRoute(SubpageScreen.TEXTBOOKS)) },
                                onOpenSelection = { stack.add(IosRoute(SubpageScreen.SELECTIONS)) },
                                onOpenTeachingCalendar = {
                                    stack.add(IosRoute(SubpageScreen.TEACHING_CALENDAR))
                                },
                            )

                            // 「我的」= 设置首页（S17 搬进共享层）。每个入口都 push 一个路由，
                            // 没搬的页面落在 IosNotPortedScreen（一句确定的话，不是点了没反应）。
                            IosTab.Me -> SettingsScreen(
                                onOpen = { screen -> stack.add(IosRoute(screen)) },
                                viewModel = meViewModel,
                            )
                        }
                    }
                }
                // 底栏只在根层出现：二级页有自己的返回键，再压一条底栏会把返回路径搞混
                if (top == null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IosTab.entries.forEach { entry ->
                            TextButton(onClick = { tab = entry }) { Text(entry.label) }
                        }
                        // Android 的入口在左侧抽屉里；iOS 这一版把抽屉收到「更多」按钮后面
                        TextButton(onClick = { drawerOpen = true }) { Text("更多") }
                    }
                }
                DbSelfTestStrip()
            }
        }

        if (drawerOpen) {
            IosDrawer(
                onPick = { route ->
                    drawerOpen = false
                    stack.add(route)
                },
                onDismiss = { drawerOpen = false },
            )
        }
    }
}

/**
 * iOS 底栏的三个 Tab。
 *
 * Android 的底栏是「今日 / 课表 / 教务」，iOS 这一版没有今日页——`TodayScreen` 依赖
 * water / ujing / qzxy / ebike / homework / inbox / jwvw / reminder 八个包，全都在 `:app`。
 * 所以第三格换成「我的」，它是真页面（`SettingsScreen`）。
 *
 * 今日页搬完之后这里改回 Android 的原序，并把「我的」移到抽屉里（Android 就是从抽屉进的）。
 */
private enum class IosTab(val label: String) {
    Week("课表"),
    Jw("教务"),
    Me("我的"),
}

/**
 * iOS 侧入口（P3，2026-11-24；M1 起是产品页面）。
 *
 * Swift 侧通过 `MainViewControllerKt.MainViewController()` 拿到这个 UIViewController，
 * 再用 `UIViewControllerRepresentable` 塞进 SwiftUI。这是 Compose Multiplatform 在 iOS 上的
 * 标准接法：Compose 负责画，Swift 只负责窗口与生命周期。
 *
 * ## 现在的页面结构（2026-11-25 起是真正的导航栈）
 *
 * | 区域 | 内容 | 说明 |
 * |---|---|---|
 * | 主体 | 二级页栈顶（[IosRouteContent]）或三个 Tab 之一 | 栈空时显示 Tab，栈非空时显示栈顶 |
 * | 底栏 | 课表 / 教务 / 我的 + 「更多」 | 只在栈空时出现；「更多」打开左侧抽屉 |
 * | 抽屉 | [IosDrawer] | 分组列出**已经搬过来**的二级页（白名单在 `IosNavigation.kt`） |
 * | 覆盖层 | [JwLoginScreen] | 点「去导入」进来的教务登录 WebView（WKWebView） |
 * | 底部 | [DbSelfTestStrip] 一行 | 读写探针，**保留是因为它是「数据链路通了」的唯一屏上证据** |
 *
 * 路由键用的是共享层的 [SubpageScreen]（Android 的 `SubpageActivity` 消费同一份），
 * 所以两端不会出现「一边有入口、一边没有」。还没搬的页面落在 `IosNotPortedScreen`，
 * 看到的是确定的一句话而不是点了没反应；完整清单见 `design/ios-port.md` §12。
 *
 * 与 Android 的**已知差异**：底栏第三格是「我的」而不是「今日」——`TodayScreen` 依赖
 * water / ujing / qzxy / ebike / homework / inbox / jwvw / reminder 八个包，全在 `:app`。
 * 今日页搬完后改回 Android 的原序。
 *
 * 搬掉的两个旧面板：`WeekGridPreview`（样例网格）与 `LinkValidationScreen`（定点小数对照）。
 * 它们的验收目的都已达成——真网格取代样例网格，小数口径由 2419 条 JVM 单测继续钉住。
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

/**
 * iOS 侧的显示设置覆盖面板（S17，2026-11-25）。
 *
 * 与 Android 的 `DisplaySettingsOverlay`（`:app`）是**同一个面板的两种壳**：
 * 里面都是共享层的 `DisplaySettingsContent`，改的全是同一份 `DisplayPrefs`。
 *
 * 壳为什么不能共用：Android 那份挂着 `PredictiveBackHandler`（`androidx.activity`）与
 * `LocalConfiguration`，两者在 `commonMain` 不可见，而且 iOS 没有「系统返回手势」这回事。
 *
 * 这一版只做「盖住下半屏 + 点遮罩关闭 + 完成按钮」：拖拽调高（把手）与预测性返回是
 * Android 的交互习惯，iOS 用户预期的是「弹层铺到底、点外部关闭」，硬搬反而别扭。
 * 面板里「背景」整组不出现——iOS 还没有 `UIImage` 解码实现（见 `IosServices`）。
 */
@Composable
private fun IosDisplaySettingsOverlay(
    viewModel: MeViewModel,
    headerMinDp: Float,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // 遮罩：点它就关（与 Android 同一手感：无波纹点击）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    onClickLabel = "关闭显示设置",
                    role = Role.Button,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "显示设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                DisplaySettingsContent(
                    viewModel = viewModel,
                    headerMinDp = headerMinDp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
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
