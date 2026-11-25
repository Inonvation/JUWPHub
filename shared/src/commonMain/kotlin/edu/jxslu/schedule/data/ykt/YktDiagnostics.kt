package edu.jxslu.schedule.data.ykt

/**
 * 一卡通失败的**诊断信息脱敏**（2026-11-25，iOS 一卡通登录「笼统失败」排查用）。
 *
 * 背景：登录失败原先被 [YktException.Network] 收敛成「网络不可达」四个字，iOS 与 Android
 * 的真实差异（TLS、连接、状态码、平台拒绝）全被吞掉，用户截图也看不出所以然。现在
 * 失败要带「异常类名 + message + 目标 URL」或「HTTP 状态码 + 响应体前 300 字」，本文件
 * 负责把这些**在离开数据层之前**洗干净：
 *
 * | 载体 | 规则 |
 * |---|---|
 * | URL | 只留 `scheme://host/path`；query 里命中敏感键的值换成 `***`，超长值截断；丢掉 fragment |
 * | 响应体 | 命中敏感键的值换成 `***`，空白压成单空格，截到 [BODY_LIMIT] 字 |
 *
 * **红线**：这里出去的字符串会进剪贴板与用户截图，学号 / 密码 / token / 卡号一个都不能有。
 * 新增敏感键往 [SENSITIVE_KEYS] 里加，别在调用点临时拼。
 *
 * 纯 Kotlin（不用 `java.net.URI`、不用 `RegexOption.DOT_MATCHES_ALL`），两端同一份实现，
 * 结果与 Android 逐字一致——`YktDiagnosticsTest` 拿真实响应形态钉住。
 */
object YktDiagnostics {

    /** 响应体片段上限（字）。够看出「登录页 HTML」还是「网关 502」，又不至于把整页塞进截图。 */
    const val BODY_LIMIT: Int = 300

    /**
     * 敏感键（小写、子串匹配）：出现在 URL query 键名或响应体键名里就整值抹掉。
     *
     * `account` / `cardno` 是一卡通卡号（6 位数字），`uuid` 是安全键盘的会话号——
     * 三者都能拿去换码，一并按凭证对待。`stdid` / `sno` 是学号：不算凭证，但按仓库口径
     * 个人信息不进截图。
     *
     * **唯一来源**：URL 脱敏与响应体脱敏都从这里生成，别在别处再抄一份键名清单。
     */
    private val SENSITIVE_KEYS: List<String> = listOf(
        "password",
        "passwd",
        "pwd",
        "token",
        "synjones-auth",
        "cardno",
        "card_no",
        "account",
        "username",
        "stdid",
        "studentid",
        "studentno",
        "sno",
        "uuid",
        "openid",
        "prepay_id",
        "prepayid",
        "ticket",
        "secret",
        "phone",
        "mobile",
        "idcard",
    )

    /** 非敏感 query 值的显示上限：长 base64 / 签名没必要进截图。 */
    private const val VALUE_LIMIT: Int = 24

    /** 响应体里的 `"key":"value"` / `key=value` 两种形态（IGNORE_CASE 是两端都有的开关）。 */
    private val KV_PATTERN: Regex = Regex(
        "(\"?(?:" + SENSITIVE_KEYS.joinToString("|") + ")\"?\\s*[:=]\\s*)" +
            "(\"[^\"]*\"|[^,;&}\\s\"]+)",
        RegexOption.IGNORE_CASE,
    )

    private val WHITESPACE: Regex = Regex("[\\s\\u0000]+")

    /** 键名是否敏感（小写子串匹配）。 */
    private fun isSensitive(key: String): Boolean {
        val k = key.lowercase()
        return SENSITIVE_KEYS.any { it in k }
    }

    /**
     * URL 脱敏：保留 `scheme://host/path`，query 的敏感值抹成 `***`。
     *
     * 为什么连非敏感的 query 值也截断：`checkmweb?prepay_id=…` 这类参数本身就是支付凭证，
     * 而键名清单永远会漏；截断保证「漏掉一个键也只泄露前 24 字的前缀」。
     */
    fun redactUrl(url: String): String {
        val fragment = url.indexOf('#')
        val withoutFragment = if (fragment >= 0) url.substring(0, fragment) else url
        val q = withoutFragment.indexOf('?')
        if (q < 0) return withoutFragment
        val base = withoutFragment.substring(0, q)
        val query = withoutFragment.substring(q + 1)
        val masked = query.split('&')
            .filter { it.isNotEmpty() }
            .joinToString("&") { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) {
                    if (isSensitive(pair)) pair + "=***" else pair
                } else {
                    val key = pair.substring(0, eq)
                    val value = pair.substring(eq + 1)
                    if (isSensitive(key)) "$key=***" else "$key=${shorten(value)}"
                }
            }
        return "$base?$masked"
    }

    /**
     * 响应体脱敏（不截断）：抹敏感值 + 压空白。
     *
     * 单独暴露是因为调用方有时只要洗不要截（例如自己要再拼前缀）。
     */
    fun redactBody(text: String): String {
        val masked = KV_PATTERN.replace(text) { m ->
            val value = m.groupValues[2]
            // 原文带引号的保持引号，让结构还看得懂
            if (value.startsWith("\"")) "${m.groupValues[1]}\"***\"" else m.groupValues[1] + "***"
        }
        return WHITESPACE.replace(masked, " ").trim()
    }

    /**
     * 响应体片段：脱敏 → 压空白 → 截到 [BODY_LIMIT] 字（超了补 `…`）。
     *
     * 空 body 返回空串（调用方据此不拼那一段），不是 "null"。
     */
    fun bodySnippet(text: String, limit: Int = BODY_LIMIT): String {
        val clean = redactBody(text)
        if (clean.length <= limit) return clean
        return clean.substring(0, limit) + "…"
    }

    /** 超长值截断显示；正常长度原样返回。 */
    private fun shorten(value: String): String =
        if (value.length <= VALUE_LIMIT) value else value.substring(0, VALUE_LIMIT) + "…"
}
