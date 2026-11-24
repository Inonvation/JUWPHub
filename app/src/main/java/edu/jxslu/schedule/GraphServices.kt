package edu.jxslu.schedule

import android.content.Context
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.platform.AppServices

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

    override fun scheduleSync(): ScheduleSync = Graph.scheduleSync(appContext)

    override fun examSync(): ExamSync = Graph.examSync(appContext)

    override fun scheduleTermImport(): ScheduleTermImport = Graph.scheduleTermImport(appContext)
}
