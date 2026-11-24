package edu.jxslu.schedule.data.qzxy

import android.content.Context
import android.content.SharedPreferences
import edu.jxslu.schedule.data.local.EncryptedPrefs

/**
 * 趣智校园登录凭证加密存储（DESIGN §4.30）。
 *
 * 与胖乖 [edu.jxslu.schedule.data.qiekj.QiekjTokenStore] 同方案、**不同文件**
 * （`secure_qzxy`）：两个第三方的会话互不相干，混在一个文件里退登录会互相牵连。
 * 创建与自愈口径见 [EncryptedPrefs.open]。新文件同样要在 `backup_rules.xml` /
 * `data_extraction_rules.xml` 里排除，免得跟着云备份或设备迁移跑到别的机子上。
 *
 * iOS 移植 S12（2026-11-24）：类名从 `QzxySessionStore` 改为 `QzxySecureSessionStore`
 * ——共享层的 [QzxySessionStore] 接口占用了原名（`QzxyRepository` 已搬进
 * `shared/commonMain`，只依赖接口）。文件名 `secure_qzxy` 与四个键都没变，
 * 老用户升级后会话照旧可用。
 */
class QzxySecureSessionStore(context: Context) : QzxySessionStore {
    private val prefs: SharedPreferences by lazy {
        EncryptedPrefs.open(context, FILE_NAME, "QzxySecureSessionStore")
    }

    override fun read(): QzxySession? {
        val loginCode = prefs.getString(KEY_LOGIN_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
        return QzxySession(
            loginCode = loginCode,
            userId = prefs.getString(KEY_USER_ID, "").orEmpty(),
            accountId = prefs.getString(KEY_ACCOUNT_ID, "").orEmpty(),
            projectId = prefs.getString(KEY_PROJECT_ID, "").orEmpty(),
            telephone = prefs.getString(KEY_TELEPHONE, "").orEmpty(),
            name = prefs.getString(KEY_NAME, "").orEmpty(),
        )
    }

    override fun save(session: QzxySession) {
        prefs.edit()
            .putString(KEY_LOGIN_CODE, session.loginCode)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_ACCOUNT_ID, session.accountId)
            .putString(KEY_PROJECT_ID, session.projectId)
            .putString(KEY_TELEPHONE, session.telephone)
            .putString(KEY_NAME, session.name)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE_NAME = "secure_qzxy"
        const val KEY_LOGIN_CODE = "login_code"
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_TELEPHONE = "telephone"
        const val KEY_NAME = "name"
    }
}
