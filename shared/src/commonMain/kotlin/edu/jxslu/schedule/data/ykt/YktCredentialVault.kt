package edu.jxslu.schedule.data.ykt

/**
 * 一卡通凭证保险箱的跨平台缝（iOS 移植 S14，2026-11-25）。
 *
 * ## 为什么必须有它
 *
 * 一卡通的学号 + 查询密码存在 Android 的 `EncryptedSharedPreferences`（`MasterKey` +
 * Keystore）里，这是 **Android 专有**能力：Kotlin/Native 上没有 Keystore，也没有
 * `EncryptedSharedPreferences` 这个类。而 [YktCredentialStore] 已经要搬进共享层，
 * 它的唯一依赖就是这份存储。
 *
 * 所以这里把「存取一份一卡通凭证」收成三个方法：共享层只认这个形状，具体怎么加密、
 * 文件叫什么、闸门状态落在哪，全由平台源集决定。
 *
 * ## 两端的实现
 *
 * | 平台 | 实现 | 状态 |
 * |---|---|---|
 * | Android | `data/session/CredentialVault`（`EncryptedSharedPreferences`，文件 `ykt_credentials.xml`，键名 `username`/`password` 沿用旧版） | 现役 |
 * | iOS | **暂无**。`IosServices.yktCredentialStore()` 返回 null，页面据此隐藏「开启」入口 | 待接 Keychain |
 *
 * iOS 侧的升级条件写在 `IosServices` 的 KDoc 里（`kSecClassGenericPassword` +
 * `kSecAttrAccessibleAfterFirstUnlock`，键名与 Android 对齐成 `username`/`password`）。
 * 在那之前**不要**给一个假实现：凭证进明文 `NSUserDefaults` 比不存更糟。
 *
 * ## 为什么不是 `fun interface`
 *
 * `WebCookieSource` 只有一个方法，能写成 `fun interface`；这里三个方法（读 / 写 / 清）
 * 凑不成一个，普通接口即可。注入形态一致——都是构造期传进去，不做进程级持有：
 * 保险箱必须**单例**（两份 `EncryptedSharedPreferences` 实例各持一份内存缓存，
 * 一边写入另一边看不见），单例由平台装配根保证（`Graph.credentialVault`）。
 */
interface YktCredentialVault {

    /** 一套一卡通凭证：学号 + 缴费平台查询密码（与教务密码**不通用**）。 */
    data class Credentials(val username: String, val password: String)

    /** 读凭证；没存过、或只存了一半，返回 null。 */
    fun read(): Credentials?

    /** 存凭证（实现负责 trim 与加密）。 */
    fun save(username: String, password: String)

    /**
     * 清凭证。
     *
     * **一次清干净**：凭证本身 + 落盘的 token（[YktTokenCache]）+ 登录闸门状态 +
     * 运行时的「已失效」标记。分开清会出现「凭证没了但状态卡还写着已失效」的悬空态，
     * 所以把这一串收在实现里，[YktCredentialStore.clear] 只转调。
     */
    fun clear()
}
