package edu.jxslu.schedule

import android.content.Context
import edu.jxslu.schedule.data.ai.AiScheduleClient
import edu.jxslu.schedule.data.kqcx.KqcxSessionCore
import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.power.PowerReadingStore
import edu.jxslu.schedule.data.power.PowerRepository
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.qiekj.BalanceData
import edu.jxslu.schedule.data.qiekj.QiekjRepository
import edu.jxslu.schedule.data.qiekj.QiekjWateringSession
import edu.jxslu.schedule.data.qiekj.QiekjWateringSessions
import edu.jxslu.schedule.data.qzxy.QzxyRepository
import kotlinx.coroutines.sync.Mutex
import edu.jxslu.schedule.data.qzxy.QzxyWateringRegistry
import edu.jxslu.schedule.data.qzxy.QzxyPrefs
import edu.jxslu.schedule.data.qzxy.QzxyLinkDiagnostics
import edu.jxslu.schedule.data.qzxy.QzxyDeviceScanner
import edu.jxslu.schedule.data.qzxy.QzxyDeviceRegistry
import edu.jxslu.schedule.data.qzxy.QzxyBluetoothLink
import edu.jxslu.schedule.data.qzxy.QzxyBalance
import edu.jxslu.schedule.data.repo.AiApplyHistoryStore
import edu.jxslu.schedule.data.repo.CampusCalendarStore
import edu.jxslu.schedule.data.repo.CurriculumRepository
import edu.jxslu.schedule.data.repo.CurriculumSync
import edu.jxslu.schedule.data.repo.DormRepairStore
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.HomeworkRepository
import edu.jxslu.schedule.data.repo.InboxRepository
import edu.jxslu.schedule.data.repo.InternshipBasesLoader
import edu.jxslu.schedule.data.repo.NoteRepository
import edu.jxslu.schedule.data.repo.ProfileSync
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.data.repo.SelectionRepository
import edu.jxslu.schedule.data.repo.SelectionSync
import edu.jxslu.schedule.data.repo.ScoreRepository
import edu.jxslu.schedule.data.repo.ScoreSync
import edu.jxslu.schedule.data.repo.ScholarProgressRepository
import edu.jxslu.schedule.data.repo.ScholarProgressSync
import edu.jxslu.schedule.data.repo.TeachingCalendarSync
import edu.jxslu.schedule.data.repo.TextbookSync
import edu.jxslu.schedule.data.session.CasEnsureResult
import edu.jxslu.schedule.data.session.LoginGateRules
import edu.jxslu.schedule.data.session.LoginTarget
import edu.jxslu.schedule.data.session.SessionExpirySink
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.data.session.WebViewCookieBridge
import edu.jxslu.schedule.data.ujing.UjingDraftStore
import edu.jxslu.schedule.data.ujing.UjingHistoryStore
import edu.jxslu.schedule.data.ujing.UjingHouseStore
import edu.jxslu.schedule.data.ujing.UjingRepository
import edu.jxslu.schedule.data.weather.WeatherRepository
import edu.jxslu.schedule.data.wenqu.WenquRepository
import edu.jxslu.schedule.data.ykt.YktCredentialStore
import edu.jxslu.schedule.data.ykt.YktRepository
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.platform.AccountCredentials
import edu.jxslu.schedule.platform.AiApiKeyStore
import edu.jxslu.schedule.platform.AppServices
import edu.jxslu.schedule.platform.HomeworkReminder
import edu.jxslu.schedule.platform.JwSessionLogout
import edu.jxslu.schedule.platform.ScheduleBackgroundFiles
import edu.jxslu.schedule.platform.UjingReminder
import edu.jxslu.schedule.ui.ebike.AndroidBikeHeading
import edu.jxslu.schedule.ui.ebike.AndroidBikeLocation
import edu.jxslu.schedule.ui.ebike.BikeLocationSource
import edu.jxslu.schedule.ui.reminder.BalanceAlertReminder
import edu.jxslu.schedule.ui.reminder.ClassReminder
import edu.jxslu.schedule.ui.reminder.UjingDoneReminder
import edu.jxslu.schedule.ui.widget.LifeWidgetSync
import edu.jxslu.schedule.ui.widget.WaterWidgetSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android 侧的共享服务装配（iOS 移植 M1，2026-11-25）。
 *
 * ## 为什么不直接让 `Graph` 实现 `AppServices`
 *
 * `Graph` 的每个 getter 都带 `context: Context` 参数（为了不动 276 处调用点）。接口方法无参，
 * 签名对不上。硬改 `Graph` 等于同时改那 276 处——一次改动把「装配」和「调用面」两件事混在一起，
 * 出问题时分不清是装配错了还是调用点漏改了。
 *
 * 所以这里只做**一层转接**：接口方法 → 带上构造期存好的 `applicationContext` → `Graph` 原方法。
 * `Graph` 本体一行不改。
 *
 * ## 存的是 `applicationContext`
 *
 * 不是 Activity：这层要被 ViewModel 的整个生命周期持有，拿 Activity 就是一次泄漏
 * （与 `platform/AppDirs.android.kt` 的 `initAppDirs` 同口径）。
 */
