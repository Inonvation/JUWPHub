package edu.jxslu.schedule.data.session

import edu.jxslu.schedule.data.jw.JwHttpSession
import edu.jxslu.schedule.data.jw.JwHttpSession.JwHttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 学校统一认证（CAS）会话的唯一持有者（DESIGN §4.27）。
 *
 * 覆盖教务、学工、签章三个系统——它们共用一套 CAS，但**各自的业务会话要各自建立**
 * （教务 `JSESSIONID`、学工 localStorage、签章 `sid`）。本类只负责 CAS 那一层。
 *
 * iOS 移植 S4（2026-11-24）：`CasEnsureResult` / `CasFetchResult` / `bodyOrNull` /
 * `notReadyReason` 与 [JwReadSession] 已搬到 `shared/commonMain`（同包），仓库层的
 * `*Sync` 只认那个接口；本类仍是**Android 侧唯一**的实现（iOS 侧是 `IosJwReadSession`，
 * 只读 `WKWebView` 的 Cookie），传输与凭证闸门一字未改。
 *
 * 三条策略：
 * 1. **可信期**：最近一次成功在 `LoginGateRules.SESSION_TRUST_MS` 内直接放行，不发请求；
 * 2. **先探后登**：有 cookie 时先 `verifySession`（学生主页，约 150KB 的那个是课表页），
 *    探通就续上信任期，比完整走一遍登录便宜；
 * 3. **闸门**：连续凭证错到阈值即停用，且**只有凭证错计数**（验证码/网络/5xx 都不计）。
 *
 * 并发由 [loginMutex] 串行化：「5 分钟内不重复登录」只是时间窗，两个协程同时撞到失效
 * 仍会并发登两次（`YktRepository.loginMutex` 是同一手法）。
 */
