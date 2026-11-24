package edu.jxslu.schedule.data.session

import edu.jxslu.schedule.data.ai.AiApiKeyStore
import edu.jxslu.schedule.data.kqcx.KqcxCredentialStore
import edu.jxslu.schedule.data.qiekj.QiekjCredentialStore
import edu.jxslu.schedule.data.qzxy.QzxySession
import edu.jxslu.schedule.data.qzxy.QzxySessionStore
import edu.jxslu.schedule.data.ujing.UjingSession
import edu.jxslu.schedule.data.ujing.UjingSessionStore
import edu.jxslu.schedule.data.wenqu.WenquSession
import edu.jxslu.schedule.data.wenqu.WenquSessionStore
import edu.jxslu.schedule.platform.IosKeychain

/**
 * iOS 侧的六个第三方凭证存储（iOS 移植，2026-11-25）。
 *
 * ## 为什么六个挤在一个文件
 *
 * 它们都只是「把几个字段塞进 Keychain / 再读回来」，每个单独成文件会有五个 20 行的类
 * 各自 import 同一批东西。真正的平台差异只有一份——[IosKeychain]；这里只是各自的键名映射。
 * 一个功能一个 `service`（Keychain 的 service 维度），退出登录时按 service 整组清。
 *
 * `service` 名与 Android 侧的加密 prefs 文件名**一一对应**（`secure_ujing` ↔
 * `secure_ujing.xml`），换平台排查时对得上号。
 *
 * ## 与 Android 实现的行为对齐点
 *
 * | 存储 | 对齐的行为 |
 * |---|---|
 * | [IosQiekjCredentialStore] | token 与手机号分开存；`clear` 一起清 |
 * | [IosKqcxCredentialStore] | 存的是**明文密码**不是 token（服务端口径要求现算 MD5） |
 * | [IosUjingSessionStore] | `clearToken` 只清 token、**保留手机号**（重登少输一次） |
 * | [IosWenquSessionStore] | `hours` 存 `toBits()` 而不是十进制字符串，避免精度漂移 |
 * | [IosQzxySessionStore] | 六个字段全存，`isComplete` 由数据类自己判 |
 * | [IosAiApiKeyStore] | 读不到或空串都算未配置 |
 *
 * ## 未验
 *
 * 本文件在 Windows 上编不了，只能靠 CI 验证。**没有**在真机上验证过「杀掉进程后仍读得到」
 * 与「锁屏时后台读得到」（`kSecAttrAccessibleAfterFirstUnlock` 的语义）。
 */
internal object IosSessionStores {
    const val QIEKJ = "secure_qiekj"
    const val KQCX = "secure_kqcx"
    const val UJING = "secure_ujing"
    const val WENQU = "secure_wenqu"
    const val QZXY = "secure_qzxy"
    const val AI = "secure_ai"
}

// ---------------------------------------------------------------- 胖乖生活

internal class IosQiekjCredentialStore : QiekjCredentialStore {

    private val service = IosSessionStores.QIEKJ

    override fun readToken(): String? =
        IosKeychain.get(service, KEY_TOKEN)?.takeIf { it.isNotBlank() }

    override fun saveToken(token: String) {
        IosKeychain.set(service, KEY_TOKEN, token)
    }

    override fun readPhone(): String? =
        IosKeychain.get(service, KEY_PHONE)?.takeIf { it.isNotBlank() }

    override fun savePhone(phone: String) {
        IosKeychain.set(service, KEY_PHONE, phone)
    }

    override fun clear() {
        IosKeychain.deleteAll(service)
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_PHONE = "phone"
    }
}

// ---------------------------------------------------------------- 快趣出行

internal class IosKqcxCredentialStore : KqcxCredentialStore {

    private val service = IosSessionStores.KQCX

    override fun readCredential(): Pair<String, String>? {
        val mobile = IosKeychain.get(service, KEY_MOBILE)?.takeIf { it.isNotBlank() } ?: return null
        val password = IosKeychain.get(service, KEY_PASSWORD)?.takeIf { it.isNotBlank() } ?: return null
        return mobile to password
    }

    override fun saveCredential(mobile: String, password: String) {
        IosKeychain.set(service, KEY_MOBILE, mobile)
        IosKeychain.set(service, KEY_PASSWORD, password)
    }

    override fun clear() {
        IosKeychain.deleteAll(service)
    }

    private companion object {
        const val KEY_MOBILE = "mobile"
        const val KEY_PASSWORD = "password"
    }
}

// ---------------------------------------------------------------- U净 洗衣房

internal class IosUjingSessionStore : UjingSessionStore {

    private val service = IosSessionStores.UJING