class GraphServices(private val context: Context) : AppServices {

    private val appContext: Context = context.applicationContext

    override fun displayPrefs(): DisplayPrefsStore = Graph.displayPrefs(appContext)

    override fun scheduleRepository(): ScheduleRepository = Graph.repository(appContext)

    override fun scoreRepository(): ScoreRepository = Graph.scoreRepository(appContext)

    override fun scheduleSync(): ScheduleSync = Graph.scheduleSync(appContext)

    override fun examSync(): ExamSync = Graph.examSync(appContext)

    override fun scoreSync(): ScoreSync = Graph.scoreSync(appContext)

    override fun scheduleTermImport(): ScheduleTermImport = Graph.scheduleTermImport(appContext)

    override fun profileSync(): ProfileSync = Graph.profileSync(appContext)

    override fun selectionRepository(): SelectionRepository = Graph.selectionRepository(appContext)

    override fun selectionSync(): SelectionSync = Graph.selectionSync(appContext)

    override fun noteRepository(): NoteRepository = Graph.noteRepository(appContext)

    override fun homeworkRepository(): HomeworkRepository = Graph.homeworkRepository(appContext)

    // 收件箱：页面搬进共享层后要它。转给 `Graph` 的同一份单例——收件箱是旁路留痕，
    // 页面读的与各 Sync 写的必须是同一个仓库实例，否则今日页角标与收件箱页会对不上。
    override fun inboxRepository(): InboxRepository = Graph.inboxRepository(appContext)

    // ---- 一卡通 / 寝室电费（S14） ----
    //
    // 两个仓库非空（纯 HTTP + 本地表），凭证存储非空（Android 有 EncryptedSharedPreferences）。
    // 全部转给 `Graph` 的现有单例——`yktRepository` / `powerRepository` 必须是同一份，
    // 否则「取码」与「流水」两条链会各持一个 token、各登一次。

    override fun yktRepository(): YktRepository = Graph.yktRepository(appContext)

    override fun yktCredentialStore(): YktCredentialStore = Graph.yktCredentialStore(appContext)

    override fun powerRepository(): PowerRepository = Graph.powerRepository(appContext)

    override fun powerReadingStore(): PowerReadingStore = Graph.powerReadingStore(appContext)

    // ---- 天气（S19） ----
    //
    // 转给 `Graph` 的同一份单例：今日页顶栏提示与天气详情页共用它（30 分钟 TTL 才有意义）。
    override fun weatherRepository(): WeatherRepository = Graph.weatherRepository(appContext)

    /** 与课表 / 成绩 / 收件箱同一个库实例（`juwDatabase(context)` 自带单例缓存）。 */
    override fun database(): JuwDatabase = juwDatabase(appContext)

    // ---- AI 调课（iOS 移植 S15 收尾，2026-11-25） ----
    //
    // 两件都是 `Graph` 的既有单例，必须转接同一份：
    // 客户端持有 Ktor 连接池（每次进页 new 一个等于重开池）；留痕按 id 对账
    // （两个实例会各写一份 JSON，撤销就撤不动对方写的记录）。
    // 图片存储与 API Key 存储**不在这里**：它们是页面侧的可空参数（`AiImageUi` / `AiApiKeys`），
    // 由 `SubpageActivity` 用 `Graph.aiImageStore` / `Graph.aiCredentialStore` 就地建。

    override fun aiScheduleClient(): AiScheduleClient = Graph.aiScheduleClient(appContext)

    override fun aiApplyHistoryStore(): AiApplyHistoryStore = Graph.aiApplyHistoryStore(appContext)

    // ---- 课表页背景图 / 主题换色（S17） ----
    //
    // 背景图只转接「删旧文件」与「文件还在不在」两件事：选图与解码压缩仍是 `:app` 的平台动作
    // （`ScheduleBackgroundStore.importUri`），文件名由平台动作回传给 ViewModel。
    // 写偏好与删旧文件的顺序由 ViewModel 保证，这里不掺和。

