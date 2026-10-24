package edu.jxslu.schedule.data.session

/** CAS 提交凭证的结果分类（DESIGN §4.27「CAS 登录结果必须分四类」）。 */
sealed interface CasLoginResult {

    /** 302 且有 Location：凭证被接受，继续走 SSO 链。 */
    data class Success(val location: String) : CasLoginResult

    /** 服务端明确说凭证不对。**只有这一类计入失败计数**。 */
    data class CredentialWrong(val message: String) : CasLoginResult

    /** 命中验证码特征：密码可能是对的，停手转 WebView，不计失败。 */
    data class NeedCaptcha(val message: String) : CasLoginResult

    /**
     * 响应 200 但认不出是什么（页面结构变了、被 WAF 拦下…）。
     *
     * 与 [NeedCaptcha] 一样转 WebView、一样不计失败——**宁可多跳一次网页，
     * 也不能把对的密码记成错的**（这就是旧实现把「无 Location」一律当凭证错的毛病）。
     */
    data class Unknown(val message: String) : CasLoginResult

    /** 5xx：平台侧问题，不计失败。 */
    data class ServerError(val message: String) : CasLoginResult
}

/**
 * 依据 HTTP 状态码与响应体判定 CAS 登录结果（DESIGN §4.27）。
 *
 * 2026-10-24 实测探针（假账号 + 错密码，`scripts/out/01_cas_login_page.html` 为登录页样本）：
 * **错误凭证返回 HTTP 401**，应答是重渲染的登录页——页面里没有任何凭证错文案，
 * 但带着 `getCaptcha()` 脚本字样。据此定两条硬规则：
 * 1. **401 即凭证错**。它是唯一可靠的拒绝信号；若学校日后启用验证码流程，挑战页会以
 *    200（或重定向）渲染，仍走下方 body 特征，不会撞上这条。
 * 2. **凭证错特征先于验证码特征比对**。登录页本身就含 captcha 字样，先查验证码会把
 *    任何重渲染的登录页（包括明确写了「密码错误」的）都判成 NeedCaptcha——上线以来
 *    所有密码输错都被提示成「要求输入验证码」就是这条顺序造成的。
 * 保守原则不变：宁可多跳一次网页，也不能把对的密码记成错的。
 */
object CasLoginClassifier {

    /** 验证码相关特征（小写比对）。 */
    private val CAPTCHA_MARKERS = listOf(
        "captcha",
        "jcaptcha",
        "checkcode",
        "validatecode",
        "verificationcode",
        "验证码",
        "看不清",
    )

    /**
     * 凭证错的文案特征。
     *
     * 只收「明确指向凭证」的短语：像「登录失败」这种既可能是密码错、也可能是验证码过的
     * 泛化文案，一律不收——误判成凭证错会把账号试到停用。
     */
    private val CREDENTIAL_MARKERS = listOf(
        "用户名或密码错误",
        "账号或密码错误",
        "用户名密码错误",
        "密码错误",
        "密码不正确",
        "bad credentials",
        "authentication failed",
    )

    fun classify(httpCode: Int, location: String?, body: String?): CasLoginResult = when {
        httpCode >= 500 -> CasLoginResult.ServerError("统一认证返回 HTTP $httpCode")
        httpCode in 300..399 && !location.isNullOrBlank() ->
            CasLoginResult.Success(location)
        httpCode == 401 -> CasLoginResult.CredentialWrong("统一认证登录失败（账号或密码错误）")
        else -> classifyBody(httpCode, body.orEmpty())
    }

    private fun classifyBody(httpCode: Int, body: String): CasLoginResult {
        val lower = body.lowercase()
        if (CREDENTIAL_MARKERS.any { it in lower }) {
            return CasLoginResult.CredentialWrong("统一认证登录失败（账号或密码错误）")
        }
        if (CAPTCHA_MARKERS.any { it in lower }) {
            return CasLoginResult.NeedCaptcha("统一认证要求输入验证码")
        }
        return CasLoginResult.Unknown("统一认证返回了认不出的页面（HTTP $httpCode）")
    }
}
