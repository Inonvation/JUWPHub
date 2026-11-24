package edu.jxslu.schedule.ui.water

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import edu.jxslu.schedule.startActivityOutsideApp

/**
 * 胖乖充值深链的 **Android 打开方式**（iOS 移植 T2，2026-11-25）。
 *
 * 深链本身（appId / 店号 / 百分号编码形态）在共享层的 [QiekjRecharge]：那是协议知识，
 * 不依赖平台，且被 `QiekjRechargeTest` 钉着。这里只剩「交给系统」这一件平台动作，
 * 所以留在 `:app`，由宿主包成 [WaterScreen] 的 `onOpenRecharge` 回调。
 *
 * @return true = 已交给支付宝；false = 本机没有能处理 `alipays://` 的应用
 *   （通常是没装支付宝）。能否解析取决于 manifest `<queries>` 里 `alipays` scheme 的
 *   声明——趣智充值时已加过，这里共用一条。
 */
fun openQiekjRecharge(context: Context): Boolean = try {
    context.startActivityOutsideApp(Intent(Intent.ACTION_VIEW, Uri.parse(QiekjRecharge.deepLink())))
    true
} catch (_: ActivityNotFoundException) {
    false
}
