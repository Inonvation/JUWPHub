package edu.jxslu.schedule

import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.platform.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * iOS 侧的共享服务装配（iOS 移植 M1，2026-11-25）。
 *
 * 与 `:app` 的 `GraphServices` 一一对应，只是这边没有 `Graph`——直接自己造：
 *
 * | 服务 | iOS 现状 |
 * |---|---|
 * | 偏好 | `DisplayPrefsStore()`，DataStore 落在 `appDataDir()`（沙箱私有目录） |
 * | 课表仓库 | `ScheduleRepository` + `BundledSQLiteDriver` 的 Room（v21，无迁移链） |
 * | 教务直取三件套 | **null**——要等 M2 的 WKWebView 会话与 Ktor 版 `JwReadSession` |
 *
 * 返回 null 不是省事，而是**如实表达能力**：课表页的「快速检测」图标与分享弹层里的
 * 「同步到日历」都据此不出现，而不是点了没反应。等 M2 接上教务导入，把这三行改成
 * 真实实例即可，页面侧一个字都不用动。
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

    private val repository: ScheduleRepository by lazy {
        // 第四个参数是调课检测用的基线快照；那份存储走文件 IO（`:app` 的
        // `ScheduleBaselineStore`），iOS 侧还没搬，先给 null = 不参与基线比对。
        ScheduleRepository(juwDatabase(), prefs, scope, baselineStore = null)
    }

    override fun displayPrefs(): DisplayPrefsStore = prefs

    override fun scheduleRepository(): ScheduleRepository = repository

    override fun scheduleSync(): ScheduleSync? = null

    override fun examSync(): ExamSync? = null

    override fun scheduleTermImport(): ScheduleTermImport? = null

    /** 首次打开时把作息表与默认学期写进库（与 Android 的 `ensureDefaults` 同步口径）。 */
    fun warmUp() {
        scope.launch { repository.ensureDefaults() }
    }
}
