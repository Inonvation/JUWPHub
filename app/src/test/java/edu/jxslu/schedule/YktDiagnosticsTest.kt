package edu.jxslu.schedule

import edu.jxslu.schedule.data.ykt.YktDiagnostics
import edu.jxslu.schedule.data.ykt.YktException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一卡通失败诊断的脱敏与措辞（2026-11-25，「iOS 登录只报无法连接」排查配套）。
 *
 * 钉住三件事：
 * 1. **凭证不出 App**——URL query 里的 token / 卡号 / prepay_id、响应体里的 password /
 *    cardNo / stdid 全部抹成 `***`。这些字符串会进剪贴板与用户截图。
 * 2. **片段有界**——响应体截到 300 字、空白压成一行，网关 HTML 错误页不会把截图撑爆。
 * 3. **两个分支的 detail 能分开**——`NETWORK` 带异常类名与目标地址，`HTTP` 带状态码与
 *    片段；这正是「连不上」与「服务器拒绝」的判据。
 */
class YktDiagnosticsTest {

    // ---- URL ----

    @Test
    fun `URL 只留主机路径_敏感 query 值抹掉`() {
        val url = "https://yktwx.juwp.edu.cn/berserker-app/ykt/tsm/batchGetBarCodeGet" +
            "?account=241364&payacc=000000&paytype=CARD"
        val out = YktDiagnostics.redactUrl(url)
        assertTrue(out.startsWith("https://yktwx.juwp.edu.cn/berserker-app/ykt/tsm/batchGetBarCodeGet?"))
        // account 是 6 位卡号：整值抹掉，键名保留（看得出请求带了哪几个参数）
        assertTrue(out.contains("account=***"))
        assertFalse(out.contains("241364"))
        // 非敏感键原样
        assertTrue(out.contains("paytype=CARD"))
    }

    @Test
    fun `URL 的 token 与 prepay_id 抹掉_超长值截断`() {
        val url = "https://yktwx.juwp.edu.cn/payment/?synjones-auth=bearer.abcdef123456&orderid=PO9" +
            "#frag"
        val out = YktDiagnostics.redactUrl(url)
        assertTrue(out.contains("synjones-auth=***"))
        assertFalse(out.contains("abcdef123456"))
        assertTrue(out.contains("orderid=PO9"))
        // fragment 不进诊断
        assertFalse(out.endsWith("#frag"))

        val long = "https://yktwx.juwp.edu.cn/p.json?sign=" + "a".repeat(120)
        val truncated = YktDiagnostics.redactUrl(long)
        // 非敏感但很长的值：只留前 24 字 + 省略号
        assertTrue(truncated.endsWith("a".repeat(24) + "…"))
    }

    @Test
    fun `无 query 的 URL 原样返回`() {
        assertEquals(
            "https://yktwx.juwp.edu.cn/berserker-secure/keyboard",
            YktDiagnostics.redactUrl("https://yktwx.juwp.edu.cn/berserker-secure/keyboard"),
        )
    }

    // ---- 响应体 ----

    @Test
    fun `响应体里的密码与 token 抹成星号_结构仍可读`() {
        // `$` 在 Kotlin 字符串里是模板前缀：真实密文就长这样（`密文$1$uuid`），用 ${'$'} 拼
        val body = """{"code":400,"msg":"Bad credentials","password":"8e1f${'$'}1${'$'}uuid-1",""" +
            """"access_token":"eyJhbGciOi"}"""
        val out = YktDiagnostics.redactBody(body)
        assertFalse(out.contains("8e1f\$1\$uuid-1"))
        assertFalse(out.contains("eyJhbGciOi"))
        assertTrue(out.contains("\"password\":\"***\""))
        assertTrue(out.contains("\"access_token\":\"***\""))
        // 非敏感字段保留，看得出是哪一类失败
        assertTrue(out.contains("Bad credentials"))
    }

    @Test
    fun `响应体里的卡号与学号抹掉`() {
        val body = """{"cardNo":"241364","account":"241364","stdid":"2023010203","msg":"ok"}"""
        val out = YktDiagnostics.redactBody(body)
        assertFalse(out.contains("241364"))
        assertFalse(out.contains("2023010203"))
        assertTrue(out.contains("\"msg\":\"ok\""))
    }

    @Test
    fun `表单式键值也抹掉`() {
        val out = YktDiagnostics.redactBody("password=123456&token=abc&grant_type=password")
        assertFalse(out.contains("123456"))
        assertFalse(out.contains("token=abc"))
        assertTrue(out.contains("grant_type=password"))
    }

    @Test
    fun `HTML 错误页压成一行且不超过 300 字`() {
        val html = "<html>\n  <head><title>502 Bad Gateway</title></head>\n" +
            "  <body>\n    " + "<p>nginx</p>".repeat(200) + "\n  </body>\n</html>"
        val snippet = YktDiagnostics.bodySnippet(html)
        assertEquals(YktDiagnostics.BODY_LIMIT + 1, snippet.length) // 300 字 + 省略号
        assertTrue(snippet.endsWith("…"))
        assertFalse(snippet.contains("\n"))
        assertTrue(snippet.startsWith("<html> <head><title>502 Bad Gateway</title></head>"))
    }

    @Test
    fun `空响应体给空串而不是 null`() {
        assertEquals("", YktDiagnostics.bodySnippet(""))
    }

    // ---- 异常两个分支的 detail ----

    @Test
    fun `NETWORK 的 detail 带异常类名与目标地址`() {
        val e = YktException.Network(
            targetUrl = "https://yktwx.juwp.edu.cn/berserker-auth/oauth/token",
            causeClass = "SocketTimeoutException",
            causeMessage = "Connect timeout has expired [url=..., connect_timeout=15000 ms]",
            cause = RuntimeException("boom"),
        )
        val d = e.detail
        assertTrue(d.startsWith("NETWORK · SocketTimeoutException"))
        assertTrue(d.contains("Connect timeout"))
        assertTrue(d.contains("https://yktwx.juwp.edu.cn/berserker-auth/oauth/token"))
        // 用户主文案只留类名，不带引擎原文里那一长串参数
        assertEquals("网络不可达（SocketTimeoutException）", e.message)
    }

    @Test
    fun `HTTP 的 detail 带状态码_目标地址与片段`() {
        val e = YktException.Http(
            code = 502,
            targetUrl = "https://yktwx.juwp.edu.cn/berserker-secure/keyboard",
            snippet = "502 Bad Gateway nginx",
        )
        val d = e.detail
        assertTrue(d.startsWith("HTTP 502 · https://yktwx.juwp.edu.cn/berserker-secure/keyboard"))
        assertTrue(d.endsWith("502 Bad Gateway nginx"))
        assertEquals("服务器返回 HTTP 502（不是密码问题）", e.message)
    }

    @Test
    fun `其余分支的 detail 退化成业务文案`() {
        assertEquals("查询密码错误，请重新输入（默认为身份证后六位）", YktException.Credential(
            "查询密码错误，请重新输入（默认为身份证后六位）",
        ).detail)
    }
}
