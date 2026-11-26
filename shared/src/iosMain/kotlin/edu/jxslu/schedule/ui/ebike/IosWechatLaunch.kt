package edu.jxslu.schedule.ui.ebike

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * 拉起微信（小程序方式的「打开微信扫一扫」与结算卡的「去微信结清」共用一个出口，2026-11-26）。
 *
 * ## 为什么是 `weixin://` 而不是「扫一扫」
 *
 * Android 那边有三条 Intent 兜底，第一条直达扫一扫相机页（`ShortCutDispatchAction` +
 * `launch_type_scan_qrcode`，见 `:app` 的 `EbikeActions.openWechatScan`）。**iOS 没有等价物**：
 * 微信在 iOS 上只开放 `weixin://` 这一个公有 scheme，落点是微信首页（「我 / 通讯录 / 发现」那一栏），
 * 扫一扫要用户自己点。所以这里只承诺「把微信拉起来」，提示语也照实说（见 [WECHAT_GUIDANCE]）。
 *
 * 前提是 `iosApp/JUWPHub/Info.plist` 的 `LSApplicationQueriesSchemes` 里有 `weixin`
 * 与 `weixinULAPI`（2026-11-26 补）。**打开**自定义 scheme 不需要白名单，白名单只影响
 * `canOpenURL`（所以这里不做预检，直接 open、按返回值判成败）；声明 `weixinULAPI` 是为
 * 将来接微信 OpenSDK 回跳留位。
 *
 * ## 与 Android 的语义差异
 *
 * | 项 | Android | iOS |
 * |---|---|---|
 * | 落点 | 扫一扫相机页（或微信首页） | 微信首页，用户自己点「发现 → 扫一扫」 |
 * | 失败提示 | `onError` 回调 → 页面 Snackbar | 同左（[onNotice]） |
 *
 * **接线时注意**：`RideScreen` 的 `onOpenWechatScan` / `onOpenWechatForSettle` 形参是
 * `(() -> Unit)?`，没有提示通道——直接用 `{ iosOpenWechat() }` 接，失败与「已打开、请手动点扫一扫」
 * 这两种结果都不会显示给用户（页面照旧只显示它自己的出码提示）。要显示就把 [onNotice]
 * 接到宿主的 Snackbar 上，别改页面签名。
 *
 * 编译状态：本机是 Windows，没有编译验证过。用的两个符号与 `IosNavigation.kt` 的
 * `iosOpenUrl` 逐字相同（`NSURL.URLWithString` + `UIApplication.sharedApplication.openURL`），
 * 没有新符号。
 *
 * @param onNotice 需要告诉用户的一句话（打不开 / 已打开但得手动点扫一扫）。默认丢弃。
 * @return true = 已交给系统打开微信。
 */
fun iosOpenWechat(onNotice: (String) -> Unit = {}): Boolean {
    val url = NSURL.URLWithString(WECHAT_SCHEME)
    if (url == null) {
        onNotice("链接无效")
        return false
    }
    val opened = UIApplication.sharedApplication.openURL(url)
    if (opened) {
        onNotice(WECHAT_GUIDANCE)
    } else {
        onNotice("无法自动打开微信，请手动打开「扫一扫」扫码")
    }
    return opened
}

/**
 * 微信的 iOS scheme（Info.plist 的 `LSApplicationQueriesSchemes` 必须含它）。
 *
 * 只有 `weixin://` 一个：微信**没有**公开「直接进扫一扫」的 iOS scheme，别去猜
 * `weixin://scanqrcode` 之类——猜错了是「点了打不开微信」，比多一步手动点击更糟。
 */
private const val WECHAT_SCHEME = "weixin://"

/** 打开微信之后给用户的一句话（iOS 只能落到微信首页，扫一扫要自己点）。 */
private const val WECHAT_GUIDANCE = "微信已打开，请在「发现 → 扫一扫」对准二维码"
