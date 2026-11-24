package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.power.PowerReadingStore
import edu.jxslu.schedule.data.power.PowerRepository
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
import edu.jxslu.schedule.data.ujing.UjingDraftStore
import edu.jxslu.schedule.data.ujing.UjingHistoryStore
import edu.jxslu.schedule.data.ujing.UjingHouseStore
import edu.jxslu.schedule.data.ujing.UjingRepository
import edu.jxslu.schedule.data.weather.WeatherRepository
import edu.jxslu.schedule.data.ykt.YktCredentialStore
import edu.jxslu.schedule.data.ykt.YktRepository

/**
 * 共享层向「各平台装配根」要服务的唯一入口（iOS 移植 M1，2026-11-25）。
 *
 * ## 为什么需要它
 *
 * 搬进 `shared/commonMain` 的 ViewModel 原来靠 `Graph.repository(context)` 拿依赖，
 * 而 `Graph` 在 `:app`、方法都以 `Context` 为参——共享层两头都看不见。
 * 把这个反向依赖收成「一个接口 + 一个进程级持有」，共享层就能自己装配。
 *
 * ## 形状为什么是「按需增长的窄接口」
 *
 * 没有一次写满 `Graph` 的一百来个 getter：搬一个 ViewModel 就加它真正用到的那几个。
 * 每加一个方法，两边装配根各补一行——**多出来的方法没有调用方就不许加**，
 * 否则两条装配根会各写一半、拼起来才发现对不上。
 *
 * ## 哪个平台装
 *
 * | 平台 | 装配根 | 落在哪 |
 * |---|---|---|
 * | Android | `edu.jxslu.schedule.GraphServices`（包一层现有 `Graph`，不动 `Graph` 本体） | `JuwApplication.onCreate` |
 * | iOS | `IosServices` | `MainViewController` 建根之前 |
 *
 * 与 `ShareSettings` / `HapticsSettings` / `initAppDirs` 同一套注入纪律：**进程级可变持有 +
 * 启动时注入**，不用 CompositionLocal（服务不需要触发重组，而 iOS 的入口在 Swift 侧）。
 */
interface AppServices {

    /** 显示偏好。DataStore 的文件位置由 [appDataDir] 决定，两端已一致。 */
    fun displayPrefs(): DisplayPrefsStore

    /** 课表仓库（Room）。 */
    fun scheduleRepository(): ScheduleRepository

    /**
     * 成绩仓库（Room，与课表共用一个库）。
     *
     * 非空：成绩是本地表，哪一端都能读写，不存在「没有这个能力」的情况——
     * 成绩页与成绩设置页都直接读它（iOS 移植 S10）。
     */
    fun scoreRepository(): ScoreRepository

    /** 考试安排同步；null 的语义同 [scheduleSync]。 */
    fun examSync(): ExamSync?

    /**
     * 成绩自动导入与变动检测；null 的语义同 [scheduleSync]。
     *
     * 页面不直接消费它（成绩页读的是 [scoreRepository]），这里是给「设置页改完提醒后
     * 立刻按新规则核对一次」那条链路留的口子（Android 侧由 `ScoreAlertReminder` 现在占着，
     * iOS 侧接后台核对时用）。
     */
    fun scoreSync(): ScoreSync?

    /**
     * 课表直取与变动检测；**null = 本平台还没接教务直取链**。
     *
     * 返回 null 而不是抛：课表页的「快速检测」按钮据此隐藏，而不是点下去才发现不支持。
     * iOS 侧要等 WebView 会话 + `JwReadSession` 的 Ktor 实现（M2）才拿得到它。
     */
    fun scheduleSync(): ScheduleSync?

    /** 学期归档 / 新学期探测；null 的语义同 [scheduleSync]。 */
    fun scheduleTermImport(): ScheduleTermImport?

    /**
     * 学籍卡补抓（姓名 / 班级 / 院系 / 专业 / 学制，落 [DisplayPrefsStore]）。
     *
     * **非空**：它只要「一条教务会话 + 偏好存储」，两端都建得起来——Android 是 `CasSession`
     * （OkHttp + 凭证保险箱），iOS 是读 `WKWebView` Cookie 的 `IosJwReadSession`。
     * iOS 侧没登录时 `ensureValid` 返回 `NeedsManualLogin`、`syncOnce` 直接给 false，
     * 页面据此提示「请先在教务导入里登录一次」——那是真实结论，不是假实现。
     *
     * 调用方是学籍信息页（`ui/jw/StudentInfoScreen`）：进页补一次、点刷新强抓一次。
     */
    fun profileSync(): ProfileSync

    // ---- 本地表仓库：非空 ----
    //
    // 下面四个都是**本地表**（Room 或文件），哪一端都能读写，不存在「没有这个能力」。
    // 它们的类型早就在 commonMain，所以先放进来；其余业务仓库（一卡通 / 电费 / 问渠 /
    // U净 / AI）的类型还在 `:app`，等各自那条流把数据层搬过来之后再加，
    // **不要**为了「先占位」把一个 `:app` 的类型引进本接口。

