package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.domain.EbikeUseMode
import edu.jxslu.schedule.startActivityOutsideApp
import edu.jxslu.schedule.ui.common.NoticeTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * [KvcxSideEffects] 的**生产实现**，以及它用到的三件平台动作（2026-11-25 从
 * `KvcxRideController.kt` 拆出来）。
 *
 * 为什么这三块留在 `:app` 而不是跟控制器一起搬进共享层：它们全要 Android 专有件——
 * `EbikeFreeRideReminder` / `EbikeAutoReturnReminder` 是 `AlarmManager` + 通知渠道，
 * `burnPendingCodes` 要 `Context` 与相册，`openWechatForSettle` 要
 * `PackageManager` + `startActivityOutsideApp`。按 `design/ios-port.md` §11.2 ④，
 * 共享层的控制器收的是**可空**的 `KvcxSideEffects`：iOS 传 `null`，逐个动作整体跳过。
 */
internal object AppKvcxSideEffects : KvcxSideEffects {

    override suspend fun startRideTimer(): Pair<String, NoticeTone> =
        startFreeRideNotice(AndroidEbikeCodePlatform)

    override suspend fun ensureReminderEnabled(): Boolean {
        val prefs = Graph.displayPrefs(Graph.appContext)
        val on = runCatching { prefs.ebikeFreeReminderEnabled.first() }.getOrDefault(true)
        if (on) return false
        runCatching { prefs.setEbikeFreeReminderEnabled(true) }
        return true
    }

    override suspend fun scheduleAutoReturn() {
        // 上一轮的结果通知先撤掉（「已自动还车」不能与新一轮骑行并存），再按设置收口：
        // 排闹钟 / 到点补跑 / 开关关着时撤销
        EbikeAutoReturnNotifier.cancel(Graph.appContext)
        EbikeAutoReturnReminder.reconcile(Graph.appContext)
    }

    override suspend fun endRideAndBurn() {
        EbikeFreeRideReminder.endRide(Graph.appContext)
        // 订单已经结束：自动还车再排着也没有对象可还
        EbikeAutoReturnReminder.cancel(Graph.appContext)
        burnPendingCodes(
            Graph.displayPrefs(Graph.appContext),
            AndroidEbikeCodePlatform,
            force = true,
        )
    }

    override suspend fun onOrderGoneExternally(): Boolean {
        val prefs = Graph.displayPrefs(Graph.appContext)
        val mode = runCatching { prefs.ebikeUseMode.first() }.getOrElse { EbikeUseMode.Default }
        val startAt = runCatching { prefs.ebikeRideStartAt.first() }.getOrDefault(0L)
        val end = EbikeFreeRide.shouldEndTimerWhenOrderGone(
            inAppRide = mode.isAccount,
            startAtMillis = startAt,
            nowMillis = System.currentTimeMillis(),
        )
        if (!end) return false
        // 与手动还车同一条收尾（清计时 / 撤闹钟 / 停常驻服务 / 撤自动还车 / 焚码），不另写一份
        endRideAndBurn()
        return true
    }

    override suspend fun recordRide(summary: KvcxReturnSummary) {
        val carNum = summary.carNum ?: return
        runCatching {
            Graph.rideRecordStore(Graph.appContext).add(
                carNum = carNum,
                endAt = System.currentTimeMillis(),
                durationSeconds = summary.durationSeconds ?: 0L,
                feeCents = summary.feeCents,
                // 拿不到结算结果按未结清记：宁可多提醒一次去核对，也不把不确定写成"已结清"
                settled = summary.settled ?: (summary.needPay == false),
            )
        }
    }

    override suspend fun recordRemoteRide(): Int = runCatching {
        Graph.displayPrefs(Graph.appContext).incrementEbikeRemoteRideCount()
    }.getOrDefault(0)
}

/**
 * 起免费时长计时并把结果映射成提示文案与语气——**唯一实现已搬进共享层**
 * （`ui/ebike/EbikeCodePlatform.kt` 的 `startFreeRideNotice`，出码页与本类共用同一份文案）。
 * 这里只剩「Android 的提醒实现是谁」这一句转接。
 *
 * 打开微信（结清欠费的唯一去处）：待支付页在快趣小程序里，而**微信不给第三方直达小程序
 * 具体页面**——URL Scheme / 短链必须由小程序自己的服务端生成（要它的 appsecret），
 * 开放平台拉起小程序又要求 App 与小程序在同一开放平台账号下绑定。所以只能把用户送到微信。
 *
 * 到了微信之后用户为什么"找不到支付入口"：官方的待支付页不挂菜单，
 * 只有两条路能到——**主页的「待支付」横幅**（onShow 查一次、60 秒节流、关过一次冷却 5 分钟）
 * 和**历史订单里的待支付记录**（点进去就是支付页）。提示把这两条写出来，别让用户白进一趟。
 */
internal fun openWechatForSettle(context: android.content.Context, onHint: (String) -> Unit) {
    val launch = try {
        context.packageManager.getLaunchIntentForPackage("com.tencent.mm")
    } catch (e: CancellationException) {
        throw e // 取消穿透
    } catch (_: Exception) {
        null
    }
    if (launch == null) {
        onHint("没装微信；欠费可在快趣小程序（或找快趣客服）结清")
        return
    }
    try {
        context.startActivityOutsideApp(launch)
        onHint(
            "微信已打开：进快趣小程序后，主页会出现「待支付」横幅（之前关过要等 5 分钟），" +
                "或在「历史订单」里点那笔待支付订单直接付",
        )
    } catch (e: CancellationException) {
        throw e // 取消穿透
    } catch (_: Exception) {
        onHint("无法打开微信；请手动进快趣小程序，主页「待支付」横幅或历史订单可直达付款页")
    }
}

/**
 * 时刻文案 `HH:mm`（骑行卡上的「更新于 / 位置」）；两页共用一个格式化器。
 */
internal fun clockText(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
