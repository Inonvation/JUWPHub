package edu.jxslu.schedule.data.local

import platform.Foundation.NSUserDefaults

/**
 * `KeyValuePrefs` 的 iOS 实现（2026-11-25）。
 *
 * ## 为什么用 `NSUserDefaults` 而不是 JSON 文件
 *
 * 这个接口服务的三份 U净 存储（订单快照 / 历史账单 / 收藏洗衣房）在 Android 上是
 * `SharedPreferences`，语义就是「小体量键值、整份读写、进程内常驻」。`NSUserDefaults`
 * 是 iOS 上语义最接近的介质，改过去不丢行为；换成 JSON 文件反而要自己管「读到哪一版」。
 *
 * 三份存储各用一个 `prefix`（`ujing_order` / `ujing_history` / `ujing_houses`），
 * 与 Android 的三个 prefs 文件名一一对应——排查时对得上号，也不会互相覆盖同一个键。
 *
 * ## 什么时候**不该**用它
 *
 * 凭证（token / 密码）**不能**走这里：`NSUserDefaults` 是明文 plist。
 * 那几份走 `IosKeychain`（见 `IosSessionStores`）。这条边界与 Android 侧
 * 「凭证进 EncryptedSharedPreferences、其余进普通 SharedPreferences」是同一条。
 *
 * ## 未验
 *
 * 本文件在 Windows 上编不了，只过了 CI 的 macOS 编译；没有真机验证过
 * 「杀掉进程后仍读得到」（`NSUserDefaults` 会在合适的时机自动落盘，正常路径下会）。
 */
internal class IosKeyValuePrefs(private val prefix: String) : KeyValuePrefs {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(nsKey(key))

    override fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = nsKey(key))
    }

    override fun getStringSet(key: String): Set<String> =
        defaults.arrayForKey(nsKey(key))
            ?.filterIsInstance<String>()
            ?.toSet()
            .orEmpty()

    override fun putStringSet(key: String, value: Set<String>) {
        // NSUserDefaults 只认 NSArray；`List<String>` 会被桥接成 NSArray<NSString>
        defaults.setObject(value.toList(), forKey = nsKey(key))
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(nsKey(key))
    }

    /**
     * 清空**本前缀下**的全部键。
     *
     * 不调 `NSUserDefaults` 的全局清理：那会把别的功能（包括凭证以外的偏好）一起抹掉。
     */
    override fun clear() {
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { it.startsWith("$prefix.") }
            .forEach { defaults.removeObjectForKey(it) }
    }

    private fun nsKey(key: String) = "$prefix.$key"
}
