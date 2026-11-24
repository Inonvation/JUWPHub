package edu.jxslu.schedule.data.qiekj

import android.content.Context
import android.content.SharedPreferences
import edu.jxslu.schedule.data.local.EncryptedPrefs

/**
 * 胖乖登录凭证加密存储（DESIGN §4.10 决策 2）。
 *
 * 方案与参考实现 light-life 一致：EncryptedSharedPreferences（密钥在 Android Keystore，
 * 主数据 AES256-GCM），配合 backup_rules / data_extraction_rules 把 secure_token.xml
 * 排除出云备份与设备迁移，避免 token 泄漏到备份体系。
 *
 * 创建与自愈口径见 [EncryptedPrefs.open]（Keystore 损坏时不再循环崩溃）。
 *
 * **留在 `:app`**（iOS 移植 S11，2026-11-24）：仓库已搬进 `:shared`，只认
 * [QiekjCredentialStore] 这个接口，本类是实现。介质是 Android Keystore，
 * commonMain 里没有等价物——iOS 侧将来用 Keychain 另写一份，两端只共用接口。
 */
class QiekjTokenStore(context: Context) : QiekjCredentialStore {
    private val prefs: SharedPreferences by lazy {
        EncryptedPrefs.open(context, FILE, "QiekjTokenStore")
    }

    override fun readToken(): String? = prefs.getString(KEY_TOKEN, null)

    override fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun readPhone(): String? = prefs.getString(KEY_PHONE, null)

    override fun savePhone(phone: String) {
        prefs.edit().putString(KEY_PHONE, phone).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_PHONE).apply()
    }

    private companion object {
        const val FILE = "secure_token"
        const val KEY_TOKEN = "token"
        const val KEY_PHONE = "phone"
    }
}
