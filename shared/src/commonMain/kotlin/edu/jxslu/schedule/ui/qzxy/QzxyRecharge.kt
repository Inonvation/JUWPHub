package edu.jxslu.schedule.ui.qzxy

/**
 * 趣智校园充值入口（DESIGN §3.18「充值」；2026-11-26 从 `:app` 挪进共享层）。
 *
 * 充值不在趣智的 REST 接口里，而是**支付宝小程序**：小程序 appId [MINI_APP_ID]，
 * 充值页 [PAGE]。官方 App 用 mPaaS 把同一个小程序嵌在应用内跑，我们不复制那条路
 * （那要厂商的 mPaaS 工作区密钥与客户端证书，等于冒充官方身份），改成深链把用户交给支付宝。
 *
 * **不拼 `myInfo`**。那是小程序侧的登录态（含 `loginCode`），官方「分享 → 复制链接」
 * 为了在别人手机上还原分享者账户才把它塞进 URL。2026-09-27 真机实测：不带它直接开
 * 充值页，小程序按支付宝身份自己登录并匹配到本校账户（页面显示学校名与余额），
 * 所以没必要把会话写进 URL。
 *
 * ## 为什么搬进共享层
 *
 * 深链是**协议知识**，两端逐字相同；`:app` 那份还夹着 `Intent` / `Uri`（Android 专有），
 * iOS 于是拿不到这条入口。现在 deepLink 在共享层、`:app` 只剩
 * `QzxyRechargeAndroid.kt` 的「交给系统」一行（与胖乖 `QiekjRecharge` /
 * `QiekjRechargeAndroid.kt` 同一种拆法）。`QzxyRechargeTest` 钉的仍是这一串。
 *
 * ## 页面在 iOS 上的现状
 *
 * 入口已具备（`QzxyScreen(onOpenRecharge = { iosQzxyRecharge() })`），但**趣智页在 iOS 上
 * 还进不去**：开阀走 `CoreBluetooth`，传输层没写（`design/ios-port.md` §12.1）。所以这笔
 * 充值在 iOS 上停在「深链与页面槽位都就绪，页面本身不可达」。
 */
object QzxyRecharge {

    /** 趣智校园支付宝小程序 appId（2026-09-27 由官方分享链重定向实测得到）。 */
    const val MINI_APP_ID = "2018090661238647"

    /** 小程序里的充值页路径。 */
    const val PAGE = "pages/recharge/recharge"

    /**
     * 拉起支付宝小程序充值页的深链。
     *
     * 拆成纯字符串是为了能在 JVM 单测里钉住（`android.net.Uri` 在单测里是空壳）。
     */
    fun deepLink(): String = "alipays://platformapi/startapp?appId=$MINI_APP_ID&page=$PAGE"
}
