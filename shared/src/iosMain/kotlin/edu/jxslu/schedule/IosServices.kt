package edu.jxslu.schedule

import edu.jxslu.schedule.data.ai.AiScheduleClient
import edu.jxslu.schedule.data.kqcx.KqcxAuthClient
import edu.jxslu.schedule.data.kqcx.KqcxSessionCore
import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.power.PowerClient
import edu.jxslu.schedule.data.power.PowerReadingStore
import edu.jxslu.schedule.data.power.PowerRepository
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.prefs.recordGuessOrder
import edu.jxslu.schedule.data.qiekj.IosQiekjExpiryMarker
import edu.jxslu.schedule.data.qiekj.IosQiekjOrderStore
import edu.jxslu.schedule.data.qiekj.IosQiekjWateringStore
import edu.jxslu.schedule.data.qiekj.QiekjRepository
import edu.jxslu.schedule.data.qiekj.QiekjWateringSessions
import edu.jxslu.schedule.data.qzxy.QzxyRepository
import kotlinx.coroutines.sync.Mutex
import edu.jxslu.schedule.data.qzxy.QzxyWateringRegistry
import edu.jxslu.schedule.data.qzxy.QzxyPrefs
import edu.jxslu.schedule.data.qzxy.QzxyLinkDiagnostics
import edu.jxslu.schedule.data.qzxy.QzxyDeviceScanner
import edu.jxslu.schedule.data.qzxy.QzxyDeviceRegistry
import edu.jxslu.schedule.data.qzxy.QzxyBluetoothLink
import edu.jxslu.schedule.data.qzxy.IosQzxyBluetoothLink
import edu.jxslu.schedule.data.repo.AiApplyHistoryStore
import edu.jxslu.schedule.data.repo.CampusCalendarStore
import edu.jxslu.schedule.data.repo.CurriculumRepository
import edu.jxslu.schedule.data.repo.CurriculumSync
import edu.jxslu.schedule.data.repo.DormRepairStore
import edu.jxslu.schedule.data.repo.ExamSnapshotStore
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.HomeworkRepository
import edu.jxslu.schedule.data.repo.InternshipBasesLoader
import edu.jxslu.schedule.data.repo.InternshipSnapshotStore
import edu.jxslu.schedule.data.repo.NoteRepository
import edu.jxslu.schedule.data.repo.InboxRepository
import edu.jxslu.schedule.data.repo.ProfileSync
import edu.jxslu.schedule.data.repo.ScheduleBaselineStore
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.data.repo.SelectionRepository
import edu.jxslu.schedule.data.repo.SelectionRoundsStore
import edu.jxslu.schedule.data.repo.SelectionSync
import edu.jxslu.schedule.data.repo.ScoreRepository
import edu.jxslu.schedule.data.repo.ScoreSync
import edu.jxslu.schedule.data.repo.ScholarProgressRepository
import edu.jxslu.schedule.data.repo.ScholarProgressSync
import edu.jxslu.schedule.data.repo.TeachingCalendarSync
import edu.jxslu.schedule.data.repo.TextbookSync
import edu.jxslu.schedule.data.session.CasEnsureResult
import edu.jxslu.schedule.data.session.CasSession
import edu.jxslu.schedule.data.session.IosCasTransport
import edu.jxslu.schedule.data.session.IosCredentialVault
import edu.jxslu.schedule.data.session.IosJwReadSession
import edu.jxslu.schedule.data.session.IosJwSessionFlag
import edu.jxslu.schedule.data.session.IosAiApiKeyStore
import edu.jxslu.schedule.data.session.IosKqcxCredentialStore
import edu.jxslu.schedule.data.session.IosQiekjCredentialStore
import edu.jxslu.schedule.data.session.IosSessionExpirySink
import edu.jxslu.schedule.data.session.IosUjingSessionStore
import edu.jxslu.schedule.data.session.IosWebCookieBridge
import edu.jxslu.schedule.data.session.IosWenquSessionStore
import edu.jxslu.schedule.data.session.IosWebCookieSource
import edu.jxslu.schedule.data.session.IosYktCredentialStore
import edu.jxslu.schedule.data.session.JwReadSession
import edu.jxslu.schedule.data.session.LoginTarget
import edu.jxslu.schedule.data.session.SessionExpirySink
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.data.session.SessionStatus
import edu.jxslu.schedule.data.session.WebCookies
import edu.jxslu.schedule.data.ujing.UjingDraftStore
import edu.jxslu.schedule.data.ujing.UjingHistoryStore
import edu.jxslu.schedule.data.ujing.UjingHouseStore
import edu.jxslu.schedule.data.local.IosKeyValuePrefs
import edu.jxslu.schedule.data.ujing.UjingOrderStore
import edu.jxslu.schedule.data.ujing.UjingRepository
import edu.jxslu.schedule.data.weather.WeatherRepository
import edu.jxslu.schedule.data.wenqu.WenquRepository
import edu.jxslu.schedule.data.ykt.YktClient
import edu.jxslu.schedule.data.ykt.YktCredentialStore
import edu.jxslu.schedule.data.ykt.YktRepository
import edu.jxslu.schedule.data.ykt.YktSuspension
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.platform.AccountCredentials
import edu.jxslu.schedule.platform.AiApiKeyStore
import edu.jxslu.schedule.platform.AppServices
import edu.jxslu.schedule.platform.ClassReminder
import edu.jxslu.schedule.platform.HomeworkReminder
import edu.jxslu.schedule.platform.IosClassReminder
import edu.jxslu.schedule.platform.IosHomeworkReminder
import edu.jxslu.schedule.platform.IosLocalNotifications
import edu.jxslu.schedule.platform.IosNotificationPermission
import edu.jxslu.schedule.platform.IosScoreReminder
import edu.jxslu.schedule.platform.IosUjingReminder
import edu.jxslu.schedule.platform.JwSessionLogout
import edu.jxslu.schedule.platform.Logger
import edu.jxslu.schedule.platform.ScheduleBackgroundFiles
import edu.jxslu.schedule.platform.UjingReminder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import edu.jxslu.schedule.ui.ai.AiApiKeys
import edu.jxslu.schedule.ui.ebike.BikeLocationSource
import edu.jxslu.schedule.ui.ebike.IosBikeLocation
import edu.jxslu.schedule.ui.week.IosScheduleBackgroundFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
 * | 教务会话 | [IosJwReadSession]：WebView 活着就同源取，否则交给共享层 `CasSession`（Ktor + Keychain 凭证） |
 * | 教务三件套 | `ScheduleSync` / `ExamSync` / `ScheduleTermImport`，全部走上面那条会话 |
 *
 * ## M2 的关键点：会话来源换了，调用方一行没改
 *
 * Android 的会话是 `CasSession`（OkHttp + 凭证保险箱 + 自动填表）。2026-11-25 那份
 * `CasSession` 搬进了 `shared/commonMain`，iOS 现在**用的是同一个类**，只换三件平台物
 * （Ktor 传输 / Keychain 凭证 / WebKit cookie 桥，见 [jwCas]）。两条实现都实现
 * [JwReadSession]，所以 `ScheduleSync` / `ExamSync` / `ScheduleTermImport` 这三个已经在
 * 共享层的类**一个字都不用改**，`WeekScreen` 的「检测」图标也会自动出现。
 *
 * ## 作用域为什么是 `Dispatchers.Default`
 *
 * `ScheduleRepository` 第三条参数是「共享热流用的作用域」。Android 侧注入的是
 * `Graph.appScope`（挂着崩溃上报的 handler）；iOS 侧还没有那套上报，所以这里自建一个
 * `SupervisorJob` 作用域——一个子协程失败不影响其它订阅。**别改回 `Dispatchers.Main`**：
 * 仓库里的热流有文件/DB 读取，挂在主线程上会卡 UI。
 *
 * **2026-11-25 晚补 [CoroutineExceptionHandler]**：Kotlin/Native 上作用域里没人接的异常
 * **会直接终止进程**。冷启动那次「安装后一开就闪退」就是这条路径（`warmUp` 里新加的 Cookie
 * 探活碰到 WebKit 主线程约束）。Android 有崩溃上报兜着，iOS 这边只能自己接：记一条日志，
 * 不让一个后台订阅把整个 App 带走。
 */
