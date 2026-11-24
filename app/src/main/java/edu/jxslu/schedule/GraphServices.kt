package edu.jxslu.schedule

import android.content.Context
import edu.jxslu.schedule.data.power.PowerReadingStore
import edu.jxslu.schedule.data.power.PowerRepository
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.HomeworkRepository
import edu.jxslu.schedule.data.repo.InboxRepository
import edu.jxslu.schedule.data.repo.NoteRepository
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.data.repo.SelectionRepository
import edu.jxslu.schedule.data.repo.SelectionSync
import edu.jxslu.schedule.data.repo.ScoreRepository
import edu.jxslu.schedule.data.repo.ScoreSync
import edu.jxslu.schedule.data.weather.WeatherRepository
import edu.jxslu.schedule.data.ykt.YktCredentialStore
import edu.jxslu.schedule.data.ykt.YktRepository
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

    override fun scoreRepository(): ScoreRepository = Graph.scoreRepository(appContext)

    override fun scheduleSync(): ScheduleSync = Graph.scheduleSync(appContext)

    override fun examSync(): ExamSync = Graph.examSync(appContext)

    override fun scoreSync(): ScoreSync = Graph.scoreSync(appContext)

    override fun scheduleTermImport(): ScheduleTermImport = Graph.scheduleTermImport(appContext)

    override fun selectionRepository(): SelectionRepository = Graph.selectionRepository(appContext)

    override fun selectionSync(): SelectionSync = Graph.selectionSync(appContext)

    override fun noteRepository(): NoteRepository = Graph.noteRepository(appContext)

    override fun homeworkRepository(): HomeworkRepository = Graph.homeworkRepository(appContext)

    // 收件箱（S19）：页面搬进共享层后要它。转给 `Graph` 的同一份单例——收件箱是旁路留痕，
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
}
