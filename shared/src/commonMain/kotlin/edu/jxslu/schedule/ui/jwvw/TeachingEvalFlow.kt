package edu.jxslu.schedule.ui.jwvw

import edu.jxslu.schedule.data.jw.JwTeachingEval
import edu.jxslu.schedule.data.jw.JwUrls

/**
 * 学生评教「取 token 再跳」那一段的判据与文案（纯 Kotlin，无平台依赖）。
 *
 * ## 这一段到底在做什么（DESIGN §4.44）
 *
 * 评教没有静态 URL：教务主页菜单走 `toZlpt_11319('jwxsjdp')`，先 POST 取一个带 JWT 的
 * 平台地址，再 `window.open` 到 `jiaowupj.juwp.edu.cn` 的 SPA。App 只把这一步搬进
 * WebView：落到教务主页后注入 [JwTeachingEval.launchJs]（**同一窗口** `location.href`），
 * 之后完全交给用户在平台上操作——App 不代评、不碰任何提交接口。
 *
 * ## 为什么注入之后还要一个看门狗
 *
 * 注入脚本是「发起即返回」：`$.ajax` 成功回调里的 `location.href` 不会让
 * `evaluateJavascript` 的返回值变样，ajax 静默失败（`$.ajax` 本身被页面改写、会话过期
 * 返回登录页 HTML）时也**不会**有任何页面事件。没有看门狗，状态条就一直停在
 * 「正在打开学生评教…」，用户对着一个不动的页面猜发生了什么。
 *
 * 代次（[attempt]）是为了刷新重试：看门狗只对**发起注入那一刻**的代次生效，否则刷新之后
 * 旧看门狗到点会给一条与事实相反的失败提示（2026-10-07 复查修）。
 */
object TeachingEvalFlow {

    /** 注入脚本的返回值档位（契约见 [JwTeachingEval.launchJs]）。 */
    enum class Injection {
        /** 已发起：`$.ajax` 在跑，跳转由页面自己的成功回调驱动。 */
        Sent,

        /** 本轮已经发起过（`window.__jwPjStarted` 为真）：不重复发。 */
        Duplicated,

        /** 页面上没有 jQuery：教务页脚本还没就绪。 */
        ScriptMissing,

        /** 返回值既不是上面几种（含 iOS 与 Android 的取值形状差异、空返回、异常）：当失败处理。 */
        NotStarted,
    }

    /** 状态条的常量文案（改动只在这里）。 */
    const val TITLE = "学生评教 · 统一认证"
    const val OPENING_NOTE = "正在打开学校统一身份认证登录…"
    const val LOGIN_NOTE = "请使用学校统一身份认证登录"
    const val LANDING_NOTE = "已登录教务主页，正在打开学生评教…"
    const val PLATFORM_NOTE = "学生评教页已打开，评教完成后返回即可。"
    const val LOGGED_IN_NOTE = "已登录教务。评教页打不开时可点右上角「刷新」重试"
    const val TIMEOUT_NOTE = "还没打开评教页：可点右上角「刷新」重试"
    const val SCRIPT_MISSING_NOTE = "教务页脚本未就绪：可点右上角「刷新」重试"
    const val INJECT_FAILED_NOTE = "没能打开评教页：可点右上角「刷新」重试"

    /**
     * 注入后的原地看门狗（[JwTeachingEval.launchJs] 是发起即返回）。
     *
     * 15 秒——比落地跳转的看门狗宽：token 往返 + 302 + SPA 首屏都在这一段里。
     */
    const val TIMEOUT_MS: Long = 15_000L

    /**
     * 解读 `evaluateJavascript` 的返回。
     *
     * 两端的取值形状不同（Android 给 JSON 字面量 `"sent"`、iOS 给 JS 值的 `toString`），
     * 所以按子串判，**不**先剥引号——剥引号的规则在 [edu.jxslu.schedule.data.jw.unwrapJsString]，
     * 而这里要的只是「三种已知档位是哪一档」。顺序上 `no-jquery` 必须最先判：它不含
     * `sent`，但将来脚本若要在这两个 token 上做组合，先判更具体的那个总不会错。
     */
    fun parseInjection(raw: String?): Injection = when {
        raw.isNullOrBlank() -> Injection.NotStarted
        "no-jquery" in raw -> Injection.ScriptMissing
        "dup" in raw -> Injection.Duplicated
        "sent" in raw -> Injection.Sent
        else -> Injection.NotStarted
    }

    /** 这一跳是否真的发起了（`Sent` / `Duplicated` 都算：重复发起说明上一轮已在途）。 */
    fun isStarted(injection: Injection): Boolean =
        injection == Injection.Sent || injection == Injection.Duplicated

    /** 注入失败时状态条该说的话。 */
    fun injectFailureNote(injection: Injection): String = when (injection) {
        Injection.ScriptMissing -> SCRIPT_MISSING_NOTE
        else -> INJECT_FAILED_NOTE
    }

    /**
     * 教务主页（登录落点）。评教没有静态目标页，注入只能落在这上面。
     *
     * 判据与 `:app` 里那条自动落页同源（`"xsMainV" in url && 教务域`）——教务未登录时会把
     * 登录页**就地渲染**在 `xsMainV` 这个 URL 上（HTTP 200、URL 不变），所以光看 URL 不够，
     * 会话探针必须先过（见页面里的 `JwSessionProbe`）。
     */
    fun isLanding(url: String?): Boolean =
        JwUrls.isJwHost(url) && url.orEmpty().contains("xsMainV")

    /**
     * 一页加载完成、还没判出别的结论时，状态条先说什么。
     *
     * 只在「这几种页面上」给确定的话；其余页面（教务处里其它随便一页）保持上一句——
     * 本窗口的入口只有评教，不该为用户随手点开的别的页面编一句解释。
     */
    fun loadedNote(url: String?): String = when {
        JwTeachingEval.isPlatformUrl(url) -> PLATFORM_NOTE
        isLanding(url) -> LANDING_NOTE
        JwUrls.isCasHost(url) -> LOGIN_NOTE
        JwUrls.isJwHost(url) -> LOGGED_IN_NOTE
        else -> OPENING_NOTE
    }

    /**
     * 看门狗到点时该不该报「还没打开」。
     *
     * 三个条件缺一不可：
     * 1. 这一轮注入还挂着（[pending]）——落到平台页 / 注入当场就失败时已经清掉了；
     * 2. 代次没变（[attempt] == [currentAttempt]）——刷新重试会开新代次，旧看门狗作废；
     * 3. 页面仍停在落点上——已经跳到平台（或用户自己点了别处）就不该再说「还没打开」。
     */
    fun stillStuck(attempt: Int, currentAttempt: Int, url: String?, pending: Boolean): Boolean =
        pending && attempt == currentAttempt && isLanding(url)
}
