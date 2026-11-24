package edu.jxslu.schedule.ui.ai

import edu.jxslu.schedule.data.ai.AiScheduleClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException

/**
 * AI 调课的网络失败 → 用户可读文案（DESIGN §4.39）。
 *
 * 只做「分类 + 一句话」：不重试、不吞错。401/404 是最常见的两种配错
 * （Key 写错 / 地址少了 `/v1`），单独点名。
 *
 * ## iOS 移植（2026-11-25）：为什么按类名判而不是按类型判
 *
 * 迁移前认的是 `java.net.SocketTimeoutException` / `java.net.UnknownHostException` /
 * `java.io.InterruptedIOException`，三个都是 JVM 专有类型，`commonMain` 里不存在，
 * iOS 上也不会有。Ktor 只在 common 暴露两类超时（[HttpRequestTimeoutException] /
 * [ConnectTimeoutException]），DNS 失败与 socket 超时仍由**引擎**抛原生异常：
 * Android 是 OkHttp 的 `java.net.*`，iOS 是 Darwin 引擎的 `NSError` 包装。
 * 所以这两类只能按 `simpleName` 认——比 `catch` 一个不存在的类型诚实。
 *
 * `simpleName` 的顺序天然对：`SocketTimeoutException` 是 `InterruptedIOException` 的子类，
 * 但取到的是最派生那个名字，不需要像迁移前那样靠 `when` 的分支顺序。
 */
internal fun aiErrorText(t: Throwable): String {
    if (t is AiScheduleClient.AiHttpException) {
        return when (t.code) {
            401, 403 -> "API Key 无效或没有权限（HTTP ${t.code}）"
            404 -> "接口地址不对（HTTP 404）：检查服务地址是否以 /v1 结尾"
            429 -> "请求过于频繁或额度不足（HTTP 429）"
            // DeepSeek 对不存在的模型名回 400 "Model Not Exist"——历史上预填的
            // 「deepseek-flash」就栽在这，点名官方现行模型名让用户一步改对
            400 -> if (t.detail.contains("model", ignoreCase = true)) {
                "模型名不被该服务接受（HTTP 400）：检查「配置」里的模型名，或点「从上游获取」选一个"
            } else {
                "服务返回 HTTP 400：${t.detail.take(120)}"
            }

            0 -> "服务返回了无法识别的响应：${t.detail.take(120)}"
            else -> "服务返回 HTTP ${t.code}：${t.detail.take(120)}"
        }
    }
    // 总时长（600s）超时：迁移前靠 OkHttp 的 callTimeout 抛 InterruptedIOException，
    // 现在 Ktor 抛 HttpRequestTimeoutException（见 `AiScheduleClient.create` 的注释）。
    if (t is HttpRequestTimeoutException) return "上游长时间没结束（超时），稍后再试"
    if (t is ConnectTimeoutException) return "请求超时，稍后再试"
    return when (t::class.simpleName) {
        // 引擎侧：OkHttp（Android）与 Darwin（iOS）各自的原生类型
        "SocketTimeoutException" -> "请求超时，稍后再试"
        "InterruptedIOException" -> "上游长时间没结束（超时），稍后再试"
        "UnknownHostException", "UnresolvedAddressException" ->
            "无法解析服务地址，检查网络或地址拼写"

        else -> "请求失败：${t::class.simpleName ?: t::class.toString()}"
    }
}