class IosServices : AppServices {

    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineExceptionHandler { _, error ->
                // 用 toString() 而不是 `::class.simpleName`：后者在 Native 上的反射支持不值得赌
                Logger.w(TAG, "后台任务失败：$error")
            },
    )

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

    /**
     * 统一认证凭证（Keychain，service `secure_jw`）+ 登录闸门（`NSUserDefaults`，前缀 `login_gate`）。
     *
     * 与 Android 的 `CredentialVault` 同一份契约（[CredentialStore]），所以
     * [CasSession] 这条链两端逐条一致：可信期、失败闸门、凭证错才计数。
     */
    private val jwVault: IosCredentialVault by lazy { IosCredentialVault() }

    /**
     * 教务 CAS 会话（共享层 `CasSession`），iOS 的三件平台物都换成这边的实现：
     * Ktor 传输（[IosCasTransport]）、Keychain 凭证（[jwVault]）、WebKit cookie 桥
     * （[IosWebCookieBridge]）。
     *
     * 它替掉了原先那条「只有 WebView cookie 一条路」的形态：用户存过学号密码之后，
     * 导入页关掉、进程重启都还能自己重新登录，不必再回导入页手登一次。
     */
    private val jwCas: CasSession by lazy {
        CasSession(
            vault = jwVault,
            http = IosCasTransport(),
            webCookies = IosWebCookieBridge,
            // iOS 没有 vpnDetector 的等价物（NSURLSession 自己走系统代理），留默认 false
        )
    }

    /**
     * 教务读取会话：`WKWebView` 活着时同源取，否则交给 [jwCas]。
     *
     * 四档判定与「为什么 WebView 优先」写在 `IosJwReadSession` 的 KDoc 里。
     */
    private val session: JwReadSession by lazy { IosJwReadSession(cas = jwCas) }

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

    /** 选课同步：同一条教务会话，轮次快照落 `appDataDir()`。 */
    private val selectionSyncImpl: SelectionSync by lazy {
        SelectionSync(
            cas = session,
            repo = SelectionRepository(juwDatabase()),
            prefs = prefs,
            roundsStore = SelectionRoundsStore(),
        )
    }

    /** 学籍卡补抓（S18）：会话 + 偏好存储，与 Android 的 `Graph.profileSync` 同一构造口径。 */
    private val profileSyncImpl: ProfileSync by lazy {
        ProfileSync(cas = session, prefs = prefs)
    }

    /** 「登录状态已过期」留痕（S20）：只存布尔，`NSUserDefaults` 就够。 */
    private val sessionExpiry: SessionExpirySink = IosSessionExpirySink()

    override fun displayPrefs(): DisplayPrefsStore = prefs

    override fun scheduleRepository(): ScheduleRepository = repository

    override fun scoreRepository(): ScoreRepository = scoreRepositoryImpl

    override fun scheduleSync(): ScheduleSync = sync

    override fun examSync(): ExamSync = examSyncImpl

    override fun scoreSync(): ScoreSync = scoreSyncImpl

    override fun scheduleTermImport(): ScheduleTermImport = termImport

    /**
     * 学籍卡补抓（S18）：与 [scheduleSync] 同一条会话（[IosJwReadSession]）。
     *
     * 没登录过教务时 `syncOnce` 直接返回 false（`ensureValid` 给 `NeedsManualLogin`），
     * 学籍页据此提示「请先在教务导入里登录一次」——iOS 侧不存密码，登录只由用户
     * 在导入页的 `WKWebView` 里完成。
     */
    override fun profileSync(): ProfileSync = profileSyncImpl

    // 下面四个都是本地表仓库，iOS 侧直接可用（Room 已搬过来，构造只要一个 JuwDatabase）。
    override fun selectionRepository(): SelectionRepository = SelectionRepository(juwDatabase())

    /**
     * 选课轮次与记录抓取。
     *
     * 与 [scheduleSync] 同一条会话（[IosJwReadSession]），所以**教务登录一次之后这里就能用**——
     * 不必等额外的接入。轮次快照落 `SelectionRoundsStore`（文件在 `appDataDir()`）。
     */
    override fun selectionSync(): SelectionSync = selectionSyncImpl

    override fun noteRepository(): NoteRepository = NoteRepository(juwDatabase())

    override fun homeworkRepository(): HomeworkRepository = HomeworkRepository(juwDatabase())

    /** 收件箱（本地表）：今日页收件箱卡与收件箱页共用的那一份，见字段 [inbox]。 */
    override fun inboxRepository(): InboxRepository = inbox

    // ---- 一卡通 / 寝室电费（S14，2026-11-25） ----
    //
    // 两个仓库非空：数据层已经是纯 Ktor + 本地表，iOS 直接建得起来。
    // 2026-11-25 晚补齐了两份可选加速：落盘 token（Keychain）与「失效标记」（进程内）。

    /**
     * 一卡通凭证与 token 的 Keychain 实现（[IosYktCredentialStore]）。
     *
     * 必须是单例：凭证存储没有内存缓存，但 token 缓存与「清凭证」都落在同一个 Keychain
     * service 上，两个实例会让「这里清了、那里还认为有」。
     */
    private val yktVault: IosYktCredentialStore by lazy { IosYktCredentialStore() }

    /** 共享层的一卡通凭证门面（唯一构造点在这里，见 `YktCredentialStore` 的 KDoc）。 */
    private val yktCredentials: YktCredentialStore by lazy { YktCredentialStore(yktVault) }

    /**
     * 「凭证被判失效」的标记：写共享层的 [SessionStatus]（进程级，不落盘）。
     *
     * 账号中心页的校园卡行读的就是它（`LoginStateRules.derive` 的 `suspended` 那一档），
     * 与 Android 的 `GraphServices` 逐字同款。**这不是「以后补」的占位**：没有它，密码错到
     * 停用时 iOS 上只会一直显示「已开启」，用户不知道该去重填密码。
     */
    private val yktSuspension: YktSuspension = YktSuspension { suspended ->
        SessionStatus.syncSuspended(LoginTarget.Ykt, suspended)
    }

    /**
     * 一卡通仓库。
     *
     * `tokenCache = yktVault`：落盘 token 进 Keychain（与 Android 的 `CredentialVault`
     * 同一份语义），冷启动少一次登录。新鲜度判定在 `TokenFreshness`，读到废 token 也只是
     * 多一次 401 重登，不影响正确性。
     *
     * `suspension = yktSuspension`：见上。
     */
    private val yktRepo: YktRepository by lazy {
        YktRepository(
            YktClient.create(),
            tokenCache = yktVault,
            suspension = yktSuspension,
        )
    }

    /** 电表读数本机记录（与课表共用一个 Room）。 */
    private val powerReadings: PowerReadingStore by lazy {
        PowerReadingStore(juwDatabase().powerReadingDao())
    }

    /**
     * 电费仓库：读数落本地表，协议留痕 release/debug 都不写。
     *
     * `suspension = yktSuspension`：电费与一卡通共用同一份凭证（学号 + 查询密码），
     * 所以失效标记也是同一格（`LoginTarget.Ykt`），与 Android 一致。
     */
    private val powerRepo: PowerRepository by lazy {
        PowerRepository(PowerClient.create(), powerReadings, debugDump = null, suspension = yktSuspension)
    }

    override fun yktRepository(): YktRepository = yktRepo

    /**
     * 校园卡凭证存储：Keychain 实现（2026-11-25 晚接上）。
     *
     * 介质是 `kSecClassGenericPassword`、service `secure_ykt`、account 沿用 Android 的
     * `username` / `password`（另加 token 两项），见 [IosYktCredentialStore]。
     *
     * 接上之后付款码 / 消费流水 / 电费 / 校园卡四处的「开启校园卡」不再是死路：`null` 时
     * 那几页整块隐藏入口，非空时读到的是真的 Keychain。**别退回 `NSUserDefaults`**
     * ——明文存密码比不存更糟。
     */
    override fun yktCredentialStore(): YktCredentialStore? = yktCredentials

    override fun powerRepository(): PowerRepository = powerRepo

    override fun powerReadingStore(): PowerReadingStore = powerReadings

    /**
     * 天气仓库（S19）：纯 Ktor + 进程内 TTL 缓存，与 Android 同一份实现。
     *
     * 惰性单例而不是每次 new：今日页顶栏提示与天气详情页共用它，
     * 两个实例等于每次进页各打一次 Open-Meteo（免费档按请求数算）。
     */
    private val weatherRepo: WeatherRepository by lazy { WeatherRepository() }

    override fun weatherRepository(): WeatherRepository = weatherRepo

    /** 与课表 / 成绩 / 收件箱同一个库实例（`juwDatabase()` 自带单例缓存）。 */
    override fun database(): JuwDatabase = juwDatabase()

    // ---- AI 调课（iOS 移植 S15 收尾，2026-11-25） ----
    //
    // 两件非空：客户端是纯 Ktor、留痕只认 `appDataDir()`，与 Android 同一份实现。
    // 差别只有图片编码器——`AiScheduleClient.create()` 不注入时恒 null，等于「不发图」，
    // 纯文本链路照常可用（iOS 的图片压缩还没接，见 `IosPage` 的说明）。

    /** 进程单例：客户端持有 Ktor 连接池，每次进页 new 一个等于每次重开池。 */
    private val aiClient: AiScheduleClient by lazy { AiScheduleClient.create() }

    /** 与「调课记录」页共用的那一份（按 id 对账，两个实例会各写一份 JSON）。 */
    private val aiHistory: AiApplyHistoryStore by lazy { AiApplyHistoryStore() }

    override fun aiScheduleClient(): AiScheduleClient = aiClient

    override fun aiApplyHistoryStore(): AiApplyHistoryStore = aiHistory

    /**
     * 课表页背景图的文件删除（iOS 移植 M4，2026-11-26 补上）。
     *
     * 与 `:app` 的 `GraphServices` 同一个形状：只转接「删旧文件」这一件事，选图与解码压缩仍是
     * 平台动作（`ui/week/IosScheduleBackgroundFiles.kt` 的类 KDoc 说明了为什么只有这一个方法）。
     *
     * 返回非 null 之后 `MeViewModel` 才会真的删旧文件；显示设置面板的「背景」整组、
     * 以及课表页的背景图层，仍要靠宿主的接线条（前者看 `onPickBackground`，后者看
     * `WeekScreen.backgroundLayer` 槽位）。
     */
    override fun scheduleBackgroundFiles(): ScheduleBackgroundFiles? = IosScheduleBackgroundFiles()

    // ---- 设置首页（S17，2026-11-25） ----

    /**
     * 版本名取 `Info.plist` 的 `CFBundleShortVersionString`（Xcode 工程里的 MARKETING_VERSION）。
     * 读不到就返回空串，设置页写成 `v—`——不编一个假版本号。
     */
    override fun appVersion(): String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
            .orEmpty()

    // ---- 胖乖生活 / 开水房（iOS 移植 T2，2026-11-25） ----

    override fun qiekjRepository(): QiekjRepository? = qiekjRepo

    /**
     * 胖乖仓库：**iOS 侧已能建起来**（2026-11-25）。
     *
     * 三份平台存储都到位了：凭证走 Keychain（[IosQiekjCredentialStore]）、订单快照走
     * `appDataDir()/qiekj_orders.json`（[IosQiekjOrderStore]）、过期标记复用
     * [IosSessionExpirySink]（[IosQiekjExpiryMarker]）。
     *
     * 惰性单例：开水页与账号中心读的必须是同一份（登录态、订单快照、开水流程的 Mutex
     * 都在仓库里），各建一个会让「开水页登录了、账号中心还是未登录」。
     *
     * **不传 `debugDumpDir`**：那是 release 下不落盘的采样目录，iOS 侧没有对应需求。
     */
    private val qiekjRepo: QiekjRepository by lazy {
        QiekjRepository(
            tokenStore = IosQiekjCredentialStore(),
            orderHistoryStore = IosQiekjOrderStore(),
            expiry = IosQiekjExpiryMarker(sessionExpiry),
        )
    }

    /**
     * 「正在出水」登记：`appDataDir()/qiekj_watering.json` 落盘（iOS 移植 V2，2026-11-25）。
     *
     * 上一版是空实现，理由是「读者（今日页服务卡）还在 `:app`」。服务卡搬进共享层之后
     * iOS 今日页读的就是这条流，所以换成真实现：与 Android 的 `QiekjWateringStore` 逐条对齐
     * （165 秒后不再显示「出水中」、5 分钟残留当没有），只换介质（见 [IosQiekjWateringStore]）。
     *
     * 单例：开水页的 `WaterViewModel` 写、今日页服务卡读，必须是同一份流。
     */
    private val qiekjWatering: QiekjWateringSessions by lazy { IosQiekjWateringStore() }

    override fun qiekjWateringSessions(): QiekjWateringSessions = qiekjWatering

    /**
     * 「猜你想用」：写进共享层的 [prefs]（DataStore 已搬过来，两端同一份介质）。
     *
     * 与 Android 的 `Graph.recordGuessOrder` 同口径：**进程作用域 + 吞错**，
     * 不让一次偏好写失败冒到开水流程里。iOS 目前没有今日页的「猜你想用」卡片，
     * 记了也没人读——保留写入是为了将来那张卡接上时不用回头补数据。
     */
    override fun recordGuessOrder(target: GuessTarget) {
        scope.launch { runCatching { prefs.recordGuessOrder(target) } }
    }

    // ---- 账号与登录（账号中心页，iOS 移植 S20，2026-11-25） ----
    //
    // 本端能给的四项：凭证快照（一卡通那一半，Keychain）、过期留痕、AI Key、应用级作用域；
    // 给不出的按能力缺口返回 null，账号中心页据此整行 / 整块不渲染
    // （`design/ios-port.md` §11.2 ④）。

    /**
     * 教务 / 一卡通的凭证快照（账号中心与「我的」页的账户卡读它）。
     *
     * **两半都是真的**：一卡通来自 [yktVault]（Keychain，`secure_ykt`），教务来自 [jwVault]
     * （Keychain，`secure_jw`）。2026-11-25 之前教务这一半恒为 null——那时 iOS 从不保存
     * 教务密码，登录只发生在导入页的 `WKWebView` 里。现在补上了凭证直登：用户在
     * 「我的 → 教务账户」保存一次学号密码，会话过期时 `CasSession` 自己重新登录。
     *
     * `resetLoginGates` 仍然只清一卡通与胖乖：教务那格的闸门由 [jwSessionLogout] 那条链
     * （`CasSession.logout` → `onCredentialsUpdated`）自己处理，与 Android 同口径。
     */
    private val iosAccountCredentials: AccountCredentials = object : AccountCredentials {

        override fun readJwUsername(): String? = jwVault.readCas()?.username

        override fun readYktUsername(): String? = yktVault.read()?.username

        /** 凭证 + 落盘 token + 运行时失效标记一起清（口径同 `CredentialVault.clearYkt`）。 */
        override fun clearYkt() {
            yktVault.clear()
        }

        override fun resetLoginGates() {
            // 本端没有一卡通 / 胖乖的落盘闸门：那两份只服务于各自仓库的重登退避，
            // 而 iOS 侧的一卡通走「token 401 就重登」、胖乖没有闸门。教务那格的闸门
            // 由 [jwSessionLogout]（`CasSession.logout`）自己清。
        }

        /**
         * 校验一对教务凭据：先清闸门再试登（与 Android 侧、引导第 2 步逐字同款）。
         *
         * 成功与否都只影响登录态与闸门，**不落库**——落库是 [saveJw]，由弹层在
         * `Ready` 之后调（「先验后存」，错密码存进来会把账号试到停用）。
         */
        override suspend fun verifyJw(username: String, password: String): CasEnsureResult {
            jwCas.onCredentialsUpdated()
            val result = jwCas.tryLogin(username, password)
            if (result is CasEnsureResult.Ready) IosJwSessionFlag.markUsable()
            return result
        }

        override fun saveJw(username: String, password: String) = jwVault.saveCas(username, password)
    }

    override fun accountCredentials(): AccountCredentials = iosAccountCredentials

    /** 「已过期」留痕：只存布尔，`NSUserDefaults` 够用（见 [IosSessionExpirySink]）。 */
    override fun sessionExpirySink(): SessionExpirySink = sessionExpiry

    /**
     * 退出教务原生会话：清 Ktor 的 cookie 罐、`WKWebsiteDataStore` 的 cookie 与 localStorage、
     * 再加上 Keychain 里的学号密码（`CasSession.logout`）。
     *
     * 2026-11-25 之前这里是 null（iOS 没有「原生会话」可退）。现在有了凭证直登，
     * 退出必须把三样一起清：只清凭证的话 WebView 里手登的会话还活着，
     * 「已登录」照旧成立，退出形同虚设（Android 侧 2026-09-28 踩过同一个坑）。
     */
    override fun jwSessionLogout(): JwSessionLogout = JwSessionLogout { jwCas.logout() }

    /**
     * AI Key：**iOS 侧已接 Keychain**（[IosAiApiKeyStore]）。
     *
     * 适配 `platform.AiApiKeyStore` 的两个方法——共享层只需要读与清，写入由设置页
     * 走 `saveApiKey`（Android 侧同一个类也只有读/清进适配器）。
     */
    override fun aiApiKeyStore(): AiApiKeyStore = aiApiKeys

    /**
     * Keychain 版 API Key 存储（[IosAiApiKeyStore]）。
     *
     * 页面要的是三方法形状（`ui/ai` 的 `AiApiKeys` 还要 `save`），账号中心要的是
     * 两方法形状（[AiApiKeyStore]）；两者指向同一个 Keychain 条目，各包一层适配，
     * 不各建一份存储——否则「账号中心清掉了、AI 页还认为有 Key」。
     */
    private val aiKeychain = IosAiApiKeyStore()

    private val aiApiKeys: AiApiKeyStore = object : AiApiKeyStore {
        override fun readApiKey(): String? = aiKeychain.readApiKey()

        override fun clear() = aiKeychain.clear()
    }

    /**
     * AI 页面要的三回调形状（读 / 写 / 清），与 [aiApiKeys] 指向**同一个** Keychain 条目。
     *
     * 两套形状并存是刻意的：账号中心只需要「读 + 清」（它不配置 Key），
     * 而 AI 设置页要能写。各建一份存储会让「账号中心清掉了、AI 页还认为有 Key」。
     */
    val aiKeys: AiApiKeys = AiApiKeys(
        read = { aiKeychain.readApiKey() },
        save = { aiKeychain.saveApiKey(it) },
        clear = { aiKeychain.clear() },
    )

    /** 趣智：**iOS 侧返回 null**——会话里含 token，等 `QzxyRepository` 的构造口。 */
    override fun qzxyRepository(): QzxyRepository? = null

    /**
     * U净 仓库：**iOS 侧已能建起来**（2026-11-25）。
     *
     * 构造要一份凭证存储（[IosUjingSessionStore]，Keychain）+ 订单快照与历史账单
     * （[IosKeyValuePrefs]，`NSUserDefaults`，与 Android 的三个 prefs 文件一一对应）。
     * 支付（`UjingPay`）与洗好提醒（`UjingReminder`）不传：iOS 侧还没有支付宝深链
     * 与通知渠道，页面据此把「支付」「提醒」两处入口整块不渲染。
     */
    override fun ujingRepository(): UjingRepository? = ujingRepo

    private val ujingOrders = UjingOrderStore(IosKeyValuePrefs("ujing_order"))
    private val ujingHistory = UjingHistoryStore(IosKeyValuePrefs("ujing_history"))

    private val ujingRepo: UjingRepository by lazy {
        UjingRepository(
            sessionStore = IosUjingSessionStore(),
            orderStore = ujingOrders,
            historyStore = ujingHistory,
            expiry = sessionExpiry,
        )
    }

    /** 收藏的洗衣房：与主页看板共用同一份（`NSUserDefaults` 前缀 `ujing_houses`）。 */
    override fun ujingHouseStore(): UjingHouseStore = ujingHouses

    private val ujingHouses = UjingHouseStore(IosKeyValuePrefs("ujing_houses"))

    /** 历史账单：与主页记录卡共用同一份实例（各建一个会出现「刚洗完看不到」）。 */
    override fun ujingHistoryStore(): UjingHistoryStore = ujingHistory

    /**
     * 问渠仓库：**iOS 侧已能建起来**（2026-11-25）。
     *
     * 构造只要两样：凭证存储（[IosWenquSessionStore]，Keychain）与过期留痕
     * （[IosSessionExpirySink]）。问渠全链路是 HTTP，没有蓝牙/通知依赖，
     * 所以登录、活动列表、报名与取消在 iOS 上都能真跑——蹲点提醒那类通知动作由页面
     * 的可空回调承担，传 null 后对应入口不渲染。
     */
    override fun wenquRepository(): WenquRepository? = wenquRepo

    private val wenquRepo: WenquRepository by lazy {
        WenquRepository(
            store = IosWenquSessionStore(),
            expiry = sessionExpiry,
        )
    }

    /** 应用级作用域（与 [repository] 的热流共用同一个，不另起一个）。 */
    override fun appScope(): CoroutineScope = scope

    /**
     * WebView 里有没有有效教务会话。
     *
     * 上一版返回 `null`（判断不了）——理由是「`getAllCookies` 是异步回调，这里要同步布尔」。
     * 后果不是少一个功能，而是**给出错误结论**：用户刚登录成功、课表都落库了，账号中心仍显示
     * 「登录」胶囊。2026-11-25 晚审计把它列为 P0。
     *
     * 现在读 [IosJwSessionFlag]（进程内标记）：冷启动由 [warmUp] 探一次 Cookie，每次抓取成功 /
     * 失败由 [IosJwReadSession] 顺手更新，导入页落到学生端时立刻置真。所以这里给的是**真布尔**，
     * 不再是「判断不了」。
     */
    override fun hasJwWebSession(): Boolean? = IosJwSessionFlag.hasSession
    // ---- U净（S21，2026-11-25） ----
    //
    // 仓库与两个本地表（订单快照 / 历史账单）2026-11-25 已接上：凭证走 Keychain、
    // 两份快照走 `NSUserDefaults`（[IosKeyValuePrefs]）。支付不在这一层——`PayTask` 只能在
    // `Activity` 上拉起，iOS 侧恒为 null（见 `UjingPay` 的 KDoc）。

    /**
     * U净 扫码草稿：纯内存 `StateFlow`，两端同一份实现——**非空**。
     *
     * 惰性单例：主页开的草稿必须与独立下单页读到的是同一个（各造一个等于「扫了码、
     * 下单页拿到 null」）。进程回收即丢，这是设计（见 `UjingDraftStore` 的 KDoc）。
     */
    private val ujingDraft: UjingDraftStore by lazy { UjingDraftStore() }

    override fun ujingDraftStore(): UjingDraftStore = ujingDraft

    /**
     * 洗衣完成提醒：`UNUserNotificationCenter` 实现（N1，2026-11-25 晚），**非空**。
     *
     * 上一版返回 null，理由是「iOS 还没有通知能力」。现在有了：授权 + `UNTimeIntervalNotificationTrigger`
     * 排一条到点通知，两个 `remove…NotificationsWithIdentifiers` 对应 Android 的 cancel / revoke。
     * 权限被拒时 [IosUjingReminder] 自己降级成 no-op（不抛异常），结论记在
     * [IosNotificationPermission] 里给页面读。授权与队列扫描在 `IosLocalNotifications`（三类共用）。
     *
     * **非 lazy 是刻意的**：进 U净 页之前也可能要先撤一条（例如冷启动恢复在案订单），
     * 而构造只取一次系统单例、没有 IO。前台横幅代理不靠它安装——那一步在 [init] 里，比页面更早。
     *
     * 这一端仍然**不能下单**（`UjingPay` 为 null，见 `UjingViewModel.createAndPay`），所以这条提醒
     * 在 iOS 上要等「有在案订单」才会被排到——闭环缺口不在提醒这一层。
     */
    private val ujingReminderSink: UjingReminder = IosUjingReminder()

    override fun ujingReminder(): UjingReminder? = ujingReminderSink
    // ---- 今日页（V1，2026-11-25） ----

    /**
     * 宿舍报修进度快照：**非空**，两端同源。
     *
     * `DormRepairStore` 是 `MirroredJsonStore`（`appDataDir()` 下一份 JSON），
     * S7 起已在 commonMain，构造器不吃 `Context`，iOS 侧建得起来。
     *
     * 注意只有**快照**能读：拉取那条链（`DormRepairSync`）W2 起已在 `commonMain`，
     * 但它还没有共享层的调用方，而 iOS 侧今日页的报修行本身还没移植——所以这里仍只给快照。
     * 接上之后由 `DormRepairSync(cas = session, store = dormRepair, webSession = null)`
     * 构造即可（iOS 没有 `CookieManager`，WebView 那一档恒为 null，直接落 CAS 分支）。
     */
    private val dormRepair: DormRepairStore by lazy { DormRepairStore() }

    override fun dormRepairStore(): DormRepairStore = dormRepair

    // ---- 趣智校园页面层（iOS 移植 V3，2026-11-25） ----
    //
    // 页面层（`ui/qzxy`）已在 commonMain，但**这一端暂时挂不出来**：
    // `qzxyRepository()` 仍是 null（会话里含 token，要一份加密存储；iOS 还没接 Keychain），
    // 所以 `QzxyViewModel.fromServices()` 直接回 null，宿主不给路由。下面五个返回 null
    // 是同一件事的另外几面，不是「先占位」——每一项缺了都对应页面上一块真实的空缺。

    /**
     * 蓝牙链路：**给出失败桩而不是 null**。
     *
     * [IosQzxyBluetoothLink] 的四个成员全部立刻回 `Result.failure`，状态恒为 Disconnected。
     * 这与「本平台不支持蓝牙」是同一句话，而且比 null 更早暴露问题：接上 CoreBluetooth 之前，
     * 任何一条走蓝牙的链路都会拿到明确的错误文案，而不是空指针。
     */
    override fun qzxyBluetoothLink(): QzxyBluetoothLink = iosQzxyLink

    /**
     * 链路诊断读数：**null**。
     *
     * 服务表与 MTU 是 GATT 服务发现的产物，iOS 侧要等 CoreBluetooth 接上才有，
     * 而那时也未必按同一形状暴露（`CBService` / `CBCharacteristic` 的属性是另一套位）。
     * 诊断页的「设备服务表」整块据此不渲染。
     */
    override fun qzxyLinkDiagnostics(): QzxyLinkDiagnostics? = null

    /** BLE 扫描：**null**（iOS 侧没有 `CBCentralManager` 扫描实现）。设备弹层里「扫描」整行不渲染。 */
    override fun qzxyScanner(): QzxyDeviceScanner? = null

    /**
     * 已绑定设备：**null**。
     *
     * Android 侧是 `SharedPreferences`（`qzxy_devices`），里面装着老用户已绑定的设备；
     * iOS 没有这份介质，换成 `NSUserDefaults` 是新开一份，与老数据无关——所以这里是 null，
     * 页面按「一台都没绑」处理，而不是给一份能写但留不住的假存储。
     */
    override fun qzxyDeviceRegistry(): QzxyDeviceRegistry? = null

    /** 调试开关 / 记住的手机号 / 清除命令候选：**null**（页面用安全默认值，见 `QzxyPrefs` 的 KDoc）。 */
    override fun qzxyPrefs(): QzxyPrefs? = null

    /** 进行中的用水记账：**null**（没有介质，页面不会出现「待核对」态）。 */
    override fun qzxyWateringRegistry(): QzxyWateringRegistry? = null

    /** 趣智的进程级流程锁：纯内存，两端都建得起来；本端一份即可。 */
    override fun qzxyFlowLock(): Mutex = qzxyFlowLockInstance

    private val iosQzxyLink: QzxyBluetoothLink by lazy { IosQzxyBluetoothLink() }

    private val qzxyFlowLockInstance: Mutex = Mutex()

    init {
        // 未捕获异常落盘（B1，2026-11-25）：`CoroutineExceptionHandler` 只接得住协程作用域里的
        // 异常；UIKit 回调 / 组合期抛出来的走「进程终止」那条路，只能靠
        // `setUnhandledExceptionHook` 在死前留一份栈到 `logs/app.log`。
        //
        // **2026-11-26 起不在这里装**：服务装配挪进了根组合（见 `MainViewController`），本类的
        // 构造就跑到 `ComposeUIViewController` 之后去了，钩子会比它晚装。调用点现在只有一个
        // ——`MainViewController()` 的第二行。
        // Cookie 来源与视图生命周期无关（直接读 `WKWebsiteDataStore`），所以装一次就够，
        // 用户关掉导入页之后课表页的「检测」照样能读登录态。纯 Kotlin 赋值，不碰系统框架。
        WebCookies.source = IosWebCookieSource
        // 通知那一套（前台横幅代理 / 排提醒 / 成绩核对）**不在这里装**：
        // 本类的构造发生在 Swift `makeUIViewController` 执行期间，那时 App 还没进 active。
        // 2026-11-26 真机「一开就闪退」的嫌疑就是它。现在挪到首帧之后的主线程上，
        // 见 [warmUpReminders]。
    }

    /** 首次打开时把作息表与默认学期写进库（与 Android 的 `ensureDefaults` 同步口径）。 */
    fun warmUp() {
        scope.launch {
            // 两步都只碰本地库与 WebKit cookie 库（后者自己切主队列），不碰通知中心。
            // 各自 runCatching：一步失败不该让另一步也不跑，更不该让进程陪着走。
            runCatching { repository.ensureDefaults() }
                .onFailure { Logger.w(TAG, "写入默认作息 / 学期失败：$it") }
            // 启动留痕：见 MainViewController 的编号说明（①–⑥ 在那边，⑦ 起在这边）
            Logger.i(TAG, "⑦ 默认作息与学期已就绪")
            // 顺手探一次「教务网页会话还在不在」：账号中心 / 教务账号页据此显示已登录，
            // 而不是因为读 Cookie 是异步就永远显示「未登录」（见 [hasJwWebSession]）
            runCatching {
                if (session.hasCookies()) {
                    IosJwSessionFlag.markUsable()
                } else {
                    IosJwSessionFlag.markLost()
                }
            }.onFailure { Logger.w(TAG, "探测教务网页会话失败：$it") }
            Logger.i(TAG, "⑧ warmUp 完成")
        }
    }

    /**
     * 首帧之后、主线程上跑的「提醒四件」（2026-11-26）。
     *
     * 与 [warmUp] 分开是因为**时机**而不是因为它们不重要：
     *
     * | 件 | 为什么不能更早 |
     * |---|---|
     * | [IosLocalNotifications.installForegroundPresentation] | `UNUserNotificationCenter` 在 App 进 active 之前碰是闪退嫌疑（见该方法的 KDoc） |
     * | 作业截止提醒重排 | 要读写系统待发队列，同上 |
     * | 上课提醒重排（N3） | 同上；另外它是「4 天窗口 → 整周」的唯一补排入口，每次冷启动都得跑 |
     * | 成绩提醒核对 | 会打教务网络，不该和首帧抢时间 |
     *
     * 每一步各自 `runCatching`：提醒是**锦上添花**，任何一步失败都不该影响开 App。
     * 调用点在 `MainViewController` 的 `LaunchedEffect` 里（`delay` 之后），所以这里不用再切线程。
     */
    suspend fun warmUpReminders() {
        Logger.i(TAG, "⑨ 开始装通知与排提醒")
        runCatching { IosLocalNotifications.installForegroundPresentation() }
            .onFailure { Logger.w(TAG, "安装通知横幅代理失败：$it") }
        // 作业截止提醒：冷启动重排一次。iOS 的本地通知到点不执行代码，必须**提前**排上；
        // 上一次进程排的那批可能已被投递，作业也可能已被勾完成 / 删掉（重排会按前缀清空重来）。
        runCatching { homeworkReminder().reschedule() }
            .onFailure { Logger.w(TAG, "重排作业截止提醒失败：$it") }
        // 上课提醒（N3）：冷启动按前缀清空重排未来 4 天。理由与作业那行相同——本地通知到点不执行
        // 代码，必须提前排上；而「4 天窗口」正是靠每次进前台补一段来覆盖整周的（§12.9.3 策略 A）。
        runCatching { classReminder().reschedule() }
            .onFailure { Logger.w(TAG, "重排上课提醒失败：$it") }
        // 成绩提醒（降级形态）：进 App 查一次，有新成绩立刻弹一条。放在最后——它要打教务，
        // 失败（没登录 / 没网）不该拖住上面两件事。
        runCatching { scoreReminderImpl.checkOnLaunch() }
            .onFailure { Logger.w(TAG, "成绩提醒核对失败：$it") }
        // 课表变化后自动重排上课提醒（2026-11-26 补，§12.14 记的那条唯一功能缺口的收口）。
        //
        // 没有这一条时：改完课、换课表、加停课/调休之后，`class.` 那批仍按旧课表排着，
        // 要等**下次冷启动**才纠正。用户刚在「课表管理」里调过课，当晚的提醒却是旧的，这是 bug。
        //
        // **必须挂在这里而不是 `warmUp`**：Room 的首个值可能在 App 进 active 之前就发射，
        // 那时碰 `UNUserNotificationCenter` 正是 `design/ios-port.md` §12.12 记的闪退嫌疑。
        // `warmUpReminders` 由首帧之后的 `LaunchedEffect` 拉起，主线程，已经过了那一关。
        //
        // 为什么不做节流：`reschedule()` 本身是「按前缀清空 + 重排」，幂等且同标识符覆盖；
        // 一次课表导入会连发十几次，每次都扫一遍待发队列——几十毫秒量级，换来的是
        // 「零延迟纠正」，比压这个数更值。真要压就加 `debounce`，那要先 OptIn `FlowPreview`。
        scope.launch {
            runCatching {
                combine(
                    repository.courses,
                    repository.semester,
                    repository.timeSlots,
                    repository.overrides,
                ) { _, _, _, _ -> Unit }.collect {
                    classReminder().reschedule()
                }
            }.onFailure { Logger.w(TAG, "课表变化后重排上课提醒失败：$it") }
        }
        Logger.i(TAG, "⑩ 提醒四件完成，启动结束")
    }

    // ---- 学业完成情况 / 培养方案（iOS 移植 V4，2026-11-25） ----
    //
    // 四项全部非空：两个仓库只认 `juwDatabase()`（与课表同一个库文件），两个同步只认
    // [session]（`IosJwReadSession`，Cookie 来自 `WKWebView`）。所以 iOS 侧这两页的
    // 「重新导入」是**真的能用**，不是空壳——登录在导入页的 `WKWebView` 里完成后，
    // Cookie 就在 `WebCookies` 里，抓取链路与 Android 走同一份 `JwReadSession` 接口。
    //
    // 单例的理由与 Android 侧一致：两个 Sync 内部的 Mutex 是「单飞」的全部依据。

    private val scholarProgressRepo: ScholarProgressRepository by lazy {
        ScholarProgressRepository(juwDatabase())
    }

    private val scholarProgressSyncImpl: ScholarProgressSync by lazy {
        ScholarProgressSync(cas = session, repo = scholarProgressRepo, prefs = prefs)
    }

    private val curriculumRepo: CurriculumRepository by lazy { CurriculumRepository(juwDatabase()) }

    private val curriculumSyncImpl: CurriculumSync by lazy {
        CurriculumSync(cas = session, repo = curriculumRepo, prefs = prefs)
    }

    override fun scholarProgressRepository(): ScholarProgressRepository = scholarProgressRepo

    override fun scholarProgressSync(): ScholarProgressSync = scholarProgressSyncImpl

    override fun curriculumRepository(): CurriculumRepository = curriculumRepo

    override fun curriculumSync(): CurriculumSync = curriculumSyncImpl

    // ---- 校历 / 实习基地（iOS 移植 W2，2026-11-25） ----
    //
    // 两个都**非空**，与 Android 侧一一对应（`GraphServices` 转接 `Graph` 的同名单例）：
    //
    // - 校历：Ktor（`SharedHttp`）+ `appDataDir()` 下的一份状态 JSON 与一个图片目录，
    //   抓取 / 7 天闸门 / 原子落盘与 Android 是同一份代码。**画不出来**的部分不在这一层
    //   ——`UIImage` 解码还没接，所以宿主给 `CampusCalendarScreen` 的 `images` 传 null，
    //   图片块整块不渲染（文案与刷新按钮照常）。
    // - 实习基地：走 [session]（`IosJwReadSession`，Cookie 来自 `WKWebView`）+ 本机快照。
    //   在导入页登录一次之后，这一页的「刷新」是真的能抓，不是空壳。
    //
    // 单例的理由与 Android 侧一致：校历页读 `state` 流、`refresh` 改同一份状态；
    // 实习基地装载器内部的 Mutex 是「单飞」的全部依据。

    private val campusCalendarStoreImpl: CampusCalendarStore by lazy { CampusCalendarStore() }

    private val internshipBasesLoaderImpl: InternshipBasesLoader by lazy {
        InternshipBasesLoader(cas = session, store = InternshipSnapshotStore())
    }

    override fun campusCalendarStore(): CampusCalendarStore = campusCalendarStoreImpl

    override fun internshipBasesLoader(): InternshipBasesLoader = internshipBasesLoaderImpl

    // ---- 教材（iOS 移植 W3，2026-11-25） ----

    /**
     * 教材抓取：构造只要三样，iOS 侧全都有——[session]（`IosJwReadSession`）、
     * [repository]（课表仓库，教材按 `Timetable.term` 挂）与 [prefs]。
     *
     * 上一版这里没有实现，于是教材页在 iOS 上**永远空**（`textbooks` 表没有任何写入方），
     * 而空态还写着「导入课表后会自动抓取」——用户会照着反复导入（2026-11-25 审计 P0）。
     *
     * 单例的理由与 Android 侧一致：`TextbookSync` 内部的 Mutex 是「一次只抓一轮」的全部依据；
     * 页面「重新导入」与进页补抓两条路都读它。
     */
    private val textbookSyncImpl: TextbookSync by lazy {
        TextbookSync(cas = session, repo = repository, prefs = prefs)
    }

    override fun textbookSync(): TextbookSync = textbookSyncImpl

    // ---- 首启引导（iOS 移植 S22，2026-11-25） ----

    /**
     * 开学日校准：构造只要 [session]（`IosJwReadSession`）与 [repository]，iOS 侧两样都有。
     *
     * 目前唯一的调用点是引导页的后台补抓，而 iOS 那条路走的是「去导入页登录」分支
     * （`jwLogin` 为 null），所以**它现在还不会被调到**。仍然给实现而不是 null：
     * 账号中心 / 导入页以后接同一个触发点时不用回来改这一层，而它会话不在时也只是
     * `ensureValid` 返回 `NeedsManualLogin` → `Failed`，是真实结论不是假实现。
     * 「没有调用方就不许加成员」这条纪律在这里成立的前提是**共享层有调用方**
     * （`OnboardingScreen.startBgSync`），它确实有。
     */
    private val teachingCalendarSyncImpl: TeachingCalendarSync by lazy {
        TeachingCalendarSync(cas = session, repo = repository)
    }

    override fun teachingCalendarSync(): TeachingCalendarSync = teachingCalendarSyncImpl

    // ---- 快趣出行（iOS 移植 S23，2026-11-25） ----

    /**
     * 快趣会话本体：**iOS 侧已能建起来**，不再是 null。
     *
     * 构造只要三样，这一端全都有：[KqcxAuthClient.create]（纯 Ktor，`data/net/SharedHttp`）、
     * [IosKqcxCredentialStore]（Keychain，service `secure_kqcx`，与 Android 的
     * `secure_kqcx.xml` 一一对应）与 [sessionExpiry]（「已过期」留痕，`NSUserDefaults`）。
     *
     * 为什么现在才接：S12 把逻辑搬进 `KqcxSessionCore` 之后，这一端差的不是代码而是
     * **一个装配点**——`:app` 的 `KqcxSessionRepository` 把 core 包成私有字段，
     * `AppServices.kqcxSession()` 当时只能收成 `ThirdPartySession`（登录态 + 退出）。
     * S23 把 core 提到装配根（`Graph.kqcxCore` / 这里），接口签名随之放宽成
     * `KqcxSessionCore?`，两端同形。
     *
     * 它**不落 token**（token 仅内存，与 Android 同一口径）；落盘的只有「记住登录」的
     * 手机号 + 明文密码，供 token 过期后静默重登——密码落 Keychain，不进日志、不进 git。
     *
     * 单例的理由与 Android 侧一致：token / 当前账号 / 重登单飞（Mutex）都是它内部的
     * 进程级状态，各建一个等于「账号中心显示已登录、页面仍是未登录」。
     */
    private val kqcxCoreImpl: KqcxSessionCore by lazy {
        KqcxSessionCore(
            client = KqcxAuthClient.create(),
            credentials = IosKqcxCredentialStore(),
            onSessionExpired = { sessionExpiry.mark(SessionService.Kqcx.id) },
            onSessionCleared = { sessionExpiry.clear(SessionService.Kqcx.id) },
        )
    }

    override fun kqcxSession(): KqcxSessionCore = kqcxCoreImpl

    // ---- 快趣出行页面层（iOS 移植 P4，2026-11-25） ----

    /**
     * 定位源：`CLLocationManager`（`shared/iosMain` 的 `IosBikeLocation`）。
     *
     * **必须是同一份**：连续定位流与一次性定位共用同一个 `CLLocationManager`，
     * 各建一个等于同时开两套 `startUpdatingLocation`。
     *
     * Info.plist 必须有 `NSLocationWhenInUseUsageDescription`，否则
     * `requestWhenInUseAuthorization()` 静默无效（不弹框、状态一直 `NotDetermined`），
     * 现象与「用户拒绝」一模一样。
     */
    private val bikeLocationImpl: BikeLocationSource by lazy { IosBikeLocation() }

    override fun bikeLocation(): BikeLocationSource = bikeLocationImpl

    /**
     * 罗盘朝向：**给 null**（本平台没有可消费的朝向）。
     *
     * `BikeMapInput.userHeadingFlow` 在 iOS 侧被忽略（`BikeMapContract.kt` 第四节：
     * 系统气球旋转后造型别扭），没有消费方就不装传感器。蓝点画成无方向圆点。
     */
    override fun bikeHeading(): Flow<Float>? = null

    // ---- 作业截止提醒（iOS 移植 N2，2026-11-26） ----

    /**
     * 作业截止提醒：`UNUserNotificationCenter` 上排若干条**一次性**本地通知（**非空**）。
     *
     * 时刻算法与 Android 同一份（`domain/ReminderPlanner.kt`），差别在「到点不执行代码」：
     * 未来若干个提醒点必须提前排上，上限 [HOMEWORK_REMINDER_SLOTS]（15 条，占 64 条待发额度的
     * 一份，分配表见 `HomeworkReminderPlatform.kt` / §12.9.3）。
     *
     * 惰性单例：它自己**不持有状态**（状态在系统的待发队列里），每次 `reschedule` 都重读库与开关。
     */
    private val homeworkReminderImpl: HomeworkReminder by lazy {
        IosHomeworkReminder(repo = homeworkRepository(), prefs = prefs, scope = scope)
    }

    override fun homeworkReminder(): HomeworkReminder = homeworkReminderImpl

    /**
     * 成绩提醒（iOS 形态）：进 App 时查一次，有新成绩就立刻弹一条本地通知。
     *
     * **周期检查这一轮不做**：`BGTaskScheduler` 由系统决定何时跑、不保证周期（§12.1），
     * 做出来的提醒会「有时有有时没有」。降级形态与三条口径见 [IosScoreReminder]；
     * 它与 [scoreSyncImpl] 共用同一份 `ScoreSync`（那道 Mutex 是「一次只抓一轮」的依据）。
     */
    private val scoreReminderImpl: IosScoreReminder by lazy {
        IosScoreReminder(prefs = prefs, sync = scoreSyncImpl)
    }

    // ---- 上课提醒（iOS 移植 N3，2026-11-26） ----

    /**
     * 上课提醒：按课表排未来 [CLASS_REMINDER_DAYS] 天每节课的本地通知（**非空**）。
     *
     * 时刻换算与 Android 同一份（`domain/ReminderPlanner.kt`），差别在「到点不执行代码」：
     * 未来若干个提醒点必须提前排上，上限 [CLASS_REMINDER_SLOTS]（40 条，占 64 条待发额度的一份，
     * 分配表见 `platform/ClassReminderPlatform.kt` / §12.9.3）。
     *
     * **不是 `AppServices` 的成员**：`AppServices` 是两端共同契约，加进去就得让 `:app` 的
     * `GraphServices` 也实现一个它已经有了的方法（Android 那条链在 `ui/reminder/ClassReminder`），
     * 而这一族在本端只有 iOS 需要。所以按 `aiKeys` 的先例，做成 [IosServices] 自己的公开成员。
     *
     * 惰性单例：它自己**不持有状态**（状态在系统的待发队列里），每次 `reschedule` 都重读课表与开关。
     */
    private val classReminderImpl: ClassReminder by lazy {
        IosClassReminder(repo = repository, prefs = prefs, scope = scope)
    }

    fun classReminder(): ClassReminder = classReminderImpl

    private companion object {
        /** 日志 tag：崩溃/后台失败都记在这个名字下，排查时先 grep 它。 */
        const val TAG = "IosServices"
    }
}
