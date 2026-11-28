package edu.jxslu.schedule.ui.life

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 充值入口的种类（免责声明放行后决定打开哪个弹层）。
 *
 * 2026-11-26 从 `:app` 的 `RechargeHost.kt` 挪进共享层：iOS 的充值宿主
 * （[SharedRechargeHost]）与 Android 的 `RechargeHost` 要的是同一个枚举，各留一份迟早
 * 会出现「这边加了渠道、那边 when 漏分支」。`:app` 侧只删声明、不改用法——同包同名，
 * `MainActivity` 的 import 一字不动。
 */
enum class RechargeKind { Campus, Power }

/**
 * 充值宿主需要的平台能力（**四类充值共用这一条缝**，别为每一类各开一条）。
 *
 * 设计口径见 `design/ios-port.md` §10.4：四类充值都不是 App 自己收钱，而是把用户交给
 * 外部支付工具。所以平台侧真正要提供的只有两件事——「谁来承载收银台页」与「怎么把一个
 * URL / 自定义 scheme 交给系统」。
 *
 * ## 两个方法与两种承载
 *
 * | 方法 | Android | iOS |
 * |---|---|---|
 * | [openPaymentPage] | `PowerBankPayActivity`（独立窗口，默认实现够用时不覆盖） | **不覆盖**：返回 false，由共享层的 [BankPayPage] 用 `WKWebView` 盖全屏 |
 * | [openExternalUrl] | `Intent.ACTION_VIEW` + `startActivityOutsideApp` | `UIApplication.openURL`（`IosRechargePlatform`） |
 *
 * 默认实现返回 false 是刻意的：**能自己承载收银台页的平台越少，收银台就越只有一份实现**。
 * 只有像 Android 那样「收银台页要注入 `AndroidFunc` 桥、还要拉高 UA / 允许三方 Cookie」
 * 的平台才值得覆盖它；覆盖方返回 false 表示「没接住」，宿主照样会退回共享页。
 */
interface RechargePlatform {

    /**
     * 把收银台 URL 交给**平台自己**的容器打开（一卡通 / 电费共用）。
     *
     * 返回 true = 平台已接住（页面由平台渲染）；false = 平台不承载，宿主用共享层的
     * [BankPayPage]。默认 false。
     */
    fun openPaymentPage(url: String): Boolean = false

    /**
     * 用系统打开一个 URL / 自定义 scheme：`alipays://`（胖乖 / 趣智充值）、`weixin://`
     * （一卡通充值的「直拉微信」那条，2026-11-28 起 iOS 也走这里）、
     * 以及收银台页里的「用浏览器打开」与掌银跳转。
     *
     * 返回 false = 本机没有能处理它的应用（未装支付宝 / 微信），调用方据此给提示或走兜底
     * （一卡通充值就是「false → 退收银台页」）。
     *
     * 返回 true **只代表系统接受了这次打开**——URL 交给了能处理它的 App（`openURL` /
     * `startActivity` 成功），但那个 App 里到底显示了什么、用户有没有付款，本 App 看不见，
     * 也不该据此对外宣称「已支付」。一卡通的到账判定只看余额 + 流水（`YktArrival`）。
     *
     * iOS 侧要 `Info.plist` 的 `LSApplicationQueriesSchemes` 声明 `alipays` / `weixin`，
     * 否则 `canOpenURL` 恒 false（见 `IosRechargePlatform`）。
     */
    fun openExternalUrl(url: String): Boolean
}

/**
 * 一次充值请求的投递口（壳层持有一份，钱包卡 / 电费账单页 / 校园卡设置页都往这里丢）。
 *
 * 为什么是「持有对象」而不是 `pending: RechargeKind?` + `onConsumed`：iPhone 上一个充值
 * 入口分布在抽屉（`IosDrawer` 的钱包卡）、缴费账单页、校园卡设置页三处，而宿主只有一个
 * （挂在根组合）。用持有对象，三处只要拿到同一个实例就能各自点火，不必把
 * `MutableState<RechargeKind?>` 一层层抬到壳层再传下去。
 *
 * 状态只有两个：**待办**（哪个弹层要开）与**内嵌收银台页的 URL**（非空 = 该页盖在最上面）。
 * 两者都由 [SharedRechargeHost] 消费；壳层只调 [request] / [openPaymentPage] /
 * [closePaymentPage]。
 */
@Stable
class RechargeRequests {

    /** 待处理的充值请求；[SharedRechargeHost] 取走后清空。 */
    internal var pending: RechargeKind? by mutableStateOf(null)
        private set

    /** 内嵌收银台页要打开的 URL；null = 没有页面。 */
    internal var paymentUrl: String? by mutableStateOf(null)
        private set

    /** 收银台页的标题（电费 / 校园卡两种流程的字不一样）。 */
    internal var paymentTitle: String = "支付"
        private set

    /**
     * 收银台页「回到商户域」的判据 host：落到它就把页面交还给宿主（查单）。
     * null = 不判——一卡通收银台本身就在商户域上，认不出「付完回来了」。
     */
    internal var paymentMerchantHost: String? by mutableStateOf(null)
        private set

    /** 发起一次充值（免责声明 → 对应弹层，全由宿主接管）。 */
    fun request(kind: RechargeKind) {
        pending = kind
    }

    /** 直接打开内嵌收银台页（一卡通充值到账前的支付页、电费收银台）。 */
    fun openPaymentPage(
        url: String,
        title: String = "支付",
        merchantReturnHost: String? = null,
    ) {
        paymentTitle = title
        paymentMerchantHost = merchantReturnHost
        paymentUrl = url
    }

    /** 收起内嵌收银台页。 */
    fun closePaymentPage() {
        paymentUrl = null
        paymentMerchantHost = null
    }

    /** 取走待办（取到即清空，同一个请求不会被两轮组合各开一次弹层）。 */
    internal fun consumePending(): RechargeKind? = pending.also { if (it != null) pending = null }
}
