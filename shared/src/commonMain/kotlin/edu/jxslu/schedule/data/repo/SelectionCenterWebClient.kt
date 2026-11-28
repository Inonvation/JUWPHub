package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.jw.SelectionCenterParser
import edu.jxslu.schedule.data.session.CasFetchResult
import edu.jxslu.schedule.data.session.JwReadSession
import edu.jxslu.schedule.domain.AutoSyncRules
import edu.jxslu.schedule.domain.SelectionCourse
import edu.jxslu.schedule.domain.SelectionQuery
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.delay

/**
 * 选课中心客户端（跨平台版，2026-11-29）：跑在共享层的 [JwReadSession] 上，iOS 用它补齐原生选课面。
 *
 * ## 与 `:app` 的 `SelectionCenterHttpClient` 是什么关系
 *
 * 同一条链路、同一套解析（[SelectionCenterParser] 一直在共享层），差别只在「谁发请求」：
 * Android 那份绑 `CasSession`（OkHttp）并在 `:app` 里，iOS 没有 OkHttp，所以这里按
 * [JwReadSession] 重写一遍编排——请求形状、参数、三态映射逐条照抄，**不新增协议知识**。
 *
 * 两份实现并存是刻意的：Android 的 `fetchForAction` 有「写操作不重登重发」这一档，换到这里
 * 会牵动 `JwReadSession` 的接口形状（见下方 shortcut），不是本轮该做的事。
 *
 * ## 三条纪律（与 Android 版一致）
 *
 * 1. **进入轮次是会话前提**：先 GET「进入选课」页 + 列表壳页（校验窗口状态、读验证码开关），
 *    之后进程内复用。撞教务 404 页 = 会话重登后丢了这个状态，读路径会重进一次再试。
 * 2. **验证码直接停**：本轮启用验证码选课就报不可用，不绕。
 * 3. **写操作不自动重试**（本版是 `fetchHtml`，见 shortcut）：提交 / 退课只发一次；失败原样报，
 *    由用户决定要不要再点——重复提交课程在教务侧会被拒，重复退课是幂等的。
 *
 * ## shortcut
 *
 * 写路径走 [JwReadSession.fetchHtml]（会话失效时它可能重登后重发一次），而不是 Android 的
 * `fetchForAction`。升级条件：`JwReadSession` 增加「写语义 GET」方法（`CasSession` 已有
 * `fetchForAction`，只需把返回类型统一成 [CasFetchResult]），届时本类的 `runAction` 改一行。
 */
