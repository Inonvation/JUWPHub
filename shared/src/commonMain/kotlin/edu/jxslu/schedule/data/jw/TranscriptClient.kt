package edu.jxslu.schedule.data.jw

import edu.jxslu.schedule.data.net.SharedHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException

/** 一次表单 POST 的结果。只有 HTTP 状态码与原始字节，如何解读交给 [TranscriptClient]。 */
data class TranscriptResponse(val code: Int, val bytes: ByteArray) {

    // data class 里有 ByteArray 必须自己写 equals/hashCode，否则是引用比较（单测断言会莫名失败）
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is TranscriptResponse && code == other.code && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * code + bytes.contentHashCode()

    /** 应答正文按 UTF-8 解出文本（失败提示是 HTML/JSON，都是 UTF-8）。 */
    fun text(): String = bytes.decodeToString()
}

/** HTTP 传输层。抽出来是为了让导出流程能在 JVM 单测里跑完整分支（不联网）。 */
fun interface TranscriptTransport {
    suspend fun post(
        url: String,
        form: List<Pair<String, String>>,
        cookie: String?,
    ): TranscriptResponse
}

/**
 * 签章系统成绩单导出（DESIGN §4.25）。契约与坑见 [PtworkTranscript]。
 *
 * 会话不走账号密码：[cookie] 由调用方从 WebView 的 CookieManager 取来（授权窗口走一遍 CAS
 * 就有了），本类既不存凭证也不碰 WebView。取 Cookie 与重登都由 UI 驱动，这个类只负责
 * 「有条件 → 拿 PDF」这一段，并且保证**拿不到真 PDF 就不返回**。
 *
 * ## iOS 移植 S9（2026-11-24）：搬进 `shared/commonMain`
 *
 * 本类只依赖 [TranscriptTransport] 这一条缝，所以整段可以共享；Android 专属的部分只剩
 * 「从 WebView 读 `sid`」，它留在 `:app` 的 `TranscriptCookies`（`android.webkit`）。
 * iOS 要接这条链路时缺的不是这里，而是 WKWebView 的 cookie 存储——见那边的 KDoc。
 */
class TranscriptClient(
    private val transport: TranscriptTransport = KtorTranscriptTransport(),
) {

    /**
     * 拉取「有成绩的学期 + 门数」。按 `pages` 翻完全部（服务端固定 15 行一页，`limit` 无效）。
     *
     * 失败分类：会话失效抛 [TranscriptException.SessionExpired]，其余见各类。
     */
    suspend fun fetchTerms(cookie: String?): List<TranscriptTerm> {
        val rows = ArrayList<TranscriptCourse>()
        var pageNo = 1
        var total = 0
        // 连同 MAX_PAGES 一起当循环条件：写在循环体末尾再 break 会多打一次接口（翻页号先自增）
        while (pageNo <= PtworkTranscript.MAX_PAGES) {
            val page = requestList(terms = emptyList(), page = pageNo, cookie = cookie)
            rows += page.rows
            total = page.total
            // 三个出口：翻到服务端说的末页、已收满 total、这一页什么都没有。
            // 后两个是防脏数据（pages 报得比实际大时不必空转），MAX_PAGES 兜底
            if (pageNo >= page.pages || rows.size >= total || page.rows.isEmpty()) break
            pageNo++
        }
        return PtworkTranscript.termsFrom(rows)
    }

    /**
     * 导出 PDF。两步：先按 [terms] 换取 pagePri，再回传它拿字节流。
     *
     * **导出前用 `total` 当闸门**：无成绩的学期服务端同样会给 pagePri，直接导出会失败；
     * 若 token 解不开，服务端会返回一张**空白却带章**的模板 PDF——那种文件绝不能落到用户手里
     * （实测 `dysj` 传垃圾串时返回 60KB 的空白模板，0 门课，照样有签章注释）。
     *
     * @param terms 目标学期（空列表 = 全部学期）。顺序不影响结果，服务端按学期升序排版。
     */
    suspend fun export(terms: List<String>, cookie: String?): ByteArray {
        val page = requestList(terms = terms, page = 1, cookie = cookie)
        if (page.total <= 0) {
            throw if (terms.isEmpty()) {
                TranscriptException.NoContent("教务里没有可导出的成绩")
            } else {
                TranscriptException.NoContent("所选学期没有成绩，换个学期再导")
            }
        }
        if (page.pagePri.isBlank()) {
            throw TranscriptException.Protocol("列表接口没有返回导出令牌")
        }
        val response = post(PtworkTranscript.BASE + PtworkTranscript.PRINT_PATH,
            PtworkTranscript.printForm(page.pagePri), cookie)
        if (PtworkTranscript.isPdf(response.bytes)) return response.bytes
        throw PtworkTranscript.classifyNonPdf(response.text())
    }

    private suspend fun requestList(
        terms: List<String>,
        page: Int,
        cookie: String?,
    ): TranscriptPage {
        val response = post(
            PtworkTranscript.BASE + PtworkTranscript.LIST_PATH,
            PtworkTranscript.listForm(terms, page),
            cookie,
        )
        val text = response.text()
        // 会话失效时服务端是 302 到登录页，跟随之后拿到 200 的登录页 HTML；
        // 先按内容判定，再交给 JSON 解析（否则会被报成「不是 JSON」这种误导性错误）
        if (PtworkTranscript.looksLikeLoginPage(text)) throw TranscriptException.SessionExpired()
        return PtworkTranscript.parsePage(text)
    }

    /**
     * 传输失败一律归成 [TranscriptException.Network]。
     *
     * 迁移前这里 `catch (java.io.IOException)`——`commonMain` 看不见 JDK，改为
     * 「先原样抛出 [CancellationException]，再把其余 `Exception` 收成 Network」，
     * 与 `PowerClient.send` 同一口径。对调用方没有行为变化：**协程取消仍然不被吞**
     * （吞掉它会让上层 `withTimeoutOrNull` 判不出超时，报成「网络错误」）。
     */
    private suspend fun post(
        url: String,
        form: List<Pair<String, String>>,
        cookie: String?,
    ): TranscriptResponse = try {
        transport.post(url, form, cookie)
    } catch (e: CancellationException) {
        throw e
    } catch (e: TranscriptException) {
        throw e
    } catch (e: Exception) {
        throw TranscriptException.Network(e)
    }
}

