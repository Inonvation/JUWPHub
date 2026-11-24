package edu.jxslu.schedule.data.session

/**
 * 统一认证（CAS）会话状态（DESIGN §4.27）。
 *
 * iOS 移植 S4（2026-11-24）：仓库层那批 `*Sync` 搬进 `shared/commonMain` 之后要判断
 * 「会话能不能用」并按分支给文案，而 `CasSession` 本体（OkHttp + WebView cookie 桥 +
 * 凭证仓）还留在 `:app`。所以**结果类型与文案**整段搬到共享层，`CasSession` 只作为
 * [JwReadSession] 的实现；包名不变（`edu.jxslu.schedule.data.session`），`:app` 侧
 * 二十多个调用点的 import 一行都不用改。
 */
sealed interface CasEnsureResult {

    /**
     * 会话可用。
     *
     * [loggedInNow] 区分两种来源：`true` = 本次真的走了一遍登录（cookie 是刚拿到的）；
     * `false` = 复用已有会话，或落在 10 分钟可信期内。它只用于文案与日志区分——
     * **没有「刚登录所以要注入 WebView」这一步了**：注入已被真机证伪，WebView 的会话
     * 由 `JwAutoLogin` 填表提交自己拿（DESIGN §4.27）。
     */
    data class Ready(val loggedInNow: Boolean = false) : CasEnsureResult

    /** 没存过统一认证密码，只能走 WebView 手登。 */
    data object NoCredential : CasEnsureResult

    /** 凭证连续出错、已停用：等用户更新密码，别再自动尝试（防锁号）。 */
    data object Suspended : CasEnsureResult

    /** 需要人工在网页里登一次（验证码页 / 认不出的页面）。 */
    data class NeedsManualLogin(val message: String) : CasEnsureResult

    /** 网络或平台侧问题，稍后还能再试。 */
    data class Failed(val message: String) : CasEnsureResult
}

/**
 * 带会话取一页数据的结果（DESIGN §4.27，2026-11-19）。
 *
 * **[Failed.reason] 必须能区分「网络不通 / 5xx / 会话被顶」**：这一层原先只回 `null`，
 * 于是十几个取数调用点只能统一写「没取到 XX（会话可能已失效）」——网络断一次就把用户
 * 往「重新登录」上带（2026-11-19 与「刚刚登录失败过」同批修）。
 */
sealed interface CasFetchResult {

    data class Ok(val body: String) : CasFetchResult

    /** 取数失败的一句话原因（调用方再加「没取到 XX」前缀，不要在这里拼场景）。 */
    data class Failed(val reason: String) : CasFetchResult
}

/**
 * 只关心「拿到没拿到」的静默降级路径用（`bodyOrNull() ?: return false`）。
 *
 * 要给用户解释原因的调用方**不要**用它——用 `when` 取 [CasFetchResult.Failed.reason]，
 * 否则又回到「所有失败一个笼统说法」的老问题。
 */
fun CasFetchResult.bodyOrNull(): String? = (this as? CasFetchResult.Ok)?.body

/**
 * 会话不可用时给用户的一句话（**唯一口径**，DESIGN §4.27 / §3.16）。
 *
 * 与 `ScholarProgressSync.describe` 是同一份文案（那里委托到这里）：取数层也要拼这句话
 * （`CasSession.fetchHtml` 拿不到会话时），而 `data.session` 不该反向依赖 `data.repo`。
 * 唯一的例外是学工报修：它没有「自动导入」这回事，NoCredential 自己写了一句
 * （`DormRepairSync.describeCas`），其余分支仍走这里。
 */
fun CasEnsureResult.notReadyReason(): String = when (this) {
    is CasEnsureResult.Ready -> ""
    CasEnsureResult.NoCredential ->
        "还没保存统一认证账号，去「我的 → 教务账户」登录一次即可自动导入"
    CasEnsureResult.Suspended -> "密码连续错误，已暂停自动登录，请更新密码后重试"
    is CasEnsureResult.NeedsManualLogin -> message
    is CasEnsureResult.Failed -> message
}

/**
 * 仓库层真正需要的会话能力（DESIGN §4.27）。
 *
 * **Android 的实现是 `:app` 的 `CasSession`**——它继续管 OkHttp 传输、cookie 回灌、凭证闸门；
 * **iOS 的实现是 `IosJwReadSession`**（Cookie 来自 `WKWebView`）。`shared/commonMain` 里那批
 * `*Sync` 只依赖这几个方法，于是编排（闸门 / 解析 / 写库）可以整段共享，而传输留在能拿到
 * 平台的模块。
 *
 * ## 为什么从两条涨到四条（iOS 移植 W2，2026-11-25）
 *
 * 学工报修（`DormRepairSync`）搬进共享层时把它要的两条也带过来了，正是原先写着
 * 「等它们真的搬过来再加，不提前铺」的那两条：
 *
 * | 方法 | 谁在用 | 为什么不能由调用方绕开 |
 * |---|---|---|
 * | [hasCookies] | 报修的前置探测 | 「进程里已有 CAS cookie 才顺带走 CAS」是**不登录**的前提；绕开就得在每个冷启动完整登一次统一认证（教务侧已踩过风控） |
 * | [postForm] | 报修列表 | 那个列表是 POST 表单接口；[fetchHtml] 只发 GET，硬拿 GET 打过去只会拿到 405 / 空页 |
 *
 * 写语义的 `fetchForAction`（选课提交）**仍然不在**这里：它没有共享层调用方，等搬了再加。
 */
interface JwReadSession {

    /** 保证会话可用。返回非 [CasEnsureResult.Ready] 时调用方自己决定转 WebView / 提示更新密码 / 稍后重试。 */
    suspend fun ensureValid(): CasEnsureResult

    /** 带会话取一个教务页面；失败一定带回原因，不返回 null。 */
    suspend fun fetchHtml(url: String): CasFetchResult

    /**
     * 进程内是否已经有会话 cookie（**只读，不发请求**）。
     *
     * 给「可选自动检查」用：拿不到就跳过这次检查，而不是为了它完整登一次统一认证。
     * Android 读 OkHttp 的 cookie jar，iOS 读 `WKWebsiteDataStore`（`WebCookies`）。
     */
    suspend fun hasCookies(): Boolean

    /**
     * 带会话 **POST 表单**（读语义：查询类接口，重发安全）。
     *
     * 与 [fetchHtml] 同一套失败口径（原因带回、不返回 null）。Android 侧多一层
     * 「会话被顶就作废重登一次再重试一次」；iOS 侧没有可重登的凭证，失败原样回报。
     */
    suspend fun postForm(url: String, form: List<Pair<String, String>>): CasFetchResult
}