    private val backgroundFiles: ScheduleBackgroundFiles by lazy {
        val store = Graph.scheduleBackground(appContext)
        object : ScheduleBackgroundFiles {
            override suspend fun delete(fileName: String?) = store.delete(fileName)
            // 判据在 store 里（`fileFor` 过白名单 + `isFile`），这里只转接，不另写一份
            override fun exists(fileName: String): Boolean = store.exists(fileName)
        }
    }

    override fun scheduleBackgroundFiles(): ScheduleBackgroundFiles = backgroundFiles

    /**
     * 主题配色改完通知桌面小组件重渲染（`LifeWidgetSync` 零网络重跑快照）。
     *
     * 与搬迁前 `MeViewModel` 里那一行**行为一致**，只是从 ViewModel 挪到了装配根——
     * 共享层不该知道「桌面小组件」这种东西存在。
     */
    override suspend fun onThemePaletteChanged() {
        LifeWidgetSync.onThemePaletteChanged(appContext)
    }

    // ---- 设置首页（S17） ----

    /** 版本名来自 `:app` 的 `BuildConfig`——共享层看不见它，只能由装配根给。 */
    override fun appVersion(): String = BuildConfig.VERSION_NAME

    // ---- 胖乖生活 / 开水房（iOS 移植 T2，2026-11-25） ----
    //
    // 开水页搬进共享层后要的几件事。仓库与开水流程本身早在共享层，这里只是把
    // 「平台存储 + 桌面小组件」两样接到 `Graph` 的现有单例上。

    /**
     * 「正在出水」登记的共享层视图。
     *
     * `QiekjWateringStore` 自己留在 `:app`（介质是 `SharedPreferences`，换介质等于让老用户
     * 设备上正在出水的那条记录读不出来），这里把它整面转过去：写侧两个方法 + 读侧那条流。
     * 读侧 [QiekjWateringSessions.watering] 直接返回 store 自己的 `StateFlow`——共享层的今日页
     * 服务卡与侧栏的 `Graph.qiekjWatering(context).watering` 是**同一条流**，没有第二份真相源。
     */
    private val qiekjWateringSessions: QiekjWateringSessions by lazy {
        val store = Graph.qiekjWatering(appContext)
        object : QiekjWateringSessions {
            override val watering: StateFlow<QiekjWateringSession?> = store.watering

            override fun begin(startedAtMillis: Long, deviceName: String, goodsId: String?) {
                store.begin(
                    QiekjWateringSession(
                        startedAtMillis = startedAtMillis,
                        deviceName = deviceName,
                        goodsId = goodsId,
                    ),
                )
            }

            override fun clear() = store.clear()
        }
    }

    override fun qiekjWateringSessions(): QiekjWateringSessions = qiekjWateringSessions

    /** 桌面胖乖开水卡：清登录态后切回未登录形态。 */
    override suspend fun refreshQiekjWaterWidget() {
        WaterWidgetSync.refreshQiekjWidget(appContext)
    }

    /** 桌面胖乖开水卡：把刚取到的余额推过去（零额外请求）。 */
    override suspend fun pushQiekjWaterBalance(balance: BalanceData) {
        WaterWidgetSync.pushQiekjBalance(appContext, balance)
    }

    /** 「猜你想用」：与搬迁前 `WaterViewModel` 里那一行同一实现（进程作用域 fire-and-forget）。 */
    override fun recordGuessOrder(target: GuessTarget) = Graph.recordGuessOrder(target)

    // ---- 账号与登录（账号中心页，iOS 移植 S20，2026-11-25） ----
    //
    // 全部转接 `Graph` 的现有单例：账号中心显示的登录态与各功能页判定的登录态
    // 必须是同一份事实，各建一份就会出现「这页说已登录、那页说未登录」。
    // 五个第三方仓库同理——退出登录必须让功能页的流立刻翻成未登录。

    /**
     * 胖乖仓库：与今日页 / 侧栏的 `Graph.qiekj(context)` **同一个实例**。
     *
     * 必须是同一份：登录态、订单快照、开水流程的 Mutex 都在仓库里，
     * 两个实例会让「开水页登录了、今日页卡片还是未登录」。
     */
    override fun qiekjRepository(): QiekjRepository = Graph.qiekj(appContext)

    override fun qzxyRepository(): QzxyRepository = Graph.qzxy(appContext)

