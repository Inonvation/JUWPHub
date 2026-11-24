package edu.jxslu.schedule.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 教务导入用的 WebView 平台缝（iOS 移植，2026-11-24）。
 *
 * 只做教务导入需要的那六件事：加载 URL、读当前 URL、`reload()`、执行 JS、读某个 URL 的
 * Cookie 请求头、页面加载完成/失败的两种回调。**JS 桥、下载拦截、多窗口、文件上传一律不做**：
 * 现在没有任何调用方需要它们，先加进来就是一整套自己验不了（iOS 侧本机编不了）的面。
 *
 * ## 为什么是「普通 class + actual 侧安装一堆 lambda」，而不是 `expect class`
 *
 * `expect class WebViewController` 要求两个平台各写一份构造器与全部成员，Native 侧还要在
 * 两份中间产物之间对齐泛型与默认参数；任何一处对不上都是**链接期**报错，而本机是 Windows、
 * 编不了 Kotlin/Native，等于把唯一可能出错的地方放在唯一验不了的地方。仓库里
 * `ui/common/Haptics.kt` 已经立了先例：可持有一个普通 class，把实现侧能力做成可替换的 `var`，
 * 启动/组合时注入。
 * 这里沿用同一个形状——[impl] 由 actual 的 `WebContentView` 在创建原生视图时安装，
 * 视图销毁时清掉。于是 commonMain 只剩下纯 Kotlin 的转发，元数据编译门禁能完整覆盖它。
 *
 * ## 生命周期（调用方必须照做）
 *
 * 1. `remember { WebViewController() }` 持有，**不要每次重组都 new**：视图是围着句柄建的。
 * 2. 用 [WebContentView] 把它挂到组合里；`url` 变化会重新加载（同 URL 不重复加载）。
 * 3. WebView 离开组合时 actual 侧会销毁原生视图并把 [impl] 置空。**之后**再调 [loadUrl] /
 *    [reload] / [evaluateJavascript] 都是静默 no-op（[evaluateJavascript] 与 [getCookieHeader]
 *    会立刻回一个 null，免得调用方永远等不到回调）。
 *
 * ## 线程
 *
 * [loadUrl] / [reload] / [evaluateJavascript] 必须在主线程调（两端 WebView 都要求如此）；
 * 本缝不做线程切换。两个回调（[onPageFinished] / [onError]）都在主线程触发。
 * [getCookieHeader] 的 `onResult` 在**两端都是**「异步或稍后」语义，调用方不要假设同步返回。
 */
class WebViewController {

    /**
     * 页面加载完成，参数是完成时的 URL。
     *
     * **不要用它判「登录成功」**：教务登录成功后是服务端跳转，`onPageFinished` 也会在登录页
     * 本身触发一次。判定交给调用方用 URL 特征（与 `:app` 的 `JwUrls` 同一套口径）。
     */
    var onPageFinished: ((url: String) -> Unit)? = null

    /** 主 frame 加载失败（断网、DNS、证书、HTTP 层错误），参数是给用户看的一句话。 */
    var onError: ((message: String) -> Unit)? = null

    /**
     * 平台实现。`null` = 原生视图还没建好或已经销毁。
     *
     * `internal` 是刻意的：这是平台侧与控制器之间的私有协议，调用方只认上面那几个函数。
     */
    internal var impl: Impl? = null

    /** 当前 URL；视图未就绪时为 null（不是空串，调用方要能区分「还没加载」）。 */
    val currentUrl: String?
        get() = impl?.currentUrl?.invoke()

    /** 加载 [url]。视图未就绪时静默丢弃——那时也还没有用户手势能触发它。 */
    fun loadUrl(url: String) {
        impl?.load?.invoke(url)
    }

    /** 重新加载当前页面（教务页的「刷新」按钮）。 */
    fun reload() {
        impl?.reload?.invoke()
    }

    /**
     * 执行一段 JS，把结果回给 [onResult]。
     *
     * 返回值的形状**取决于平台**：Android 的 `evaluateJavascript` 给的是 JSON 字面量
     * （字符串带引号、`null` 就是字符串 `"null"`）；iOS 给的是 JS 值的 `toString`。
     * 教务导入的脚本都自己 `JSON.stringify` 再取，所以这条差异不影响使用；
     * 要写「严格按 JSON 解析结果」的调用方，得自己按平台名与 iOS 侧对齐。
     */
    fun evaluateJavascript(script: String, onResult: (String?) -> Unit = {}) {
        val target = impl ?: return onResult(null)
        target.evaluateJavascript(script, onResult)
    }

    /**
     * 读 [url] 这个域的 Cookie，拼成 HTTP 请求头的值：`"k=v; k2=v2"`，没有 Cookie 时给 null。
     *
     * 用途只有一个：把 WebView 里登出来的会话交给共享层的 Ktor 用（教务的
     * `JSESSIONID` / CAS 的 `TGC` 都在这条路上）。**只读**——不往 WebView 写 Cookie：
     * `design/04c-services.md` 已定案「WebView 的会话只能由 WebView 自己登出来，
     * 注入无效」，反过来回灌才是唯一方向。
     */
    fun getCookieHeader(url: String, onResult: (String?) -> Unit) {
        val target = impl ?: return onResult(null)
        target.cookieHeader(url, onResult)
    }

    /**
     * actual 侧安装的实现组。
     *
     * 用一组函数类型而不是接口：接口要求两个平台各写一个 class，而这里根本没有可复用的
     * 状态（状态都在原生视图里），多一层类型只是多一处要跟着改的签名。
     */
    internal class Impl(
        val load: (String) -> Unit,
        val reload: () -> Unit,
        val currentUrl: () -> String?,
        val evaluateJavascript: (String, (String?) -> Unit) -> Unit,
        val cookieHeader: (String, (String?) -> Unit) -> Unit,
    )
}

/**
 * 把 [controller] 对应的原生 WebView 放进组合。
 *
 * - `url` 首次进入组合时加载，之后**只有值变化**才重新加载（同值不重载，否则每次重组都刷一次
 *   教务页，登录表单会被清空）。
 * - 离开组合时销毁原生视图；[controller] 可以被下一个 [WebContentView] 重新接管。
 * - 尺寸由 [modifier] 决定，两端都要求调用方给出确定尺寸（教务导入是整屏，一般 `fillMaxSize()`）。
 */
@Composable
expect fun WebContentView(controller: WebViewController, url: String, modifier: Modifier = Modifier)
