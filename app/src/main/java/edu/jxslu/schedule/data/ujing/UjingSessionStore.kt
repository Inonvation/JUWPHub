package edu.jxslu.schedule.data.ujing

import android.content.Context
import android.content.SharedPreferences
import edu.jxslu.schedule.data.local.EncryptedPrefs

/** U净 本机会话（DESIGN §4.37）：手机号 + JWT。 */
data class UjingSession(
    val mobile: String,
    val token: String,
    val userId: String = "",
) {
    val isComplete: Boolean get() = mobile.isNotBlank() && token.isNotBlank()
}

/**
 * U净 登录凭证加密存储（DESIGN §4.37）。
 *
 * 与胖乖 / 趣智 / 快趣同方案、**不同文件**（`secure_ujing`）：各自会话互不相干，
 * 混在一个文件里退登录会互相牵连。创建与自愈口径见 [EncryptedPrefs.open]。
 * 新文件同样要排除两套备份规则（`backup_rules.xml` / `data_extraction_rules.xml`），
 * 不跟云备份与设备迁移走。
 */
class UjingSessionStore(context: Context) {
    private val prefs: SharedPreferences by lazy {
        EncryptedPrefs.open(context, FILE_NAME, "UjingSessionStore")
    }

    fun read(): UjingSession? {
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return UjingSession(
            mobile = prefs.getString(KEY_MOBILE, "").orEmpty(),
            token = token,
            userId = prefs.getString(KEY_USER_ID, "").orEmpty(),
        )
    }

    /** 登录表单预填用：上次登录的手机号。 */
    fun lastMobile(): String = prefs.getString(KEY_MOBILE, "").orEmpty()

    fun save(session: UjingSession) {
        prefs.edit()
            .putString(KEY_MOBILE, session.mobile)
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .apply()
    }

    /** 会话过期：只清 token、保留手机号（重新登录少输一次号码）。 */
    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "secure_ujing"
        const val KEY_MOBILE = "mobile"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
    }
}
