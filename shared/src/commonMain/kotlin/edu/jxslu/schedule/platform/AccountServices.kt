package edu.jxslu.schedule.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * 账号中心页（`ui/me/hub/AccountsHubScreen`）要的那几条**账号侧平台能力**（iOS 移植 S20）。
 *
 * 为什么单独成文件而不是直接往 [AppServices] 里塞一堆方法：这三个接口各只有 1–4 个方法，
 * 放在一起读得清「页面到底要平台给什么」；[AppServices] 那边只留一行 `fun xxx(): Xxx?`。
 *
 * 三个都是**可空成员**（`AppServices` 里返回 `Xxx?`），null 的语义统一是
 * 「本平台没有这个能力」——页面据此整行 / 整块不渲染，而不是给一个点了没反应的入口
 * （`design/ios-port.md` §11.2 ④）。
 */

/**
 * 教务与一卡通的凭证快照 + 登录闸门（DESIGN §4.27）。
 *
 * **只读用户名、只清一卡通**：页面要的是「这个平台配过没有」（账号卡的三档状态），
 * 不是密码本身。密码永远不出实现类——共享层拿到的 [String] 只是学号。
 *
 * Android 实现转接 `CredentialVault`（`EncryptedSharedPreferences` + `MasterKey`）；
 * iOS 侧要等 Keychain 实现，在那之前账号中心把这两行按「未配置」显示。
 */
interface AccountCredentials {

    /** 统一认证（教务）的学号；没存过返回 null。 */
    fun readJwUsername(): String?

    /** 一卡通 / 缴费平台的学号；没存过返回 null。 */
    fun readYktUsername(): String?

    /**
     * 清一卡通凭证：凭证 + 落盘 token 一起清（`CredentialVault.clearYkt`）。
     *
     * **不动教务那一套**——两份凭证互不牵连，清一卡通不该把教务也退了。
     */
    fun clearYkt()

    /**
     * 把一卡通与胖乖的登录闸门恢复成「没有失败记录」。
     *
     * 只在「清除全部凭证」时用：闸门是**落盘**的（连续失败次数 / 停用标记），
     * 不清掉的话用户清完凭证再登录一次，可能仍被上一轮的停用标记挡住。
     * 教务那格由 [JwSessionLogout] 自己处理，不在这里。
     */
    fun resetLoginGates()
}

/**
 * 退出教务原生会话（DESIGN §4.27）。
 *
 * Android 实现是 `CasSession.logout()`：清 OkHttp cookie + 凭证 + WebView 会话，
 * 三件事必须一起做——只清凭证的话 WebView 里手登的会话还活着，「已登录」照旧成立。
 *
 * **iOS 侧为 null**：那边没有原生教务会话，登录只发生在 `WKWebView` 里，
 * 退出等于清 `WKWebsiteDataStore`——等 `WebCookieSource` 补上反向能力再给实现。
 */
fun interface JwSessionLogout {

    /** 失败抛异常，调用方 `runCatching` 收成一句提示（清其余凭证不该被它挡住）。 */
    suspend fun logout()
}

/**
 * AI 调课的 API Key（DESIGN §4.49）。
 *
 * 只给账号中心那一行用：读到 / 没读到决定它显示「已配置」还是「去配置」，
 * 「清除全部凭证」时删掉。真正的调用方（`AiScheduleClient`）自己拿 Key，不走这里。
 *
 * **null = 本平台没有加密存储**（iOS 要等 Keychain，与 [AccountCredentials] 同一条件）。
 */
interface AiApiKeyStore {

    /** 没配过返回 null。 */
    fun readApiKey(): String?

    fun clear()
}

/**
 * 账号中心要的「第三方会话」最小形状：登录态 + 退出。
 *
 * 只给快趣用。其余四个第三方仓库（胖乖 / 趣智 / U净 / 问渠）的类型早在 `commonMain`，
 * 装配根直接返回原类型即可；快趣的逻辑在共享层 `KqcxSessionCore`，但 `:app` 的
 * `KqcxSessionRepository` 把 core 包成**私有字段**（那个文件本轮不在可改范围），
 * 装配根拿不到 core 本体。于是这里只要它对外暴露的那两个能力，不把 `:app` 的类型
 * 引进接口——那样 iOS 侧连签名都写不出来。
 */
interface ThirdPartySession {

    /** 登录态；会话不可用时实现方给一个恒 false 的流。 */
    val loggedIn: StateFlow<Boolean>

    /** 退出登录并清本机凭证。 */
    fun logout()
}
