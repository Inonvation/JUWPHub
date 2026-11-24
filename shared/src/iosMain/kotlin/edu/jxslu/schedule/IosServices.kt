package edu.jxslu.schedule

import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.power.PowerClient
import edu.jxslu.schedule.data.power.PowerReadingStore
import edu.jxslu.schedule.data.power.PowerRepository
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.prefs.recordGuessOrder
import edu.jxslu.schedule.data.qiekj.QiekjRepository
import edu.jxslu.schedule.data.qiekj.QiekjWateringSessions
import edu.jxslu.schedule.data.repo.ExamSnapshotStore
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.HomeworkRepository
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
import edu.jxslu.schedule.data.session.IosJwReadSession
import edu.jxslu.schedule.data.session.IosWebCookieSource
import edu.jxslu.schedule.data.session.JwReadSession
import edu.jxslu.schedule.data.session.WebCookies
import edu.jxslu.schedule.data.weather.WeatherRepository
import edu.jxslu.schedule.data.ykt.YktClient
import edu.jxslu.schedule.data.ykt.YktCredentialStore
import edu.jxslu.schedule.data.ykt.YktRepository
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.platform.AppServices
import edu.jxslu.schedule.platform.ScheduleBackgroundFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle

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
    // 但**都不带落盘 token、也不带「失效标记」**——那两样各要一条平台能力（见下）。

    /**
     * 一卡通仓库。
     *
     * `tokenCache = null`：落盘 token 只能进 Android 的 `EncryptedSharedPreferences`，
     * iOS 侧要等 Keychain（同 [yktCredentialStore] 的升级条件）。缺了它只是每次冷启动
     * 多登一次（取安全键盘 + 换 token 两条请求），对外行为不变。
     *
     * `suspension = null`：iOS 还没有「我的」页账户卡，没有地方显示「登录状态已失效」。
     * 失效判定本身仍由服务端 401 驱动。
     */
    private val yktRepo: YktRepository by lazy {
        YktRepository(YktClient.create(), tokenCache = null, suspension = null)
    }

    /** 电表读数本机记录（与课表共用一个 Room）。 */
    private val powerReadings: PowerReadingStore by lazy {
        PowerReadingStore(juwDatabase().powerReadingDao())
    }

    /** 电费仓库：读数落本地表，协议留痕 release/debug 都不写。 */
    private val powerRepo: PowerRepository by lazy {
        PowerRepository(PowerClient.create(), powerReadings, debugDump = null, suspension = null)
    }

    override fun yktRepository(): YktRepository = yktRepo

    /**
     * 校园卡凭证存储：**iOS 侧还没有实现，返回 null**。
     *
     * 升级条件（照 `data/ykt/YktCredentialVault.kt` 的约定）：用 Keychain 实现
     * `YktCredentialVault`——`kSecClassGenericPassword`，service 取包名、
     * account 沿用 Android 的 `username` / `password` 两个键名，
     * `kSecAttrAccessibleAfterFirstUnlock`（后台刷新要用，不能是 `WhenUnlocked`），
     * `clear()` 里连 token 项一起删。在那之前**不要**退回 `NSUserDefaults`：
     * 明文存密码比不存更糟，而三页的「开启校园卡」入口在 null 时会整块隐藏。
     */
    override fun yktCredentialStore(): YktCredentialStore? = null

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

    /**
     * 胖乖仓库：**iOS 侧还没有实现，返回 null**。
     *
     * `QiekjRepository` 本身是纯编排，但构造要三份平台存储：凭证（Android 是
     * `EncryptedSharedPreferences`）、订单快照（普通 `SharedPreferences`，换介质等于让老用户
     * 丢掉已有的 50 笔本地账单）、过期标记。iOS 侧一件都还没有。
     *
     * 升级条件：用 Keychain 实现 `QiekjCredentialStore`（`kSecClassGenericPassword`，
     * 与 [yktCredentialStore] 同一套口径），订单快照与过期标记各给一份
     * `appDataDir()` 下的 JSON / 偏好实现，然后把 `QiekjRepository(...)` 建在这里。
     * **在那之前不要**退回 `NSUserDefaults` 存 token：明文存凭证比不存更糟。
     *
     * 后果是开水页在 iOS 上没有入口（`IosPage` 里也没有这一项）——不是「点了没反应」。
     */
    override fun qiekjRepository(): QiekjRepository? = null

    /**
     * 「正在出水」登记：**iOS 侧是不落地的实现**。
     *
     * 这条状态唯一的读者是今日页服务卡与侧栏（都在 `:app`），iOS 侧两者都还没移植，
     * 所以登记了也没有人看。等 iOS 接上 [qiekjRepository] 与今日页时，这里换成
     * `appDataDir()` 下的 JSON 落盘实现（与 Android 的 `QiekjWateringStore` 同口径：
     * 5 分钟过期、165 秒后不再显示「出水中」）。
     */
    private val qiekjWatering: QiekjWateringSessions = object : QiekjWateringSessions {
        override fun begin(startedAtMillis: Long, deviceName: String, goodsId: String?) = Unit

        override fun clear() = Unit
    }

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
