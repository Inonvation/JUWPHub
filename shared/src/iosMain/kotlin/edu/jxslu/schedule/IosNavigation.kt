package edu.jxslu.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.kqcx.KqcxBikeClient
import edu.jxslu.schedule.data.kqcx.KqcxOrderStore
import edu.jxslu.schedule.data.repo.RideRecordStore
import edu.jxslu.schedule.data.xg.XgForm
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.ui.ai.AiChatScreen
import edu.jxslu.schedule.ui.ai.AiChatViewModel
import edu.jxslu.schedule.ui.ai.AiHistoryScreen
import edu.jxslu.schedule.ui.ai.AiHistoryViewModel
import edu.jxslu.schedule.ui.ai.AiSettingsScreen
import edu.jxslu.schedule.ui.ai.AiSettingsViewModel
import edu.jxslu.schedule.ui.me.hub.AccountsHubScreen
import edu.jxslu.schedule.ui.campus.CampusCalendarScreen
import edu.jxslu.schedule.ui.campus.CampusCardSettingsScreen
import edu.jxslu.schedule.ui.campus.CampusCardViewModel
import edu.jxslu.schedule.ui.campus.DormRepairScreen
import edu.jxslu.schedule.ui.campus.LeaveHubScreen
import edu.jxslu.schedule.ui.campus.PayCodeScreen
import edu.jxslu.schedule.ui.campus.PayCodeViewModel
import edu.jxslu.schedule.ui.internship.InternshipBasesScreen
import edu.jxslu.schedule.ui.me.CalendarSettingsScreen
import edu.jxslu.schedule.ui.me.CalendarSettingsViewModel
import edu.jxslu.schedule.ui.me.JwAccountScreen
import edu.jxslu.schedule.ui.me.ReminderSettingsScreen
import edu.jxslu.schedule.ui.me.ReminderSettingsViewModel
import edu.jxslu.schedule.ui.me.ShortcutSettingsScreen
import edu.jxslu.schedule.ui.me.ShortcutSettingsViewModel
import edu.jxslu.schedule.ui.me.hub.AboutUpdateScreen
import edu.jxslu.schedule.ui.me.hub.AppearanceSettingsScreen
import edu.jxslu.schedule.ui.me.hub.IosPermissionsStorageScreen
import edu.jxslu.schedule.ui.me.hub.QuietHoursScreen
import edu.jxslu.schedule.ui.me.hub.SupportDevScreen
import edu.jxslu.schedule.ui.me.hub.TermsDeclarationsScreen
import edu.jxslu.schedule.ui.me.hub.TimetableHubScreen
import edu.jxslu.schedule.ui.timetable.ScheduleChangesScreen
import edu.jxslu.schedule.ui.timetable.TimetableManageScreen
import edu.jxslu.schedule.ui.timetable.TimetableViewModel
import edu.jxslu.schedule.ui.toolbox.CainiaoQueryScreen
import edu.jxslu.schedule.ui.ujing.UjingHistoryScreen
import edu.jxslu.schedule.ui.ujing.UjingOrderScreen
import edu.jxslu.schedule.ui.ujing.UjingScreen
import edu.jxslu.schedule.ui.ujing.UjingViewModel
import edu.jxslu.schedule.ui.water.WaterScreen
import edu.jxslu.schedule.ui.wenqu.WenquGrabScreen
import edu.jxslu.schedule.ui.wenqu.WenquGrabViewModel
import edu.jxslu.schedule.ui.wenqu.WenquRecordsScreen
import edu.jxslu.schedule.ui.wenqu.WenquRecordsViewModel
import edu.jxslu.schedule.ui.wenqu.WenquScreen
import edu.jxslu.schedule.ui.wenqu.WenquSettingsScreen
import edu.jxslu.schedule.ui.wenqu.WenquViewModel
import edu.jxslu.schedule.ui.xg.XgFormScreen
import edu.jxslu.schedule.ui.water.WaterViewModel
import edu.jxslu.schedule.ui.toolbox.ToolboxScreen
import edu.jxslu.schedule.ui.tweak.CourseTweakScreen
import edu.jxslu.schedule.ui.tweak.CourseTweakViewModel
import edu.jxslu.schedule.ui.campus.StatementScreen
import edu.jxslu.schedule.ui.campus.StatementViewModel
import edu.jxslu.schedule.ui.curriculum.CurriculumScreen
import edu.jxslu.schedule.platform.IosEbikeReminder
import edu.jxslu.schedule.ui.ebike.BikeMapViewModel
import edu.jxslu.schedule.ui.ebike.EbikeViewModel
import edu.jxslu.schedule.ui.ebike.IosEbikeQrGallery
import edu.jxslu.schedule.ui.ebike.iosCanvasBikeMapSlot
import edu.jxslu.schedule.ui.ebike.iosOpenWechat
import edu.jxslu.schedule.ui.ebike.KvcxOrdersScreen
import edu.jxslu.schedule.ui.ebike.KvcxOrdersViewModel
import edu.jxslu.schedule.ui.ebike.KvcxScreen
import edu.jxslu.schedule.ui.ebike.KvcxViewModel
import edu.jxslu.schedule.ui.ebike.rememberIosBikeScanner
import edu.jxslu.schedule.ui.ebike.RideScreen
import edu.jxslu.schedule.ui.exam.ExamScreen
import edu.jxslu.schedule.ui.exam.ExamSettingsScreen
import edu.jxslu.schedule.ui.homework.HomeworkCourseScreen
import edu.jxslu.schedule.ui.homework.HomeworkDetailScreen
import edu.jxslu.schedule.ui.homework.HomeworkLibraryScreen
import edu.jxslu.schedule.ui.homework.HomeworkTodoScreen
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.inbox.InboxScreen
import edu.jxslu.schedule.ui.jw.JwScheduleListScreen
import edu.jxslu.schedule.ui.jw.StudentInfoScreen
import edu.jxslu.schedule.ui.jw.TeachingCalendarScreen
import edu.jxslu.schedule.ui.jw.TextbookScreen
import edu.jxslu.schedule.ui.life.PowerBillScreen
import edu.jxslu.schedule.ui.life.PowerBillViewModel
import edu.jxslu.schedule.ui.life.PowerRoomPickerViewModel
import edu.jxslu.schedule.ui.me.DataSettingsScreen
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.notes.NoteCourseScreen
import edu.jxslu.schedule.ui.notes.NoteDetailScreen
import edu.jxslu.schedule.ui.notes.NoteLibraryScreen
import edu.jxslu.schedule.ui.score.ScoreScreen
import edu.jxslu.schedule.ui.score.ScoreSettingsScreen
import edu.jxslu.schedule.ui.score.TranscriptHistoryContent
import edu.jxslu.schedule.ui.score.TranscriptScreen
import edu.jxslu.schedule.ui.score.iosSaveTranscriptPdf
import edu.jxslu.schedule.ui.score.iosShareTranscriptPdf
import edu.jxslu.schedule.ui.scholar.ScholarScreen
import edu.jxslu.schedule.ui.selection.SelectionCenterScreen
import edu.jxslu.schedule.ui.selection.SelectionRecordsScreen
import edu.jxslu.schedule.ui.selection.SelectionScreen
import edu.jxslu.schedule.ui.selection.SelectionWishesScreen
import edu.jxslu.schedule.ui.weather.WeatherScreen
import edu.jxslu.schedule.ui.weather.WeatherSettingsScreen
import edu.jxslu.schedule.ui.weather.WeatherViewModel
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS 的二级页路由（2026-11-25）。
 *
 * ## 为什么复用 `SubpageScreen` 而不是另立一套
 *
 * Android 侧的路由枚举就是 [SubpageScreen]（`shared/commonMain`，`SubpageActivity` 消费）。
 * iOS 另立一套枚举的后果是：以后每加一个页面都要改两处、漏一处就出现「Android 有入口、
 * iOS 没有」。所以这里直接用同一个枚举当路由键，抽屉里的分组与条目也照 Android 的来。
 *
 * ## 抽屉里只放已经搬过来的页面
 *
 * [iosDrawerEntries] 是**手工维护的白名单**：搬完一个页面就往这里加一行，同时
 * [IosRouteContent] 里把对应的 `else` 分支换成真页面。没进白名单的页面不会出现在抽屉里
 * ——`design/ios-port.md` §11.2 ④：留一个点了没反应的入口比没有入口更糟。
 *
 * 但 [IosRouteContent] 仍然要覆盖枚举的全部取值（`else` 落到 [IosNotPortedScreen]），
 * 因为 `SettingsScreen` / `JwHubScreen` 这类页面的回调参数类型就是 `SubpageScreen`，
 * 编译器不认「白名单之外不存在」。
 *
 * ## 参数
 *
 * 少数页面要一个字符串参数（`NOTES_COURSE` / `HOMEWORK_COURSE` 要课程名），详情页还要
 * 条目 id 与「进页即编辑」两个参数。路由条目因此是 [IosRoute]（screen + 可选 arg + 两个
 * 详情页参数），不是裸枚举。
 */