    override fun read(): UjingSession? {
        val token = IosKeychain.get(service, KEY_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        return UjingSession(
            mobile = IosKeychain.get(service, KEY_MOBILE).orEmpty(),
            token = token,
            userId = IosKeychain.get(service, KEY_USER_ID).orEmpty(),
        )
    }

    override fun lastMobile(): String = IosKeychain.get(service, KEY_MOBILE).orEmpty()

    override fun save(session: UjingSession) {
        IosKeychain.set(service, KEY_MOBILE, session.mobile)
        IosKeychain.set(service, KEY_TOKEN, session.token)
        IosKeychain.set(service, KEY_USER_ID, session.userId)
    }

    override fun clearToken() {
        IosKeychain.delete(service, KEY_TOKEN)
    }

    override fun clear() {
        IosKeychain.deleteAll(service)
    }

    private companion object {
        const val KEY_MOBILE = "mobile"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
    }
}

// ---------------------------------------------------------------- 问渠

internal class IosWenquSessionStore : WenquSessionStore {

    private val service = IosSessionStores.WENQU

    override fun read(): WenquSession? {
        val phone = IosKeychain.get(service, KEY_PHONE)?.takeIf { it.isNotBlank() } ?: return null
        return WenquSession(
            phone = phone,
            password = IosKeychain.get(service, KEY_PASSWORD).orEmpty(),
            token = IosKeychain.get(service, KEY_TOKEN).orEmpty(),
            expiresAt = IosKeychain.get(service, KEY_EXPIRES_AT)?.toLongOrNull() ?: 0L,
            starLevel = IosKeychain.get(service, KEY_STAR_LEVEL)?.toIntOrNull() ?: 0,
            // 与 Android 侧一致：存 Double 的位模式，避免十进制往返丢精度
            hours = Double.fromBits(IosKeychain.get(service, KEY_HOURS_BITS)?.toLongOrNull() ?: 0L),
            hoursFetchedAt = IosKeychain.get(service, KEY_HOURS_FETCHED_AT)?.toLongOrNull() ?: 0L,
        )
    }

    override fun save(session: WenquSession) {
        IosKeychain.set(service, KEY_PHONE, session.phone)
        IosKeychain.set(service, KEY_PASSWORD, session.password)
        IosKeychain.set(service, KEY_TOKEN, session.token)
        IosKeychain.set(service, KEY_EXPIRES_AT, session.expiresAt.toString())
        IosKeychain.set(service, KEY_STAR_LEVEL, session.starLevel.toString())
        IosKeychain.set(service, KEY_HOURS_BITS, session.hours.toBits().toString())
        IosKeychain.set(service, KEY_HOURS_FETCHED_AT, session.hoursFetchedAt.toString())
    }

    override fun clear() {
        IosKeychain.deleteAll(service)
    }

    private companion object {
        const val KEY_PHONE = "phone"
        const val KEY_PASSWORD = "password"
        const val KEY_TOKEN = "token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_STAR_LEVEL = "star_level"
        const val KEY_HOURS_BITS = "hours_bits"
        const val KEY_HOURS_FETCHED_AT = "hours_fetched_at"
    }
}

// ---------------------------------------------------------------- 趣智校园

internal class IosQzxySessionStore : QzxySessionStore {

    private val service = IosSessionStores.QZXY

    override fun read(): QzxySession? {
        val loginCode = IosKeychain.get(service, KEY_LOGIN_CODE)?.takeIf { it.isNotBlank() }
            ?: return null
        return QzxySession(
            loginCode = loginCode,
            userId = IosKeychain.get(service, KEY_USER_ID).orEmpty(),
            accountId = IosKeychain.get(service, KEY_ACCOUNT_ID).orEmpty(),
            projectId = IosKeychain.get(service, KEY_PROJECT_ID).orEmpty(),
            telephone = IosKeychain.get(service, KEY_TELEPHONE).orEmpty(),
            name = IosKeychain.get(service, KEY_NAME).orEmpty(),
        )
    }

    override fun save(session: QzxySession) {
        IosKeychain.set(service, KEY_LOGIN_CODE, session.loginCode)
        IosKeychain.set(service, KEY_USER_ID, session.userId)
        IosKeychain.set(service, KEY_ACCOUNT_ID, session.accountId)
        IosKeychain.set(service, KEY_PROJECT_ID, session.projectId)
        IosKeychain.set(service, KEY_TELEPHONE, session.telephone)
        IosKeychain.set(service, KEY_NAME, session.name)
    }

    override fun clear() {
        IosKeychain.deleteAll(service)
    }

    private companion object {
        const val KEY_LOGIN_CODE = "login_code"
        const val KEY_USER_ID = "user_id"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_TELEPHONE = "telephone"
        const val KEY_NAME = "name"
    }
}

// ---------------------------------------------------------------- AI 调课

internal class IosAiApiKeyStore : AiApiKeyStore {

    private val service = IosSessionStores.AI

    override fun readApiKey(): String? =
        IosKeychain.get(service, KEY_API_KEY)?.takeIf { it.isNotBlank() }

    override fun saveApiKey(key: String) {
        IosKeychain.set(service, KEY_API_KEY, key.trim())
    }

    override fun clear() {
        IosKeychain.deleteAll(service)
    }

    private companion object {
        const val KEY_API_KEY = "api_key"
    }
}