/**
 * 传输层的 Ktor 实现（iOS 移植 S9，2026-11-24；替换掉原来的 `OkHttpTranscriptTransport`）。
 *
 * 原实现从 `AppHttp.base` 派生 OkHttp client：超时 connect 20s / read 60s（出单在服务端要跑
 * 一次 Excel → PDF 转换，PDF 约 350KB）。Ktor 侧没有独立的写超时，写与读同受 socket 管
 * （写请求体最大几百字节，用不完 60s），总上限仍取 120s 与 `AppHttp.base.callTimeout` 对齐。
 *
 * IPv4 优先 DNS 不用在这儿补：它已经是引擎级配置（`:shared` 的 androidMain 在
 * `createPlatformHttpClient` 里给 OkHttp 引擎挂了 `dns(Ipv4FirstDns)`）——校园域名同时有
 * A 与 AAAA 记录，校园 IPv6 在移动网络下实测是黑洞。iOS 侧由 NSURLSession 的
 * Happy Eyeballs 自己并行试，见 `HttpEngine.ios.kt`。
 *
 * 三处口径差异，写下来免得以为与 OkHttp 逐字等价：
 * - **表单转义**：OkHttp 的 `FormBody` 把空格编成 `+`，[FormDataContent] 编成 `%20`。
 *   本链路唯一的「带空格的值」是 `pagePri`（服务端把 base64 的 `+` 换成了空格），
 *   两种写法在标准 `x-www-form-urlencoded` 解码下都是空格，Java 侧（金格系统）两种都认。
 *   若某天出单开始报「令牌无效」，先怀疑这一条。
 * - **重复键**：`Parameters.build` 保留同名的多个值，`xnxq` 多学期仍是重复字段，
 *   与 `FormBody.add` 一致。
 * - **重定向**：见 [post] 里的「POST 的 302 要自己补一跳」。
 */
class KtorTranscriptTransport(
    private val http: HttpClient = SharedHttp.create(
        requestTimeoutSeconds = 120,
        // 校园网建连慢（CAS/门户都是明文 + 弱口令机房），20s 与 OkHttp 原值一致
        connectTimeoutSeconds = 20,
        socketTimeoutSeconds = 60,
    ),
) : TranscriptTransport {

    /**
     * 发一次表单 POST，并按 OkHttp 的老口径跟随重定向。
     *
     * ⚠️ **Ktor 默认不跟随 POST 的重定向**：它的 `HttpRedirect` 插件开着
     * `checkHttpMethod`，`ALLOWED_FOR_REDIRECT` 只有 GET/HEAD——POST 收到的 302 会原样
     * 回给调用方（OkHttp 则是把 302 降级成 GET 再跟随）。这一跳必须自己补，否则 `sid`
     * 过期时拿到的是「302 + 空正文」，[TranscriptClient.requestList] 会把它判成
     * 「列表接口返回的不是 JSON」而不是 [TranscriptException.SessionExpired]——用户会看到
     * 「接口变了」这种误导性提示。
     *
     * 只在**第一跳**手工跟随：后续链路已经是 GET，Ktor 会自己跟完。Location 用 Ktor 自己的
     * `URLBuilder.takeFrom` 解析（与它内部跟重定向是同一套解析），绝对地址直接透传。
     */
    override suspend fun post(
        url: String,
        form: List<Pair<String, String>>,
        cookie: String?,
    ): TranscriptResponse {
        var response = send(url, HttpMethod.Post, form, cookie)
        var current = url
        var hops = 0
        while (response.status.value in 300..399) {
            val location = response.headers[HttpHeaders.Location] ?: break
            if (hops++ >= MAX_REDIRECTS) {
                // 交回给 TranscriptClient 归成「网络错误」：与 OkHttp 跟跳超限
                // （IOException: Too many follow-up requests）的对外表现一致
                throw IllegalStateException("重定向次数过多，链路可能已变")
            }
            // 丢弃重定向响应体，让引擎把连接还回池子后再发下一跳
            runCatching { response.bodyAsBytes() }
            current = if (location.startsWith("http://") || location.startsWith("https://")) {
                location
            } else {
                URLBuilder(current).takeFrom(location).buildString()
            }
            // 降级为 GET：302 之后重发 POST 会丢原表单语义（同 JwHttpSession 的既有口径）
            response = send(current, HttpMethod.Get, emptyList(), cookie)
        }
        return TranscriptResponse(response.status.value, response.bodyAsBytes())
    }

    private suspend fun send(
        url: String,
        method: HttpMethod,
        form: List<Pair<String, String>>,
        cookie: String?,
    ): HttpResponse = http.request(url) {
        this.method = method
        header(HttpHeaders.UserAgent, UA)
        if (!cookie.isNullOrBlank()) header(HttpHeaders.Cookie, cookie)
        if (method == HttpMethod.Post) {
            setBody(
                FormDataContent(
                    Parameters.build { form.forEach { (name, value) -> append(name, value) } },
                ),
            )
        }
    }

    private companion object {
        /** 与 `JwHttpSession.MAX_REDIRECTS` 同值：挡住 Location 打转时不至于空转。 */
        const val MAX_REDIRECTS = 15

        const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