internal data class IosRoute(
    val screen: SubpageScreen,
    val arg: String? = null,
    /** 详情页的条目 id（0 = 新建）。 */
    val itemId: Long = 0L,
    /** 详情页进页即进编辑态（列表里的铅笔入口）。 */
    val startEditing: Boolean = false,
)

/**
 * 抽屉分组，顺序与 Android 左侧栏一致（身份 / 钱包 / 生活 / 江水生活 / 学习 / 设置）。
 *
 * 「江水生活」只剩开水房与 U净（问渠与 AI 调课在精简版里下线，见 [IOS_REMOVED_SCREENS]）；
 * 空组不渲染，所以哪一组一条都没有时整组不出现。
 */
internal data class IosDrawerEntry(
    val section: String,
    val route: IosRoute,
    val label: String,
)

/**
 * 抽屉里能进的页面白名单（2026-11-25）。
 *
 * 加条目的同时必须让 [IosRouteContent] 认识它——两处都要改，这是有意的：白名单与渲染分支
 * 对不上时，点进去会落在「还在 Android 侧」的兜底页，一眼能看出来，而不是静默无反应。
 */
internal fun iosDrawerEntries(): List<IosDrawerEntry> = listOf(
    // 学习
    IosDrawerEntry("学习", IosRoute(SubpageScreen.THEORY_SCHEDULE), "理论课表"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.LAB_SCHEDULE), "实验课表"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.TEXTBOOKS), "教材"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.TEACHING_CALENDAR), "教学日历"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.SCHOLAR), "学业完成情况"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.CURRICULUM), "培养方案"),
    // 教务
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SCORES), "成绩"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SCORE_SETTINGS), "成绩设置"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.EXAMS), "考试"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.EXAM_SETTINGS), "考试设置"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SELECTIONS), "选课"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SELECTION_CENTER), "选课中心"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SELECTION_RECORDS), "已选课程"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SELECTION_WISHES), "预选清单"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.STUDENT_INFO), "学籍信息"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.INTERNSHIP_BASES), "实习基地"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.CAMPUS_CALENDAR), "校历"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.LEAVE_HUB), "请假"),
    // 钱包
    IosDrawerEntry("钱包", IosRoute(SubpageScreen.POWER_BILL), "电费"),
    IosDrawerEntry("钱包", IosRoute(SubpageScreen.CAMPUS_STATEMENT), "消费流水"),
    // 校园卡 / 付款码（iOS 移植 Y1，2026-11-25）：两条都在 Android 侧栏有钱包区入口
    // （「一卡通账号」行进校园卡设置，右下角「付款码」进付款码页），相对顺序照 Android。
    // 追加在既有两条之后，不动「电费 / 消费流水」的既有位置。
    IosDrawerEntry("钱包", IosRoute(SubpageScreen.CAMPUS_CARD_SETTINGS), "校园卡"),
    IosDrawerEntry("钱包", IosRoute(SubpageScreen.PAY_CODE), "付款码"),
    // 生活
    IosDrawerEntry("生活", IosRoute(SubpageScreen.INBOX), "收件箱"),
    IosDrawerEntry("生活", IosRoute(SubpageScreen.DORM_REPAIR), "宿舍报修"),
    IosDrawerEntry("生活", IosRoute(SubpageScreen.WEATHER), "天气"),
    IosDrawerEntry("生活", IosRoute(SubpageScreen.WEATHER_SETTINGS), "天气设置"),
    // 设置
    // 课表三页（iOS 移植 X1，2026-11-25）：Android 侧在「设置 → 课表」与「设置 → 数据与权限」，
    // 这里同组相邻，顺序照 Android 的设置页
    IosDrawerEntry("设置", IosRoute(SubpageScreen.COURSE_TWEAK), "调课"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.SCHEDULE_CHANGES), "变动检测"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.TIMETABLE_MANAGE), "课表管理"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.DATA_SETTINGS), "数据与备份"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.CALENDAR_SETTINGS), "校历与日历同步"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.REMINDER_SETTINGS), "上课提醒"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.SHORTCUTS), "快捷方式"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.JW_ACCOUNT), "教务账号"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.ABOUT_UPDATE), "关于与更新"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.TERMS_DECLARATIONS), "条款与声明"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.PERMISSIONS_STORAGE), "权限与存储"),
    IosDrawerEntry("设置", IosRoute(SubpageScreen.SUPPORT_DEV), "支持开发"),
    // 账号
    IosDrawerEntry("账号", IosRoute(SubpageScreen.ACCOUNTS), "账号中心"),
    // 江水生活（已搬的三块）
    IosDrawerEntry("江水生活", IosRoute(SubpageScreen.WATER), "开水房"),
    IosDrawerEntry("江水生活", IosRoute(SubpageScreen.UJING), "U净洗衣房"),
    IosDrawerEntry("江水生活", IosRoute(SubpageScreen.UJING_HISTORY), "洗衣账单"),
)

