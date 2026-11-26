package edu.jxslu.schedule.ui.qzxy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

import edu.jxslu.schedule.startActivityOutsideApp

/**
 * 趣智校园充值：把 `alipays://` 深链交给系统（Android 侧唯一的一行）。
 *
 * 深链本身（appId / 页面路径 / 逐字形态）在共享层的 [QzxyRecharge]：那是协议知识，
 * 不依赖平台，且被 `QzxyRechargeTest` 钉着。这里只剩「交给系统」这一件平台动作，
 * 与胖乖的 `openQiekjRecharge`（`ui/water/QiekjRechargeAndroid.kt`）同一种拆法。
 *
 * @return true = 已交给支付宝；false = 本机没有能处理 `alipays://` 的应用
 *   （通常是没装支付宝）。能否解析取决于 manifest `<queries>` 里 `alipays` scheme 的
 *   声明——缺了这条会恒返回 false。
 */
fun openQzxyRecharge(context: Context): Boolean = try {
    context.startActivityOutsideApp(Intent(Intent.ACTION_VIEW, Uri.parse(QzxyRecharge.deepLink())))
    true
} catch (_: ActivityNotFoundException) {
    false
}
