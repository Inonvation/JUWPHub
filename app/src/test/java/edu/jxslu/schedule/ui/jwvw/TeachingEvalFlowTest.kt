package edu.jxslu.schedule.ui.jwvw

import edu.jxslu.schedule.data.jw.JwUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评教「取 token 再跳」这一段的状态机（DESIGN §4.44，2026-11-26 随页面搬进共享层）。
 *
 * 钉的是三条**今天靠真机才看得出来**的判据：
 * ① 注入返回值怎么读——两端 `evaluateJavascript` 的形状不同（Android 给 JSON 字面量、
 *    iOS 给 JS 的 `toString`），读错就会把「已经发起」当失败，或者反过来；
 * ② 看门狗什么时候该报「还没打开」——代次 / 在途 / 仍停在落点，三个条件缺一不可，
 *    缺了就会给一条与事实相反的告警（2026-10-07 复查修过的那个 bug）；
 * ③ 哪种页面算「落地」——教务未登录时把登录页**就地渲染在 `xsMainV` 这个 URL 上**，
 *    所以落地判据只管「注入该不该发」，会话可用性另由会话探针回答。
 *
 * 注入之后页面到底长什么样、平台 SPA 能不能用，只能真机点验，不在单测口径内。
 */
class TeachingEvalFlowTest {

    // ---- 注入返回值 ----

    @Test
    fun parseAndroidQuotedSent() {
        // Android 的 evaluateJavascript 回 JSON 字面量：字符串带引号
        assertEquals(TeachingEvalFlow.Injection.Sent, TeachingEvalFlow.parseInjection("\"sent\""))
    }

    @Test
    fun parseIosBareSent() {
        // iOS 回的是 JS 值的 toString：没有引号
        assertEquals(TeachingEvalFlow.Injection.Sent, TeachingEvalFlow.parseInjection("sent"))
    }

    @Test
    fun parseDuplicated() {
        assertEquals(TeachingEvalFlow.Injection.Duplicated, TeachingEvalFlow.parseInjection("\"dup\""))
        assertEquals(TeachingEvalFlow.Injection.Duplicated, TeachingEvalFlow.parseInjection("dup"))
    }

    @Test
    fun parseScriptMissing() {
        assertEquals(
            TeachingEvalFlow.Injection.ScriptMissing,
            TeachingEvalFlow.parseInjection("\"no-jquery\""),
        )
    }

    @Test
    fun parseUnknownAndBlankAreNotStarted() {
        assertEquals(TeachingEvalFlow.Injection.NotStarted, TeachingEvalFlow.parseInjection(null))
        assertEquals(TeachingEvalFlow.Injection.NotStarted, TeachingEvalFlow.parseInjection(""))
        assertEquals(TeachingEvalFlow.Injection.NotStarted, TeachingEvalFlow.parseInjection("   "))
        // JS 抛异常时两端都可能给 null 或 "null"
        assertEquals(TeachingEvalFlow.Injection.NotStarted, TeachingEvalFlow.parseInjection("null"))
    }

    @Test
    fun startedCoversSentAndDuplicate() {
        assertTrue(TeachingEvalFlow.isStarted(TeachingEvalFlow.Injection.Sent))
        assertTrue(TeachingEvalFlow.isStarted(TeachingEvalFlow.Injection.Duplicated))
        assertFalse(TeachingEvalFlow.isStarted(TeachingEvalFlow.Injection.ScriptMissing))
        assertFalse(TeachingEvalFlow.isStarted(TeachingEvalFlow.Injection.NotStarted))
    }

