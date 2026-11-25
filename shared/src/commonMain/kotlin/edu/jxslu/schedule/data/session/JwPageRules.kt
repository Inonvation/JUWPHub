package edu.jxslu.schedule.data.session

/**
 * 教务应答的两种「不算数据」形态（判据的唯一实现，两端共用）。
 *
 * 从 `:app` 的 `JwHttpSession` 提出来（2026-11-25）：iOS 的 `IosCasTransport` 用 Ktor 走
 * 同一条登录链，**必须用同一份判据**。两边各写一遍的话，最坏的情形是一边认得出登录页、
 * 另一边把登录页当课表交给解析器——那正是 `login-session.md` 里记着的两次真机事故。
 *
 * 用户没有登录时教务有三种应答，其中两种长得像正常页面，这是本文件存在的全部理由：
 *
 * 1. 860 字节的提示页，正文有「用户没有登录」；
 * 2. **就地渲染的登录页**（HTTP 200，约 79KB，title「登录」，表单 `action="/jsxsd/xk/LoginToXk"`）
 *    ——字节数阈值过得去、提示文案也没有，只能靠密码框特征认（[LOGIN_PAGE_MARKERS]）；
 * 3. 会话被别处登录顶掉（强智单会话）：HTTP 200 + `{"flag1":3,"msgContent":"您的账号在其它地方登录"}`。
 */
object JwPageRules {

    /** 正常学生主页约 150KB；退回登录提示页时只有 860 字节。 */
    const val HOME_MIN_BYTES = 20_000

    /** 历史兜底文案。按 DESIGN §4.17 实测在真实页面上出现 0 次，真正的判据是密码框。 */
    const val NOT_LOGGED = "用户没有登录"

    /**
     * 登录页特征：密码框的 id/name（实测 11 处；已登录主页为 **0**）。
     *
     * **⚠️ `LoginToXk` 不能当特征**：它是强智选课入口的通用路径，已登录主页（151KB）的
     * 「退出登录」隐藏表单 action 也是 `/jsxsd/xk/LoginToXk?method=exit`（实测出现 2 次）。
     * 把它当特征会把每个正常主页判成登录页：探针永远失败 → 完整登录 → 第 6 步校验又失败，
     * 自动续登整条瘫痪（`JwHttpSessionTest.loggedInHomeWithLogoutForm_isNotALoginPage` 钉着）。
     */
    private val LOGIN_PAGE_MARKERS = listOf("userPassword")

    private const val SESSION_CONFLICT_MESSAGE = "您的账号在其它地方登录"

    private const val SESSION_CONFLICT_KEY = "flag1"

    /** 页面是不是登录页：既含 860 字节的提示页，也含就地渲染的登录页。 */
    fun looksLikeLoginPage(html: String): Boolean =
        NOT_LOGGED in html || LOGIN_PAGE_MARKERS.any { it in html }

    /** 学生主页是不是真的登录进去了（字节数阈值 + 登录页特征）。 */
    fun isUsableHome(html: String): Boolean =
        html.length >= HOME_MIN_BYTES && !looksLikeLoginPage(html)

    /**
     * 会话是不是被别处登录顶掉了。
     *
     * 判据要**同时**命中业务文案与 `flag1` 键：单看 `flag1` 会把正常 layui 应答误判成会话失效
     * （代价：清 cookie + 白登一次 + 这次数据拿不回来），单看文案可能被任意页面正文撞上。
     */
    fun looksLikeSessionConflict(body: String): Boolean =
        SESSION_CONFLICT_MESSAGE in body && SESSION_CONFLICT_KEY in body
}