/**
 * iOS 精简版**下线**的二级页（2026-11-25，用户指定）。
 *
 * 白名单（[iosDrawerEntries]）只挡抽屉这一条路；页面内部还有别的入口能推到同一页
 * （「我的」里的 AI 调课行、账号中心的问渠行、课程详情弹窗的笔记/作业）。逐个改那些页面
 * 等于把共享层的入口全拆一遍，所以再加一道**统一闸门**：宿主 push 前问一次这里，
 * 命中就不进栈。
 *
 * 命中的页面在界面上仍然可能是「能点但没反应」——所以凡是能从共享层点到的入口，
 * 都已同时改成可空回调（null = 不渲染）：
 *
 * | 页面 | 共享层入口的处理 |
 * |---|---|
 * | 笔记 / 作业 | `WeekScreen` / `TodayScreen` 的 `onOpenCourseNotes` / `onOpenCourseHomework` 传 null |
 * | 百宝箱 | `SettingsScreen(showToolbox = false)` |
 * | AI 调课 | `SettingsScreen(showAiSchedule = false)` + `AccountsHubScreen(onOpenAiSettings = null)` |
 * | 问渠 | `AccountsHubScreen(onOpenWenqu = null)` + `TodayViewModel(blockedGuessTargets)` |
 * | 调课记录 | `ScheduleChangesScreen(onOpenHistory = null)` |
 *
 * 这一层留着的意义：以后新加的页面如果忘了摘入口，用户点进去落在「已下线」这一句上，
 * 而不是静默无反应。**别把它当唯一手段**——真正的下线要连入口一起摘。
 */
internal val IOS_REMOVED_SCREENS: Set<SubpageScreen> = setOf(
    SubpageScreen.NOTES,
    SubpageScreen.NOTES_COURSE,
    SubpageScreen.NOTE_DETAIL,
    SubpageScreen.HOMEWORK,
    SubpageScreen.HOMEWORK_TODO,
    SubpageScreen.HOMEWORK_COURSE,
    SubpageScreen.HOMEWORK_DETAIL,
    SubpageScreen.TOOLBOX,
    SubpageScreen.TOOLBOX_CAINIAO,
    SubpageScreen.WENQU,
    SubpageScreen.WENQU_SETTINGS,
    SubpageScreen.WENQU_RECORDS,
    SubpageScreen.WENQU_GRAB,
    SubpageScreen.AI_SCHEDULE,
    SubpageScreen.AI_SETTINGS,
    SubpageScreen.AI_HISTORY,
)

