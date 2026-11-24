package edu.jxslu.schedule.data.ykt

/**
 * 一卡通 access token 的落盘缓存（DESIGN §4.27）。
 *
 * 存在的唯一理由是「冷启动少登一次」：token 有 70 天寿命，却因为「只存内存」的旧口径
 * 每次进程重启都要重新登录（取安全键盘 + 换 token 两条请求）。实现是平台侧的凭证保险箱，
 * 抽成接口是为了让 `YktRepository` 在 JVM 单测里能注入假缓存。
 *
 * 它**不是授权依据**——真正的失效判定在服务端（401 → 重登）。
 *
 * iOS 移植 S14（2026-11-25）：本文件从 `data/session/` 搬进 `data/ykt/`，
 * **包名随之从 `data.session` 变成 `data.ykt`**。原因是 `data/session` 属于并行的登录流，
 * 而 token 缓存只是 [YktRepository] 的私有依赖、没有第二个消费者——跟着仓库走，
 * 两条流就不会在同一个目录里互相踩。全仓只有 `CredentialVault` 与 `YktRepository` 引用它。
 */
interface YktTokenCache {

    /** 取还新鲜的 token；没有、过期、或时钟异常都返回 null。 */
    fun readToken(nowMs: Long): String?

    fun saveToken(token: String, nowMs: Long)

    fun clearToken()
}
