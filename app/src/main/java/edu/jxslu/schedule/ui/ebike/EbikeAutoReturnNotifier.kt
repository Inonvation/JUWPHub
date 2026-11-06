package edu.jxslu.schedule.ui.ebike

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import edu.jxslu.schedule.R
import edu.jxslu.schedule.SubpageRequest
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.domain.EbikeAutoReturn
import edu.jxslu.schedule.subpageLaunchIntent
import edu.jxslu.schedule.ui.reminder.ReminderVibration

/**
 * 自动还车结果通知（DESIGN §3.9，2026-10-10）：**每次尝试一条**——成功一条，
 * 失败按次写清原因（如「当前位置不在还车区域内」）与是否还会重试。同 id 覆盖，
 * 通知栏永远只留最新那一条（第 1 次失败会被第 2 次尝试的结果顶掉，不堆一摞）。
 *
 * channel 用新 id（[EbikeAutoReturn.NotificationIds.CHANNEL]）：重要性 / 震动
 * 建出来就改不动，与免费提醒 `_v2` 同一踩坑（见 `EbikeFreeRide.NotificationIds`）。
 * 通知 id 1014 与上课（1001/1004）、作业（1002/1003）、免费提醒（1000/1005/1006）
 * 错开，由 `EbikeAutoReturnTest` 钉住。
 *
 * 落点是「骑行」页：收到通知时用户要么在路上、要么刚停下车，去那里能手动还车、
 * 也能看结果卡。
 */
internal object EbikeAutoReturnNotifier {

    private const val TAG = "EbikeAutoReturnNotify"

    /** 通知点击落点的 requestCode：与免费提醒（2000/2005/2006）、余额（3005/3006）错开。 */
    private const val REQUEST_CODE = 2007

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        subpageLaunchIntent(context, SubpageRequest(SubpageScreen.RIDE)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** channel 创建（幂等）。 */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            if (manager.getNotificationChannel(EbikeAutoReturn.NotificationIds.CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        EbikeAutoReturn.NotificationIds.CHANNEL,
                        "自动还车",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "用车满 15 分钟前的自动还车结果：成功，或每次失败的原因"
                        enableVibration(true)
                        vibrationPattern = ReminderVibration.PATTERN
                    },
                )
            }
        }.onFailure { Log.w(TAG, "ensureChannel failed", it) }
    }

    /** 还车成功。返回是否真的发出去（无通知权限时系统静默丢弃）。 */
    fun postSuccess(context: Context, text: String, strongVibration: Boolean): Boolean =
        post(context, EbikeAutoReturn.TITLE_SUCCESS, text, strongVibration)

    /** 某一次尝试失败（原因 + 是否重试，文案由 [EbikeAutoReturn.failureText] 出）。 */
    fun postFailure(context: Context, text: String, strongVibration: Boolean): Boolean =
        post(context, EbikeAutoReturn.TITLE_FAILURE, text, strongVibration)

    /**
     * 过窗：任务被推迟到迟到窗口之外，这一轮**一次都没尝试**。
     *
     * 复用 [EbikeAutoReturn.NotificationIds.RESULT] 同一个 id 与 channel：它说的仍是
     * "这一轮自动还车的结论"，同 id 覆盖能让上一轮残留的结果通知被顶掉，通知栏不会多出
     * 一摞条目。**别为此新建 channel**——channel 建出来后重要性改不动（见类 KDoc）。
     */
    fun postMissed(context: Context, strongVibration: Boolean): Boolean =
        post(context, EbikeAutoReturn.TITLE_MISSED, EbikeAutoReturn.MISSED_TEXT, strongVibration)

    /**
     * 加急任务的前台服务通知（2026-11-06，见 `EbikeAutoReturnReminder.enqueueNow`）。
     *
     * **为什么必须有这一条**：`setExpedited` 在 Android 11 及以下是**前台服务**实现的，
     * WorkManager 会调 `CoroutineWorker.getForegroundInfo()` 来拿这条通知；拿不到就直接
     * 以 `IllegalStateException("... but did not provide ForegroundInfo")` 让这次工作失败
     * ——自动还车在那些机型上会**彻底不跑**，比不加急还糟（audit-2026-11-06 E1 的修复
     * 如果只加 `setExpedited` 就是这个后果）。Android 12+ 走 JobScheduler 的加急通道，
     * 不读这个通知。
     *
     * id 与结果通知**不同**（见 [EbikeAutoReturn.NotificationIds.FOREGROUND]）：
     * 任务结束时 WorkManager 会摘掉这一条，同 id 会把刚发出去的结果一起撤掉。
     */
    fun foregroundNotification(context: Context): Notification {
        ensureForegroundChannel(context)
        return NotificationCompat.Builder(
            context,
            EbikeAutoReturn.NotificationIds.FOREGROUND_CHANNEL,
        )
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("正在自动还车")
            .setContentText("正在结束订单，稍等几秒")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent(context))
            .build()
    }

    /**
     * 前台服务那条 channel（LOW、无声、不震动；幂等）。
     *
     * 与结果 channel 分开的理由见 [EbikeAutoReturn.NotificationIds.FOREGROUND_CHANNEL] 的
     * KDoc：结果通知要响要震，这条只挂几秒，**别合并**。
     */
    fun ensureForegroundChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val id = EbikeAutoReturn.NotificationIds.FOREGROUND_CHANNEL
        runCatching {
            if (manager.getNotificationChannel(id) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(id, "自动还车执行中", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "自动还车那几秒的进度提示" },
                )
            }
        }.onFailure { Log.w(TAG, "ensureForegroundChannel failed", it) }
    }

    /** 清掉结果通知（新一轮骑行开始时：上一轮的结果已经过期）。 */
    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context)
                .cancel(EbikeAutoReturn.NotificationIds.RESULT)
        }.onFailure { Log.w(TAG, "cancel failed", it) }
    }

    /**
     * [strongVibration] = 通用设置「通知强提醒」（默认关）时，通知之外再补一次
     * [ReminderVibration.vibrate]（口径与免费时长提醒一致：渠道震动可能被 ROM 拦，
     * 直接震动走硬件、也穿免打扰，所以收在显式开关后面）。
     */
    private fun post(
        context: Context,
        title: String,
        text: String,
        strongVibration: Boolean,
    ): Boolean {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(
            context,
            EbikeAutoReturn.NotificationIds.CHANNEL,
        )
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        val posted = runCatching {
            NotificationManagerCompat.from(context)
                .notify(EbikeAutoReturn.NotificationIds.RESULT, notification)
        }.onFailure { Log.w(TAG, "post failed", it) }.isSuccess
        if (strongVibration) ReminderVibration.vibrate(context)
        return posted
    }
}