class CasSession(
    private val vault: CredentialStore,
    private val http: CasTransport = JwHttpSession.create(),
    private val webCookies: WebCookieBridge = WebViewCookieBridge,
    /**
     * 代理 / VPN 探测（DESIGN §4.27 兜底矩阵）。
     *
     * 学校对直连出口与代理出口区别对待：开着第三方 VPN 时 CAS 超时、SSO 落点 500。
     * 命中时**直接返回、连请求都不发**——省一次必然失败的往返，也避免把「代理的锅」
     * 记到凭证头上（那会白消耗一次失败计数）。
     *
     * 抽成函数只为可测：真实现要 Context（`JwVpnDetector.isVpnActive`）。
     */
    private val isProxyActive: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) : JwReadSession {

    private val loginMutex = Mutex()

    /**
     * 保证 CAS 会话可用。**这是所有教务侧取数的唯一入口。**
     *
     * 返回 [CasEnsureResult.Ready] 即可以放心发业务请求；其余分支由调用方决定
     * 是转 WebView、提示更新密码，还是稍后重试。
     */
    override suspend fun ensureValid(): CasEnsureResult = loginMutex.withLock { ensureValidLocked() }

    /**
     * 进程内是否已经有 CAS / 教务 cookie（只读，不发请求）。
     *
     * 给「可选自动检查」用（宿舍报修进度）：冷启动时 jar 是空的，若为了一个低频只读检查
     * 直接走 [ensureValid]，每个冷启动都会完整登一次 CAS（教务侧脚本已经踩过风控）。
     * 调用方可以按「已有 cookie 才顺带用 CAS，否则退 WebView 会话」来避免这次登录。
     */
    override suspend fun hasCookies(): Boolean = http.hasCookies()

    /** [ensureValid] 的实现体；调用方必须已经持有 [loginMutex]（Mutex 不可重入）。 */
    private suspend fun ensureValidLocked(): CasEnsureResult {
        if (isProxyActive()) return CasEnsureResult.Failed(PROXY_HINT)
        val now = clock()
        var gate = vault.readGate(LoginTarget.Jw)
        SessionStatus.syncSuspended(LoginTarget.Jw, gate.suspended)
        if (gate.suspended) return CasEnsureResult.Suspended
        // 信任期**必须同时要求会话还在**：`last_success` 落盘、cookie 不落盘，
        // 进程重启后时间戳还在而 jar 已空。只看时间戳就会把「空会话」当「可用会话」，
        // 于是 WebView 拿到一个空 jar、什么都不注入，用户看到的还是登录页
        // （2026-09-24 实测：last_success 与当前只差 190 秒，但会话早没了）。
        if (LoginGateRules.sessionTrusted(gate, now) && http.hasCookies()) {
            return CasEnsureResult.Ready()
        }

        val credentials = vault.readCas()

        // 有 cookie 就先探：探通比完整登录便宜，探不通再走登录。
        // 探针的失败要全接住（它和登录一样会抛 Network / Protocol），否则异常会穿透
        // 到调用方——那里只当它是「会话不可用」，反而把真实原因丢了。
        //
        // **探针失败不写闸门**（2026-11-19）：它是一次 GET 学生主页的会话探活，不是登录，
        // 不消耗 CAS 的任何尝试次数。旧行为把它记成「一次尝试」，于是探针超时之后的 5 分钟里
        // 连登录都不许——用户点检测只会看到「刚刚登录失败过」，实际起因只是一次读超时。
        if (http.hasCookies()) {
            val verified = try {
                http.verifySession()
            } catch (e: JwHttpException) {
                return CasEnsureResult.Failed(e.message ?: "会话校验失败")
            }
            if (verified) {
                gate = LoginGateRules.afterSuccess(clock())
                vault.writeGate(LoginTarget.Jw, gate)
                SessionStatus.syncSuspended(LoginTarget.Jw, false)
                return CasEnsureResult.Ready()
            }
        }

        if (credentials == null) {
            // 没存过统一认证密码。但 WebView 里可能有一份**用出来的**会话：引导跳过教务、
            // 后来在导入页手登一次的人就是这个形状（DESIGN §4.27 回灌是唯一方向）。
            // 认下它，学籍卡 / 成绩 / 学业完成情况这些 OkHttp 取数才不是「未登录」。
            //
            // 上面已经探过 jar 里那份（有 cookie 才会探），到这里还留着 cookie 就是探不通；
            // 只有 jar 空着才值得抄一次，避免每次调用都探两遍。
            if (http.hasCookies()) return CasEnsureResult.NoCredential
            // 先判有没有会话标识再抄：`bzb_njw` 这类站点标记也在 CookieManager 里长期存着，
            // 抄回来只会白探一次教务（探不通还会把 jar 填上一份死 cookie）。
            if (!webCookies.hasAnyCookie(ADOPT_URLS)) return CasEnsureResult.NoCredential
            val adopted = webCookies.adopt(ADOPT_URLS)
            if (adopted.isEmpty()) return CasEnsureResult.NoCredential
            http.adoptCookies(adopted)
            // **先验再用**：回灌本身不写闸门——没验过的 cookie 一旦记成 last_success，
            // 信任期就会把一份过期会话当可用，10 分钟内所有取数都拿到登录页。
            val verified = try {
                http.verifySession()
            } catch (_: JwHttpException) {
                return CasEnsureResult.NoCredential
            }
            if (!verified) return CasEnsureResult.NoCredential
            vault.writeGate(LoginTarget.Jw, LoginGateRules.afterSuccess(clock()))
            SessionStatus.syncSuspended(LoginTarget.Jw, false)
            return CasEnsureResult.Ready()
        }
        if (!LoginGateRules.canAttempt(gate, now)) {
            // 文案按上一次的失败性质给：凭证错 / 网络断 / 验证码的下一步动作完全不同，
            // 一句「刚刚登录失败过」会把用户往「再等等」的死路上带（2026-11-19 用户报障）。
            return CasEnsureResult.Failed(LoginGateRules.blockedMessage(gate.lastFailure))
        }

        return try {
            http.login(credentials.username, credentials.password)
            vault.writeGate(LoginTarget.Jw, LoginGateRules.afterSuccess(clock()))
            SessionStatus.syncSuspended(LoginTarget.Jw, false)
            CasEnsureResult.Ready(loggedInNow = true)
        } catch (e: JwHttpException.Credential) {
            val next = LoginGateRules.afterCredentialFailure(gate, clock())
            vault.writeGate(LoginTarget.Jw, next)
            SessionStatus.syncSuspended(LoginTarget.Jw, next.suspended)
            if (next.suspended) CasEnsureResult.Suspended
            else CasEnsureResult.Failed(e.message ?: "统一认证登录失败")
        } catch (e: JwHttpException.Manual) {
            // 验证码 / 认不出的页面：停手转人工，**不计失败**
            vault.writeGate(
                LoginTarget.Jw,
                LoginGateRules.afterTransientFailure(gate, clock(), LoginFailureKind.Captcha),
            )
            CasEnsureResult.NeedsManualLogin(e.message ?: "需要在网页里重新登录一次")
        } catch (e: JwHttpException.Network) {
            vault.writeGate(
                LoginTarget.Jw,
                LoginGateRules.afterTransientFailure(gate, clock(), LoginFailureKind.Network),
            )
            CasEnsureResult.Failed(e.message ?: "网络不可用")
        } catch (e: JwHttpException.Protocol) {
            vault.writeGate(
                LoginTarget.Jw,
                LoginGateRules.afterTransientFailure(gate, clock(), LoginFailureKind.Server),
            )
            CasEnsureResult.Failed(e.message ?: "教务链路异常")
        }
    }

    /**
     * 用**给定**凭证试登一次（引导第 2 步，DESIGN §3.16）。
     *
     * 与 [ensureValid] 的区别：那个读已存凭证、受信任期约束；这里是「验证用户刚敲进去的
     * 密码」，所以先清掉旧 cookie，确保成功真的来自这份凭证而不是残留会话。
     * 失败分类口径不变：凭证错计数、验证码/网络/5xx 都不计。
     *
     * 成功后由调用方落库（本类拿不到写凭证的权限，那是 [CredentialStore] 的写侧）。
     */
    suspend fun tryLogin(username: String, password: String): CasEnsureResult = loginMutex.withLock {
        if (isProxyActive()) return CasEnsureResult.Failed(PROXY_HINT)
        val gate = vault.readGate(LoginTarget.Jw)
        try {
            http.clearCookies()
            http.login(username, password)
            vault.writeGate(LoginTarget.Jw, LoginGateRules.afterSuccess(clock()))
            SessionStatus.syncSuspended(LoginTarget.Jw, false)
            CasEnsureResult.Ready(loggedInNow = true)
        } catch (e: JwHttpException.Credential) {
            val next = LoginGateRules.afterCredentialFailure(gate, clock())
            vault.writeGate(LoginTarget.Jw, next)
            SessionStatus.syncSuspended(LoginTarget.Jw, next.suspended)
            if (next.suspended) CasEnsureResult.Suspended
            else CasEnsureResult.Failed(e.message ?: "统一认证登录失败")
        } catch (e: JwHttpException.Manual) {
            vault.writeGate(
                LoginTarget.Jw,
                LoginGateRules.afterTransientFailure(gate, clock(), LoginFailureKind.Captcha),
            )
            CasEnsureResult.NeedsManualLogin(e.message ?: "需要在网页里登录一次")
        } catch (e: JwHttpException.Network) {
            vault.writeGate(
                LoginTarget.Jw,
                LoginGateRules.afterTransientFailure(gate, clock(), LoginFailureKind.Network),
            )
            CasEnsureResult.Failed(e.message ?: "网络不可用")
        } catch (e: JwHttpException.Protocol) {
            vault.writeGate(
                LoginTarget.Jw,
                LoginGateRules.afterTransientFailure(gate, clock(), LoginFailureKind.Server),
            )
            CasEnsureResult.Failed(e.message ?: "教务链路异常")
        }
    }

    /**
     * 带会话取一个教务页面（读语义，所有教务取数链的入口）。
     *
     * 失败**一定带回原因**（[CasFetchResult.Failed]）：网络断、5xx、会话被顶、重登窗口
     * 由调用方拼进「没取到 XX」里。原先这里只回 `null`，十几个调用点只能统一写
     * 「会话可能已失效」，把一次网络抖动说成登录问题（2026-11-19 与「刚刚登录失败过」
     * 同批修）。要静默降级的地方用 [bodyOrNull]。
     *
     * **会话被别处登录顶掉时会作废本地会话并重试一次**（2026-10-06）：强智是单会话，
     * 而信任期内 [ensureValid] 不会去验会话，于是一份已经作废的会话会被一路用下去，
     * 表现成「导入成功但教材 / 学籍卡静默缺失」。只对
     * [JwHttpException.SessionInvalid] 重试——5xx / 页面结构变化重试没有意义；
     * 重试也**只做一次**（失败不重试的纪律不变）。作废的是会话不是凭证，
     * 所以不动闸门里的失败计数（那是「防锁号」的计数器）。
     */
    override suspend fun fetchHtml(url: String): CasFetchResult {
        val ensured = ensureValid()
        if (ensured !is CasEnsureResult.Ready) return CasFetchResult.Failed(ensured.notReadyReason())
        val first = runCatchingCancellable { http.fetchHtml(url) }
        first.getOrNull()?.let { return CasFetchResult.Ok(it) }
        if (first.exceptionOrNull() !is JwHttpException.SessionInvalid) {
            return CasFetchResult.Failed(reasonOf(first.exceptionOrNull()))
        }
        // 重登要过一道**独立窗口**：一次同步里多个 URL 各自都能触发重登，
        // 而每次重登都会把对方顶掉（强智单会话），不加冷却就是登录乒乓
        if (!SessionStatus.tryAcquireRelogin(clock())) {
            return CasFetchResult.Failed(reloginDeniedReason())
        }
        val relogined = relogin()
        if (relogined !is CasEnsureResult.Ready) {
            // 许可退回：一次**失败**的重登不该占掉下一分钟（后面还有别的 URL 要试）
            SessionStatus.releaseRelogin()
            return CasFetchResult.Failed(relogined.notReadyReason())
        }
        return runCatchingCancellable { http.fetchHtml(url) }.fold(
            onSuccess = { CasFetchResult.Ok(it) },
            onFailure = { CasFetchResult.Failed(reasonOf(it)) },
        )
    }

    /**
     * 带会话 **POST 表单**（读语义，2026-10-08 起；选课中心的列表查询用它）。
     *
     * 与 [fetchHtml] 同一套编排（含失败原因与「重登许可失败即退回」）：会话被顶
     * （[JwHttpException.SessionInvalid]）时作废本地会话、重登**一次**、重试**一次**；
     * 其余失败带回原因。查询没有副作用，重试安全。
     */
    override suspend fun postForm(url: String, form: List<Pair<String, String>>): CasFetchResult {
        val ensured = ensureValid()
        if (ensured !is CasEnsureResult.Ready) return CasFetchResult.Failed(ensured.notReadyReason())
        val first = runCatchingCancellable { http.postForm(url, form) }
        first.getOrNull()?.let { return CasFetchResult.Ok(it) }
        if (first.exceptionOrNull() !is JwHttpException.SessionInvalid) {
            return CasFetchResult.Failed(reasonOf(first.exceptionOrNull()))
        }
        if (!SessionStatus.tryAcquireRelogin(clock())) {
            return CasFetchResult.Failed(reloginDeniedReason())
        }
        val relogined = relogin()
        if (relogined !is CasEnsureResult.Ready) {
            SessionStatus.releaseRelogin()
            return CasFetchResult.Failed(relogined.notReadyReason())
        }
        return runCatchingCancellable { http.postForm(url, form) }.fold(
            onSuccess = { CasFetchResult.Ok(it) },
            onFailure = { CasFetchResult.Failed(reasonOf(it)) },
        )
    }

    /**
     * 带会话 GET、一次就是一次（**写语义**，2026-10-08 起；选课提交 / 退课用它）。
     *
     * 与 [fetchHtml] 的差别只有一处、也是存在理由：**会话被顶时不自动重登重试**——
     * 这两个 GET 是写操作（一次请求就是一次选课提交），静默重发可能造成重复提交；
     * 会话问题原样抛给调用方收场（提示用户重新登录后再试）。
     * 会话不可用 / 链路失败也照抛 [JwHttpException]，由调用方映射成可读终态。
     */
    suspend fun fetchForAction(url: String): String {
        if (ensureValid() !is CasEnsureResult.Ready) {
            throw JwHttpException.SessionInvalid("教务会话不可用（可能需要在导入页重新登录）")
        }
        return http.fetchHtml(url)
    }

    /**
     * 作废本地会话后重走一次 [ensureValid]：有凭证就真登一次，没凭证就回灌 WebView 那份。
     *
     * **清 cookie 与重登必须在同一把锁里**（与 [logout] 同一条纪律）：否则与在途登录并发时，
     * `clearCookies` 会落在别人登录链路的中途，把那次登录打成「取不到 SSO ticket」，
     * 或者落在它刚成功之后，让它的取数拿到登录页再触发一次重登。
     */
    private suspend fun relogin(): CasEnsureResult = loginMutex.withLock {
        http.clearCookies()
        ensureValidLocked()
    }

    /**
     * 取数失败 → 一句给用户的话（调用方再补「没取到 XX」前缀）。
     *
     * 只归类、不改写：`Protocol` / `SessionInvalid` 的 message 本身就是人话（「教务登录没走通
     * …」「账号在其它地方登录」），原样透出比换成笼统说法有用；网络层那句是 OkHttp 原文
     * （`timeout` / `Failed to connect to …`），前面补「网络不可用」才看得出该换网络还是重登。
     */
    private fun reasonOf(error: Throwable?): String = when (error) {
        is JwHttpException.Network ->
            "网络不可用（${error.message?.takeIf { it.isNotBlank() } ?: "连接失败"}）"
        is JwHttpException.SessionInvalid -> error.message ?: "教务会话已失效"
        is JwHttpException.Protocol -> error.message ?: "教务链路异常"
        else -> error?.message?.takeIf { it.isNotBlank() } ?: "取数失败"
    }

    /**
     * 重登许可拿不到时的原因（[fetchHtml] / [postForm] 共用）。
     *
     * 两种成因要说不同的话：已停用（凭证被判错到阈值）要等用户改密码，
     * 而「60 秒内刚重登过」是一次同步里多个 URL 同时撞到被顶的正常情形，不是失败。
     */
    private fun reloginDeniedReason(): String =
        if (SessionStatus.isSuspended(LoginTarget.Jw)) {
            CasEnsureResult.Suspended.notReadyReason()
        } else {
            RELOGIN_WINDOW_HINT
        }

    /**
     * 与 `runCatching` 同形，但让 [CancellationException] 穿透。
     *
     * 吞掉它会把「协程被取消」变成一次静默的取数失败：调用方继续往下走，
     * 期间可能再发一次请求（WorkManager worker 被停时尤其）。
     * 与项目 2026-10-02 体检批的「CancellationException 穿透」同口径。
     */
    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * 用户在 WebView 里手登成功后调用：把 cookie 抄回会话，并续上信任期。
     *
     * 不记这一步的话，手登完紧接着的 `ensureValid` 又会去走一次登录——白撞一次风控。
     */
    suspend fun adoptFromWebView(urls: List<String> = ADOPT_URLS) {
        val cookies = webCookies.adopt(urls)
        if (cookies.isNotEmpty()) http.adoptCookies(cookies)
        vault.writeGate(LoginTarget.Jw, LoginGateRules.afterSuccess(clock()))
        SessionStatus.syncSuspended(LoginTarget.Jw, false)
    }

    /** 用户更新了统一认证密码：清零闸门，允许重新尝试。 */
    fun onCredentialsUpdated() {
        vault.writeGate(LoginTarget.Jw, LoginGateRules.reset())
        SessionStatus.syncSuspended(LoginTarget.Jw, false)
        // 凭证换过了，自动填表那道的 5 分钟节流也该放开——否则刚改完密码还得手动登一次。
        SessionStatus.clearAutoLoginThrottle()
    }

    /**
     * 退出教务登录：清 OkHttp 会话、WebView cookie、凭证（一卡通那套不受影响）。
     *
     * **WebView 那侧必须一起清**（2026-09-28 用户实测）：不清的话，之前手登/自动填表
     * 留在 `CookieManager` 里的会话仍然有效——导入页探针通过照常直进教务，
     * 「我的」页状态卡（`hasAnyCookie`）也一直报「已登录」，退出形同虚设。
     * 挂起因为 `CookieManager` 的读写要在有 Looper 的线程上（桥内部已切 Main）。
     *
     * **必须在 [loginMutex] 里做**（2026-09-28 审查）：否则与在途登录并发时，
     * 登录协程返回成功后会把 cookie 写回 jar、闸门记成 `afterSuccess`——
     * 凭证已清但会话复活，10 分钟信任期内取数照常跑，退出被悄悄撤销。
     * 与 `ensureValid`/`tryLogin` 串行化后，要么登录先完成退出再清，要么退出先完成
     * 登录后拿到「凭证不存在」自然放弃。
     */
    suspend fun logout() = loginMutex.withLock {
        http.clearCookies()
        webCookies.clearAll()
        vault.clearCas()
        onCredentialsUpdated()
    }

    /** 进程退出时释放连接池与线程池。会话本身是进程级的，别在任务结束时调。 */
    fun shutdown() = http.shutdown()

    companion object {
        /** 代理出口的提示。文案唯一来源 [NetworkHint]（引导页给输入框留位置，别在这加长句）。 */
        const val PROXY_HINT: String = NetworkHint.VPN

        /**
         * 会话被顶、但重登许可还被 [SessionStatus.RELOGIN_WINDOW_MS] 的窗口挡着时的话。
         *
         * 60 秒内第二次撞到「账号在别处登录」的正常情形（一次同步多个 URL），
         * 重登不是失败——别让用户以为要重新登录。
         */
        private const val RELOGIN_WINDOW_HINT = "会话刚在别处登录过，已重新登过，请稍后再试"

        /**
         * 回灌时要读的域。
         *
         * 覆盖 CAS 与三个业务系统：教务业务在 `:8080`、预热在 `:81`、SSO 回跳在根域，
         * 三者是**不同的 URL 但同一个 host**，`getCookie` 按 URL 取，所以都要列上。
         *
         * **路径必须落在 cookie 的作用域里**：`getCookie` 按 URL 的路径筛，CAS 的
         * `JSESSIONID` 作用域是 `/cas`、`TGC` 是 `/cas/`、教务的 `JSESSIONID` 是 `/jsxsd`。
         * 早先这几条写成根路径，读回来的只有教务预热写的 `bzb_njw` 这类站点标记，
         * 于是「有没有会话」全靠标记撑着（2026-09-27 真机实测：WebView 里只剩 `bzb_*`，
         * 状态卡却写着「已登录」；反过来 CAS 会话还在时又读不到）。
         */
        val ADOPT_URLS: List<String> = listOf(
            "https://eapp2.juwp.edu.cn:9443/cas/",
            "http://jiaowu.juwp.edu.cn/sso.jsp",
            "https://jiaowu.juwp.edu.cn:81/",
            "http://jiaowu.juwp.edu.cn:8080/jsxsd/",
            "https://xgxt.juwp.edu.cn/",
            "http://jwxyxx.juwp.edu.cn/ptwork/",
        )
    }
}