    override fun ujingRepository(): UjingRepository = Graph.ujing(appContext)

    /**
     * 快趣会话本体（S23 起直接给 `KqcxSessionCore`）。
     *
     * 上一版这里包了个只含「登录态 + 退出」的匿名对象，原因是 `KqcxSessionRepository`
     * 把 core 包成私有字段、装配根拿不到本体（见 `AppServices.kqcxSession` 的 KDoc）。
     * 现在 core 由 `Graph.kqcxCore` 装配，这一层不再需要转接：账号中心读 `loggedIn` /
     * 调 `logout()` 照旧，而 iOS 侧要用到的那一面（骑行状态、开锁还车、订单、卡券）
     * 也终于能从装配根取到。
     */
    override fun kqcxSession(): KqcxSessionCore = Graph.kqcxCore(appContext)

    override fun wenquRepository(): WenquRepository = Graph.wenqu(appContext)

    /**
     * 凭证快照与登录闸门：`CredentialVault` 是 `EncryptedSharedPreferences` + `MasterKey`，
     * 只能在 Android 侧，这里把它收窄成「读学号 / 清一卡通 / 重置闸门 / 验存教务密码」几件事。
     *
     * 密码不出这个适配器——`readJwUsername` 只给学号；写入（[AccountCredentials.saveJw]）
     * 由页面经 `CasLoginDialog` 走「先验后存」，与引导第 2 步同一条链。
     */
    private val accountCredentials: AccountCredentials by lazy {
        val vault = Graph.credentialVault(appContext)
        object : AccountCredentials {
            override fun readJwUsername(): String? = vault.readCas()?.username

            override fun readYktUsername(): String? = vault.readYkt()?.username

            override fun clearYkt() = vault.clearYkt()

            /** 与搬迁前那两行 `writeGate(target, LoginGateRules.reset())` 逐字等价。 */
            override fun resetLoginGates() {
                vault.writeGate(LoginTarget.Ykt, LoginGateRules.reset())
                vault.writeGate(LoginTarget.Qiekj, LoginGateRules.reset())
            }

            /**
             * 先清闸门再试登（与 `AndroidOnboardingJwLogin.verify` 逐字同款）：
             * 用户重新输入等于重新开始计数，不清的话上一次的失败次数会把刚敲对的密码顶掉。
             */
            override suspend fun verifyJw(username: String, password: String): CasEnsureResult {
                val cas = Graph.casSession(appContext)
                cas.onCredentialsUpdated()
                // 取消异常原样抛：弹层被关掉时这条链不该在后台跑完再落一次库
                return cas.tryLogin(username, password)
            }

            override fun saveJw(username: String, password: String) = vault.saveCas(username, password)
        }
    }

    override fun accountCredentials(): AccountCredentials = accountCredentials

    /** 与仓库层写的「已过期」标记是同一份（`Graph.sessionExpirySink` 已缓存单例）。 */
    override fun sessionExpirySink(): SessionExpirySink = Graph.sessionExpirySink(appContext)

    /** 退出教务 = `CasSession.logout()`（清 OkHttp cookie + 凭证 + WebView 会话）。 */
    override fun jwSessionLogout(): JwSessionLogout =
        JwSessionLogout { Graph.casSession(appContext).logout() }

    override fun aiApiKeyStore(): AiApiKeyStore {
        val store = Graph.aiCredentialStore(appContext)
        return object : AiApiKeyStore {
            override fun readApiKey(): String? = store.readApiKey()

            override fun clear() = store.clear()
        }
    }

    /**
     * 进页消费「登录状态已过期」标记。
     *
     * `SessionService` 枚举现在也在共享层了，但页面只认字符串 id，这一层保持字符串入口。
     * 对不上直接抛，不静默丢标记（与 `AndroidSessionExpirySink` 同一纪律）。
     */
    override fun consumeSessionExpiry(serviceId: String): Boolean {
        val service = SessionService.entries.firstOrNull { it.id == serviceId }
            ?: error("未知的会话服务 id: $serviceId")
        return Graph.sessionExpiry(appContext).consume(service)
    }

    /** 与崩溃上报共用的应用级作用域（`Graph.appScope`），清凭证要跑完。 */
    override fun appScope(): CoroutineScope = Graph.appScope

