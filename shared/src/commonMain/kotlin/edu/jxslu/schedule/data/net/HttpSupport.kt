package edu.jxslu.schedule.data.net

import edu.jxslu.schedule.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequest
import io.ktor.http.Cookie
import io.ktor.http.Url

/**
 * Ktor 侧的「`AppHttp` 附带能力」补齐（iOS 移植 P1，2026-11-24）。
 *
 * 这三件事原先由 OkHttp 免费提供、迁到 Ktor 后必须自己补，否则**迁过去的模块会静默降级**：
 *
 * | `AppHttp`（OkHttp） | 这里 |
 * |---|---|
 * | `Ipv4FirstDns`（校园 IPv6 黑洞） | 各平台 actual 负责，见 [createPlatformHttpClient] |
 * | `CookieJar` / `MemoryCookieJar`（CAS 会话） | [InMemoryCookieStorage] + `HttpCookies` 插件 |
 * | `DebugRequestCounter`（按 host 计数） | [requestCounterPlugin]，只在 debug 挂 |
 *
 * 为什么不做成「默认全开」：Cookie 与计数都是有副作用的（会把 cookie 发给不该发的 host、
 * 会多一层拦截），所以 [SharedHttp.create] 用**显式参数**打开，各模块按需传。
 */
object HttpSupport {

    /**
     * 按 host 分隔的内存 Cookie 罐（对应 Android 侧的 `MemoryCookieJar`）。
     *
     * 与 OkHttp 的 `CookieJar` 语义差异，写下来免得误以为等价：
     * - **不做过期淘汰**：`expires` 到了的 cookie 仍然会被回放。CAS 会话的生命周期由它自己的
     *   服务端控制（过期后返回 302/401，各客户端已经有「会话失效」分支），这里再判一次只是重复。
     * - **不按 path 匹配**：同一 host 的 cookie 全部回放。校园这套系统都是根路径下的会话 cookie，
     *   按 path 过滤只会引入「某个 .do 路径拿不到 JSESSIONID」这类难查的问题。
     * - **domain 缺省取请求 host**：服务端没写 Domain 时（会话 cookie 的常见写法）按当前 host 归档。
     */
    class InMemoryCookieStorage : CookiesStorage {

        private val byHost = LinkedHashMap<String, MutableList<Cookie>>()

        override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
            val host = requestUrl.host
            val bucket = byHost.getOrPut(host) { mutableListOf() }
            // 同名同路径视为同一个 cookie，直接替换，避免 Set-Cookie 反复追加导致请求头膨胀
            bucket.removeAll { it.name == cookie.name && it.path == cookie.path }
            // Max-Age=0 / 空值不是删除语义：保留空值会让服务端把它当「未登录」，
            // 而 OkHttp 的行为是照样回放。这里照抄「照样回放」，只做去重。
            bucket.add(cookie)
        }

        override suspend fun get(requestUrl: Url): List<Cookie> {
            val host = requestUrl.host
            val exact = byHost[host].orEmpty()
            // 父域 cookie（Domain=.edu.cn 这种）也回放：只在 host 后缀匹配时匹配一次，
            // 不做完整的 PSL 判定——校园域名结构固定，这里够用。
            val parents = byHost.entries
                .filter { (key, _) -> key != host && host.endsWith(".$key") }
                .flatMap { it.value }
            return exact + parents
        }

        /** 会话销毁时清空（对应 Android 侧 `JwHttpSession` 的 evictAll）。 */
        override fun close() {
            byHost.clear()
        }

        /** 当前持有的 cookie 条数，仅供诊断显示。 */
        fun size(): Int = byHost.values.sumOf { it.size }

        /**
         * 全量快照（对应 Android `MemoryCookieJar.snapshot`）。
         *
         * 两个用途：诊断「罐子里到底有什么」，以及 `CasTransport.cookies()` 那个只读自述口
         * （Android 的 `MemoryCookieJar.snapshot()` 是同一个形状）。
         */
        fun snapshot(): List<Cookie> = byHost.values.flatten()

