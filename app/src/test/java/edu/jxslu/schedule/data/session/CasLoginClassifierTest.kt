package edu.jxslu.schedule.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CAS 应答分类（DESIGN §4.27）。
 *
 * 最关键的一条：**「无 Location 的 200」不能一律当凭证错**——旧实现就是这么写的，
 * 于是验证码页会把对的密码记成错的，累计两次还把账号停用。
 */
class CasLoginClassifierTest {

    @Test
    fun redirectWithLocation_isSuccess() {
        val r = CasLoginClassifier.classify(302, "https://portal.juwp.edu.cn/cas/login_portal", "")
        assertEquals(CasLoginResult.Success("https://portal.juwp.edu.cn/cas/login_portal"), r)
    }

    @Test
    fun serverError_isNotCounted() {
        assertTrue(CasLoginClassifier.classify(500, null, "500 error") is CasLoginResult.ServerError)
        assertTrue(CasLoginClassifier.classify(503, null, "") is CasLoginResult.ServerError)
    }

    @Test
    fun captchaMarkers_areDetected() {
        assertTrue(
            CasLoginClassifier.classify(200, null, "<input id=\"captchaImg\">") is CasLoginResult.NeedCaptcha,
        )
        assertTrue(
            CasLoginClassifier.classify(200, null, "<div>请输入验证码</div>") is CasLoginResult.NeedCaptcha,
        )
    }

    @Test
    fun credentialMarkers_areDetected() {
        assertTrue(
            CasLoginClassifier.classify(200, null, "<div>用户名或密码错误</div>") is
                CasLoginResult.CredentialWrong,
        )
    }

    /** 302 没带 Location、正文是错误页：仍要能认出是凭证错（浏览器会自动跳，OkHttp 不跟）。 */
    @Test
    fun redirectWithoutLocation_fallsBackToBody() {
        assertTrue(
            CasLoginClassifier.classify(200, null, "bad credentials") is
                CasLoginResult.CredentialWrong,
        )
    }

    /** 认不出的 200 一律 Unknown（转 WebView），绝不能落到凭证错。 */
    @Test
    fun unknownBody_isUnknownNotCredential() {
        val r = CasLoginClassifier.classify(200, null, "<html><body>welcome</body></html>")
        assertTrue(r is CasLoginResult.Unknown)
    }

    /** 「登录失败」这类泛化文案不收：它也可能是验证码过不了。 */
    @Test
    fun genericFailureText_isNotCredential() {
        assertTrue(CasLoginClassifier.classify(200, null, "登录失败") is CasLoginResult.Unknown)
    }

    /** 大写变形也要能认（服务端大小写不定）。 */
    @Test
    fun markers_areCaseInsensitive() {
        assertTrue(CasLoginClassifier.classify(200, null, "Bad Credentials") is CasLoginResult.CredentialWrong)
        assertTrue(CasLoginClassifier.classify(200, null, "CAPTCHA required") is CasLoginResult.NeedCaptcha)
    }

    /**
     * 2026-10-24 实测（假账号探针）：本校 CAS 错误凭证返回 **401** + 重渲染的登录页，
     * 页面没有任何凭证文案、却带 captcha 脚本字样——旧排序把它判成 NeedCaptcha，
     * 所有密码输错都提示「要求输入验证码」。401 必须直接落凭证错。
     */
    @Test
    fun unauthorized401_isCredentialWrong_evenWithCaptchaMarkup() {
        val failurePage = "<html><script>function getCaptcha(){</script>" +
            "<div>You have not logged in for more than 90 days.</div></html>"
        val r = CasLoginClassifier.classify(401, null, failurePage)
        assertTrue(r is CasLoginResult.CredentialWrong)
    }

    /** 凭证错特征先于验证码特征：两样都在的页面（失败重渲染的登录页）按凭证错算。 */
    @Test
    fun credentialMarkers_winOverCaptchaMarkers() {
        val page = "<input id=\"captchaImg\"><div>用户名或密码错误</div>"
        assertTrue(CasLoginClassifier.classify(200, null, page) is CasLoginResult.CredentialWrong)
    }
}
