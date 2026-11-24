package edu.jxslu.schedule

import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.ExamSnapshotStore
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.InboxRepository
import edu.jxslu.schedule.data.repo.ScheduleBaselineStore
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.data.repo.ScoreRepository
import edu.jxslu.schedule.data.repo.ScoreSync
import edu.jxslu.schedule.data.session.IosJwReadSession
import edu.jxslu.schedule.data.session.IosWebCookieSource
import edu.jxslu.schedule.data.session.JwReadSession
import edu.jxslu.schedule.data.session.WebCookies
import edu.jxslu.schedule.platform.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * iOS 侧的共享服务装配（iOS 移植 M1 起，M2 补齐教务三件套）。
 *
 * 与 `:app` 的 `GraphServices` 一一对应，只是这边没有 `Graph`——直接自己造：
 *
 * | 服务 | iOS 实现 |
 * |---|---|
 * | 偏好 | `DisplayPrefsStore()`，DataStore 落在 `appDataDir()`（沙箱私有目录） |
 * | 课表仓库 | `ScheduleRepository` + `BundledSQLiteDriver` 的 Room（v21，无迁移链）+ 基线快照 |
 * | 收件箱 | `InboxRepository(juwDatabase().inboxDao())` |
 * | 教务会话 | [IosJwReadSession]：Cookie 来自 `WebCookies`（WKWebView 登录后写入） |
 * | 教务三件套 | `ScheduleSync` / `ExamSync` / `ScheduleTermImport`，全部走上面那条会话 |
 *
 * ## M2 的关键点：会话来源换了，调用方一行没改
 *
 * Android 的会话是 `CasSession`（OkHttp + 凭证保险箱 + 自动填表）；iOS 没有保险箱，
 * 也不该拿明文密码去填表——登录交给 `WKWebView` 里人自己完成，App 只从
 * `WKWebsiteDataStore` 读 Cookie（见 `data/session/WebCookieSource.kt`）。
 * 两条实现都实现 [JwReadSession]，所以 `ScheduleSync` / `ExamSync` / `ScheduleTermImport`
 * 这三个已经在共享层的类**一个字都不用改**，`WeekScreen` 的「检测」图标也会自动出现
 * （它读的是 `WeekViewModel.canQuickCheck`，而那读的是这三个服务是否非空）。
 *
 * ## 作用域为什么是 `Dispatchers.Default`
 *
 * `ScheduleRepository` 第三条参数是「共享热流用的作用域」。Android 侧注入的是
 * `Graph.appScope`（挂着崩溃上报的 handler）；iOS 侧还没有那套上报，所以这里自建一个
 * `SupervisorJob` 作用域——一个子协程失败不影响其它订阅。**别改回 `Dispatchers.Main`**：
 * 仓库里的热流有文件/DB 读取，挂在主线程上会卡 UI。
 */
class IosServices : AppServices {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val prefs: DisplayPrefsStore by lazy { DisplayPrefsStore() }

    private val inbox: InboxRepository by lazy { InboxRepository(juwDatabase().inboxDao()) }

    private val repository: ScheduleRepository by lazy {
        // 调课检测基线（DESIGN §4.17）：落盘走 `appDataDir()`，iOS 侧直接可用。
        ScheduleRepository(
            juwDatabase(),
            prefs,
            scope,
            baselineStore = ScheduleBaselineStore(),
        )
    }

    /** 教务读取会话。Cookie 在请求时才从 `WebCookies` 取，所以登录前后都能立刻生效。 */
    private val session: JwReadSession by lazy { IosJwReadSession() }

    private val sync: ScheduleSync by lazy {
        ScheduleSync(cas = session, repo = repository, prefs = prefs, inbox = inbox)
    }

    /**
     * 成绩仓库（S10，2026-11-25）：成绩页与成绩设置页搬进共享层后要它。
     *
     * 与课表共用同一个 Room（`juwDatabase()`），不需要会话——读的是本地表。
     */
    private val scoreRepositoryImpl: ScoreRepository by lazy {
        ScoreRepository(juwDatabase())
    }

    /** 成绩自动导入与变动检测（口径同 [examSyncImpl]：会话 + 仓库 + 偏好 + 收件箱）。 */
    private val scoreSyncImpl: ScoreSync by lazy {
        ScoreSync(cas = session, repo = scoreRepositoryImpl, prefs = prefs, inbox = inbox)
    }

    private val examSyncImpl: ExamSync by lazy {
        ExamSync(
            cas = session,
            prefs = prefs,
            snapshotStore = ExamSnapshotStore(),
            inbox = inbox,
            repo = repository,
        )
    }

    private val termImport: ScheduleTermImport by lazy {
        ScheduleTermImport(
            cas = session,
            repo = repository,
            prefs = prefs,
            inbox = inbox,
            sync = sync,
        )
    }

    override fun displayPrefs(): DisplayPrefsStore = prefs

    override fun scheduleRepository(): ScheduleRepository = repository

    override fun scoreRepository(): ScoreRepository = scoreRepositoryImpl

    override fun scheduleSync(): ScheduleSync = sync

    override fun examSync(): ExamSync = examSyncImpl

    override fun scoreSync(): ScoreSync = scoreSyncImpl

    override fun scheduleTermImport(): ScheduleTermImport = termImport

    init {
        // Cookie 来源与视图生命周期无关（直接读 `WKWebsiteDataStore`），所以装一次就够，
        // 用户关掉导入页之后课表页的「检测」照样能读登录态。
        WebCookies.source = IosWebCookieSource
    }

    /** 首次打开时把作息表与默认学期写进库（与 Android 的 `ensureDefaults` 同步口径）。 */
    fun warmUp() {
        scope.launch { repository.ensureDefaults() }
    }
}