class SelectionCenterWebClient(
    private val session: JwReadSession,
    /** 关键词之间的礼貌间隔（默认走项目统一的 200–600ms 随机；仅测试注入用）。 */
    private val pause: suspend () -> Unit = { delay(AutoSyncRules.interRequestDelayMs()) },
) : SelectionCenterClient {

    /** 已「进入」过的轮次（进程内）；读路径撞 404 会清掉它重进一次。 */
    private val prepared = mutableSetOf<String>()

    override suspend fun fetchCourses(roundId: String, keywords: List<String>): List<SelectionCourse> {
        requireRound(roundId)
        val clean = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return emptyList()
        try {
            prepare(roundId)
            val merged = LinkedHashMap<String, SelectionCourse>()
            clean.forEachIndexed { index, keyword ->
                if (index > 0) pause()
                fetchKeyword(merged, roundId, keyword)
            }
            return merged.values.toList()
        } catch (e: SelectionClientException) {
            if (e.fatal) prepared.remove(roundId)
            throw e
        }
    }

    override suspend fun fetchPage(
        roundId: String,
        query: SelectionQuery,
        start: Int,
        pageSize: Int,
    ): SelectionCenterParser.CourseListPage {
        requireRound(roundId)
        try {
            prepare(roundId)
            return fetchPageRetrying(roundId, query, start, pageSize)
        } catch (e: SelectionClientException) {
            if (e.fatal) prepared.remove(roundId)
            throw e
        }
    }

    override suspend fun submit(roundId: String, courseId: String): SelectionSubmitResult {
        requireRound(roundId)
        val (jx0404id, jx02id, cfbs) = SelectionCenterParser.splitCourseKey(courseId)
            ?: return SelectionSubmitResult.Fatal("课程标识不合法，请刷新列表后重试")
        val url = buildString {
            append(JwUrls.SELECTION_PLAN_SUBMIT)
            append("?kcid=").append(encode(jx02id))
            // 页面 JS 会把空的 cfbs 拼成字面量 "null"（`"" + null`）——照抄，别自作聪明
            append("&cfbs=").append(encode(cfbs.ifBlank { "null" }))
            append("&jx0404id=").append(encode(jx0404id))
            append("&xkzy=&trjf=&sfsyjc=&sfkvtj=")
        }
        return runAction(roundId, url)
    }

    override suspend fun drop(roundId: String, courseId: String): SelectionSubmitResult {
        requireRound(roundId)
        val (jx0404id, _, _) = SelectionCenterParser.splitCourseKey(courseId)
            ?: return SelectionSubmitResult.Fatal("课程标识不合法，请刷新列表后重试")
        return runAction(roundId, "${JwUrls.SELECTION_DROP}?jx0404id=${encode(jx0404id)}")
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /** 进入轮次（每个轮次一次）：GET「进入选课」页 + 列表壳页，校验窗口与验证码。 */
    private suspend fun prepare(roundId: String) {
        if (roundId in prepared) return
        val entryUrl = JwUrls.selectionRoundUrl(roundId)
            ?: throw SelectionClientException(fatal = true, message = "轮次 id 不合法：$roundId")
        val entry = fetchText(entryUrl) { "没取到选课进入页，$it" }
        if (SelectionCenterParser.notInWindow(entry)) {
            throw SelectionClientException(fatal = true, message = SelectionCenterParser.NOT_IN_WINDOW_TEXT)
        }
        if (SelectionCenterParser.noOpen(entry)) {
            throw SelectionClientException(fatal = true, message = "教务选课功能暂未开放（no-open 页）")
        }
        val shell = fetchText(JwUrls.SELECTION_PLAN_LIST_SHELL) { "没取到选课列表壳页，$it" }
        if (SelectionCenterParser.captchaEnabled(shell)) {
            throw SelectionClientException(
                fatal = true,
                message = "本轮启用了验证码选课，App 内不支持（不绕过验证码）——请在教务页面操作",
            )
        }
        prepared += roundId
    }

    /** 单个关键词抓取：按 `iTotalRecords` 兜底翻页，最多 [MAX_PAGES] 页防失控。 */
    private suspend fun fetchKeyword(
        merged: MutableMap<String, SelectionCourse>,
        roundId: String,
        keyword: String,
    ) {
        val query = SelectionQuery(keyword = keyword)
        var seen = 0
        var start = 0
        repeat(MAX_PAGES) {
            val page = fetchPageRetrying(roundId, query, start, SelectionCenterParser.PAGE_SIZE)
            page.rows.forEach { merged[it.id] = it }
            seen += page.rows.size
            if (seen >= page.total || page.rows.isEmpty()) return
            start += SelectionCenterParser.PAGE_SIZE
            pause()
        }
    }

    /** 单页拉取 + 「重新进轮次」自愈：撞 404 错误页就重进轮次再试一次（读语义，可重试）。 */
    private suspend fun fetchPageRetrying(
        roundId: String,
        query: SelectionQuery,
        start: Int,
        pageSize: Int,
    ): SelectionCenterParser.CourseListPage {
        val url = listUrl(query)
        val body = SelectionCenterParser.listBody(start = start, pageSize = pageSize)
        var text = fetchPost(url, body) { "没取到选课列表，$it" }
        if (SelectionCenterParser.qzNotFound(text)) {
            prepared.remove(roundId)
            prepare(roundId)
            text = fetchPost(url, body) { "没取到选课列表，$it" }
        }
        return try {
            SelectionCenterParser.parseList(text)
        } catch (e: IllegalStateException) {
            throw SelectionClientException(fatal = true, message = e.message ?: "选课列表无法识别")
        }
    }

    /** 列表请求 URL：查询条件全在 URL 上（键序照页面 `queryKxkcList`）。 */
    private fun listUrl(query: SelectionQuery): String {
        val params = SelectionCenterParser.listQuery(query)
            .joinToString("&") { (name, value) -> "$name=${encode(value)}" }
        return "${JwUrls.SELECTION_PLAN_LIST_API}?$params"
    }

    /** 写操作：进入轮次（失败映射 Fatal）→ 单次 GET → 三态映射。 */
    private suspend fun runAction(roundId: String, url: String): SelectionSubmitResult {
        try {
            prepare(roundId)
        } catch (e: SelectionClientException) {
            prepared.remove(roundId)
            return SelectionSubmitResult.Fatal(e.message ?: SESSION_UNAVAILABLE)
        }
        val text = when (val fetched = session.fetchHtml(url)) {
            is CasFetchResult.Ok -> fetched.body
            // 写路径拿不到应答时结果未知：报 Rejected 而不是 Fatal，让用户刷新「已选课程」核对
            is CasFetchResult.Failed -> return SelectionSubmitResult.Rejected(
                "网络异常，提交结果未知：${fetched.reason}",
            )
        }
        val outcome = try {
            SelectionCenterParser.parseAction(text)
        } catch (e: IllegalStateException) {
            return SelectionSubmitResult.Fatal(e.message ?: "教务应答无法识别")
        }
        return when {
            outcome.success && !outcome.conflictPending -> SelectionSubmitResult.Success(outcome.message)
            SelectionCenterParser.isFatalMessage(outcome.message) ->
                SelectionSubmitResult.Fatal(outcome.message)

            else -> SelectionSubmitResult.Rejected(outcome.message)
        }
    }

    private suspend fun fetchText(url: String, reason: (String) -> String): String =
        when (val fetched = session.fetchHtml(url)) {
            is CasFetchResult.Ok -> fetched.body
            is CasFetchResult.Failed -> throw SelectionClientException(fatal = true, message = reason(fetched.reason))
        }

    private suspend fun fetchPost(
        url: String,
        form: List<Pair<String, String>>,
        reason: (String) -> String,
    ): String = when (val fetched = session.postForm(url, form)) {
        is CasFetchResult.Ok -> fetched.body
        is CasFetchResult.Failed -> throw SelectionClientException(fatal = true, message = reason(fetched.reason))
    }

    private fun requireRound(roundId: String) {
        if (!JwUrls.ROUND_ID_PATTERN.matches(roundId)) {
            throw SelectionClientException(fatal = true, message = "轮次 id 不合法：$roundId")
        }
    }

    /** URL 参数编码：Ktor 的 `encodeURLParameter` 与 `URLEncoder` 同为 UTF-8 + `%xx`（空格是 `%20`）。 */
    private fun encode(value: String): String = value.encodeURLParameter()

    private companion object {
        const val MAX_PAGES = 3
        const val SESSION_UNAVAILABLE = "教务会话不可用，请在导入页重新登录后重试"
    }
}
