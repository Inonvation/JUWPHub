package edu.jxslu.schedule.data.net

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/**
 * 跨平台 HTTP 客户端（iOS 移植 P1，2026-11-24）。
 *
 * `:app` 那份 `AppHttp` 是 OkHttp 专用的（`Dns` / `Dispatcher` / `Interceptor` 全是 JVM 类型），
 * iOS 上没有。这里只保留**两端都成立**的那部分：一份进程共享的 client + 超时口径。
 *
 * 引擎不在这里指定：`:shared` 的 `androidMain` 引 `ktor-client-okhttp`、`iosMain` 引
 * `ktor-client-darwin`，具体引擎与它的平台开关由 [createPlatformHttpClient] 各端实现
 * （Android 要在引擎上挂 IPv4 优先 DNS），commonMain 只负责两端口径一致的插件装配。
 *
 * ## 与 `AppHttp` 的口径对应关系
 *
 * | `AppHttp` | 这里 |
 * |---|---|
 * | base 的 `connectTimeout(15s)` | [CONNECT_TIMEOUT_SECONDS] |
 * | base 的 `readTimeout/writeTimeout(20s)` | [SOCKET_TIMEOUT_SECONDS] |
 * | `callTimeout(120s)` 总上限 | [REQUEST_TIMEOUT_SECONDS] |
 * | `withCallTimeout(20)` 派生 | 需要更短的链路自己传 `create(…)` |
 *
 * Android 侧的连接池共享仍然成立：Ktor 的 OkHttp 引擎只有一个实例（[base]），
 * 各模块拿的都是它。
 */
object SharedHttp {

    /** 建立连接的上限。校园网 IPv6 黑洞那类问题靠它快速失败（k=v4 优先由引擎侧 DNS 处理）。 */
    const val CONNECT_TIMEOUT_SECONDS: Long = 15

    /** 单次读写间隔的上限。 */
    const val SOCKET_TIMEOUT_SECONDS: Long = 20

    /** 整个请求的总上限，封死「慢滴流响应体无限挂死」。 */
    const val REQUEST_TIMEOUT_SECONDS: Long = 120

    /**
     * 进程共享的 client。**惰性创建**：模块首次用到时才建引擎，
     * 避免冷启动时为一个可能用不上的网络模块付初始化成本。
     */
    val base: HttpClient by lazy { create() }

    /**
     * 造一个新 client。默认用上面三个常量；需要更短的链路（下单/支付/读表）自己收紧。
     *
     * 注意每调一次就是一个**独立实例**（独立连接池），所以模块内应该只建一次并存下来，
     * 别在请求路径上反复调。
     */
    fun create(
        requestTimeoutSeconds: Long = REQUEST_TIMEOUT_SECONDS,
        connectTimeoutSeconds: Long = CONNECT_TIMEOUT_SECONDS,
        socketTimeoutSeconds: Long = SOCKET_TIMEOUT_SECONDS,
        followRedirects: Boolean = true,
        cookieStorage: HttpSupport.InMemoryCookieStorage? = null,
        countRequests: Boolean = false,
    ): HttpClient = createPlatformHttpClient(
        requestTimeoutSeconds = requestTimeoutSeconds,
        connectTimeoutSeconds = connectTimeoutSeconds,
        socketTimeoutSeconds = socketTimeoutSeconds,
        followRedirects = followRedirects,
        cookieStorage = cookieStorage,
        countRequests = countRequests,
    )
}
