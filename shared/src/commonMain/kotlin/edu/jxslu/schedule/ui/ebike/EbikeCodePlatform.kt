package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.BarcodeMatrix
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.ui.common.NoticeTone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 快趣出码页（`EbikeViewModel`）要的两类**平台能力**（iOS 移植 P4，2026-11-25）。
 *
 * ## 为什么是两个接口而不是更多 ViewModel 参数
 *
 * 搬迁前 `EbikeViewModel` 直接调 `:app` 的五件平台件：`EbikeFreeRideReminder` /
 * `EbikeAutoReturnReminder`（`AlarmManager` + 通知渠道）、`EbikeQrBitmaps`（MediaStore）、
 * `Graph.appContext`、`System.currentTimeMillis`。前两件是「提醒」，第三件是「相册」，
 * 按能力分成 [EbikeReminderPort] 与 [EbikeQrGallery] 两个口子；后两件根本不是能力，
 * 改成构造参数与 [AppClock]。
 *
 * ## `null` 的语义（`design/ios-port.md` §11.2 ④）
 *
 * 两个都可空，`null` = **本平台确实没有这项能力**，不是「以后补」的占位：
 *
 * | 能力 | `null` 时页面怎么降级 |
 * |---|---|
 * | [EbikeReminderPort] | 免费时长提醒 / 自动还车两组设置整块不渲染；计时照走（计时是本地偏好，谁都能算） |
 * | [EbikeQrGallery] | 「存相册 / 生成后自动保存 / 扫完即焚」整组不渲染，码本身照出（码是矩阵，屏幕上看得到） |
 *
 * 给一个点了没反应的入口比没有更糟——所以不是空实现，是 `null`。
 */

/**
 * 免费时长提醒与自动还车（闹钟 + 系统通知）的平台缝。
 *
 * Android 实现是 `:app` 的 `AndroidEbikeCodePlatform`（转接 `EbikeFreeRideReminder` /
 * `EbikeAutoReturnReminder`）；iOS 侧要接 `UNUserNotificationCenter` 时另写一份。
 *
 * 为什么连**文案**都不在这里：`Outcome` → 提示语的映射是同一段中文，写在共享层的
 * [startFreeRideNotice] 里，两端不会各漂一份。
 */
interface EbikeReminderPort {

    /**
     * 起免费时长计时并把提醒排上（点「打开微信扫一扫」传 `now + SCAN_DELAY_MS`，
     * 本机开锁成功传当下）。结果的提示文案由 [startFreeRideNotice] 出。
     */
    suspend fun startFreeRide(startAtMillis: Long): EbikeReminderOutcome

    /** 进页 / 设置变更时核对一次：计时中缺服务/闹钟就补上，过期状态就清干净（幂等）。 */
    suspend fun checkFreeRide(): EbikeReminderOutcome

    /** 手动结束骑行：清计时起点、撤提醒、停常驻倒计时。 */
    suspend fun endFreeRide(): EbikeReminderOutcome

    /**
     * 自动还车排程重算。[catchUp] = false 时**不追补**已过触发点的这一轮
     * （设置刚开不该立刻还车）。
     */
    suspend fun reconcileAutoReturn(catchUp: Boolean = true)

    /** 撤掉自动还车（订单已结束 / 计时已清）。 */
    suspend fun cancelAutoReturn()
}

/** 一次提醒动作的结果（一对一映射 `:app` 的 `EbikeFreeRideReminder.Outcome`）。 */
sealed interface EbikeReminderOutcome {
    /** 计时已起，常驻倒计时与提醒都在。 */
    data object Started : EbikeReminderOutcome

    /** 提醒开关关着：计时起了，但不出通知。 */
    data object StartedSilent : EbikeReminderOutcome

    /** 计时起了，但系统通知被关：提醒发不出来。 */
    data object StartedNoNotification : EbikeReminderOutcome

    /** 提醒已清空（结束骑行 / 开关关掉）。 */
    data object Ended : EbikeReminderOutcome

    /** 什么都不用做（幂等核对时的常态）。 */
    data object Nothing : EbikeReminderOutcome

    /** 排程失败；[message] 直接展示给用户。 */
    data class Failed(val message: String) : EbikeReminderOutcome
}

/**
 * 二维码落系统相册的平台缝（扫完即焚的删除也归它）。
 *
 * Android 走 MediaStore（API 29+）或公共目录直写（26–28）；iOS 要 `UIImageWriteToSavedPhotosAlbum`
 * 时另写一份，没接之前给 `null`。
 *
 * **位阵进来、位阵不出**：调用方给的是 [BarcodeMatrix]，渲染成位图是平台自己的事
 * （`android.graphics.Bitmap` 进不了 `commonMain`，屏幕上的码由 `BarcodeMatrixImage`
 * 用 Compose 画布直接画，根本不经过位图）。
 */
interface EbikeQrGallery {

    /**
     * 存一张码进相册。[dark] 决定黑白（深色主题出「深底白码」，与屏幕上那张一致）。
     * 成功返回 [EbikeQrSaveResult.Saved]（附待焚毁 key），失败给原因。
     */
    suspend fun save(matrix: BarcodeMatrix, dark: Boolean, displayName: String): EbikeQrSaveResult

