package edu.jxslu.schedule

import android.content.Context
import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.power.PowerReadingStore
import edu.jxslu.schedule.data.power.PowerRepository
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.qiekj.BalanceData
import edu.jxslu.schedule.data.qiekj.QiekjRepository
import edu.jxslu.schedule.data.qiekj.QiekjWateringSession
import edu.jxslu.schedule.data.qiekj.QiekjWateringSessions
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.HomeworkRepository
import edu.jxslu.schedule.data.repo.InboxRepository
import edu.jxslu.schedule.data.repo.NoteRepository
import edu.jxslu.schedule.data.repo.ProfileSync
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.data.repo.SelectionRepository
import edu.jxslu.schedule.data.repo.SelectionSync
import edu.jxslu.schedule.data.repo.ScoreRepository
import edu.jxslu.schedule.data.repo.ScoreSync
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.data.weather.WeatherRepository
import edu.jxslu.schedule.data.ykt.YktCredentialStore
import edu.jxslu.schedule.data.ykt.YktRepository
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.platform.AppServices
import edu.jxslu.schedule.platform.ScheduleBackgroundFiles
import edu.jxslu.schedule.ui.widget.LifeWidgetSync
import edu.jxslu.schedule.ui.widget.WaterWidgetSync

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

    /** 共享层与 `:app` 拿到的是**同一个**库实例（`juwDatabase(context)` 自带单例缓存）。 */
    override fun database(): JuwDatabase = juwDatabase(appContext)

    // ---- 课表页背景图 / 主题换色（S17） ----
    //
    // 背景图只转接「删旧文件」这一件事：选图与解码压缩仍是 `:app` 的平台动作
    // （`ScheduleBackgroundStore.importUri`），文件名由平台动作回传给 ViewModel。
    // 写偏好与删旧文件的顺序由 ViewModel 保证，这里不掺和。

    private val backgroundFiles: ScheduleBackgroundFiles by lazy {
        val store = Graph.scheduleBackground(appContext)
        object : ScheduleBackgroundFiles {
            override suspend fun delete(fileName: String?) = store.delete(fileName)
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
    // 开水页搬进共享层后要的五件事。仓库与开水流程本身早在共享层，这里只是把
    // 「平台存储 + 桌面小组件」两样接到 `Graph` 的现有单例上。

    /**
     * 胖乖仓库：与今日页 / 侧栏的 `Graph.qiekj(context)` **同一个实例**。
     *
     * 必须是同一份：登录态、订单快照、开水流程的 Mutex 都在仓库里，
     * 两个实例会让「开水页登录了、今日页卡片还是未登录」。
     */
    override fun qiekjRepository(): QiekjRepository = Graph.qiekj(appContext)

    /**
     * 「正在出水」登记的共享层视图。
     *
     * `QiekjWateringStore` 自己留在 `:app`（它同时被今日页服务卡与侧栏读，`watering` 流
     * 只在那边用得上）；这里只把开水页需要的两个写方法转过去。读侧仍走
     * `Graph.qiekjWatering(context).watering`，两条路指向同一个 store 实例。
     */
    private val qiekjWateringSessions: QiekjWateringSessions by lazy {
        val store = Graph.qiekjWatering(appContext)
        object : QiekjWateringSessions {
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

    /**
     * 进页消费「登录状态已过期」标记。
     *
     * id → 枚举的映射放在这里而不是共享层：`SessionService` 枚举还在 `:app`
     * （`SessionExpiryStore.kt`），共享层只认字符串 id。对不上直接抛，
     * 不静默丢标记（与 `AndroidSessionExpirySink` 同一纪律）。
     */
    override fun consumeSessionExpiry(serviceId: String): Boolean {
        val service = SessionService.entries.firstOrNull { it.id == serviceId }
            ?: error("未知的会话服务 id: $serviceId")
        return Graph.sessionExpiry(appContext).consume(service)
    }
}
