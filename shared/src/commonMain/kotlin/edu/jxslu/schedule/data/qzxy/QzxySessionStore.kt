package edu.jxslu.schedule.data.qzxy

/**
 * 趣智校园会话的读写能力（iOS 移植 S12，2026-11-24）。
 *
 * 会话里有 `loginCode`（等于凭证），必须落在平台的加密存储里：Android 是
 * EncryptedSharedPreferences + Keystore 自愈（`:app` 的 `QzxySecureSessionStore`，
 * 文件 `secure_qzxy`，已排除云备份与设备迁移），iOS 将来接 Keychain。
 * 加密实现搬不进 `commonMain`，所以只把三个动作收成接口。
 *
 * 形态照 [edu.jxslu.schedule.data.session.JwReadSession]：共享层只依赖这里，
 * 实现留在能拿到平台加密 API 的模块。
 */
interface QzxySessionStore {

    /** 没有登录过返回 null。 */
    fun read(): QzxySession?

    fun save(session: QzxySession)

    /** 退出登录 / 会话失效时清空。 */
    fun clear()
}
