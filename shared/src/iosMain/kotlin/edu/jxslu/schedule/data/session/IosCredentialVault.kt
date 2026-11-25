package edu.jxslu.schedule.data.session

import edu.jxslu.schedule.data.local.IosKeyValuePrefs
import edu.jxslu.schedule.platform.IosKeychain

/**
 * 统一认证凭证的 iOS 实现：介质换成 Keychain（`kSecClassGenericPassword`）。
 *
 * 对齐对象是 `:app` 的 `CredentialVault`（`EncryptedSharedPreferences`）。逐条对齐的行为：
 *
 * | Android（`CredentialVault`） | 这里 |
 * |---|---|
 * | 文件 `jw_credentials.xml`，键 `username` / `password` | service `secure_jw`，account 同名 |
 * | 文件 `login_gate`（**普通** prefs：闸门不是机密） | `NSUserDefaults`（[IosKeyValuePrefs]），键名同上 |
 * | 写时 trim 学号、密码原样 | 同（密码可能含前后空格，改了就登不上） |
 * | 只存了一半（空学号或空密码）读作未登录 | 同（[readCas] 回 null） |
 * | 闸门键 `jw_failures` / `jw_suspended` / `jw_last_success` / `jw_last_attempt` / `jw_last_failure` | 同（见 [key]） |
 *
 * ## 为什么这一步才做
 *
 * 用户报「首启登录导入都成功，点返回课表又要登录」：iOS 的会话此前只能由 `WKWebView`
 * 用出来，导入页一关就只剩一份没验过的 cookie，过期后无处可登。有了这份凭证 + 第 4 步的
 * Ktor 传输层，`CasSession.ensureValid` 才能走「存了学号密码 → 自动登录一次」那一档。
 *
 * ## 为什么没有内存缓存
 *
 * Android 那份有 `@Volatile` 缓存，因为 `EncryptedSharedPreferences` 每次读要过 Keystore 解密
 * （首次几十毫秒）、而三个 WebView 入口都在主线程各读一次。Keychain 一次 `SecItemCopyMatching`
 * 是同样量级的系统调用但没有解密开销，缓存换来的收益抵不过「缓存与真实值分叉」的风险
 * （`.agents/rules/login-session.md` 记过这类悬空态）。读点都在协程或组合期，一次几毫秒。
 *
 * ## 未验
 *
 * 本文件在 Windows 上编不了（Kotlin/Native 的 Apple 目标要 macOS），只过了 CI 的 macOS 编译；
 * 真机上「杀掉进程后仍读得到」「锁屏时后台读得到」（`kSecAttrAccessibleAfterFirstUnlock`）
 * 也还没验过。
 */
internal class IosCredentialVault : CredentialStore {

    private val gate = IosKeyValuePrefs(GATE_PREFIX)

    override fun readCas(): SessionCredentials? {
        val username = IosKeychain.get(SERVICE, KEY_USERNAME)?.trim().orEmpty()
        val password = IosKeychain.get(SERVICE, KEY_PASSWORD).orEmpty()
        if (username.isEmpty() || password.isEmpty()) return null
        return SessionCredentials(username, password)
    }

    override fun saveCas(username: String, password: String) {
        IosKeychain.set(SERVICE, KEY_USERNAME, username.trim())
        IosKeychain.set(SERVICE, KEY_PASSWORD, password)
    }

    override fun clearCas() {
        IosKeychain.delete(SERVICE, KEY_USERNAME)
        IosKeychain.delete(SERVICE, KEY_PASSWORD)
    }

    /**
     * 登录闸门（连续失败次数 / 停用标记 / 时间戳）。
     *
     * 数值一律存字符串：`NSUserDefaults` 能存数字，但 [IosKeyValuePrefs] 只有字符串与集合
     * 两个读写口，为它多加一对方法会让另外三份存储也长出一条没人用的路。字符串在
     * `toLongOrNull()` / `toBooleanStrictOrNull()` 面前一样精确。
     *
     * 升级兼容：`last_failure` 没有这个键时读作 [LoginFailureKind.None]（窗口按 0 处理），
     * 与 Android 那份同口径——旧数据不该把一次陈旧失败变成 5 分钟的门。
     */
    override fun readGate(target: LoginTarget): LoginGateState = LoginGateState(
        consecutiveCredentialFailures = gate.getString(key(target, SUFFIX_FAILURES))?.toIntOrNull() ?: 0,
        suspended = gate.getString(key(target, SUFFIX_SUSPENDED))?.toBooleanStrictOrNull() ?: false,
        lastSuccessMs = gate.getString(key(target, SUFFIX_LAST_SUCCESS))?.toLongOrNull() ?: 0L,
        lastAttemptMs = gate.getString(key(target, SUFFIX_LAST_ATTEMPT))?.toLongOrNull() ?: 0L,
        lastFailure = LoginFailureKind.entries
            .firstOrNull { it.name == gate.getString(key(target, SUFFIX_LAST_FAILURE)) }
            ?: LoginFailureKind.None,
    )

    override fun writeGate(target: LoginTarget, state: LoginGateState) {
        gate.putString(key(target, SUFFIX_FAILURES), state.consecutiveCredentialFailures.toString())
        gate.putString(key(target, SUFFIX_SUSPENDED), state.suspended.toString())
        gate.putString(key(target, SUFFIX_LAST_SUCCESS), state.lastSuccessMs.toString())
        gate.putString(key(target, SUFFIX_LAST_ATTEMPT), state.lastAttemptMs.toString())
        gate.putString(key(target, SUFFIX_LAST_FAILURE), state.lastFailure.name)
    }

    /** 与 Android 完全相同的键名：`<target>_<suffix>`（`jw_failures` 一类）。 */
    private fun key(target: LoginTarget, suffix: String) = "${target.name.lowercase()}_$suffix"

    private companion object {
        /** `secure_` 前缀 + 系统名，与六个第三方存储同一套规则（`secure_jw` ↔ `jw_credentials.xml`）。 */
        const val SERVICE = "secure_jw"

        /** account 维度沿用 Android 的键名。 */
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"

        /** 闸门前缀：Android 的 prefs 文件叫 `login_gate`，这里用它当 `NSUserDefaults` 的键前缀。 */
        const val GATE_PREFIX = "login_gate"

        const val SUFFIX_FAILURES = "failures"
        const val SUFFIX_SUSPENDED = "suspended"
        const val SUFFIX_LAST_SUCCESS = "last_success"
        const val SUFFIX_LAST_ATTEMPT = "last_attempt"
        const val SUFFIX_LAST_FAILURE = "last_failure"
    }
}