    /** 选课记录仓库（DESIGN §4.35）。 */
    fun selectionRepository(): SelectionRepository

    /** 选课轮次与记录抓取；null 的语义同 [scheduleSync]。 */
    fun selectionSync(): SelectionSync?

    /** 笔记仓库（DESIGN §3.11）。 */
    fun noteRepository(): NoteRepository

    /** 作业仓库（DESIGN §3.11）。 */
    fun homeworkRepository(): HomeworkRepository

    /**
     * 收件箱仓库（DESIGN §4.38）。
     *
     * **非空**：`inbox_messages` 是单表 Room，写入方全是各业务 Sync、读取方是收件箱页
     * 与今日页的收件箱卡，哪一端都建得起来。类型早就在 commonMain
     * （`data/repo/InboxRepository`），此前缺的只是这个入口。
     */
    fun inboxRepository(): InboxRepository

    // ---- 一卡通 / 寝室电费（iOS 移植 S14，2026-11-25） ----
    //
    // 四个成员按「有没有平台能力」分两类：纯 HTTP + 内存缓存与本地表都返回非空，
    // 只有要保险箱（Android 的 EncryptedSharedPreferences）的那一个可空。
    // 上面那条「类型还在 :app 的仓库等搬过来再加」的纪律到此为止——这两块的数据层
    // 已经在 commonMain 了。

    /**
     * 一卡通仓库（登录 / 付款码 / 余额 / 流水 / 充值）。
     *
     * **非空**：它只要一个 Ktor client 与内存里的 token，两端都建得起来。
     * 落盘 token（`YktTokenCache`）与「失效标记」（`YktSuspension`）都是可选加速，
     * 缺了只是每次冷启动多登一次、状态卡不显示「已失效」。
     */
    fun yktRepository(): YktRepository

    /**
     * 校园卡凭证存储；**null = 本平台还没有加密凭证存储**。
     *
     * 返回 null 而不是抛：付款码 / 流水 / 设置三页据此整块隐藏「开启校园卡」入口，
     * 而不是点下去才发现存不住密码。Android 侧是 `EncryptedSharedPreferences`
     * （`CredentialVault`）；iOS 要等 Keychain 实现（`kSecClassGenericPassword`），
     * 升级条件写在 `IosServices` 的 KDoc 里。
     */
    fun yktCredentialStore(): YktCredentialStore?

    /**
     * 缴费平台（寝室电费）仓库。
     *
     * **非空**：凭证由调用方传进来（与一卡通同一份学号 + 查询密码），仓库自己只要
     * Ktor client + 本地读数表，两端都能建。
     */
    fun powerRepository(): PowerRepository

    /** 电表读数本机记录（DESIGN §3.13「用电统计」）：本地表，两端都能读写。 */
    fun powerReadingStore(): PowerReadingStore

    // ---- 天气（iOS 移植 S19，2026-11-25） ----

    /**
     * 天气仓库（DESIGN §3.29）。
     *
     * **非空**：纯 Ktor（Open-Meteo）+ 进程内 TTL 缓存，不碰凭证、不落盘，两端都建得起来。
     * 今日页顶栏那行提示与天气详情页共用这一份——必须是同一个实例，否则两个页面各取一次数，
     * 30 分钟 TTL 等于白设（免费档按请求数算）。
     */
    fun weatherRepository(): WeatherRepository

    /**
     * 应用唯一的 Room 库（`JuwDatabase`，v21）。
     *
     * 为什么需要：消费流水页的 ViewModel 直接读 DAO 的响应式流
     * （`observeMonth` / `observeMonthSummary` / `observeMonthByType` / `observeCount`），
     * 并把库交给 `YktTurnoverSyncer` 落库——这两件事都没法包在某个仓库后面，
     * 包一层等于把同一个 DAO 再抄一遍。
     *
     * **非空**：Room 是本地表，两端都建得起来（Android 走 `juwDatabase(context)`，
     * iOS 走 `BundledSQLiteDriver` 的同一个库）。共享层拿到的必须是**同一份实例**，
     * 否则两个库文件各写一半，页面看不到对方写进去的行。
     */
    fun database(): JuwDatabase

    // ---- 课表页背景图 / 主题换色（iOS 移植 S17，2026-11-25） ----
    //
    // 这两个是「我的」与显示设置搬进共享层带出来的。一个需要平台图像能力（可空），
    // 一个只是通知（iOS 空实现即可）——按同一把尺子分开写，别为了对称都做成可空。

    /**
     * 课表页背景图的文件删除（DESIGN §4.21）。
     *
     * **null = 本平台没接背景图落盘**：iOS 侧还没有 `UIImage` 的解码压缩实现，
     * 显示设置面板里的「背景」整组据此不渲染。Android 侧转接 `Graph.scheduleBackground`。
     */
    fun scheduleBackgroundFiles(): ScheduleBackgroundFiles?

