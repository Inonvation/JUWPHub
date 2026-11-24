package edu.jxslu.schedule.data.session

import platform.Foundation.NSUserDefaults

/**
 * [SessionExpirySink] 的 iOS 实现（iOS 移植 S20，2026-11-25）。
 *
 * ## 为什么这一条能实现，凭证那几条不能
 *
 * 这张表只存**布尔**：某个服务「刚刚被判过登录失效」。没有账号、没有密码、没有 token，
 * 所以 `NSUserDefaults` 就够——不需要 Keychain（与 `CredentialVault` 那种存密码的
 * 场景完全不同，那边返回 null，见 `IosServices.accountCredentials`）。
 *
 * 键名沿用 Android 的 `SessionService.id`（`qiekj` / `qzxy` / `ujing` / `kqcx` / `wenqu`），
 * 两端落的是同一套口径，只是介质不同。
 *
 * ## 编译状态
 *
 * **本机未验证**（Windows 无 Kotlin/Native）。用到的符号只有
 * `NSUserDefaults.standardUserDefaults` 的 `boolForKey` / `setBool:forKey:` / `removeObjectForKey`，
 * 若 CI 报 unresolved，改动收敛在本文件。
 */
class IosSessionExpirySink : SessionExpirySink {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun mark(serviceId: String) {
        defaults.setBool(true, forKey = key(serviceId))
    }

    override fun clear(serviceId: String) {
        defaults.removeObjectForKey(key(serviceId))
    }

    override fun isExpired(serviceId: String): Boolean = defaults.boolForKey(key(serviceId))

    override fun consume(serviceId: String): Boolean {
        if (!isExpired(serviceId)) return false
        clear(serviceId)
        return true
    }

    private fun key(serviceId: String) = "$PREFIX$serviceId"

    private companion object {
        /** 前缀防止和别的 `NSUserDefaults` 键撞（同一个域里还存着偏好设置）。 */
        const val PREFIX = "session_expiry_"
    }
}