/**
 * 渲染一个二级页。
 *
 * `push` / `pop` 由宿主（[IosRoot]）提供：页面自己的返回键走 `pop`，页面里的二级入口走 `push`。
 * `onHint` 用于「这一页能显示但某个动作本平台没有」的说明——比静默无反应好。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IosRouteContent(
    route: IosRoute,
    services: IosServices,
    meViewModel: MeViewModel,
    push: (IosRoute) -> Unit,
    pop: () -> Unit,
) {
    when (route.screen) {
        // ---------------------------------------------------------------- 学习
        SubpageScreen.NOTES -> NoteLibraryScreen(
            onBack = pop,
            onOpenNote = { courseName, id, startEditing ->
                push(IosRoute(SubpageScreen.NOTE_DETAIL, courseName, id, startEditing))
            },
            // null = 本平台还没有附件图解码（见 IosServices 的说明），缩略图整行不出现
            images = null,
        )

        SubpageScreen.NOTES_COURSE -> NoteCourseScreen(
            courseName = route.arg.orEmpty(),
            onBack = pop,
            onOpenNote = { id, startEditing ->
                push(IosRoute(SubpageScreen.NOTE_DETAIL, route.arg, id, startEditing))
            },
            images = null,
        )

        // 笔记详情（V4 搬进共享层）：正文渲染、附件图、编辑底栏、外链四件平台能力
        // iOS 都还没有 → 全传 null。表现是：正文按纯文本排版（图片位置给一行说明）、
        // 编辑态没有附件条与工具条、链接不可点、点图不响应。文字读写是真的能用。
        SubpageScreen.NOTE_DETAIL -> NoteDetailScreen(
            courseName = route.arg.orEmpty(),
            noteId = route.itemId,
            startEditing = route.startEditing,
            onBack = pop,
        )

        SubpageScreen.HOMEWORK -> HomeworkLibraryScreen(
            onBack = pop,
            onOpenHomework = { courseName, id, startEditing ->
                push(IosRoute(SubpageScreen.HOMEWORK_DETAIL, courseName, id, startEditing))
            },
            images = null,
            markdown = null,
            // 勾完成 / 删作业后重排截止提醒（2026-11-26 起 iOS 真有排程，见 IosHomeworkReminder）
            onHomeworkChanged = { services.homeworkReminder().reschedule() },
        )

        SubpageScreen.HOMEWORK_TODO -> HomeworkTodoScreen(
            onBack = pop,
            onOpenHomework = { courseName, id, startEditing ->
                push(IosRoute(SubpageScreen.HOMEWORK_DETAIL, courseName, id, startEditing))
            },
            onOpenLibrary = { push(IosRoute(SubpageScreen.HOMEWORK)) },
            images = null,
            markdown = null,
            onHomeworkChanged = { services.homeworkReminder().reschedule() },
        )

        SubpageScreen.HOMEWORK_COURSE -> HomeworkCourseScreen(
            courseName = route.arg.orEmpty(),
            onBack = pop,
            onOpenHomework = { id, startEditing ->
                push(IosRoute(SubpageScreen.HOMEWORK_DETAIL, route.arg, id, startEditing))
            },
            images = null,
            markdown = null,
            onHomeworkChanged = { services.homeworkReminder().reschedule() },
        )

        // 作业详情（V4 搬进共享层）：与笔记详情同一组 null——平台能力都还没有。
        // 改截止日 / 勾完成会重排截止提醒（2026-11-26 起）；正文里的图片与公式仍只有 Android 能渲染。
        SubpageScreen.HOMEWORK_DETAIL -> HomeworkDetailScreen(
            courseName = route.arg.orEmpty(),
            homeworkId = route.itemId,
            startEditing = route.startEditing,
            onBack = pop,
            onHomeworkChanged = { services.homeworkReminder().reschedule() },
        )

        SubpageScreen.THEORY_SCHEDULE -> JwScheduleListScreen(
            kind = CourseKind.Theory,
            onBack = pop,
        )

        SubpageScreen.LAB_SCHEDULE -> JwScheduleListScreen(
            kind = CourseKind.Lab,
            onBack = pop,
        )

        // 教材：抓取链由 `IosServices.textbookSync()` 给（与 Android 同一份 `TextbookSync`，
        // 会话是 `IosJwReadSession`），所以这一页在 iOS 上真的能抓到数据：进页空态自动补抓一次，
        // 顶栏「重新导入」是无闸门的强抓。上一版这里传 `syncAvailable = false`、空态说
        // 「本平台还没有教材抓取」，是 2026-11-25 审计的 P0（用户会照着反复导入课表）。
        SubpageScreen.TEXTBOOKS -> TextbookScreen(onBack = pop)

        SubpageScreen.TEACHING_CALENDAR -> TeachingCalendarScreen(onBack = pop)

        // 学业完成情况 / 培养方案（V4 搬进共享层）：顶栏「在教务网页核对」是唯一平台动作，
        // iOS 没有内置浏览窗口 → 不传（默认 null），那只图标整行不渲染。
        // 两页的「重新导入」是真的能用：抓取链路走 `JwReadSession`，iOS 是 `IosJwReadSession`。
        SubpageScreen.SCHOLAR -> ScholarScreen(onBack = pop)

        SubpageScreen.CURRICULUM -> CurriculumScreen(onBack = pop)

        // ---------------------------------------------------------------- 教务
        SubpageScreen.SCORES -> ScoreScreen(
            onBack = pop,
            onOpenSettings = { push(IosRoute(SubpageScreen.SCORE_SETTINGS)) },
            // 盖章成绩单（2026-11-26 搬进共享层）：页面与 PDF 抓取都在共享层，走 Ktor，
            // 不依赖 WebView 下载，所以 iOS 上**真能导出**（落到 appDataDir()/transcripts/）。
            // 打开 / 分享 / 另存三个出口等 iOS 的「文件分享」缝补完再挂（§12.14），
            // 现在页面能进、能导出、能在「最近导出」里看到，只是三颗按钮不渲染。
            onOpenTranscript = { push(IosRoute(SubpageScreen.TRANSCRIPTS)) },
        )

        SubpageScreen.SCORE_SETTINGS -> ScoreSettingsScreen(onBack = pop)

        SubpageScreen.EXAMS -> ExamScreen(
            onBack = pop,
            onOpenSettings = { push(IosRoute(SubpageScreen.EXAM_SETTINGS)) },
        )

        SubpageScreen.EXAM_SETTINGS -> ExamSettingsScreen(onBack = pop)

        SubpageScreen.SELECTIONS -> SelectionScreen(
            onBack = pop,
            onOpenCenter = { roundId ->
                // 选课中心带轮次参数：iOS 侧路由只带字符串参数，轮次 id 落在 arg 里
                push(IosRoute(SubpageScreen.SELECTION_CENTER, roundId))
            },
            onOpenRecords = { push(IosRoute(SubpageScreen.SELECTION_RECORDS)) },
            onOpenWishes = { push(IosRoute(SubpageScreen.SELECTION_WISHES)) },
        )

        SubpageScreen.SELECTION_CENTER -> SelectionCenterScreen(
            onBack = pop,
            focusRoundId = route.arg,
            // null = 本平台还没有选课接口客户端（iOS 的教务直取链只做了只读同步）
            client = null,
        )

        SubpageScreen.SELECTION_RECORDS -> SelectionRecordsScreen(onBack = pop)

        SubpageScreen.SELECTION_WISHES -> SelectionWishesScreen(
            onBack = pop,
            onOpenCenter = { push(IosRoute(SubpageScreen.SELECTION_CENTER)) },
            client = null,
        )

        SubpageScreen.STUDENT_INFO -> StudentInfoScreen(onBack = pop)

        // ---------------------------------------------------------------- 盖章成绩单
        //
        // 2026-11-26 搬进 `commonMain`（`ui/score/TranscriptScreen` + 历史导出页 + `TranscriptStore`）。
        // 关键点：**PDF 不是从 WebView 下载的**，是 `TranscriptClient`（Ktor）带着网页会话的
        // `sid` 直取，落盘走 `TranscriptStore`（okio，`appDataDir()/transcripts/`）。所以 iOS 上
        // 「导出」这条链是完整可用的，不依赖 `onDownload` 事件。
        //
        // 三个出口暂为 null（§12.14 记着升级条件）：
        // - `onOpenPdf`：要交给系统 PDF 阅读器（`QLPreviewController` / `UIDocumentInteractionController`）
        // - `onSharePdf`：要文件分享（`UIActivityViewController` + `NSURL`）
        // - `onSavePdf`：要 `UIDocumentPickerViewController` 或落到「文件」App 可见的目录
        // 三者都补完之后把这里换成真回调即可，页面本身不用改。
        //
        // 「最近导出」不占二级页枚举：它在 `:app` 那边也只是同一个窗口里的列表，所以这里
        // 用本地状态顶掉表单（与 `LEAVE_HUB` 的请假表单同一做法）。
        SubpageScreen.TRANSCRIPTS -> {
            var showHistory by remember { mutableStateOf(false) }
            if (showHistory) {
                TranscriptHistoryContent(
                    onBack = { showHistory = false },
                    // 历史里每一份也能直接分享（与列表页同一份 `iosShareTranscriptPdf`）；
                    // 「打开」与「在网页里核对」仍旧留空，理由见下面 TranscriptScreen 那段。
                    onSharePdf = ::iosShareTranscriptPdf,
                )
            } else {
                TranscriptScreen(
                    onBack = pop,
                    onOpenHistory = { showHistory = true },
                    // 分享：落到 `Documents/exports/` 再交给系统面板（`ShareIntents` 的 iOS actual，
                    // 需要根组合里调过 `IosSharePresenter()`——`MainViewController` 已经调了）。
                    onSharePdf = ::iosShareTranscriptPdf,
                    // 另存：拷一份到 `Documents/exports/`，用户在「文件 App → JUWPHub」里能看到。
                    // iOS 没有「下载目录」这个概念，所以「存到下载」在这里的语义就是这一份。
                    onSavePdf = ::iosSaveTranscriptPdf,
                    // 打开：沙箱里的 PDF 没有公有「打开」入口（`UIApplication.openURL(file://)`
                    // 不生效），要 `QLPreviewController`（得往 project.yml 加 QuickLook.framework）
                    // 或自建 WKWebView 预览页。两件都超出「一个回调」的体量，所以保持 null：
                    // 页面上那颗「打开」按钮不渲染（§11.2 ④）。
                    onOpenPdf = null,
                    // 自动填表脚本：iOS 的教务登录页没有注入脚本的通道（登录由 WKWebView 里
                    // 用户自己完成），传 null 即不注入。
                    casAutoFillScript = null,
                )
            }
        }

        // ---------------------------------------------------------------- 钱包

        SubpageScreen.POWER_BILL -> {
            val vm = remember {
                PowerBillViewModel(
                    repo = services.powerRepository(),
                    credentialStore = services.yktCredentialStore(),
                    readingStore = services.powerReadingStore(),
                )
            }
            PowerBillScreen(viewModel = vm, onBack = pop)
        }

        SubpageScreen.CAMPUS_STATEMENT -> {
            val vm = remember {
                StatementViewModel(
                    repo = services.yktRepository(),
                    credentialStore = services.yktCredentialStore(),
                    db = services.database(),
                )
            }
            StatementScreen(viewModel = vm, onBack = pop)
        }

        // 校园卡设置（iOS 移植 Y1，2026-11-25）：**这是 iOS 上第一处能真录入学号密码的地方**。
        // 凭证进 Keychain（`IosYktCredentialStore`，service `secure_ykt`），
        // 存成功之后「消费流水」「电费」两页的「凭证未配置」才会消失、真去拉数据。
        //
        // 两个平台槽位传 null（§11.2 ④，都是「本平台确实没有」而非待补）：
        // - `onOpenPaymentUrl = null`：这一版不做充值。拉起微信/收银台要
        //   `LSApplicationQueriesSchemes` + 外部应用跳转，`Info.plist` 还没有
        //   （`design/ios-port.md` §12.3「支付调起」），所以「充值」按钮整块不渲染，
        //   余额卡上只留「消费流水」；
        // - `notificationPermission = null`：iOS 还没有 `UNUserNotificationCenter` 与后台
        //   周期任务（§12.1），没有提醒可排，「余额提醒」整节不渲染。
        //
        // `powerRoomPicker` 给的是真实现：电费仓库与凭证存储两端都有，所以 iOS 用户
        // **能选/改绑寝室房间**——电费查询与充值都以缴费平台确认的房间为准（DESIGN §4.24）。
        SubpageScreen.CAMPUS_CARD_SETTINGS -> {
            val credentialStore = services.yktCredentialStore()
            if (credentialStore == null) {
                // 没有加密凭证存储时这一页是个空壳：不给录入界面，而不是让人填完存不住
                IosNotPortedScreen(screen = route.screen, onBack = pop)
            } else {
                val vm = remember {
                    CampusCardViewModel(
                        repo = services.yktRepository(),
                        credentialStore = credentialStore,
                        prefs = services.displayPrefs(),
                        db = services.database(),
                    )
                }
                val roomPicker = remember {
                    PowerRoomPickerViewModel(services.powerRepository(), credentialStore)
                }
                CampusCardSettingsScreen(
                    viewModel = vm,
                    onBack = pop,
                    onOpenStatement = { push(IosRoute(SubpageScreen.CAMPUS_STATEMENT)) },
                    onOpenPaymentUrl = null,
                    notificationPermission = null,
                    powerRoomPicker = roomPicker,
                )
            }
        }

        // 付款码（Y1 搬进共享层）：码图由共享层的 `BarcodeMatrixImage` 用 Compose 画布绘制，
        // 位阵来自 `YktPayCode.matrices`（iOS 侧走 CoreImage），所以**码本身能出**。
        // `windowGuard = null`：防截屏（`FLAG_SECURE`）与亮度拉满都没有 iOS 实现（§12.8），
        // 这一版只是不加这两道保护，页面不空。
        // 扫码扣款命中的 `vibrate` 也不传：iOS 侧没有 UIKit 触感反馈实现。
        SubpageScreen.PAY_CODE -> {
            val credentialStore = services.yktCredentialStore()
            if (credentialStore == null) {
                IosNotPortedScreen(screen = route.screen, onBack = pop)
            } else {
                val vm = remember {
                    PayCodeViewModel(
                        repo = services.yktRepository(),
                        credentialStore = credentialStore,
                        prefs = services.displayPrefs(),
                        db = services.database(),
                    )
                }
                // 付款码页把屏幕提到最亮（扫码枪对亮度敏感），离开时写回进页前的值。
                // iOS 没有窗口级亮度，只能改系统亮度，所以**必须**自己还原，见 IosScreenBrightness.kt。
                // 这里用全限定名，省掉两条 import：本文件已经很长，导入块按包名分组排得很紧。
                DisposableEffect(Unit) {
                    val previous = edu.jxslu.schedule.ui.campus.raiseScreenBrightnessForPayCode()
                    onDispose { edu.jxslu.schedule.ui.campus.restoreScreenBrightness(previous) }
                }
                PayCodeScreen(
                    viewModel = vm,
                    onBack = pop,
                    onOpenStatement = { push(IosRoute(SubpageScreen.CAMPUS_STATEMENT)) },
                    windowGuard = null,
                )
            }
        }

        // ---------------------------------------------------------------- 生活
        SubpageScreen.INBOX -> InboxScreen(onBack = pop)

        SubpageScreen.WEATHER -> {
            val vm = remember { WeatherViewModel(services.weatherRepository()) }
            WeatherScreen(viewModel = vm, onBack = pop)
        }

        // 天气设置（V4 搬进共享层）：ViewModel 复用根上那一份 `MeViewModel`
        // （开关写的是同一份 DataStore `weather_enabled`）。
        SubpageScreen.WEATHER_SETTINGS -> WeatherSettingsScreen(
            viewModel = meViewModel,
            onBack = pop,
            onOpenWeather = { push(IosRoute(SubpageScreen.WEATHER)) },
        )

        // ---------------------------------------------------------------- 设置
        SubpageScreen.DATA_SETTINGS -> DataSettingsScreen(onBack = pop, viewModel = meViewModel)

        // ---------------------------------------------------------------- 宿舍报修
        //
        // 页面读本机快照（`dormRepairStore()`，两端同一份 JSON）；两个平台动作传 null：
        // 「去学工报修页核对」要打开学工 WebView（iOS 还没搬），「下拉刷新」要
        // `DormRepairSync`（带 WebView 登录态，还在 `:app`）。对应入口整行不渲染，
        // 本机缓存照常展示。
        SubpageScreen.DORM_REPAIR -> DormRepairScreen(
            onBack = pop,
            focusItemId = route.arg,
        )

        // ---------------------------------------------------------------- 账号中心
        //
        // 十个入口里两个在精简版下线（问渠 / AI 设置）→ 传 null，那两行不渲染。
        // 账号中心自己会按 `accountCredentials()` / 各仓库是否为 null 决定显示什么，
        // 所以 iOS 上它表现为「全部未配置」——那是真实结论（凭证存储还没接全）。
        SubpageScreen.ACCOUNTS -> AccountsHubScreen(
            onBack = pop,
            onOpenJw = { push(IosRoute(SubpageScreen.JW_ACCOUNT)) },
            onOpenYkt = { push(IosRoute(SubpageScreen.CAMPUS_CARD_SETTINGS)) },
            onOpenQiekj = { push(IosRoute(SubpageScreen.WATER)) },
            onOpenQzxy = { push(IosRoute(SubpageScreen.QZXY)) },
            onOpenUjing = { push(IosRoute(SubpageScreen.UJING)) },
            onOpenKvcx = { push(IosRoute(SubpageScreen.KVCX)) },
            onOpenRide = { push(IosRoute(SubpageScreen.RIDE)) },
        )

        // ---------------------------------------------------------------- 快趣 · 账号页与订单页
        //
        // 2026-11-26 搬进共享层（K2）。两页在 iOS 上**完整可用**：会话本体是
        // `IosServices.kqcxSession()`（Keychain 凭证 + 进程内 token），订单缓存与本机骑行记录
        // 都落在同一个 Room 库，与 Android 同一份代码。
        //
        // 两处平台能力缺口，按 §11.2 ④ 传 null（不是「以后补」的占位）：
        // - `onReconcileAutoReturn = null`：Android 用 `AlarmManager.setAlarmClock` + WorkManager
        //   加急任务做自动还车的精确排程，iOS 的 `BGTaskScheduler` 不保证触发时刻，
        //   排了是假承诺。传 null 后「进页 / 登录时那一轮兜底对账」不跑，页面其余照常。
        // - `brandIcon = null`：`R.drawable.ic_brand_kqcx` 只有 Android 有，头像位整块不占位。
        //
        // 订单页的入口这次给**真回调**（iOS 有二级页栈），所以账号页的「骑行记录」整块会渲染。
        SubpageScreen.KVCX -> {
            val database = services.database()
            val viewModel = remember {
                KvcxViewModel(
                    session = services.kqcxSession(),
                    records = RideRecordStore(database.rideRecordDao()),
                    prefs = services.displayPrefs(),
                    orderCache = KqcxOrderStore(database.kqcxOrderDao()),
                    onReconcileAutoReturn = null,
                )
            }
            KvcxScreen(
                viewModel = viewModel,
                onBack = pop,
                onOpenOrders = { push(IosRoute(SubpageScreen.KVCX_ORDERS)) },
                brandIcon = null,
            )
        }

        SubpageScreen.KVCX_ORDERS -> {
            val database = services.database()
            val viewModel = remember {
                KvcxOrdersViewModel(
                    session = services.kqcxSession(),
                    orderCache = KqcxOrderStore(database.kqcxOrderDao()),
                )
            }
            KvcxOrdersScreen(viewModel = viewModel, onBack = pop)
        }

        // ---------------------------------------------------------------- 快趣 · 出行页
        //
        // 2026-11-26 搬进共享层（P4）。页面本体、各面板、出码页、设置弹层全在 `commonMain`，
        // 这一层只做**装配**——Android 的对应物是 `:app/ui/ebike/AppRideScreen.kt`。
        //
        // ## 地图：画布 + 高德栅格底图（2026-11-26 晚）
        //
        // MapKit 在这个 Kotlin 版本（2.1.21）的绑定里放不了标注：`MKAnnotationProtocol.coordinate`
        // 是 final、`MKPointAnnotation.coordinate` 是 `val`、`addOverlay(s)` 全 Unresolved。
        // 所以底图与车标都走共享层的画布：`iosCanvasBikeMapSlot()` 把 `BikeMapCanvas` 交出去，
        // 瓦片由 `IosBikeMapTiles` 取（高德栅格，与 Android 回退底图同一接口）。
        // **不要再改回 `bikeMap = null`**——那会让地图区退回「本平台暂不显示地图」的空态。
        //
        // ## 其余平台动作：iOS 没接的按 §11.2 ④ 传 null（不是「以后补」）
        //
        // | 动作 | 为什么给不出 |
        // |---|---|
        // | `onRequestLocationPermission` / 三个「去设置」 | 定位授权由 `IosBikeLocation` 自己在开流时请求；没有对应的系统设置深链 |
        // | `mapCache` / `showMapSourceChoice` | 瓦片只在内存里（不落盘，没有「地图缓存」可清）；底图只有一种，没有「来源」可选 |
        //
        // 扫车身码 **已接**（2026-11-26 晚）：`IosBikeScanner` 是 AVFoundation 取景页，
        // `onScanBodyCode` 非空后「车号 / 出码」面板里的扫码行才会渲染。
        // 拉起微信（`onOpenWechatScan` / `onOpenWechatForSettle`）也已经接了：`Info.plist` 有
        // `LSApplicationQueriesSchemes`，`iosOpenWechat()` 负责 `UIApplication.openURL`。
        // `EbikeViewModel` 的 `gallery` / `reminders` 也是真实现（存相册、免费时长提醒）；
        // 「自动还车」与「扫完即焚」在 iOS 关闭，对应设置整块不渲染。
        SubpageScreen.RIDE -> {
            val mapViewModel = remember {
                BikeMapViewModel(
                    client = KqcxBikeClient.create(),
                    prefs = services.displayPrefs(),
                    // 会话本体同时是「本机用车编排」与「还车点 / 禁停区图层」两条缝的实现
                    kqcx = services.kqcxSession(),
                    zoneSource = services.kqcxSession(),
                    // 控制器用**进程级单例**（2026-11-26 晚）：今日页的快趣卡片读的是同一份状态，
                    // 各建一份就等于「今日页说骑行中、出行页说没单」。
                    rideController = services.kvcxRideController(),
                    // zoneCache 留默认 null：图层缓存只是「省一个往返」。
                )
            }
            val ebikeViewModel = remember {
                // 相册与提醒两个缝在 iOS 上是真实现（2026-11-26，T1 轨道）：
                // 存相册走 `UIImageWriteToSavedPhotosAlbum`，免费时长提醒走本机通知。
                // 「自动还车」与「扫完即焚」两项能力在 iOS 上关着（前者要 BGTaskScheduler，
                // 后者要能删相册里的图），对应设置整块不渲染——见两个实现类的 KDoc。
                EbikeViewModel(
                    prefs = services.displayPrefs(),
                    gallery = IosEbikeQrGallery(),
                    reminders = IosEbikeReminder(services.displayPrefs()),
                )
            }
            val bikeMapSlot = remember { iosCanvasBikeMapSlot() }
            // 扫车身码（2026-11-26 晚）：AVFoundation 取景页，识别到第一枚码就关页；
            // 取消 / 权限被拒回 null，`RideScreen` 那边按「取消」处理（不弹错误）。
            val bikeScanner = rememberIosBikeScanner()
            RideScreen(
                viewModel = mapViewModel,
                ebikeViewModel = ebikeViewModel,
                onBack = pop,
                initialFocusCarNum = route.arg,
                // 车标：纯 Compose 画布自绘（Web 墨卡托投影 + 手势），绕开 MapKit 的标注绑定
                // ——那套在本仓 Kotlin 2.1.21 的绑定里用不了（见 design/ios-port.md §12.13）。
                bikeMap = bikeMapSlot,
                // 扫车身码：填进「车号 / 出码」面板的那一行；非空才会渲染
                onScanBodyCode = bikeScanner,
                onOpenKvcxAccount = { push(IosRoute(SubpageScreen.KVCX)) },
                // 拉起微信：`Info.plist` 已补 `LSApplicationQueriesSchemes`（weixin / weixinULAPI）
                onOpenWechatScan = { iosOpenWechat() },
                onOpenWechatForSettle = { iosOpenWechat() },
            )
        }

        // ---------------------------------------------------------------- AI 调课
        //
        // 三页在 iOS 上都能跑：客户端与调课历史仓都是 `appDataDir()` 落盘（两端同一份），
        // API Key 走 Keychain。图片那套（截图 / 相册）iOS 没有，`imageUi = null`
        // 让输入区的图片入口整行不渲染。
        SubpageScreen.AI_SCHEDULE -> {
            val vm = remember {
                AiChatViewModel(
                    repo = services.scheduleRepository(),
                    client = services.aiScheduleClient(),
                    historyStore = services.aiApplyHistoryStore(),
                    imageStore = null,
                    apiKeys = services.aiKeys,
                )
            }
            AiChatScreen(
                viewModel = vm,
                onBack = pop,
                onOpenSettings = { push(IosRoute(SubpageScreen.AI_SETTINGS)) },
                onOpenHistory = { push(IosRoute(SubpageScreen.AI_HISTORY)) },
                imageUi = null,
            )
        }

        SubpageScreen.AI_SETTINGS -> {
            val vm = remember {
                AiSettingsViewModel(
                    repo = services.scheduleRepository(),
                    client = services.aiScheduleClient(),
                    apiKeys = services.aiKeys,
                )
            }
            AiSettingsScreen(viewModel = vm, onBack = pop)
        }

        SubpageScreen.AI_HISTORY -> {
            val vm = remember {
                AiHistoryViewModel(
                    repo = services.scheduleRepository(),
                    historyStore = services.aiApplyHistoryStore(),
                )
            }
            AiHistoryScreen(viewModel = vm, onBack = pop)
        }

        // ---------------------------------------------------------------- 百宝箱
        //
        // 列表页与菜鸟批量查在 iOS 上可用；水印与拼接是纯位图管线（`android.graphics.Bitmap`），
        // 留在 `:app`，两个入口传 null 后那两张工具卡不渲染。
        SubpageScreen.TOOLBOX -> ToolboxScreen(onBack = pop)

        SubpageScreen.TOOLBOX_CAINIAO -> CainiaoQueryScreen(onBack = pop)

        // ---------------------------------------------------------------- 课表管理 / 调课 / 变动检测
        //
        // 三页（iOS 移植 X1，2026-11-25 搬进共享层）在 iOS 上都是**完整可用**的：
        // 仓库是同一个 Room 库，教务直取走 `IosJwReadSession`（Cookie 来自 WKWebView），
        // 归档链 `scheduleTermImport()` 也非空，所以「从教务导入全部学期」在 iOS 上真能跑。
        // 唯一没有的是提醒调度（WorkManager + 通知渠道）——两个回调不传，开关照常落库、
        // 这一侧不排任务（见 `ScheduleChangesScreen` 的 KDoc）。
        SubpageScreen.TIMETABLE_MANAGE -> {
            val vm = remember { TimetableViewModel(services.scheduleRepository()) }
            TimetableManageScreen(onBack = pop, viewModel = vm)
        }

        SubpageScreen.COURSE_TWEAK -> {
            val vm = remember { CourseTweakViewModel(services.scheduleRepository()) }
            CourseTweakScreen(onBack = pop, viewModel = vm)
        }

        // 顶栏「调课记录」不传：那页属于 AI 调课，精简版已下线（IOS_REMOVED_SCREENS）。
        SubpageScreen.SCHEDULE_CHANGES -> ScheduleChangesScreen(
            onBack = pop,
        )

        // ---------------------------------------------------------------- 我的 · 设置子页
        //
        // 六个子页在 iOS 上都能进。平台动作一律传 null（日历同步要系统 Calendar、
        // 提醒要通知渠道、快捷方式要 PackageManager 查已安装应用、赞赏码要位图），
        // 对应入口整行不渲染；页面本身（列表 / 开关 / 文案）照常可用。
        // 三个设置子页（2026-11-25 晚补）：它们早就在 commonMain 里、签名只要 onBack + MeViewModel，
        // 只是这一层没写分支，用户点进去得到的是「这一页还在 Android 侧」——一句假话。
        SubpageScreen.APPEARANCE_SETTINGS -> AppearanceSettingsScreen(
            onBack = pop,
            viewModel = meViewModel,
        )

        SubpageScreen.TIMETABLE_HUB -> TimetableHubScreen(
            onBack = pop,
            viewModel = meViewModel,
        )

        SubpageScreen.QUIET_HOURS -> QuietHoursScreen(onBack = pop)

        SubpageScreen.CALENDAR_SETTINGS -> {
            val vm = remember {
                CalendarSettingsViewModel(
                    repo = services.scheduleRepository(),
                    // null = 本平台没有系统日历写入（Android 走 Calendar Provider）
                    actions = null,
                )
            }
            CalendarSettingsScreen(onBack = pop, viewModel = vm)
        }

        SubpageScreen.JW_ACCOUNT -> JwAccountScreen(onBack = pop, viewModel = meViewModel)

        SubpageScreen.REMINDER_SETTINGS -> {
            val vm = remember { ReminderSettingsViewModel(services.scheduleRepository()) }
            ReminderSettingsScreen(
                onBack = pop,
                viewModel = vm,
                // 上课提醒（2026-11-26 起 iOS 真有排程，见 `IosClassReminder`）：
                // `class.` 前缀、未来 4 天、上限 40 条；改开关就按前缀清空重排一次。
                // 授权在这一步顺带请求（三个提醒族共用一次系统弹框），被拒则静默不排。
                onReschedule = { services.classReminder().reschedule() },
                // 作业截止提醒：进本页与改开关都会重排一次。
                onRecheckHomework = { services.homeworkReminder().reschedule() },
            )
        }

        SubpageScreen.SHORTCUTS -> {
            val vm = remember { ShortcutSettingsViewModel(services.scheduleRepository()) }
            ShortcutSettingsScreen(
                onBack = pop,
                viewModel = vm,
                // iOS 没有 Android 那套品牌 webp，全部退回矢量图标
                brandIcon = { null },
                focusItemId = route.arg,
            )
        }

        // 权限与存储（iOS 版）：权限块传 null（电池优化白名单 / 自启动在 iOS 上不存在），
        // 存储诊断三行 + 清缓存 + 导出诊断日志是真实现。见 IosPermissionsStorageScreen。
        SubpageScreen.PERMISSIONS_STORAGE -> IosPermissionsStorageScreen(onBack = pop)

        SubpageScreen.ABOUT_UPDATE -> AboutUpdateScreen(
            onBack = pop,
            onOpenUrl = ::iosOpenUrl,
            // 开源许可全文在 :app 的 assets 里，iOS 还没有；该行不渲染
            licensesDialog = null,
        )

        SubpageScreen.TERMS_DECLARATIONS -> TermsDeclarationsScreen(
            onBack = pop,
            noticeDialog = { title, intro, items, dismiss ->
                IosNoticeDialog(title = title, intro = intro, items = items, onDismiss = dismiss)
            },
        )

        SubpageScreen.SUPPORT_DEV -> SupportDevScreen(onBack = pop)

        // ---------------------------------------------------------------- 学工 / 校历 / 实习
        //
        // 三页的数据层今晚搬进共享层了（学工 URL 规则、报修解析、校历存储、实习基地抓取）。
        // 校历与实习基地的平台动作仍传 null（前者要位图解码、后者要内置浏览器），对应入口整行不渲染。
        //
        // **请假三张表单 2026-11-26 起能开**：`XgFormScreen` 搬进共享层，走 `WKWebView` 那条缝
        // （`ui/web/WebViewController`），所以 `onOpenForm` 给真回调后三行入口会渲染。
        // 表单窗口不是独立二级页：它在 hub 这一层用本地状态顶掉列表，返回即回列表——
        // 这样不用给 `SubpageScreen` 加一个带参数的枚举（那要 `:app` 同步改）。
        // iOS 上仍缺**一件**：附件上传（`onPickFiles` 在 K/N 绑定里没有 `WKOpenPanelParameters`，
        // 且 JS 侧改不了 `input.files`，没有可用替代）。销假的定位签到 2026-11-26 已接
        //（`WebViewController.ios.kt` 的 `WKUIDelegate`）。不带附件的单子能填能提交。
        SubpageScreen.LEAVE_HUB -> {
            val openForm = remember { mutableStateOf<XgForm?>(null) }
            val form = openForm.value
            if (form == null) {
                LeaveHubScreen(onBack = pop, onOpenForm = { openForm.value = it })
            } else {
                XgFormScreen(form = form, onBack = { openForm.value = null })
            }
        }

        SubpageScreen.CAMPUS_CALENDAR -> CampusCalendarScreen(onBack = pop)

        SubpageScreen.INTERNSHIP_BASES -> InternshipBasesScreen(onBack = pop)

        // ---------------------------------------------------------------- 江水生活 · 开水房
        //
        // **iOS 上唯一能真开到水的一条链**：胖乖走 HTTP（不是蓝牙），三份平台存储
        // 2026-11-25 全部到位（Keychain 凭证 + 订单快照 JSON + 过期留痕），
        // 所以 `qiekjRepository()` 不再是 null。
        //
        // 两个平台动作传 null：品牌位图（Android 的 `ic_brand_pangguai`）与「充值」按钮
        // （要拉起支付宝深链，`Info.plist` 还没声明 `LSApplicationQueriesSchemes`）。
        SubpageScreen.WATER -> {
            val repo = services.qiekjRepository()
            if (repo == null) {
                IosNotPortedScreen(screen = route.screen, onBack = pop)
            } else {
                val vm = remember {
                    WaterViewModel(
                        repo = repo,
                        wateringStore = services.qiekjWateringSessions(),
                    )
                }
                WaterScreen(
                    viewModel = vm,
                    prefsViewModel = meViewModel,
                    onBack = pop,
                )
            }
        }

        // ---------------------------------------------------------------- 江水生活 · 问渠
        //
        // 问渠全链路是 HTTP，iOS 上能完整用：登录、活动列表、报名 / 取消、工时记录。
        // 凭证走 Keychain，过期留痕与账号中心共用一张表。
        //
        // 通知类动作（蹲点提醒、报名提醒、微信拉起、证书下载）全部传 null：
        // iOS 还没有 `UNUserNotificationCenter` 与 `LSApplicationQueriesSchemes`，
        // 对应入口整行不渲染。
        SubpageScreen.WENQU -> {
            val repo = services.wenquRepository()
            if (repo == null) {
                IosNotPortedScreen(screen = route.screen, onBack = pop)
            } else {
                val vm = remember { WenquViewModel(repo, services.displayPrefs()) }
                WenquScreen(
                    onBack = pop,
                    viewModel = vm,
                    onOpenSettings = { push(IosRoute(SubpageScreen.WENQU_SETTINGS)) },
                    onOpenRecords = { push(IosRoute(SubpageScreen.WENQU_RECORDS)) },
                    onOpenGrab = { push(IosRoute(SubpageScreen.WENQU_GRAB)) },
                )
            }
        }

        SubpageScreen.WENQU_RECORDS -> {
            val repo = services.wenquRepository()
            if (repo == null) {
                IosNotPortedScreen(screen = route.screen, onBack = pop)
            } else {
                val vm = remember { WenquRecordsViewModel(repo) }
                WenquRecordsScreen(onBack = pop, viewModel = vm)
            }
        }

        SubpageScreen.WENQU_SETTINGS -> {
            val repo = services.wenquRepository()
            if (repo == null) {
                IosNotPortedScreen(screen = route.screen, onBack = pop)
            } else {
                WenquSettingsScreen(
                    onBack = pop,
                    onOpenRecords = { push(IosRoute(SubpageScreen.WENQU_RECORDS)) },
                    repository = repo,
                )
            }
        }

        SubpageScreen.WENQU_GRAB -> {
            val repo = services.wenquRepository()
            if (repo == null) {
                IosNotPortedScreen(screen = route.screen, onBack = pop)
            } else {
                val vm = remember { WenquGrabViewModel(repo, services.displayPrefs()) }
                WenquGrabScreen(onBack = pop, viewModel = vm)
            }
        }

        // ---------------------------------------------------------------- 江水生活 · U净
        //
        // 看板（附近洗衣机 + 在用订单状态）与历史账单在 iOS 上都能看：三份本地存储
        // 2026-11-25 全部到位（Keychain 凭证 + 三个 NSUserDefaults 前缀）。
        //
        // **开洗这一条走不通**：扫码要相机（`onScan` 传 null → 扫码卡整块不渲染），
        // 支付要支付宝深链（`pay` 不传 → 「支付」入口不渲染）。所以 iOS 上 U净 是
        // 「能看不能下单」，不是「点了没反应」。
        SubpageScreen.UJING -> {
            val repo = services.ujingRepository()
            if (repo == null) {
                IosNotPortedScreen(screen = route.screen, onBack = pop)
            } else {
                val vm = remember {
                    UjingViewModel(
                        repo = repo,
                        houseStore = services.ujingHouseStore(),
                        historyStore = services.ujingHistoryStore(),
                        draftStore = services.ujingDraftStore(),
                        prefs = services.displayPrefs(),
                        // 洗衣完成提醒（N1，2026-11-25 晚）：`UNUserNotificationCenter` 实现已接上，
                        // ViewModel 里排 / 撤提醒的那三处（`refreshOrder` / `dismissOrder` / 登出）
                        // 因此自然生效。支付缝仍不传——iOS 没有支付宝 SDK，下单链在创建订单前就被拦，
                        // 所以这一端「有在案订单」只能来自别的路径，见 `IosUjingReminder` 的 KDoc。
                        reminder = services.ujingReminder(),
                    )
                }
                UjingScreen(
                    viewModel = vm,
                    onBack = pop,
                    onOpenOrder = { push(IosRoute(SubpageScreen.UJING_ORDER)) },
                    onOpenHistory = { push(IosRoute(SubpageScreen.UJING_HISTORY)) },
                )
            }
        }

        SubpageScreen.UJING_ORDER -> UjingOrderScreen(
            draftStore = services.ujingDraftStore(),
            onBack = pop,
        )

        SubpageScreen.UJING_HISTORY -> UjingHistoryScreen(
            store = services.ujingHistoryStore(),
            onBack = pop,
        )

        else -> IosNotPortedScreen(screen = route.screen, onBack = pop)
    }
}

/**
 * 「这一页还在 Android 侧」的兜底页。
 *
 * 有意做成**能看见的页面**而不是空实现：抽屉里不会列出它，但 `SettingsScreen` /
 * `JwHubScreen` 的回调参数是 `SubpageScreen`，编译器不认白名单，所以必须有一个兜底。
 * 落在这一页时用户看到的是确定的一句话，不是「点了没反应」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosNotPortedScreen(screen: SubpageScreen, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("iOS 移植中", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "这一页还在 Android 侧",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = "「${screen.name}」还没有搬进共享层。\n" +
                    "完整清单见 design/ios-port.md §12。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}


/**
 * iOS 侧打开外部链接（开源仓库 / 用户协议那几行用）。
 *
 * `UIApplication.openURL` 在新系统上被标记 deprecated，但仍然可用，且签名最简单
 * （`openURL:options:completionHandler:` 要多传两个空参，收益为零）。
 * 返回 null = 已交给系统；非 null = 失败文案（页面直接显示）。
 *
 * **未验**：只在编译期验证过；`canOpenURL` 对未声明 `LSApplicationQueriesSchemes` 的
 * 自定义 scheme 会返回 false，所以这里**不**做预检，直接 open——http/https 不需要声明。
 */
internal fun iosOpenUrl(url: String): String? {
    val nsUrl = NSURL.URLWithString(url) ?: return "链接无效"
    return if (UIApplication.sharedApplication.openURL(nsUrl)) null else "打不开这个链接"
}

/**
 * iOS 侧的「条款 / 声明」弹窗（`TermsDeclarationsScreen` 的槽位）。
 *
 * 与 Android 那份 `LegalDialogs` 是同一份内容的两种壳：标题 + 导语 + 条目 + 一个关闭键。
 * Android 那份还带「同意 / 拒绝」的勾选与一次性记录，iOS 这一版只做阅读。
 */
@Composable
internal fun IosNoticeDialog(
    title: String,
    intro: String,
    items: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            LazyColumn {
                item {
                    Text(
                        intro,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(items) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
