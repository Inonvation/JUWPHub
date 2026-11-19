package edu.jxslu.schedule.ui.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 提醒渠道与闹钟 PendingIntent 的唯一实现（2026-11-19 收口）。
 *
 * 此前 12 个提醒各自抄一份渠道注册、7 个各抄一份闹钟 PendingIntent，参数只有
 * id / 名称 / 重要性 / 说明 / 振动，行为一致。渠道 **id 不能改**：改了等于新建渠道，
 * 用户此前关掉的开关会被重置为开。
 *
 * [configure] 留给振动这类只有部分渠道需要的属性，别为它加一串布尔参数。
 */
internal fun ensureReminderChannel(
    context: Context,
    id: String,
    name: String,
    importance: Int,
    description: String? = null,
    configure: NotificationChannel.() -> Unit = {},
) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(id) != null) return
    manager.createNotificationChannel(
        NotificationChannel(id, name, importance).apply {
            if (description != null) this.description = description
            configure()
        },
    )
}

/**
 * 闹钟落点的 PendingIntent：7 个提醒只有 Receiver 类与 requestCode 不同。
 * 用 reified 而不是传 `Class<*>`，调用点保持一行。
 */
internal inline fun <reified T : BroadcastReceiver> broadcastPendingIntent(
    context: Context,
    requestCode: Int,
): PendingIntent = PendingIntent.getBroadcast(
    context,
    requestCode,
    Intent(context, T::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)
