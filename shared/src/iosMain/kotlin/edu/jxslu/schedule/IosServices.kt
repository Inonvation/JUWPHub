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
import edu.jxslu.schedule.data.session.IosJwReadSession
import edu.jxslu.schedule.data.session.IosJwSessionFlag
import edu.jxslu.schedule.data.session.IosAiApiKeyStore
import edu.jxslu.schedule.data.session.IosKqcxCredentialStore
import edu.jxslu.schedule.data.session.IosQiekjCredentialStore
import edu.jxslu.schedule.data.session.IosSessionExpirySink
import edu.jxslu.schedule.data.session.IosUjingSessionStore
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
     * 课表页背景图：**iOS 侧还没有实现，返回 null**（iOS 移植 S17，2026-11-25）。
     *
     * 升级条件：在 `iosMain` 里实现 [ScheduleBackgroundFiles]，介质换成
     * `NSTemporaryDirectory()`/沙箱 `appDataDir()` 下的 `schedule_bg/` 目录，解码压缩用
     * `UIImage` + `UIGraphicsImageRenderer`（对应 Android 的 `ImageImport`：
     * 长边钳到 `ScheduleBackground.MAX_SIDE`、超 `MAX_BYTES` 重编码成 JPEG）。
     * 在那之前显示设置面板的「背景」整组不渲染——选图入口给一个点了没反应的按钮更糟。
     */
    override fun scheduleBackgroundFiles(): ScheduleBackgroundFiles? = null

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
     * **一卡通那一半是真的**：学号来自 [yktVault]（Keychain，`secure_ykt` 的 `username`）。
     *
     * **教务那一半恒为 null**，不是漏做：iOS 从不保存教务密码（登录只在导入页的
     * `WKWebView` 里由用户完成，见 `IosJwReadSession`），`CredentialVault` 里的 CAS 学号
     * 在本端没有对应的写入方。给它一个值就只能靠「拿一卡通学号顶上」这类推定，
     * 那正是 `login-session.md` 里「别猜」那条。两个页面都有兜底：`JwAccountScreen` 的
     * 学号位回退到学籍卡的 `profileStudentId`，账号中心的教务行靠 [hasJwWebSession]
     * 显示「已登录」——所以这里给 null 不会让那一行变成假话。
     *
     * `resetLoginGates` 在本端是空实现：登录闸门（连续失败次数 / 停用标记）只服务于
     * `CasSession` 的自动填表，iOS 没有那套，也没有第二处落盘。唯一存在的「闸门类状态」
     * 是 [SessionStatus] 里的进程内停用标记，它已由 [AccountCredentials.clearYkt] 那条路
     * （`IosYktCredentialStore.clear()`）撤掉，这里再清一次只是重复。
     */
    private val iosAccountCredentials: AccountCredentials = object : AccountCredentials {

        override fun readJwUsername(): String? = null

        override fun readYktUsername(): String? = yktVault.read()?.username

        /** 凭证 + 落盘 token + 运行时失效标记一起清（口径同 `CredentialVault.clearYkt`）。 */
        override fun clearYkt() {
            yktVault.clear()
        }

        override fun resetLoginGates() {
            // 本端没有落盘闸门，见本段 KDoc。
        }
    }

    override fun accountCredentials(): AccountCredentials = iosAccountCredentials

    /** 「已过期」留痕：只存布尔，`NSUserDefaults` 够用（见 [IosSessionExpirySink]）。 */
    override fun sessionExpirySink(): SessionExpirySink = sessionExpiry

    /**
     * 退出教务原生会话：**iOS 侧返回 null**。
     *
     * iOS 的登录只发生在 `WKWebView` 里，没有「OkHttp 会话」可退；退出的正确做法是清
     * `WKWebsiteDataStore` 的 cookie 与 `localStorage`，而 `WebCookieSource` 现在只有
     * 读的方向（反向被明确否决，见那个文件）。等那里补上「清本机 WebView 数据」再给实现。
     * 页面在 null 时跳过这一步，其余凭证照常清。
     */
    override fun jwSessionLogout(): JwSessionLogout? = null

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
    // 页面与 ViewModel 已经搬进共享层，但 iOS 侧还**建不起仓库与两个本地表**：
    // 它们要「凭证存储 + `KeyValuePrefs` 落点」两样介质，这边都还没有。三处返回 null，
    // 口径同 [yktCredentialStore]：入口整块不出现，而不是点进去才崩。



    /**
     * U净 扫码草稿：纯内存 `StateFlow`，两端同一份实现——**非空**。
     *
     * 惰性单例：主页开的草稿必须与独立下单页读到的是同一个（各造一个等于「扫了码、
     * 下单页拿到 null」）。进程回收即丢，这是设计（见 `UjingDraftStore` 的 KDoc）。
     */
    private val ujingDraft: UjingDraftStore by lazy { UjingDraftStore() }

    override fun ujingDraftStore(): UjingDraftStore = ujingDraft

    /**
     * 洗衣完成提醒：**iOS 侧还没有实现，返回 null**。
     *
     * 升级条件：`UNUserNotificationCenter` 授权 + `UNTimeIntervalNotificationTrigger` 排闹钟，
     * `removePendingNotificationRequests` / `removeDeliveredNotifications` 对应
     * Android 的 cancel / revoke 两个动作。在那之前订单卡与 15 秒轮询照常，只是没有「洗好提醒」。
     */
    override fun ujingReminder(): UjingReminder? = null
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
        // Cookie 来源与视图生命周期无关（直接读 `WKWebsiteDataStore`），所以装一次就够，
        // 用户关掉导入页之后课表页的「检测」照样能读登录态。
        WebCookies.source = IosWebCookieSource
    }

    /** 首次打开时把作息表与默认学期写进库（与 Android 的 `ensureDefaults` 同步口径）。 */
    fun warmUp() {
        scope.launch {
            repository.ensureDefaults()
            // 顺手探一次「教务网页会话还在不在」：账号中心 / 教务账号页据此显示已登录，
            // 而不是因为读 Cookie 是异步就永远显示「未登录」（见 [hasJwWebSession]）
            if (session.hasCookies()) {
                IosJwSessionFlag.markUsable()
            } else {
                IosJwSessionFlag.markLost()
            }
        }
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

    private companion object {
        /** 日志 tag：崩溃/后台失败都记在这个名字下，排查时先 grep 它。 */
        const val TAG = "IosServices"
    }
}
