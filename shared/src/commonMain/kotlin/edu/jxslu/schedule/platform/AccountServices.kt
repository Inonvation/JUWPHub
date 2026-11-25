package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.session.CasEnsureResult
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
 * 页面要的是「这个平台配过没有」（账号卡的三档状态）与「能不能补一份教务密码」，
 * 不是密码本身：[readJwUsername] / [readYktUsername] 只回学号，密码永远不出实现类。
 *
 * Android 实现转接 `CredentialVault`（`EncryptedSharedPreferences` + `MasterKey`）；
 * iOS 实现转接 Keychain（`IosCredentialVault` / `IosYktCredentialStore`）。
 *
 * ## 2026-11-25：补上教务的写侧
 *
 * 在此之前本接口**只读、只清一卡通**——iOS 根本不保存教务密码（登录只在 `WKWebView` 里），
 * 于是「首启登录导入成功、点返回课表又要登录」无从修。写侧补上之后，两端都能在
 * 「我的 → 教务账户」里存一份学号密码，会话过期时由 `CasSession` 自己重新登录。
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

    /**
     * 验证一对教务凭据（`CasLoginDialog` 的「保存并登录」第一步）。
     *
     * **实现里必须先清失败闸门再登**（Android：`CasSession.onCredentialsUpdated()` 后
     * `tryLogin`）：用户重新输入等于重新开始计数，不清的话上一次的失败次数会把这次
     * 刚敲对的密码顶掉（`.agents/rules/login-session.md` 第 ⑤ 条）。
     *
     * 取消异常必须原样抛：弹层被关掉时这条链不该在后台跑完再落一次库。
     */
    suspend fun verifyJw(username: String, password: String): CasEnsureResult

    /**
     * 落库一份教务凭证（`CredentialVault.saveCas` / `IosCredentialVault.saveCas`）。
     *
     * **只在 [verifyJw] 返回 `Ready` 之后调用**：错密码存进去会让后续自动续登反复撞 CAS
     * 失败计数，累计到阈值账号就被停用（同一条「先验后存」纪律）。
     *
     * 没有配对的 `clearJw()`：清教务凭证必须连 WebView 的 cookie 一起清
     * （否则手登的会话还活着，「已登录」照旧成立），那条链是 [JwSessionLogout]，
     * 单开一个只删密码的入口只会做出一个假退出。
     */
    fun saveJw(username: String, password: String)
}

/**
 * 退出教务原生会话（DESIGN §4.27）。
 *
 * Android 实现是 `CasSession.logout()`；iOS 也是同一个 `CasSession`（2026-11-25 起，
 * 那类搬进了 `shared/commonMain`）。三件事必须一起做：清原生 cookie 罐 → 清 WebView 的
 * cookie 与 localStorage → 清凭证。只清凭证的话 WebView 里手登的会话还活着，
 * 「已登录」照旧成立，退出形同虚设。
 *
 * null = 本平台没有「原生会话」这个概念（现在的两端都有，留着是给平台能力缺口的口径）。
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