    /**
     * 失败文案按档位分：`no-jquery` 是「脚本没就绪」（刷新即可），其余是「没能打开」
     * ——两者给用户的下一步动作不同，不能合成一句。
     */
    @Test
    fun failureNoteSplitsOnScriptReadiness() {
        assertEquals(
            TeachingEvalFlow.SCRIPT_MISSING_NOTE,
            TeachingEvalFlow.injectFailureNote(TeachingEvalFlow.Injection.ScriptMissing),
        )
        assertEquals(
            TeachingEvalFlow.INJECT_FAILED_NOTE,
            TeachingEvalFlow.injectFailureNote(TeachingEvalFlow.Injection.NotStarted),
        )
    }

    // ---- 落地判据 ----

    @Test
    fun landingIsJwMainPage() {
        assertTrue(TeachingEvalFlow.isLanding(JwUrls.XSD_BASE + "/jsxsd/framework/xsMainV.jsp"))
        // 教务其它页不是落点：评教注入靠的是主页里那份 jQuery 与同源 cookie
        assertFalse(TeachingEvalFlow.isLanding(JwUrls.SCHEDULE_LIST))
        assertFalse(TeachingEvalFlow.isLanding(JwUrls.SSO_WARMUP))
        // 评教平台自己也不是落点（已经在平台上了，不该再注入）
        assertFalse(TeachingEvalFlow.isLanding("http://jiaowupj.juwp.edu.cn/jwxsjdp"))
        assertFalse(TeachingEvalFlow.isLanding(null))
        assertFalse(TeachingEvalFlow.isLanding(""))
    }

    /** 域名必须是教务域：别的站点上碰巧含 `xsMainV` 不该被当落点。 */
    @Test
    fun landingRequiresJwHost() {
        assertFalse(TeachingEvalFlow.isLanding("https://example.com/xsMainV"))
    }

    // ---- 状态条 ----

    @Test
    fun loadedNoteFollowsPageKind() {
        assertEquals(
            TeachingEvalFlow.PLATFORM_NOTE,
            TeachingEvalFlow.loadedNote("http://jiaowupj.juwp.edu.cn/jwxsjdp"),
        )
        assertEquals(
            TeachingEvalFlow.LANDING_NOTE,
            TeachingEvalFlow.loadedNote(JwUrls.XSD_BASE + "/jsxsd/framework/xsMainV.jsp"),
        )
        assertEquals(TeachingEvalFlow.LOGIN_NOTE, TeachingEvalFlow.loadedNote(JwUrls.ENTRY))
        assertEquals(TeachingEvalFlow.LOGGED_IN_NOTE, TeachingEvalFlow.loadedNote(JwUrls.SCORE_FRM))
        // 都不认识（例如门户 / 空白）：给入口那句，别编一句解释
        assertEquals(TeachingEvalFlow.OPENING_NOTE, TeachingEvalFlow.loadedNote(null))
    }

    // ---- 看门狗 ----

    @Test
    fun stuckOnlyWhenPendingSameAttemptStillLanding() {
        val landing = JwUrls.XSD_BASE + "/jsxsd/framework/xsMainV.jsp"
        assertTrue(TeachingEvalFlow.stillStuck(3, 3, landing, pending = true))
    }

    /** 刷新重试会开新代次：旧看门狗到点即废（否则会给一条与事实相反的失败提示）。 */
    @Test
    fun staleAttemptIsNotStuck() {
        assertFalse(
            TeachingEvalFlow.stillStuck(
                3,
                4,
                JwUrls.XSD_BASE + "/jsxsd/framework/xsMainV.jsp",
                pending = true,
            ),
        )
    }

    @Test
    fun navigatedToPlatformIsNotStuck() {
        assertFalse(
            TeachingEvalFlow.stillStuck(
                1,
                1,
                "http://jiaowupj.juwp.edu.cn/jwxsjdp",
                pending = true,
            ),
        )
    }

    @Test
    fun alreadySettledIsNotStuck() {
        assertFalse(
            TeachingEvalFlow.stillStuck(
                1,
                1,
                JwUrls.XSD_BASE + "/jsxsd/framework/xsMainV.jsp",
                pending = false,
            ),
        )
    }
}
