package edu.jxslu.schedule.data.session

/**
 * 一次登录尝试的失败性质（DESIGN §4.27）。
 *
 * 决定两件事：**多久之后允许再试**、以及**被挡住时对用户怎么说**。两种性质的代价完全不同：
 * 凭证错连着试会把账号试到验证码 / 锁定，必须等满 5 分钟；网络不可达只是这一趟没连上，
 * 网一好就该能重试——按同一条 5 分钟窗口处理会把一次网络抖动放大成 5 分钟的全链路不可用。
 */
enum class LoginFailureKind {
    /** 没有失败记录（首次尝试 / 成功后清零）。 */
    None,

    /** 凭证错：计入停用阈值，5 分钟窗口。 */
    Credential,

    /** 命中验证码 / 认不出的页面：不计失败，等人工登录，5 分钟窗口。 */
    Captcha,

    /** 平台侧异常（5xx、页面结构、CAS 过了但教务会话没建立）：不计失败，短窗口。 */
    Server,

    /** 网络不可达 / 超时：不计失败，短窗口。 */
    Network,
}

/**
 * 登录闸门状态（DESIGN §4.27）。
 *
 * 必须**持久化**：停用只存在内存的话，进程一重启闸门就忘了，用户改密码之前每次冷启动
 * 都会再撞两次风控——那正是这条规则要防的事。
 */
data class LoginGateState(
    /** 连续凭证错次数。成功一次清零。 */
    val consecutiveCredentialFailures: Int = 0,
    /** 已停用：不再自动尝试，等用户更新密码。 */
    val suspended: Boolean = false,
    /** 最近一次登录/会话校验成功时刻。 */
    val lastSuccessMs: Long = 0L,
    /** 最近一次尝试时刻（含失败）。 */
    val lastAttemptMs: Long = 0L,
    /** 最近一次失败的性质：决定重复窗口长短与被挡时的文案。 */
    val lastFailure: LoginFailureKind = LoginFailureKind.None,
)

/**
 * 闸门规则（纯函数，便于 JVM 单测）：
 *
 * 1. 同一账号在重复窗口内不重复尝试——打开三个窗口不该登三次；
 * 2. [MAX_CREDENTIAL_FAILURES] 次**连续凭证错**即停用，只有凭证错计数，
 *    验证码 / 网络 / 5xx 都不计（它们的成因不在密码上，计数只会误伤）；
 * 3. 最近一次成功在 [SESSION_TRUST_MS] 内直接信会话，不再打校验请求
 *    ——这条是「减少请求」的那一半。
 *
 * **窗口按失败性质分两档**（2026-11-19）：凭证错 / 验证码要等满 [REPEAT_WINDOW_MS]，
 * 那是「别连着撞风控」；网络与平台异常只等 [TRANSIENT_RETRY_MS]——一趟没连上不代表
 * 密码有问题，按 5 分钟处理会把一次网络抖动变成整条教务链 5 分钟不可用（用户点「检测」
 * 只会看到「刚刚登录失败过」，2026-11-19 用户报障）。
 */
object LoginGateRules {

    const val MAX_CREDENTIAL_FAILURES = 2
    const val REPEAT_WINDOW_MS = 5 * 60_000L
    const val SESSION_TRUST_MS = 10 * 60_000L

    /**
     * 网络 / 平台异常后的重试冷却。
     *
     * 只为挡住「连点成片」：一次检测要跑两条链，第一条失败后第二条若立刻再发一次登录，
     * 一次点击就是两次 CAS 登录。30 秒足够让一次点击落定，又不至于让用户等到忘了。
     */
    const val TRANSIENT_RETRY_MS = 30_000L

    /**
     * 自动填表登录的重复窗口。
     *
     * 页面级那个「只自动一次」挡不住重开窗口：密码改过而 App 还存着旧的时，每开一次
     * 导入页 / 报修 / 成绩单就撞一次 CAS —— 正好是最容易触发风控的形状。
     */
    const val AUTO_LOGIN_WINDOW_MS = 5 * 60_000L

    /**
     * 自动填表登录是否放行。
     *
     * [lastAutoLoginMs] = 上次放行时刻，0 = 从没放过；[suspended] = 该平台的凭证已被判错到停用
     * ——那时再拿同一份密码去撞一次 CAS 只有坏处（账号锁定就靠这个计数）。
     */
    fun shouldAutoLogin(
        lastAutoLoginMs: Long,
        nowMs: Long,
        suspended: Boolean = false,
        windowMs: Long = AUTO_LOGIN_WINDOW_MS,
    ): Boolean = !suspended && (lastAutoLoginMs <= 0L || nowMs - lastAutoLoginMs >= windowMs)

