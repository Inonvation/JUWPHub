package edu.jxslu.schedule.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Android 侧的引擎装配（iOS 移植 P1，2026-11-24）。
 *
 * 这里补的是 Ktor 不会替你做的两件事，而它们原先由 `AppHttp` 的 OkHttp base client 提供：
 *
 * 1. **IPv4 优先 DNS**。校园域名同时有 A 与 AAAA 记录，校园 IPv6 在移动网络下实测是黑洞
 *    （连接要等 TCP 超时才落回 v4，单步多等十几到三十秒）。Ktor 的 OkHttp 引擎不会自己改，
 *    必须 `preconfigured { dns(...) }`——这也是为什么 client 的创建要做成 expect/actual：
 *    `preconfigured` 是 `OkHttpConfig` 的成员，commonMain 拿不到这个类型。
 * 2. **Debug 请求计数**由共享层的 `requestCounterPlugin` 承担（`countRequests` 参数），
 *    这里不用再挂 OkHttp 的 `EventListener`。
 *
 * 连接池共享仍然成立：OkHttp 引擎自己维护连接池，本模块所有 client 都是同一份
 * [HttpSupport] 配置派生出来的，行为与 `AppHttp.base` + `newBuilder()` 一致。
 */
private val Ipv4FirstDns = object : okhttp3.Dns {
    override fun lookup(hostname: String): List<InetAddress> = ipv4First(okhttp3.Dns.SYSTEM.lookup(hostname))
}

/** v4 排前、v6 保留原序殿后；无 v4 时原样返回（与 `AppHttp.ipv4First` 逐字一致）。 */
internal fun ipv4First(all: List<InetAddress>): List<InetAddress> {
    val v4 = all.filterIsInstance<Inet4Address>()
    if (v4.isEmpty()) return all
    return v4 + (all - v4.toSet())
}

internal actual fun createPlatformHttpClient(
    requestTimeoutSeconds: Long,
    connectTimeoutSeconds: Long,
    socketTimeoutSeconds: Long,
    followRedirects: Boolean,
    cookieStorage: HttpSupport.InMemoryCookieStorage?,
    countRequests: Boolean,
): HttpClient = HttpClient(OkHttp) {
    engine {
        // 解析到的 Ktor 版本里 OkHttp 引擎给的是 config { }（OkHttpClient.Builder 作用域），preconfigured 是「直接塞一个现成 client」，不是块。
        config { dns(Ipv4FirstDns) }
    }
    installSharedPlugins(
        requestTimeoutSeconds = requestTimeoutSeconds,
        connectTimeoutSeconds = connectTimeoutSeconds,
        socketTimeoutSeconds = socketTimeoutSeconds,
        followRedirects = followRedirects,
        cookieStorage = cookieStorage,
        countRequests = countRequests,
    )
}
