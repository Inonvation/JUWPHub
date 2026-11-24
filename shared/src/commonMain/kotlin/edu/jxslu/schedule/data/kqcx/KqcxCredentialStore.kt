package edu.jxslu.schedule.data.kqcx

/**
 * 快趣账号凭证的读写能力（iOS 移植 S12，2026-11-24）。
 *
 * ## 为什么立缝
 *
 * `KqcxSessionCore` 需要「手机号 + 密码」才能在 token 过期后静默重登，而凭证本身
 * 必须落在**平台加密存储**里：Android 是 EncryptedSharedPreferences + Keystore
 * 自愈（`:app` 的 `KqxCredentialStore`，文件名 `secure_kqcx`，已排除云备份与设备迁移），
 * iOS 将来要接 Keychain。加密实现搬不进 `commonMain`（那是 `android.security.*`），
 * 所以只把**三个动作**收成接口，实现留在各平台。
 *
 * ## 形态照 [edu.jxslu.schedule.data.session.JwReadSession]
 *
 * 只放共享层真正调用的成员：读、写、清。**唯一的 Android 实现是 `:app` 的
 * `KqxCredentialStore`**（注意那个类名少一个 `c`，是历史拼写，别改——改了会让
 * `data/ai/AiCredentialStore` 的 KDoc 指向不存在的类）。
 *
 * ## 落的是明文密码，不是 token
 *
 * 快趣的密码登录（`userLoginByPassword`）无签名、凭证即全部：密码原文落盘、请求时现算
 * MD5（服务端口径）。token 本身**只存内存**（见 [KqcxSessionCore]）。
 */
interface KqcxCredentialStore {

    /** 凭证缺失（未登录）返回 null；返回 `手机号 to 密码明文`。 */
    fun readCredential(): Pair<String, String>?

    /** 登录成功且用户勾了「记住密码」时写入。 */
    fun saveCredential(mobile: String, password: String)

    /** 退出登录 / 用户选择不记住密码时清空。 */
    fun clear()
}
