package edu.jxslu.schedule.data.xg

/**
 * 报修列表的「WebView 会话」来源（iOS 移植 W2，2026-11-25）。
 *
 * ## 为什么是缝而不是搬过来的实现
 *
 * `:app` 的 `DormRepairWebClient` 是这条链路里**最便宜**的一档：不登录，直接把
 * `CookieManager` 里现成的学工 office 会话（`session_oa` / `JSESSIONID`）抄出来，
 * 带 `Cookie` 头发同一个只读列表请求。它要的两样东西 `commonMain` 都拿不到——
 * `android.webkit.CookieManager`（要有 Looper 的线程）与 OkHttp。
 *
 * 但**取数顺序与降级语义**（WebView 会话优先、CAS 兜底、两条都不可用就静默跳过）
 * 属于编排，已经搬进共享层的 [edu.jxslu.schedule.data.repo.DormRepairSync]。
 * 所以这里只把「最便宜的那一档」收成一条接口，编排留在共享层。
 *
 * ## `null` 的语义
 *
 * `DormRepairSync` 的 `webSession` 参数可空：**null = 本平台没有现成 WebView 会话可抄**
 * （iOS 侧还没有 `WKWebsiteDataStore` 的同步读，登录态只在 `WebCookies` 里、由
 * `IosJwReadSession` 走 CAS 那一档），此时同步链直接落到 CAS 分支。
 * 这不是「以后补」的占位：少一档只会多一次 CAS 探测，不会少数据。
 *
 * 契约与实现逐条对齐（`DormRepairWebClient` 的 KDoc 是权威口径）：
 * **不落盘、不打印 Cookie、不碰写接口**；进程重启后 WebView 会话没了就回
 * [Result.NoSession]，由上层静默降级，不弹错误、不动卡片上的旧快照。
 */
interface DormRepairWebSource {

    sealed interface Result {
        data class Ok(val body: String) : Result

        /** 本机没有可用的学工 office 会话（没在报修页手登过，或进程重启后没了）。 */
        data object NoSession : Result

        data class Failed(val message: String) : Result
    }

    /** 取一次「个人提交记录」的原始应答。失败**不抛**，按 [Result] 分支回报。 */
    suspend fun fetch(): Result
}