    /**
     * 主题配色改动后的重渲染通知（Android 是桌面小组件的胶囊色）。
     *
     * **给空实现而不是可空**：这不是用户可见的入口，而是一条「改完通知一声」的副作用。
     * iOS 没有桌面小组件（WidgetKit 那条要单独排，见 `design/ios-port.md` §1.4），
     * 所以默认实现就是什么都不做——调用方也不用写 `?.invoke()`。
     *
     * `suspend`：Android 侧要重跑一次小组件快照（读库 + 拼 RemoteViews），是挂起调用。
     */
    suspend fun onThemePaletteChanged() {}

    /**
     * 应用版本名（设置页「关于与更新」那一行的 `v…`）。
     *
     * 非空且由装配根给：`BuildConfig` 在 `:app` 的构建里，共享层看不见它；
     * iOS 侧对应 `CFBundleShortVersionString`。读不到就返回空串，页面写成 `v—`。
     */
    fun appVersion(): String

    // ---- U净（iOS 移植 S21，2026-11-25） ----
    //
    // 类型全在 commonMain（S15 搬过），这里只是「谁给同一份实例」。必须是同一个实例：
    // 主页的订单卡、下单页的草稿、历史账单页读的都是对方写的同一份，
    // 各造一个等于「扫码开了草稿、下单页拿到 null」。
    //
    // 支付与完成提醒按「有没有平台能力」分开：支付要 `Activity`（见 [UjingPay] 的 KDoc），
    // 所以不进这里、由宿主直接传给 ViewModel；提醒只要 `Context`，装配根给得起。

    /**
     * U净 仓库（登录 / 看板 / 扫码 / 订单链）。Android 转接 `Graph.ujing`。
     *
     * **null = 本平台还建不起来**：它要一个凭证存储（`UjingSessionStore`，Android 是
     * `EncryptedSharedPreferences`）+ 一个 [edu.jxslu.schedule.data.local.KeyValuePrefs]
     * 落订单快照，iOS 侧这两样都还没有（Keychain / `NSUserDefaults` 待接）。
     * 返回 null 而不是抛：iOS 的 U净 页入口据此整块不出现，而不是点进去才崩。
     */
    fun ujingRepository(): UjingRepository?

    /**
     * U净 收藏的洗衣房（本机偏好）。主页看板与「添加洗衣房」共用。
     *
     * **null = 本平台没有 `KeyValuePrefs` 落点**（同 [ujingRepository]）：iOS 侧
     * 收藏看板整块不渲染，`stores/near` 也就不会被打。
     */
    fun ujingHouseStore(): UjingHouseStore?

    /**
     * U净 历史账单（本机留痕）：主页记录卡与历史账单页共用。
     *
     * **null = 本平台没有 `KeyValuePrefs` 落点**（同 [ujingRepository]）：iOS 侧
     * 记录卡整块不占位（它本来就是「没有记录就不占位」的形状）。
     */
    fun ujingHistoryStore(): UjingHistoryStore?

    /**
     * U净 扫码草稿（进程内一次性交接）：主页与独立下单页共用。
     *
     * **非空**：纯内存 `StateFlow`，不落盘（见 `UjingDraftStore` 的 KDoc），两端都建得起来。
     * 必须同一份实例，否则主页开的草稿下单页读不到。
     */
    fun ujingDraftStore(): UjingDraftStore

    /**
     * 洗衣完成提醒（闹钟 + 系统通知）。
     *
     * **null = 本平台没有闹钟与通知渠道**（iOS 待接 `UNUserNotificationCenter` +
     * `UNTimeIntervalNotificationTrigger`）：排 / 撤提醒整体跳过，订单卡与 15 秒轮询照常工作。
     * Android 侧转接 `ui/reminder/UjingDoneReminder`（那个文件留在 `:app`，是 `AlarmManager`
     * 直调，搬不动也不该搬）。
     */
    fun ujingReminder(): UjingReminder?
}

/**
 * [AppServices] 的进程级持有。
 *
 * 读方（共享层）在**任何**装配根写入之前读到它就会立刻抛——与其让 ViewModel 拿着半成品
 * 去构造一个空仓库，不如在启动期就炸出来。错误文案点名「谁没装」，所以写成常量。
 */
object Services {

    private var impl: AppServices? = null

    /** 由装配根在启动时调用一次；重复调用只是覆盖同一个实例，无副作用。 */
    fun install(services: AppServices) {
        impl = services
    }

    /** 测试用：清掉装配，避免不同用例互相看见对方的假实现。 */
    fun reset() {
        impl = null
    }

    val current: AppServices
        get() = requireNotNull(impl) {
            "AppServices 未安装：:app 的 JuwApplication.onCreate / iOS 的 IosServices 必须先 install"
        }
}
