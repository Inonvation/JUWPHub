package edu.jxslu.schedule.data.session

/**
 * 一条会话 Cookie 的中性形态（`CasTransport` 的 cookie 交换单位）。
 *
 * ## 为什么不是 `okhttp3.Cookie` / `io.ktor.http.Cookie`
 *
 * `CasTransport` 进 `shared/commonMain` 之后要跨到 iOS：OkHttp 的类型在那边不存在，
 * Ktor 的类型在 `:app` 的 `MemoryCookieJar` 里又用不上。往返一次会话只需要五个字段
 * （名字、值、落在哪个 host、path、secure），两端的 jar 各自保留自己的模型，
 * 只在接口边界转一次——比把两套 jar 统一到同一个类型上改动小得多。
 *
 * 五个字段一个都不能少：
 * - [host] 是分桶键（`MemoryCookieJar` / `InMemoryCookieStorage` 都按 host 归档，
 *   CAS 在 `eapp2.`、教务在 `jiaowu.`，同名 `JSESSIONID` 不许互相污染）；
 * - [path] 决定回放范围（CAS 的 `TGC` 在 `/cas/`、教务的 `JSESSIONID` 在 `/jsxsd`）；
 * - [secure] 在 OkHttp 侧本来就是 `Cookie` 必填项，丢了两端构造不出可用的 cookie。
 *
 * **不带过期时间**：两端的 jar 都不做过期淘汰（服务端过期时回 302/401，各自有分支），
 * 多一个字段只会多一处「两端不一致」的余地。
 */
data class SessionCookie(
    val name: String,
    val value: String,
    val host: String,
    val path: String = "/",
    val secure: Boolean = false,
)
