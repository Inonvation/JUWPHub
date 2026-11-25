package edu.jxslu.schedule.data.session

/** 一套凭证：学号 + 密码。CAS 与一卡通各一份，字段同名但**来源与用途完全不同**。 */
data class SessionCredentials(val username: String, val password: String)

/**
 * `CasSession` 对存储的全部要求（DESIGN §4.27）。
 *
 * 抽成接口只有一个目的：让会话策略（可信期 / 闸门 / 结果映射）能在 JVM 单测里跑完整
 * 分支。真正的实现 [CredentialVault] 依赖 Android Keystore，测不了。
 */
interface CredentialStore {

    fun readCas(): SessionCredentials?

    /**
     * 落库一份统一认证凭证（引导第 2 步 / 教务账户页「保存账号密码」）。
     *
     * **调用方必须先验证再过这一关**（`CasSession.tryLogin` 返回 `Ready` 才算过）：
     * 错密码存进来会让后续自动续登反复撞 CAS 失败计数，累计到阈值账号就被停用
     * （`.agents/rules/login-session.md`「先验后存」）。
     *
     * 写侧放在这个接口里而不是另起一个：读与写在两端都是同一份介质
     * （Android `EncryptedSharedPreferences`、iOS Keychain），拆开只会让两个实现各自
     * 拼一遍键名。
     */
    fun saveCas(username: String, password: String)

    fun clearCas()

    fun readGate(target: LoginTarget): LoginGateState

    fun writeGate(target: LoginTarget, state: LoginGateState)
}
