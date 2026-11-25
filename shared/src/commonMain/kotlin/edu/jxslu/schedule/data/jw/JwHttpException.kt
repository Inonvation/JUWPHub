package edu.jxslu.schedule.data.jw

/**
 * 教务链路的失败分类（DESIGN §4.27）：`CasTransport` 的契约，两端传输层共用。
 *
 * 从 `JwHttpSession` 的嵌套类提到顶层（2026-11-25 搬进 `shared/commonMain`）：
 * `CasSession` 要按这四类分别决定「记不记失败计数、转不转人工」，而它现在在共享层，
 * 看不见 `:app` 里的嵌套类型。名字与分支一个没改。
 *
 * **分类的全部意义是「凭证错才计数」**：学校 CAS 有验证码与锁定机制，把网络抖动、
 * 验证码页、5xx 记成凭证错会让对的密码被连着记两次错，然后账号被停用
 * （旧实现「无 Location 一律当凭证错」就踩过这个坑）。
 */
sealed class JwHttpException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 账号或密码错误（CAS 应答明确指向凭证）。**唯一会计入失败计数的一类。** */
    class Credential(message: String) : JwHttpException(message)

    /** 网络 / 超时 / DNS。静默重试口径，不发通知。 */
    class Network(cause: Throwable) : JwHttpException(cause.message ?: "网络错误", cause)

    /** 链路异常：execution 缺失、落点不对、会话校验不过、页面结构变更。 */
    class Protocol(message: String) : JwHttpException(message)

    /**
     * 会话在服务端已经作废（2026-10-06 用户实测的强智**单会话**形态）。
     *
     * 两种表现都归这里：业务页面退回登录页，或直接回一段
     * `{"flag1":3,"msgContent":"您的账号在其它地方登录"}`。
     * **与 [Protocol] 分开**是因为调用方要据此作废本地会话再重登一次
     * （`CasSession.fetchHtml` 的重试）——5xx / 页面结构变化那些 Protocol 重试没有意义。
     */
    class SessionInvalid(message: String) : JwHttpException(message)

    /**
     * 需要人工在网页里登录：命中验证码特征，或 CAS 返回了认不出的页面（§4.27）。
     *
     * **不计入失败计数**——成因不在密码上，计数只会把对的密码记成错的。
     */
    class Manual(message: String) : JwHttpException(message)
}
