package edu.jxslu.schedule

import edu.jxslu.schedule.data.ai.AiScheduleClient
import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.power.PowerClient
import edu.jxslu.schedule.data.power.PowerReadingStore
import edu.jxslu.schedule.data.power.PowerRepository
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.prefs.recordGuessOrder
import edu.jxslu.schedule.data.qiekj.IosQiekjExpiryMarker
import edu.jxslu.schedule.data.qiekj.IosQiekjOrderStore
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
import edu.jxslu.schedule.data.session.IosJwReadSession
import edu.jxslu.schedule.data.session.IosAiApiKeyStore
import edu.jxslu.schedule.data.session.IosQiekjCredentialStore
import edu.jxslu.schedule.data.session.IosSessionExpirySink
import edu.jxslu.schedule.data.session.IosUjingSessionStore
import edu.jxslu.schedule.data.session.IosWenquSessionStore
import edu.jxslu.schedule.data.session.IosWebCookieSource
import edu.jxslu.schedule.data.session.JwReadSession
import edu.jxslu.schedule.data.session.SessionExpirySink
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
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.platform.AccountCredentials
import edu.jxslu.schedule.platform.AiApiKeyStore
import edu.jxslu.schedule.platform.AppServices
import edu.jxslu.schedule.platform.JwSessionLogout
import edu.jxslu.schedule.platform.ScheduleBackgroundFiles
import edu.jxslu.schedule.platform.ThirdPartySession
import edu.jxslu.schedule.platform.UjingReminder
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

    // ---- 账号与登录（账号中心页，iOS 移植 S20，2026-11-25） ----
    //
    // 这一组只有「已过期留痕」与「应用级作用域」两端都能给；其余按能力缺口返回 null，
    // 账号中心页据此整行 / 整块不渲染（`design/ios-port.md` §11.2 ④）。

    /**
     * 教务 / 一卡通的凭证快照：**iOS 侧返回 null**。
     *
     * 一卡通那份已经有 Keychain 实现（`IosSessionStores`），但这里还要「教务 CAS 的学号」
     * 与「登录闸门状态」两样，它们都在 `:app` 的 `CredentialVault` 里。等 iOS 侧补齐
     * 闸门落盘（`appDataDir()` 下的 JSON）再给实现——两者要一起做，否则账号中心说
     * 「已配置」、功能页却说「没登录」。在那之前账号中心的两行按「未配置」显示。
     */
    override fun accountCredentials(): AccountCredentials? = null

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

    /** 快趣：**iOS 侧返回 null**，理由同上。 */
    override fun kqcxSession(): ThirdPartySession? = null

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
     * WebView 里有没有有效教务会话：**iOS 侧返回 null（判断不了）**。
     *
     * `WKWebsiteDataStore.getAllCookies` 是异步回调（见 `IosWebCookieSource`），而这里要在
     * 组合期同步给一个布尔。升级条件：给 `IosWebCookieSource` 加一份进程内缓存
     * （登录页关闭时刷新），或把本方法改成 suspend 让页面在 `LaunchedEffect` 里读。
     * 在那之前账号中心的教务行只按「凭证在不在」判——没凭证就显示未登录。
     */
    override fun hasJwWebSession(): Boolean? = null
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
        scope.launch { repository.ensureDefaults() }
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
}
