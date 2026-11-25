package edu.jxslu.schedule.data.ykt

/**
 * 校园卡登录凭证存储（DESIGN §4.19）。
 *
 * **公开签名一个都没动**（8 个调用点：生活页 / 账单页 / 付款码 / 设置页 / 余额提醒…），
 * 内部改为委托 [YktCredentialVault]——加密存储的唯一读写口（DESIGN §4.27「改动面」）。
 * Android 侧文件仍是 `ykt_credentials.xml`、键名仍是 `username` / `password`，升级用户的
 * 凭证原样可读；`backup_rules` / `data_extraction_rules` 照旧排除该文件，
 * 凭证不随云备份与设备迁移走。
 *
 * 构造点**只有装配根一处**（Android 是 `Graph.yktCredentialStore`）——保险箱必须是单例，
 * 否则两份 `EncryptedSharedPreferences` 实例各持一份内存缓存，一边写入另一边看不见。
 *
 * iOS 移植 S14（2026-11-25）：搬进 `:shared`。平台差异全部收进 [YktCredentialVault]：
 * Android 是 `CredentialVault`（`EncryptedSharedPreferences`），iOS 是
 * `IosYktCredentialStore`（Keychain，service `secure_ykt`），两端的 `IosServices` /
 * `GraphServices` 各构造一份，页面代码一行不分叉。
 */
class YktCredentialStore(private val vault: YktCredentialVault) {

    data class Credentials(val username: String, val password: String)

    fun read(): Credentials? = vault.read()?.let { Credentials(it.username, it.password) }

    fun save(username: String, password: String) = vault.save(username, password)

    /**
     * 清除凭证（关闭开关时调用）。
     *
     * 同时清掉一卡通的登录闸门、停用标记与落盘 token——凭证都没了，「已失效」也没有意义。
     * **不动教务那套**——两份凭证互不牵连（DESIGN §4.27）。这一串动作由
     * [YktCredentialVault.clear] 的实现负责（闸门状态是平台侧存储，共享层看不见）。
     */
    fun clear() = vault.clear()
}
