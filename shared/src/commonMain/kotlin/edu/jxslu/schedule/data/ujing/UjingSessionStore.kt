package edu.jxslu.schedule.data.ujing

/**
 * U净 凭证的**存储契约**（iOS 移植 S15，2026-11-24）。
 *
 * 与 [edu.jxslu.schedule.data.wenqu.WenquSessionStore] 同一个理由：凭证必须加密落盘，
 * 而 `:app` 的实现靠 `EncryptedSharedPreferences` + Keystore 自愈（`data/local/EncryptedPrefs`），
 * Android 专有。这里只钉仓库用得到的五个动作，实现各端各给一份：
 *
 * | 平台 | 实现 | 文件 |
 * |---|---|---|
 * | Android | `UjingCredentialStore`（`EncryptedSharedPreferences`，`secure_ujing`） | `app/.../data/ujing/UjingCredentialStore.kt` |
 * | iOS | 待接（Keychain） | — |
 *
 * [clearToken] 与 [clear] 是两个不同的动作，别合并：会话过期只清 token、**手机号要留着**
 * （重新登录少输一次号码），主动退出才整份清掉。
 */
interface UjingSessionStore {

    /** 读会话；没有 token 返回 null。 */
    fun read(): UjingSession?

    /** 登录表单预填用：上次登录的手机号（token 没了也还在）。 */
    fun lastMobile(): String

    fun save(session: UjingSession)

    /** 会话过期：只清 token、保留手机号。 */
    fun clearToken()

    /** 主动退出：整份清掉。 */
    fun clear()
}