    /**
     * 只读 `CookieManager`，不发请求（`WebViewCookieBridge.hasAnyCookie` 的原实现）。
     *
     * 必须在有 Looper 的线程调用——页面在组合期读它，正是主线程。
     */
    override fun hasJwWebSession(): Boolean = WebViewCookieBridge.hasAnyCookie()
    // ---- U净（S21） ----
    //
    // 四个仓库 / 存储全部转给 `Graph` 的现有单例：主页的订单卡、下单页的草稿、历史账单页
    // 读的必须是对方写的同一份（各自 new 一个等于「扫了码、下单页拿到 null」）。
    // 支付不在这里——`PayTask` 只能在 Activity 上拉起，见 `UjingPay` 的 KDoc。


    override fun ujingHouseStore(): UjingHouseStore = Graph.ujingHouses(appContext)

    override fun ujingHistoryStore(): UjingHistoryStore = Graph.ujingHistory(appContext)

    override fun ujingDraftStore(): UjingDraftStore = Graph.ujingDraft(appContext)

    /**
     * 洗衣完成提醒：转接 `ui/reminder/UjingDoneReminder`（`AlarmManager` + 通知渠道）。
     *
     * 那个文件**故意留在 `:app`**：它直调 `AlarmManager` / `NotificationCompat`，
     * 共享层拿不到；搬过去只会得到一份 expect/actual 的空壳。三个动作与共享层的
     * [UjingReminder] 逐字对应，语义（精确闹钟 / 兜底闹钟 / 撤已发通知）在那边。
     */
    private val ujingReminderSink: UjingReminder = object : UjingReminder {
        override fun schedule(orderId: String, triggerAtMillis: Long) {
            UjingDoneReminder.schedule(appContext, orderId, triggerAtMillis)
        }

        override fun cancel() {
            UjingDoneReminder.cancel(appContext)
        }

        override fun revoke() {
            UjingDoneReminder.revoke(appContext)
        }
    }

    override fun ujingReminder(): UjingReminder = ujingReminderSink

    // ---- 学业完成情况 / 培养方案（iOS 移植 V4，2026-11-25） ----
    //
    // 四个都转给 `Graph` 的现有单例。同步那两个**必须**是同一份：它们内部的 Mutex 是
    // 「单飞」的全部依据，各 new 一个等于引导页与页面刷新同时各抓一轮教务。

    override fun scholarProgressRepository(): ScholarProgressRepository =
        Graph.scholarProgressRepository(appContext)

    override fun scholarProgressSync(): ScholarProgressSync = Graph.scholarProgressSync(appContext)

    override fun curriculumRepository(): CurriculumRepository = Graph.curriculumRepository(appContext)

    override fun curriculumSync(): CurriculumSync = Graph.curriculumSync(appContext)

    // ---- 校园卡（iOS 移植 V2，2026-11-25） ----
    //
    // 两个**平台副作用**，转给 `:app` 现有的实现。两者都留在原地：
    // 前者是 Glance（`androidx.glance` 只有 Android 变体），后者是 `AlarmManager` +
    // 通知渠道；搬过去只会得到一份 expect/actual 的空壳。

    override suspend fun pushCampusBalance(cardFen: Long) {
        LifeWidgetSync.pushCampusBalance(appContext, cardFen)
    }

    /** 付款已经成功，提醒评估失败不该让「支付成功」的收尾翻车（与搬迁前同样吞错）。 */
    override suspend fun evaluateYktBalance(cardFen: Long) {
        BalanceAlertReminder.evaluateYktBalance(appContext, cardFen)
    }

    // ---- 宿舍报修（iOS 移植 V1/V2，2026-11-25） ----

    /**
     * 报修本机快照：转给 `Graph.dormRepairStore` 的同一份单例。
     *
     * 必须是同一份：同步链（`DormRepairSync`）写它、今日页卡片与详情页读它，
     * 各建一个就会出现「同步成功了、页面还显示没有报修」。
     */
    override fun dormRepairStore(): DormRepairStore = Graph.dormRepairStore(appContext)

    // ---- 趣智校园页面层（iOS 移植 V3，2026-11-25） ----
    //
    // 六条缝的 Android 实现全在 `QzxyPlatform.kt`（同包）：那里只做「转发 + 换类型」，
    // `SharedPreferences` 的文件名与键一个不改，`QzxyGattLink` 的链路语义一行不动。
    // 桌面趣智开水卡的两个副作用转接 `ui/widget/WaterWidgetSync`（Glance 是 Android 专有，
    // 那个文件留在 `:app`）。

    override fun qzxyBluetoothLink(): QzxyBluetoothLink = Graph.qzxyLink(appContext)

    override fun qzxyLinkDiagnostics(): QzxyLinkDiagnostics =
        AppQzxyLinkDiagnostics(Graph.qzxyLink(appContext))

