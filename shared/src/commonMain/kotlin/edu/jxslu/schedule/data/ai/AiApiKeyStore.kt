package edu.jxslu.schedule.data.ai

/**
 * AI 调课的 API Key 存储（iOS 移植，2026-11-25）。
 *
 * ## 为什么立这个接口
 *
 * Android 侧的实现是 `:app` 的 `AiCredentialStore`（`EncryptedSharedPreferences`，
 * 文件 `secure_ai`，键 `api_key`），它没有接口——因为此前只有 Android 一个调用方。
 * iOS 要接 Keychain，就必须有一个共享层认得的形状，否则页面无法「有 API Key 就跳过配置」。
 *
 * 接口刻意只留三个方法，与 [edu.jxslu.schedule.data.kqcx.KqcxCredentialStore] 同款：
 * 读、写、清。baseUrl / 模型名 / 脱敏开关都**不是**机密，走 `DisplayPrefsStore`，不在这里。
 *
 * ## 两端的实现
 *
 * | 平台 | 实现 | 介质 |
 * |---|---|---|
 * | Android | `:app` 的 `AiCredentialStore` | `EncryptedSharedPreferences` + Keystore |
 * | iOS | `shared/src/iosMain` 的 `IosAiApiKeyStore` | Keychain（`kSecClassGenericPassword`） |
 */
interface AiApiKeyStore {

    /** 未配置返回 null（空串也当未配置）。 */
    fun readApiKey(): String?

    /** 写入前实现负责 `trim()`。 */
    fun saveApiKey(key: String)

    fun clear()
}