    /** 按待焚毁 key 删除一张已存的码；true = 已删除，false = 失败/不存在/校验不过（保留 key 下次重试）。 */
    suspend fun deletePending(key: String): Boolean
}

/** 相册保存结果。key 的构造与语义在 `domain/EbikeQr`（`pendingMediaKey` / `pendingFileKey`）。 */
sealed interface EbikeQrSaveResult {
    data class Saved(val pendingKey: String) : EbikeQrSaveResult
    data class Failed(val message: String) : EbikeQrSaveResult
}

/**
 * 起免费时长计时并把结果映射成提示文案与语气（点扫一扫 / 本机开锁成功共用，**唯一实现**）。
 *
 * 搬迁前这段映射在 `:app` 的 `KvcxRideSideEffects.kt`，出码页另有一份；两处口径必须一致，
 * 所以收进共享层，Android 的 `AppKvcxSideEffects.startRideTimer()` 直接调这里。
 *
 * 起点由调用方给：点「打开微信扫一扫」传 `now + EbikeFreeRide.SCAN_DELAY_MS`
 * （切微信、选图、扫码、开车要时间，倒计时从点击 5 秒后才开始走）；本机开锁成功传当下。
 *
 * [port] 为 null = 本平台没有提醒能力：仍然算「已开始计时」，只是不会出通知栏倒计时。
 */
suspend fun startFreeRideNotice(
    port: EbikeReminderPort?,
    startAtMillis: Long = AppClock.nowMillis(),
): Pair<String, NoticeTone> {
    val outcome = port?.startFreeRide(startAtMillis) ?: EbikeReminderOutcome.Nothing
    return when (outcome) {
        EbikeReminderOutcome.Started ->
            "已开始计时，通知栏已显示倒计时" to NoticeTone.Success
        EbikeReminderOutcome.StartedNoNotification ->
            "通知被关闭，提醒发不出来，请到系统设置打开" to NoticeTone.Warning
        EbikeReminderOutcome.StartedSilent ->
            "已开始计时（免费时长提醒未开启）" to NoticeTone.Info
        is EbikeReminderOutcome.Failed ->
            "已开始计时；提醒排程失败：${outcome.message}" to NoticeTone.Warning
        else ->
            "已开始计时" to NoticeTone.Info
    }
}

/** 焚毁的进程级互斥：出码页与地图页可能同时触发（还车 + 回页兜底），进行中的不叠跑。 */
private val burnGate = Mutex()

/**
 * 扫完即焚（DESIGN §3.9）：删除所有记录在案的待焚毁二维码，成功才移出记录，
 * 返回实际删掉的条数（0 = 什么都没删，调用方据此决定要不要出声）。
 * 开关关闭时不删不清——关掉 = 完全回到旧语义。
 *
 * [force] = false 时先看计时：**免费时长还在跑就不删**（图要留着反复扫），删除交给
 * `EbikeFreeRideReminder.check`（免费结束闹钟/周期核对）或手动结束骑行；无计时在案
 * （没点「打开微信扫一扫」）才维持「回 App 即删」——码是开锁耗材，没有计时段落兜着
 * 就不能留在相册。[force] = true 用于手动结束骑行 / 还车，无条件删。
 *
 * 顶层函数而非 `EbikeViewModel` 的成员：地图页还车也要走同一条口径
 * （「骑完车自动删除」不该因为从哪个页面还车而不同）。
 *
 * **public 而不是 internal**：`:app` 的 `AppKvcxSideEffects.endRideAndBurn` 也要走它，
 * 跨模块看不见 `internal`。
 *
 * [gallery] 为 null（本平台没有相册能力）= 从来没有 key 被写进记录，直接返回 0。
 */
suspend fun burnPendingCodes(
    prefs: DisplayPrefsStore,
    gallery: EbikeQrGallery?,
    force: Boolean,
): Int {
    if (gallery == null) return 0
    if (!burnGate.tryLock()) return 0
    try {
        // 开关真值必须读原始流：ebikePrefs 的 stateIn 快照在 DataStore
        // 首次发射前是默认值（true），冷启动恢复的首帧竞态下会误删
        // 「用户已关闭焚毁」时留下的记录。
        if (!prefs.ebikeBurnAfterScan.first()) return 0
        if (!force) {
            val startAt = prefs.ebikeRideStartAt.first()
            if (EbikeFreeRide.isActive(startAt, AppClock.nowMillis())) return 0
        }
        var total = 0
        while (true) {
            val pending = prefs.ebikePendingDelete.first()
            if (pending.isEmpty()) break
            val deleted = pending.filter { key -> gallery.deletePending(key) }
            prefs.updateEbikePendingDelete { it - deleted.toSet() }
            if (deleted.isEmpty()) break // 全部失败（如文件已不在），保留记录别空转
            total += deleted.size
            if (deleted.size == pending.size) break
            // 有失败项：下轮重试剩余的；连续失败会在下一轮走 break
        }
        return total
    } finally {
        burnGate.unlock()
    }
}
