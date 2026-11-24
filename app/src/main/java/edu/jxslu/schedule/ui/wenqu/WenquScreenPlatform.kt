package edu.jxslu.schedule.ui.wenqu

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.repo.WenquCertStore
import edu.jxslu.schedule.data.session.SessionExpiryStore
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.data.wenqu.WenquActivity
import edu.jxslu.schedule.startActivityOutsideApp
import edu.jxslu.schedule.ui.reminder.ReminderChime
import edu.jxslu.schedule.ui.reminder.ReminderVibration
import edu.jxslu.schedule.ui.reminder.WenquActivityAlertReminder
import edu.jxslu.schedule.ui.reminder.WenquWeeklyAlertReminder
import edu.jxslu.schedule.ui.score.maybeRequestNotifPermission
import edu.jxslu.schedule.ui.score.openPdf

/**
 * 问渠四页（主页 / 记录 / 设置 / 蹲点）要宿主代劳的平台动作（iOS 移植 S16，2026-11-25）。
 *
 * ## 为什么在这一侧
 *
 * 四页已搬进 `shared/commonMain`，那里没有 `Context` / `Activity`，也拿不到提醒链
 * （`WenquPreregReminder` 的精确闹钟 + 短时前台服务、`WenquActivityAlertReminder` 的共享周期任务、
 * `ReminderVibration` 的震动）与通知权限、文件落盘 + `FileProvider`。
 * 按本仓已定的口径（同 `ui/score/ScoreScreenPlatform.kt`、`ui/week/WeekScreenPlatform.kt`），
 * 这些动作收成一组回调交给共享层；共享页面对 `null` 的处理是「入口整行不渲染」，
 * 所以这里的每个字段都必须是**真的能执行**的，不要为了凑参数塞空 lambda。
 *
 * ## 为什么几个字段是 `suspend`
 *
 * 预报名排程、提醒重排、证书下载都要读到刚落盘的偏好值：调用点（共享页面）在
 * `scope.launch` 里「先写盘、再调这里」，顺序必须保住。做成普通 `() -> Unit` 就等于
 * 另起一个协程，可能与写盘并发——重排会拿着旧值去排任务。
 */
class WenquScreenActions(
    /** 预报名：排精确闹钟 + 兜底任务（`WenquPreregReminder.arm`）。 */
    val requestPrereg: suspend (WenquActivity, opensAt: Long) -> Unit,
    /** 取消预报名：清落盘 + 撤闹钟 + 撤兜底任务。 */
    val cancelPrereg: suspend () -> Unit,
    /** 盯 / 取消盯之后重排共享周期任务（只重排、不立刻叫醒）。 */
    val pinChanged: suspend () -> Unit,
    /** 蹲点命中那一刻的提醒：震动 + 提示音（用户可能没盯着屏幕）。 */
    val hitAlert: () -> Unit,
    /** 去微信报名：活动名已由共享页复制好，这里只递手并给一句回执。 */
    val openWechat: (onNotice: (String) -> Unit) -> Unit,
    /** 进页一次性提示：`loggedIn` 为真清标记，为假消费一次并回文案（null = 不必提示）。 */
    val sessionExpired: (loggedIn: Boolean) -> String?,
    /** 申请通知权限（API 33+ 且未授予才弹）。 */
    val requestNotifPermission: () -> Unit,
    /** 「新增志愿活动提醒」开关 / 间隔变更后重排。 */
    val alertSettingsChanged: suspend () -> Unit,
    /** 「每周日 12:00 抢活动提醒」开关 / 提前量变更后重排。 */
    val weeklyAlertSettingsChanged: suspend () -> Unit,
    /** 下载星级证书：取 PDF → 落 [WenquCertStore] → 交给系统 PDF 查看器；失败抛异常。 */
    val downloadCert: suspend (onNotice: (String) -> Unit) -> Unit,
)

/**
 * 组合期一次性取齐本组动作。
 *
 * `remember` 只按 `context` 记忆：`openWechat` / 证书落盘都读 Activity 实例，
 * 换 Activity 就是换一套（`SubpageActivity` 每次进页都是新实例，记忆键跟着换才不会拿旧实例去 startActivity）。
 */
@Composable
fun rememberWenquScreenActions(): WenquScreenActions {
    val context = LocalContext.current
    // 开启提醒那一刻请求 POST_NOTIFICATIONS（API 33+），拒绝不阻塞开关本身
    // （口径同成绩页 / 校园卡设置页：功能在系统设置里授权后自动生效）
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    return remember(context) {
        WenquScreenActions(
            requestPrereg = { activity, opensAt ->
                WenquPreregReminder.arm(context, activity, opensAt)
            },
            cancelPrereg = { WenquPreregReminder.cancel(context) },
            pinChanged = { WenquActivityAlertReminder.onPinChanged(context) },
            hitAlert = {
                ReminderVibration.vibrate(context)
                ReminderChime.play(context)
            },
            openWechat = { onNotice -> openWechatForSignup(context, onNotice) },
            sessionExpired = { loggedIn -> consumeSessionExpired(context, loggedIn) },
            requestNotifPermission = { maybeRequestNotifPermission(context, notifPermissionLauncher) },
            alertSettingsChanged = { WenquActivityAlertReminder.onSettingsChanged(context) },
            weeklyAlertSettingsChanged = { WenquWeeklyAlertReminder.onSettingsChanged(context) },
            downloadCert = { onNotice ->
                val cert = Graph.wenqu(context).downloadCert()
                val store = WenquCertStore(context)
                val file = store.save(cert.bytes, cert.fileName)
                openPdf(context, store.uriOf(file)) { msg, _ -> onNotice(msg) }
            },
        )
    }
}

/**
 * 「登录状态已过期」的一次性提示（原 `ui/common/SessionExpiredNotice` 的问渠那一份）。
 *
 * 为什么搬到这里：那张留痕表（`SessionExpiryStore`）的介质是 `SharedPreferences` + `Context`，
 * 留在 `:app`，共享层看不见，所以由宿主按共享页给的时机（`LaunchedEffect(loggedIn)`）执行。
 * 口径与原来逐字一致：已登录 → 清旧标记；未登录且标记在 → 取走并提示一次。
 */
private fun consumeSessionExpired(context: Context, loggedIn: Boolean): String? {
    val store = Graph.sessionExpiry(context)
    return if (loggedIn) {
        store.clear(SessionService.Wenqu)
        null
    } else if (store.consume(SessionService.Wenqu)) {
        SessionExpiryStore.NOTICE_TEXT
    } else {
        null
    }
}

/**
 * 拉起微信并给一句引导（App 不代报名）。
 *
 * 走 [startActivityOutsideApp]：微信是 singleTask、永远开不进本 task，
 * 从它返回时二级页的右推入过渡会被重放（用户报过「界面跳动」），这个出口会先换静止过渡。
 * 只做一级兜底——这里只是「递到微信首页」，没有扫一扫那种直达诉求。
 */
private fun openWechatForSignup(context: Context, onNotice: (String) -> Unit) {
    val launch = context.packageManager.getLaunchIntentForPackage("com.tencent.mm")
    if (launch == null) {
        onNotice("没检测到微信；活动名已复制，可到小程序「问渠」里报名")
        return
    }
    runCatching {
        context.startActivityOutsideApp(launch)
        onNotice("活动名已复制，请在微信小程序「问渠」里报名")
    }.onFailure {
        onNotice("打不开微信；活动名已复制，请手动打开小程序报名")
    }
}