        /**
         * 回灌：把一批 cookie 直接写进罐子（对应 Android `MemoryCookieJar.adopt`）。
         *
         * **分桶键用 `domain` 去掉前导点**：`Set-Cookie` 常带 `Domain=.juwp.edu.cn`，
         * 而本罐按请求 host 分桶、[get] 时再按后缀匹配父域，不去点就会落到一个谁都不查的桶里。
         * 同名同路径的旧值替换掉，避免回灌几次后请求头里出现重复 cookie。
         *
         * 为什么是同步方法而不是走 `CookiesStorage.addCookie`：那条是挂起契约，而
         * `CasTransport.adoptCookies` 是同步接口（Android 的 jar 也是同步写），
         * 回灌发生在不关心网络往返的路径上，加一层 suspend 只会让调用方被迫开协程。
         */
        fun adopt(cookies: List<Cookie>) {
            cookies.forEach { cookie ->
                val host = cookie.domain?.removePrefix(".").orEmpty()
                if (host.isEmpty()) return@forEach
                val bucket = byHost.getOrPut(host) { mutableListOf() }
                bucket.removeAll { it.name == cookie.name && it.path == cookie.path }
                bucket.add(cookie)
            }
        }
    }

    /**
     * 按 host 统计请求数的诊断插件（对应 Android 的 `DebugRequestCounter`）。
     *
     * 只在 debug 构建挂：[SharedHttp.create] 的 `countRequests` 由 `:app` 传 `BuildConfig.DEBUG`，
     * 共享层不引 `BuildConfig`（那是 Android 生成的类）。
     */
    private val COUNTS = LinkedHashMap<String, IntArray>()

    /** 见 [COUNTS]；抽成函数是为了让 `installSharedPlugins` 内联安装时逻辑只出现一次。 */
    fun newRequestCounter() = createClientPlugin("RequestCounter") {
        onRequest { request, _ ->
            val host = request.url.host
            val n = COUNTS.getOrPut(host) { intArrayOf(0) }
            n[0]++
            Logger.d("HttpCount", "$host ← ${n[0]}")
        }
    }
}

/**
 * 造一个平台引擎的 client。
 *
 * 为什么是 `expect fun` 而不是在 commonMain 里 `HttpClient { }` + 运行期解析引擎：
 * Android 侧要用 OkHttp 的 `preconfigured { dns(Ipv4FirstDns) }` 修校园 IPv6 黑洞，
 * 而 `preconfigured` 是 `OkHttpConfig` 的成员——commonMain 里拿不到这个类型。
 * iOS 侧（Darwin / NSURLSession）没有对应开关，直接建即可。
 */
internal expect fun createPlatformHttpClient(
    requestTimeoutSeconds: Long,
    connectTimeoutSeconds: Long,
    socketTimeoutSeconds: Long,
    followRedirects: Boolean,
    cookieStorage: HttpSupport.InMemoryCookieStorage?,
    countRequests: Boolean,
): HttpClient

/** 两端共用的插件装配；只在各平台的 actual 里调用一次。 */
internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installSharedPlugins(
    requestTimeoutSeconds: Long,
    connectTimeoutSeconds: Long,
    socketTimeoutSeconds: Long,
    followRedirects: Boolean,
    cookieStorage: HttpSupport.InMemoryCookieStorage?,
    countRequests: Boolean,
) {
    // 非 2xx 不抛异常：现状各模块都是自己判 `isSuccessful` 再换算成各自的异常类型，
    // 打开这个开关会把「HTTP 500」变成 Ktor 自己的异常，错误文案就变了。
    expectSuccess = false
    // 默认跟随重定向；一卡通充值下单那类**要靠 302 的 Location 拿收银台 URL**，
    // 必须拿到原始 302 而不是落地页，所以给调用方留一个关掉的开关。
    this.followRedirects = followRedirects
    install(HttpTimeout) {
        requestTimeoutMillis = requestTimeoutSeconds * 1000
        connectTimeoutMillis = connectTimeoutSeconds * 1000
        socketTimeoutMillis = socketTimeoutSeconds * 1000
    }
    if (cookieStorage != null) {
        install(HttpCookies) { storage = cookieStorage }
    }
    if (countRequests) {
        install(HttpSupport.newRequestCounter())
    }
}