    override fun qzxyScanner(): QzxyDeviceScanner = QzxyPlatform.scanner(appContext)

    override fun qzxyDeviceRegistry(): QzxyDeviceRegistry = QzxyPlatform.deviceRegistry(appContext)

    override fun qzxyPrefs(): QzxyPrefs = QzxyPlatform.prefs(appContext)

    override fun qzxyWateringRegistry(): QzxyWateringRegistry =
        QzxyPlatform.wateringRegistry(appContext)

    /** 进程级同一份：今日页面板、趣智页、诊断页共用同一条 GATT 链路，锁必须也共用。 */
    override fun qzxyFlowLock(): Mutex = Graph.qzxyFlowLock

    override suspend fun refreshQzxyWaterWidget() {
        runCatching { WaterWidgetSync.refreshQzxyWidget(appContext) }
    }

    override suspend fun pushQzxyAccount(balance: QzxyBalance) {
        runCatching { WaterWidgetSync.pushQzxyAccount(appContext, balance) }
    }

    // ---- 校历 / 实习基地（iOS 移植 W2，2026-11-25） ----
    //
    // 两个都转给 `Graph` 的现有单例。**必须同一份**：校历页读 `state` 流、`refresh` 改同一份
    // 状态；实习基地装载器内部的 Mutex 是「单飞」的全部依据，各 new 一个等于两个入口同时各抓一轮。

    override fun campusCalendarStore(): CampusCalendarStore = Graph.campusCalendar(appContext)

    override fun internshipBasesLoader(): InternshipBasesLoader =
        Graph.internshipBasesLoader(appContext)

    // ---- 教材（iOS 移植 W3，2026-11-25） ----

    /**
     * 教材抓取：转接 `Graph.textbookSync` 的同一份单例。
     *
     * 必须是同一份：`JwImportScreen`（导入写库成功后顺带抓）、`JuwApplication`（冷启动补抓）
     * 与教材页的「重新导入」三处共用它，`TextbookSync` 内部的 Mutex 是「一次只抓一轮」的
     * 全部依据。各 new 一个等于导入那一次与页面这次同时各打一遍教务。
     */
    override fun textbookSync(): TextbookSync = Graph.textbookSync(appContext)

    // ---- 首启引导（iOS 移植 S22，2026-11-25） ----

    /** 开学日校准：转接 `Graph.teachingCalendarSync` 的同一份单例（Mutex 即「一次一张表」的依据）。 */
    override fun teachingCalendarSync(): TeachingCalendarSync =
        Graph.teachingCalendarSync(appContext)

    // ---- 快趣出行页面层（iOS 移植 P4，2026-11-25） ----
    //
    // 两条传感器缝。**定位源必须是进程内同一份**：骑行页的连续定位流与一次性定位
    // 共用同一个 `LocationManager`（`AndroidBikeLocation` 自己缓存它），各建一个等于
    // 同时向系统注册两套监听。
    //
    // 罗盘每次订阅都会新建一条 `callbackFlow`（订阅即注册、取消即注销），
    // 但它不持有除 `applicationContext` 以外的状态，所以每次现建；
    // 反过来持有一个长命实例没有任何好处——`SensorManager` 的监听生命周期本来就是订阅级的。

    private val bikeLocation: BikeLocationSource by lazy { AndroidBikeLocation(appContext) }

    override fun bikeLocation(): BikeLocationSource = bikeLocation

    override fun bikeHeading(): Flow<Float> = AndroidBikeHeading(appContext).heading()

    // ---- 作业截止提醒（iOS 移植 N2，2026-11-25） ----

    /**
     * 作业截止提醒：转接 `ui/reminder/ClassReminder.enqueueCheck`（一次性 `AlarmManager` 闹钟
     * + 15 分钟兜底核对，与上课提醒共用最近的那一枚闹钟）。
     *
     * **与 `SubpageActivity` 四处作业页的 `onHomeworkChanged` 指向同一个函数**，所以这里不是
     * 第二套实现：两处都只是「喊一声重排」，时刻算法与发通知全在 `ClassReminder` 里。
     * 那一份直调 `AlarmManager` / `NotificationCompat`，故意留在 `:app`（搬进共享层只会得到
     * 一个 expect/actual 空壳），共享层只持有这个接口。
     */
    private val homeworkReminderSink = HomeworkReminder { ClassReminder.enqueueCheck(appContext) }

    override fun homeworkReminder(): HomeworkReminder = homeworkReminderSink
}
