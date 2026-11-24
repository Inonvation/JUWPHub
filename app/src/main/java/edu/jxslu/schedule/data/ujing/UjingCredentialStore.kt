package edu.jxslu.schedule.data.ujing

import android.content.Context
import android.content.SharedPreferences
import edu.jxslu.schedule.data.local.EncryptedPrefs

/**
 * U净 登录凭证加密存储（DESIGN §4.37）：[UjingSessionStore] 的 Android 实现。
 *
 * 与胖乖 / 趣智 / 快趣同方案、**不同文件**（`secure_ujing`）：各自会话互不相干，
 * 混在一个文件里退登录会互相牵连。创建与自愈口径见 [EncryptedPrefs.open]。
 * 新文件同样要排除两套备份规则（`backup_rules.xml` / `data_extraction_rules.xml`），
 * 不跟云备份与设备迁移走。
 *
 * iOS 移植 S15（2026-11-24）：仓库与模型都进了 `:shared`，**只有这个文件留在 `:app`**——
 * 介质是 `EncryptedSharedPreferences` + Android Keystore；iOS 侧对应 Keychain。
 * 契约在共享层的 [UjingSessionStore]。
 */
class UjingCredentialStore(context: Context) : UjingSessionStore {
    private val prefs: SharedPreferences by lazy {
        EncryptedPrefs.open(context, FILE_NAME, "UjingCredentialStore")
    }

    override fun read(): UjingSession? {
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return UjingSession(
            mobile = prefs.getString(KEY_MOBILE, "").orEmpty(),
            token = token,
            userId = prefs.getString(KEY_USER_ID, "").orEmpty(),
        )
    }

    /** 登录表单预填用：上次登录的手机号。 */
    override fun lastMobile(): String = prefs.getString(KEY_MOBILE, "").orEmpty()

    override fun save(session: UjingSession) {
        prefs.edit()
            .putString(KEY_MOBILE, session.mobile)
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .apply()
    }

    /** 会话过期：只清 token、保留手机号（重新登录少输一次号码）。 */
    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "secure_ujing"
        const val KEY_MOBILE = "mobile"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
    }
}
