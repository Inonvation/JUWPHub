package edu.jxslu.schedule.data.wenqu

import android.content.Context
import android.content.SharedPreferences
import edu.jxslu.schedule.data.local.EncryptedPrefs

/**
 * 问渠凭证加密存储（DESIGN §4.45）：[WenquSessionStore] 的 Android 实现。
 *
 * 与 [edu.jxslu.schedule.data.qzxy.QzxySessionStore] 同方案、**不同文件**（`secure_wenqu`）：
 * 各第三方会话互不相干，混在一个文件里退登录会互相牵连。创建与自愈口径见
 * [EncryptedPrefs.open]。新文件同样要在 `backup_rules.xml` / `data_extraction_rules.xml`
 * 里排除（正式件 + `.fallback` 两份），免得跟着云备份或设备迁移跑到别的机子上。
 *
 * iOS 移植 S15（2026-11-24）：仓库与模型都进了 `:shared`，**只有这个文件留在 `:app`**——
 * 介质是 `EncryptedSharedPreferences` + Android Keystore，commonMain 拿不到；
 * iOS 侧要接的是 Keychain，不是同一套 API。契约在共享层的 [WenquSessionStore]。
 */
class WenquCredentialStore(context: Context) : WenquSessionStore {
    private val prefs: SharedPreferences by lazy {
        EncryptedPrefs.open(context, FILE_NAME, TAG)
    }

    override fun read(): WenquSession? {
        val phone = prefs.getString(KEY_PHONE, null)?.takeIf { it.isNotBlank() } ?: return null
        val password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        val token = prefs.getString(KEY_TOKEN, "").orEmpty()
        return WenquSession(
            phone = phone,
            password = password,
            token = token,
            expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L),
            starLevel = prefs.getInt(KEY_STAR_LEVEL, 0),
            hours = Double.fromBits(prefs.getLong(KEY_HOURS_BITS, 0L)),
            hoursFetchedAt = prefs.getLong(KEY_HOURS_FETCHED_AT, 0L),
        )
    }

    override fun save(session: WenquSession) {
        prefs.edit()
            .putString(KEY_PHONE, session.phone)
            .putString(KEY_PASSWORD, session.password)
            .putString(KEY_TOKEN, session.token)
            .putLong(KEY_EXPIRES_AT, session.expiresAt)
            .putInt(KEY_STAR_LEVEL, session.starLevel)
            .putLong(KEY_HOURS_BITS, session.hours.toBits())
            .putLong(KEY_HOURS_FETCHED_AT, session.hoursFetchedAt)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val TAG = "WenquCredentialStore"
        const val FILE_NAME = "secure_wenqu"
        const val KEY_PHONE = "phone"
        const val KEY_PASSWORD = "password"
        const val KEY_TOKEN = "token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_STAR_LEVEL = "star_level"
        const val KEY_HOURS_BITS = "hours_bits"
        const val KEY_HOURS_FETCHED_AT = "hours_fetched_at"
    }
}
