package edu.jxslu.schedule.data.wenqu

/**
 * 问渠凭证的**存储契约**（iOS 移植 S15，2026-11-24）。
 *
 * ## 为什么是接口
 *
 * 凭证必须加密落盘，而 `:app` 的实现靠 `EncryptedSharedPreferences` + Keystore
 * （`data/local/EncryptedPrefs`，含「密钥损坏就删文件重建」的三级自愈）——Android 专有，
 * 搬不进 commonMain。iOS 那边对应的是 Keychain，介质与自愈口径都不同，
 * 所以这里只钉**仓库用得到的那三个动作**，实现各端各给一份：
 *
 * | 平台 | 实现 | 文件 |
 * |---|---|---|
 * | Android | `WenquCredentialStore`（`EncryptedSharedPreferences`，`secure_wenqu`） | `app/.../data/wenqu/WenquCredentialStore.kt` |
 * | iOS | 待接（Keychain） | — |
 *
 * 介质与文件名留在各端实现里，**不要**为了「统一」把文件名搬进共享层：Android 侧的文件名
 * 与两份备份排除 xml（`backup_rules` / `data_extraction_rules`）是同一套口径，搬走等于
 * 让老用户的凭证读不到。
 */
interface WenquSessionStore {

    /** 读凭证；没有（或残缺到没有手机号）返回 null。 */
    fun read(): WenquSession?

    fun save(session: WenquSession)

    /** 退出登录：整份清掉（手机号也不留）。 */
    fun clear()
}
