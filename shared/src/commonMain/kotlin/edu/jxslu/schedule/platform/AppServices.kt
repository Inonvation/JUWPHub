package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.HomeworkRepository
import edu.jxslu.schedule.data.repo.NoteRepository
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.data.repo.SelectionRepository
import edu.jxslu.schedule.data.repo.SelectionSync
import edu.jxslu.schedule.data.repo.ScoreRepository
import edu.jxslu.schedule.data.repo.ScoreSync

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
