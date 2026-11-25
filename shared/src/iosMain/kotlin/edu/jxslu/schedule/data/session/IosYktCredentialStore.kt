package edu.jxslu.schedule.data.session

import edu.jxslu.schedule.data.ykt.YktCredentialVault
import edu.jxslu.schedule.data.ykt.YktTokenCache
import edu.jxslu.schedule.platform.IosKeychain

/**
 * 一卡通凭证的 iOS 实现：介质换成 Keychain（`kSecClassGenericPassword`）。
 *
 * 对齐对象是 `:app` 的 `data/session/CredentialVault`（`EncryptedSharedPreferences`）——
 * 它同时是 [YktCredentialVault] 与 [YktTokenCache] 的实现，这里也一并实现两个接口，
 * 理由见下面「token 为什么也在这里」。逐条对齐的行为：
 *
 * | Android（`CredentialVault`） | 这里 |
 * |---|---|
 * | 文件 `ykt_credentials.xml`，键 `username` / `password` | service `secure_ykt`，account 同名 |
 * | 文件 `ykt_tokens`，键 `token` / `token_at` | 同一个 service，account 同名 |
 * | 写时 trim 学号、密码原样 | 同（密码里可能有前后空格，改它会登不上） |
 * | 只存了一半（空学号或空密码）读作未登录 | 同（`read()` 回 null） |
 * | `clear()` = 清凭证 + 清落盘 token | 同（`deleteAll(service)` 一次清干净） |
 *
 * ## 键名规则
 *
 * **service = `secure_<系统>`**：与 [IosSessionStores] 那六个第三方存储同一套前缀
 * （`secure_qiekj` ↔ `secure_qiekj.xml`、`secure_ujing` ↔ `secure_ujing.xml`），
 * 所以 `secure_ykt` ↔ Android 的 `ykt_credentials.xml` + `ykt_tokens`，换平台排查时对得上号。
 * **account 沿用 Android 的键名**（`username` / `password` / `token` / `token_at`）：
 * 两端的键名一致，看日志或写迁移脚本时不用再查一张映射表。
 *
 * ## token 为什么也在这里
 *
 * [YktCredentialVault.clear] 的契约是「凭证 + 落盘 token 一次清干净」；iOS 若不落盘 token，
 * 这一步就是空转。同一份 Keychain 里顺手存 token，换来的是冷启动少登一次（取安全键盘 +
 * 换 token 两条请求），与 Android 的收益一致。**它仍不是授权依据**——新鲜度判定走
 * [TokenFreshness]，服务端回 401 时 `YktRepository` 的重登逻辑照常接管。
 *
 * ## 为什么没有登录闸门
 *
 * 闸门（连续失败次数 / 停用标记）只服务于 `CasSession` 的自动填表登录，那套在 iOS 上不存在
 * （登录只在 `WKWebView` 里由用户完成）。所以这里不实现 `CredentialStore` 的
 * `readGate` / `writeGate`——本端没有读者，写一份没人读的落盘状态只会让「清了凭证、
 * 闸门还记着上次失败」这种悬空态有地方发生。
 *
 * ## 失败怎么表现
 *
 * 与 [IosKeychain] 同口径：读不到回 null，写/删失败不回滚也不抛。读失败等于「未开启」，
 * 页面显示登录表单——比崩掉好。**别把写失败当「用户没存过」去清数据**，旧值可能还在。
 *
 * ## 未验
 *
 * 本文件在 Windows 上编不了（Kotlin/Native 的 Apple 目标要 macOS），只能靠 CI 验证；
 * 真机上「杀掉进程后仍读得到」「锁屏时后台读得到」（`kSecAttrAccessibleAfterFirstUnlock`）
 * 也还没验过。
 */
internal class IosYktCredentialStore : YktCredentialVault, YktTokenCache {

    // ---------------------------------------------------------------- 凭证

    override fun read(): YktCredentialVault.Credentials? {
        val username = IosKeychain.get(SERVICE, KEY_USERNAME)?.trim().orEmpty()
        val password = IosKeychain.get(SERVICE, KEY_PASSWORD).orEmpty()
        if (username.isEmpty() || password.isEmpty()) return null
        return YktCredentialVault.Credentials(username, password)
    }

    override fun save(username: String, password: String) {
        IosKeychain.set(SERVICE, KEY_USERNAME, username.trim())
        IosKeychain.set(SERVICE, KEY_PASSWORD, password)
    }

    /**
     * 凭证与 token 都在同一个 service 下，一次删干净（口径见类注释「token 为什么也在这里」）。
     *
     * 顺手撤掉运行时的「已失效」标记：它是本端唯一存在的登录态残余（见类注释
     * 「为什么没有登录闸门」），不清就会留下「凭证没了、状态卡还写着已失效」的悬空态
     * ——[YktCredentialVault.clear] 的契约要求实现方负责这一串。
     */
    override fun clear() {
        IosKeychain.deleteAll(SERVICE)
        SessionStatus.clear(LoginTarget.Ykt)
    }

    // ---------------------------------------------------------------- token

    override fun readToken(nowMs: Long): String? {
        val token = IosKeychain.get(SERVICE, KEY_TOKEN).orEmpty()
        val savedAt = IosKeychain.get(SERVICE, KEY_TOKEN_AT)?.toLongOrNull() ?: 0L
        if (token.isEmpty() || !TokenFreshness.isFresh(savedAt, nowMs)) return null
        return token
    }

    override fun saveToken(token: String, nowMs: Long) {
        IosKeychain.set(SERVICE, KEY_TOKEN, token)
        IosKeychain.set(SERVICE, KEY_TOKEN_AT, nowMs.toString())
    }

    override fun clearToken() {
        IosKeychain.delete(SERVICE, KEY_TOKEN)
        IosKeychain.delete(SERVICE, KEY_TOKEN_AT)
    }

    private companion object {
        /** Keychain 的 service 维度：`secure_` 前缀 + 系统名，与六个第三方存储同一套规则。 */
        const val SERVICE = "secure_ykt"

        // account 维度沿用 Android 的键名（`ykt_credentials.xml` / `ykt_tokens` 里的键）。
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_AT = "token_at"
    }
}
