package edu.jxslu.schedule.data.kqcx

import android.content.Context
import android.content.SharedPreferences
import edu.jxslu.schedule.data.local.EncryptedPrefs

/**
 * 快趣出行账号凭证加密存储（DESIGN §4.32，2026-09-28）。
 *
 * 形态与 [edu.jxslu.schedule.data.qiekj.QiekjTokenStore] 同款（EncryptedSharedPreferences
 * + Keystore 损坏自愈，创建见 [EncryptedPrefs.open]），但存的是**账号密码**而非 token——
 * 快趣的密码登录（`userLoginByPassword`）无签名、凭证即全部，token 过期后用本地凭证
 * 静默重登才能不劳用户重新输入（token 本身仍仅内存，见 `KqcxSessionRepository`）。
 * `secure_kqcx.xml` 已排除出云备份与设备迁移（backup_rules / data_extraction_rules）。
 */
class KqxCredentialStore(context: Context) : KqcxCredentialStore {
    private val prefs: SharedPreferences by lazy {
        EncryptedPrefs.open(context, FILE, TAG)
    }

    /** 凭证缺失（未登录）返回 null。密码落的是**明文原文**，MD5 在请求时现算——服务端口径如此。 */
    override fun readCredential(): Pair<String, String>? {
        val mobile = prefs.getString(KEY_MOBILE, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        if (mobile.isBlank() || password.isBlank()) return null
        return mobile to password
    }

    override fun saveCredential(mobile: String, password: String) {
        prefs.edit().putString(KEY_MOBILE, mobile).putString(KEY_PASSWORD, password).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_MOBILE).remove(KEY_PASSWORD).apply()
    }

    private companion object {
        const val TAG = "KqxCredentialStore"
        const val FILE = "secure_kqcx"
        const val KEY_MOBILE = "mobile"
        const val KEY_PASSWORD = "password"
    }
}