    fun canAttempt(
        state: LoginGateState,
        nowMs: Long,
        windowMs: Long = windowFor(state.lastFailure),
    ): Boolean {
        if (state.suspended) return false
        // 窗口只挡「失败后的重试」。`lastAttempt ≤ lastSuccess` 说明那次尝试是**成功**的
        // （旧版本在成功时也写 lastAttempt），不该被挡——进程重启后 cookie 丢了，
        // 正需要立刻重登一次（2026-09-24 实测：引导登录 4.6 分钟后冷启动，
        // lastAttempt 还停在成功那一刻，于是重登被窗口挡掉，用户看到「还是要手动登」）。
        if (state.lastAttemptMs <= state.lastSuccessMs) return true
        return nowMs - state.lastAttemptMs >= windowMs
    }

    /**
     * 该类失败之后要等多久才允许再试。
     *
     * 只有**凭证错**（连撞会把账号试锁）与**验证码**（要人接手的形态，重试只会再撞一次）
     * 用 5 分钟；网络 / 平台异常是「这一趟没成」，给一个挡连点的短冷却。
     * `None` 返回 0：旧版本落盘的闸门没有失败种类，按「没有待冷却的失败」处理。
     */
    fun windowFor(kind: LoginFailureKind): Long = when (kind) {
        LoginFailureKind.Credential, LoginFailureKind.Captcha -> REPEAT_WINDOW_MS
        LoginFailureKind.Server, LoginFailureKind.Network -> TRANSIENT_RETRY_MS
        LoginFailureKind.None -> 0L
    }

    /**
     * 被重复窗口挡住时对用户说的话：**说上一次到底怎么失败的**。
     *
     * 旧文案「刚刚登录失败过，请稍后再试」把凭证错、网络断、验证码全抹成一句，用户无从判断
     * 该等、该换网络还是该改密码（2026-11-19 用户报障：课表 / 考试检测同时报这句）。
     */
    fun blockedMessage(kind: LoginFailureKind): String = when (kind) {
        LoginFailureKind.Credential -> "账号或密码错误，请稍后再试"
        LoginFailureKind.Captcha -> "需要验证码，请到导入页手动登录一次"
        LoginFailureKind.Server -> "教务链路异常，请稍后再试"
        LoginFailureKind.Network -> "网络不可用，请稍后再试"
        // None 走不到这里（[windowFor] 给 0，等于不挡）；留着只为穷尽分支。
        LoginFailureKind.None -> "刚刚登录失败过，请稍后再试"
    }

    fun afterSuccess(nowMs: Long): LoginGateState = LoginGateState(
        consecutiveCredentialFailures = 0,
        suspended = false,
        lastSuccessMs = nowMs,
        // **不设 lastAttemptMs**：重复窗口只该由「失败」驱动。
        // 成功时把它一起推进会让下一次尝试被窗口挡住——而进程重启后 cookie 是丢的、
        // `last_success` 是留的，那时正需要立刻重登一次（2026-09-24 实测踩到）。
        lastAttemptMs = 0L,
        lastFailure = LoginFailureKind.None,
    )

    fun afterCredentialFailure(
        state: LoginGateState,
        nowMs: Long,
        maxFailures: Int = MAX_CREDENTIAL_FAILURES,
    ): LoginGateState {
        val failures = state.consecutiveCredentialFailures + 1
        return state.copy(
            consecutiveCredentialFailures = failures,
            suspended = failures >= maxFailures,
            lastAttemptMs = nowMs,
            lastFailure = LoginFailureKind.Credential,
        )
    }

    /** 非凭证错：只推进时间窗（长度由 [kind] 决定），不清零也不累加。 */
    fun afterTransientFailure(
        state: LoginGateState,
        nowMs: Long,
        kind: LoginFailureKind,
    ): LoginGateState = state.copy(lastAttemptMs = nowMs, lastFailure = kind)

    /** 会话是否还在可信期内。 */
    fun sessionTrusted(
        state: LoginGateState,
        nowMs: Long,
        ttlMs: Long = SESSION_TRUST_MS,
    ): Boolean = state.lastSuccessMs > 0L && nowMs - state.lastSuccessMs <= ttlMs

    fun reset(): LoginGateState = LoginGateState()
}
