package edu.jxslu.schedule.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS 侧的引擎装配（iOS 移植 P1，2026-11-24）。
 *
 * 用 Darwin（NSURLSession）：
 * - 没有 OkHttp 的 `preconfigured` / `dns` 开关，IPv4 优先这件事在 iOS 上交给系统——
 *   NSURLSession 的 Happy Eyeballs 会并行尝试 v4/v6 并取先连上的，不会像 Android 那样
 *   等满一个 TCP 超时才落回 v4。所以这里**不需要**对应的补偿逻辑，不是漏做。
 * - 连接池与 Cookie 都由 NSURLSession 自己管；但会话 cookie 的回放仍然走
 *   `HttpCookies` 插件（[installSharedPlugins]），因为跨 client 实例要共享 CAS 会话。
 */
internal actual fun createPlatformHttpClient(
    requestTimeoutSeconds: Long,
    connectTimeoutSeconds: Long,
    socketTimeoutSeconds: Long,
    followRedirects: Boolean,
    cookieStorage: HttpSupport.InMemoryCookieStorage?,
    countRequests: Boolean,
): HttpClient = HttpClient(Darwin) {
    installSharedPlugins(
        requestTimeoutSeconds = requestTimeoutSeconds,
        connectTimeoutSeconds = connectTimeoutSeconds,
        socketTimeoutSeconds = socketTimeoutSeconds,
        followRedirects = followRedirects,
        cookieStorage = cookieStorage,
        countRequests = countRequests,
    )
}
