package edu.jxslu.schedule.ui.life

import edu.jxslu.schedule.ui.water.QiekjRecharge
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS 的充值平台能力（2026-11-26，`design/ios-port.md` §10.4 要求的唯一一条缝）。
 *
 * ## 两个方法各自落在哪
 *
 * | 方法 | iOS 实现 |
 * |---|---|
 * | [RechargePlatform.openPaymentPage] | **不覆盖** —— 接口默认返回 false，宿主 `SharedRechargeHost` 于是用共享层的 `BankPayPage`（`WKWebView`）盖全屏。一卡通**必须**走这条：从原生 App 直拉 `weixin://` 会被微信拒（商户域名与 referer 对不上），得让收银台页面自己跳 |
 * | [RechargePlatform.openExternalUrl] | [iosOpenExternalUrl]：`canOpenURL` 预检（仅对白名单 scheme）+ `UIApplication.openURL` |
 *
 * 胖乖的入口有现成实现 [iosQiekjRecharge]（`WaterScreen.onOpenRecharge`）。趣智的深链也在
 * 共享层（`QzxyRecharge.deepLink()`），接法同一条 `iosOpenExternalUrl(...)`，但**趣智页在
 * iOS 上还进不去**（开阀走 `CoreBluetooth`，传输层没写），所以这一轮不出现成的 qzxy 函数。
 *
 * ## Info.plist 那一半（少了就是「点了没反应」）
 *
 * `LSApplicationQueriesSchemes` 必须含 `alipays`（2026-11-26 补）与 `weixin` / `weixinULAPI`
 * （2026-11-26 已有）。少了 `alipays`：`canOpenURL` 恒 false → 胖乖 / 趣智充值点了没反应，
 * 而且**不报错**（`openURL` 自己也会静默失败）。
 *
 * 但白名单只影响 `canOpenURL`，**不影响** `openURL`：所以这里只对「已声明过」的 scheme 做
 * 预检（`alipays` / `weixin`）——预检能把「没装支付宝」与「打开失败」区分开，页面据此给
 * 「先装一个支付宝」而不是静默；未声明的 scheme 直接 open，不拿 `canOpenURL` 的 false
 * 当判据（那会把本来能打开的链接误判成打不开，与 `IosNavigation.iosOpenUrl` 同一条口径）。
 *
 * ## 编译状态
 *
 * **未编译验证**：本机是 Windows，没有 Kotlin/Native。用到的两个符号与仓内已编过的
 * `IosWechatLaunch.iosOpenWechat`、`IosNavigation.iosOpenUrl` 逐字相同
 * （`NSURL.URLWithString` + `UIApplication.sharedApplication.openURL` /
 * `canOpenURL`），没有新符号。万一 `canOpenURL` 在绑定里叫别的名字，退路是删掉预检、
 * 直接 `openURL`（行为退化成「未装支付宝时点击无反应」，与 `iosOpenUrl` 现在一致）。
 */
object IosRechargePlatform : RechargePlatform {

    override fun openExternalUrl(url: String): Boolean = iosOpenExternalUrl(url)
}

/**
 * 用系统打开一个 URL / 自定义 scheme；返回 false = 没打开（未装对应 App / 链接无效）。
 *
 * [DECLARED_SCHEMES] 里的 scheme 先过 `canOpenURL`：那是 `LSApplicationQueriesSchemes`
 * 声明过的，预检结果可信，「没装支付宝」因此能变成一句确定的提示。
 */
fun iosOpenExternalUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val nsUrl = NSURL.URLWithString(url) ?: return false
    val app = UIApplication.sharedApplication
    val scheme = url.substringBefore(':').lowercase()
    if (scheme in DECLARED_SCHEMES && !app.canOpenURL(nsUrl)) return false
    return app.openURL(nsUrl)
}

/**
 * 打开胖乖生活的支付宝小程序充值页：`WaterScreen(onOpenRecharge = …)` 的 iOS 实现。
 *
 * 深链本身在共享层 [QiekjRecharge]（`QiekjRechargeTest` 钉着逐字形态），这里只负责交给系统。
 * 胖乖页在 iOS 上已能进（`IosNavigation` 的 `SubpageScreen.WATER` 分支），所以这一条就是
 * 那行的现成实现——壳层补 `onOpenRecharge = { iosQiekjRecharge() }` 即可。
 *
 * @return false = 没装支付宝（或链接无效），页面据此提示「先装一个支付宝」。
 */
fun iosQiekjRecharge(): Boolean = iosOpenExternalUrl(QiekjRecharge.deepLink())

/**
 * 已在 `Info.plist` 的 `LSApplicationQueriesSchemes` 里声明、因而 `canOpenURL` 可信的
 * scheme（`weixinULAPI` 是微信 OpenSDK 回跳用的，不用于打开，不列）。
 */
private val DECLARED_SCHEMES = setOf("alipays", "weixin")
