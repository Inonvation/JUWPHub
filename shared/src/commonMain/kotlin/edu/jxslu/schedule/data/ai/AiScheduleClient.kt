package edu.jxslu.schedule.data.ai

import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.domain.AiScheduleTool
import edu.jxslu.schedule.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * AI 调课的 OpenAI 兼容客户端（DESIGN §4.39，2026-10-06）。
 *
 * 对接 `/chat/completions`（**流式 SSE**）与 `/models` 两个端点，baseUrl 由用户在
 * 设置里配（预填 `https://api.deepseek.com/v1`，可清空自定义）。
 *
 * **流式**：`stream: true`，逐条解析 `data:` 行，把三种增量分开吐给上层——
 * `reasoning_content`（思考过程）、`content`（正文）、`tool_calls[].function.arguments`
 * （工具参数分片，需累积拼完整）。用户要看到「边想边出」而不是等一坨。
 *
 * **思考档位与强制工具调用的冲突**（最容易踩的坑）：DeepSeek 的 `thinking` 默认开启，
 * 而**指定函数名的 `tool_choice` 在思考模式下直接返回 400**（官方文档 Tool Calls 节）。
 * 所以：档位 = 关闭 → `thinking: disabled` + 指定函数名；档位 = 开启 → 不带强制，
 * `tool_choice: auto`，靠 system prompt 要求调工具（正文兜底见 `AiScheduleTool.extractJson`）。
 * `thinking` / `reasoning_effort` 只对 `baseUrl` 含 `deepseek` 的端点发——其它 OpenAI
 * 兼容服务不认识，发了可能被判非法参数。
 *
 * **无任何日志拦截器**：API Key 绝不进 Logcat（对齐一卡通 / 电费 / 快趣 / U净）。
 *
 * ## iOS 移植 S15（2026-11-24）：搬进 `:shared`，OkHttp → Ktor
 *
 * 这是本轮唯一有技术风险的一块（流式），口径写在这里：
 *
 * 1. **不再用 `enqueue + Callback`**：OkHttp 那条路要的是「协程取消打断不了
 *    `readUtf8Line()`，只有 `call.cancel()` 能立刻关 socket」。Ktor 没有这个问题——
 *    读流是**挂起**的（[ByteReadChannel.readUTF8Line]），collector 一取消，
 *    `channelFlow` 的块随之取消，Ktor 关闭响应体与底层连接，socket 当场断。
 *    用户点「停止」的意图（**别再烧额度**）照样成立，且不需要任何回调。
 * 2. **背压取代 `trySendBlocking`**：读流侧现在在协程里，直接 `send`（挂起）即可。
 *    原来 `callbackFlow` 默认容量 64、`trySend` 不看返回值会把分片静默丢掉
 *    （2026-11-06 审计 A1：工具参数变成半截 JSON）；改成挂起发送后**结构上不可能丢**，
 *    外面那条 `buffer(UNLIMITED)` 保留——它挡的是「消费端慢 → 读流被反压 → socket 读停」，
 *    与原来那条注释同一个理由。
 * 3. **`Dispatchers.IO` → `Dispatchers.Default`**：commonMain 取不到 IO 调度器。
 *    流式读取本身是挂起等待，占不住线程；Default 池足够。
 * 4. **超时口径**：`readTimeout(90s)`（两次读的间隔）→ socket 超时；
 *    `callTimeout(600s)`（总时长）→ request 超时；`connectTimeout(15s)` 原样。
 *    见 [create]。
 * 5. **异常类型**：`AiHttpException` 不再继承 `java.io.IOException`（commonMain 没有它），
 *    改成 `Exception`。上层全部按 `is AiHttpException` 判，所以 `:app` 的 `AiErrorText`
 *    不用改。**已知文案差异**：总时长（600s）触发时 Ktor 抛 `HttpRequestTimeoutException`，
 *    它不在 `AiErrorText` 认的那几类里，用户会看到「请求失败：HttpRequestTimeoutException」；
 *    socket 超时（90s 不吐字节）仍是 `java.net.SocketTimeoutException`，文案照旧。
 *    等 `ui/ai` 搬进共享层时给 `AiErrorText` 补一条分支即可。
 * 6. **图片入参的类型**：`images: List<Any>` + 构造时注入的 [imageEncoder]。
 *    唯一调用点 `ui/ai/AiChatViewModel` 传的是 `List<File>`，而那条页面本轮不许动——
 *    `List<File>` 按协变可以直接塞进 `List<Any>`，读文件与 base64 编码留在 `:app`
 *    （见 `:app` 的 `AiScheduleClientImages.kt`），commonMain 只认「元素 → data URI」这一步。
 *    iOS 侧接的时候传自己的图片类型、编码器在装配根里给。
 */
class AiScheduleClient private constructor(
    private val http: HttpClient,
    /**
     * 「图片元素 → `data:image/jpeg;base64,…`」；返回 null = 这张跳过（不整批失败）。
     *
     * 缺省是恒 null：不注入就等于「这个平台不发图片」，纯文本链路照常可用。
     * 见类注释第 6 条（为什么不是强类型）。
     */
    private val imageEncoder: (Any) -> String? = { null },
) {

    /** 流式增量。上层累积成三段文本，结束时解析工具参数。 */
    sealed interface StreamEvent {
        /** 思考过程增量（仅思考档位开启时有）。 */
        data class Reasoning(val delta: String) : StreamEvent

        /** 正文增量。 */
        data class Content(val delta: String) : StreamEvent

        /** 工具参数分片，需累积拼接成完整 JSON。 */
        data class ToolArgs(val delta: String) : StreamEvent
    }

    /** HTTP 层失败。[code] 为 0 表示响应体不是预期结构；[detail] 是响应原文截断，供 UI 提示。 */
    class AiHttpException(val code: Int, val detail: String) : Exception("HTTP $code")

    // 思考档位（原 `AiScheduleClient.Effort` 嵌套枚举）2026-11-24 搬到
    // `:shared` 的 data/ai/Effort.kt：偏好层要读它，而偏好层已经进了共享模块。
    // 同包所以这里不用 import，`Effort.xxx` 照旧。

    /**
     * 发一次调课请求，流式返回增量。[baseUrl] 结尾有无 `/` 都行。
     * 网络异常与 HTTP 错误都抛 [AiHttpException] / 平台网络异常，由上层分类提示，不自动重试。
     *
     * [images] 是随本轮发出的图片（字节）。**空列表时 content 仍是字符串**，
     * 与非视觉模型、老网关保持兼容；非空才升成 `[{text}, {image_url}…]` 的多模态数组，
     * **文本在前、图片在后**（顺序影响模型理解）。
     */
    fun chatStream(
        baseUrl: String,
        apiKey: String,
        model: String,
        system: String,
        user: String,
        effort: Effort,
        images: List<Any> = emptyList(),
    ): Flow<StreamEvent> = channelFlow {
        val body = buildChatBody(baseUrl, model, system, user, effort, images)
        try {
            val response = http.request(endpoint(baseUrl, "chat/completions")) {
                method = HttpMethod.Post
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                // 错误响应体整段读（不是流），与迁移前 `resp.code + body.take(500)` 同口径
                val text = runCatching { response.bodyAsText() }.getOrDefault("")
                throw AiHttpException(response.status.value, text.take(500))
            }
            readStream(response.bodyAsChannel(), this)
            close()
        } catch (e: CancellationException) {
            // 用户点「停止」：原样抛出，别当成失败气泡
            throw e
        } catch (e: Throwable) {
            close(e)
        }
    }.buffer(Channel.UNLIMITED)
        .flowOn(Dispatchers.Default)

    /** 请求体（与迁移前逐字一致的 JSON 形状，便于与抓包样本对照）。 */
    private fun buildChatBody(
        baseUrl: String,
        model: String,
        system: String,
        user: String,
        effort: Effort,
        images: List<Any>,
    ): String {
        val thinking = effort != Effort.NONE
        val isDeepSeek = baseUrl.contains("deepseek", ignoreCase = true)
        return buildJsonObject {
            put("model", model)
            put("stream", true)
            if (isDeepSeek) {
                putJsonObject("thinking") { put("type", if (thinking) "enabled" else "disabled") }
                if (thinking) put("reasoning_effort", effort.wire)
            }
            if (thinking) {
                // 思考模式不支持指定函数名（400），退回 auto，靠 system prompt 要求调工具
                put("tool_choice", "auto")
            } else {
                putJsonObject("tool_choice") {
                    put("type", "function")
                    putJsonObject("function") { put("name", AiScheduleTool.FUNCTION_NAME) }
                }
            }
            putJsonArray("tools") {
                addJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", AiScheduleTool.FUNCTION_NAME)
                        put("description", TOOL_DESCRIPTION)
                        put("parameters", format.parseToJsonElement(AiScheduleTool.PARAMETERS_JSON))
                    }
                }
            }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    val dataUris = images.mapNotNull(imageEncoder)
                    if (dataUris.isEmpty()) {
                        put("content", user)
                    } else {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", user)
                            }
                            dataUris.forEach { uri ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", uri) }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }

    /**
     * 逐行读 SSE。三种增量分开吐；同时兜住三类「静默成功」的坑：
     *
     * 1. **200 但不是 SSE**（网关 HTML / 整体 JSON 错误页）：一行都解析不出时抛错，
     *    而不是让上层把空结果当成「模型没有返回内容」的**成功**气泡。
     * 2. **流中途的 `error` 块**：部分网关这么报（额度 / 内容策略），原样抛出去。
     * 3. **`finish_reason` 是 length / content_filter**：输出被截断，工具参数必然是半截 JSON，
     *    早点报错比让解析层全拒更好。
     *
     * **本方法吃 [ByteReadChannel]**（Ktor 的响应体流）而不是 OkHttp 的 `BufferedSource`：
     * 单测用 `ByteReadChannel(sse)` 直接喂 SSE 文本，钉住「某种 delta 不掐断流」的行为。
     *
     * 发送用**挂起的 [SendChannel.send]**：消费端慢时读流自然被反压，分片一个都不会丢
     * （见类注释第 2 条）。
     */
    private suspend fun readStream(body: ByteReadChannel, out: SendChannel<StreamEvent>) {
        val snippet = StringBuilder()
        var sawEvent = false
        while (true) {
            val line = body.readUTF8Line() ?: break
            if (snippet.length < SNIPPET_MAX) {
                snippet.append(line.take(120)).append('\n')
            }
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) continue
            if (payload == "[DONE]") {
                sawEvent = true
                break
            }
            val root = runCatching { format.parseToJsonElement(payload) }.getOrNull() as? JsonObject
                ?: continue
            (root["error"] as? JsonObject)?.let {
                throw AiHttpException(0, it.toString().take(300))
            }
            val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: continue
            sawEvent = true
            val finish = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
            if (finish == "length" || finish == "content_filter") {
                throw AiHttpException(0, "上游输出被截断（finish_reason=$finish），结果不完整")
            }
            val delta = choice["delta"] as? JsonObject ?: continue
            (delta["reasoning_content"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
                ?.let { out.send(StreamEvent.Reasoning(it)) }
            (delta["content"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotEmpty() }
                ?.let { out.send(StreamEvent.Content(it)) }
            // **本函数最要紧的一行纪律**：不认识的 delta 一律 `continue`，绝不 `return`。
            // 2026-10-06 修复：此前「delta 里没有 tool_calls → return」会把整个流在第一条
            // 正文 / 思考 / finish 块就掐断——工具参数只收到零个分片，上层解析出空结果，
            // 用户看到的就是「发了任何消息都返回无效」。V4.1-Flash 的输出顺序一变必踩。
            // 同理，只认 0 号工具：**跳过**别的 index / 别的函数名的 toolCall，继续读，
            // 而不是结束——并行 tool_call 或网关注入别的函数时不能把正主一起扔掉。
            val toolCalls = delta["tool_calls"] as? JsonArray ?: continue
            for (element in toolCalls) {
                val toolCall = element as? JsonObject ?: continue
                val toolIndex = (toolCall["index"] as? JsonPrimitive)?.intOrNull ?: 0
                if (toolIndex != 0) continue
                val function = toolCall["function"] as? JsonObject
                val functionName = (function?.get("name") as? JsonPrimitive)?.contentOrNull
                if (functionName != null && functionName != AiScheduleTool.FUNCTION_NAME) continue
                (function?.get("arguments") as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { out.send(StreamEvent.ToolArgs(it)) }
            }
        }
        if (!sawEvent) {
            throw AiHttpException(
                0,
                "响应不是流式数据（可能被网关拦截）：${snippet.toString().trim().take(300)}",
            )
        }
    }

    /** 单测入口：与 [readStream] 同一实现，用 `ByteReadChannel(sse)` 直接喂 SSE 文本。 */
    suspend fun readStreamForTest(body: ByteReadChannel, out: SendChannel<StreamEvent>) {
        readStream(body, out)
    }

    /** 拉上游模型列表（设置页「获取模型列表」用）。解析失败返回空列表而不是抛错。 */
    suspend fun listModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.Default) {
            val text = runCatching {
                val response = http.request(endpoint(baseUrl, "models")) {
                    method = HttpMethod.Get
                    header("Authorization", "Bearer $apiKey")
                }
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                if (!response.status.isSuccess()) {
                    throw AiHttpException(response.status.value, body.take(500))
                }
                body
            }.onFailure {
                Logger.w(TAG, "models failed: ${it::class.simpleName}")
            }.getOrThrow()
            val root = runCatching { format.parseToJsonElement(text) }.getOrNull() as? JsonObject
                ?: return@withContext emptyList()
            (root["data"] as? JsonArray).orEmpty()
                .mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }
                .filter { it.isNotBlank() }
        }

    companion object {
        private const val TAG = "AiScheduleClient"
        private const val TOOL_DESCRIPTION =
            "把调课通知翻译成对当前课表的改动。课程必须引用课表清单里的编号（existingIndex），" +
                "不要臆造课程；按具体日期发布的放假 / 调休补课安排走 overrides。"
        private val format = Json { ignoreUnknownKeys = true; isLenient = true }

        /** 非 SSE 响应体截取多少字给用户看。 */
        private const val SNIPPET_MAX = 500

        /** 「上游不吃图」的报错关键词。见 [looksLikeImageRejection]。 */
        private val IMAGE_REJECT_HINTS = listOf(
            "image_url", "image", "vision", "multimodal", "modality", "图片",
        )

        /** baseUrl 结尾的 `/` 归一后拼子路径；已带 `/v1` 的照原样接。 */
        private fun endpoint(baseUrl: String, path: String): String =
            baseUrl.trim().trimEnd('/') + "/" + path

        /**
         * 上游是不是在抱怨「不吃图片」。只作**提示**用（决定失败气泡给不给「去掉图片重发」），
         * 不是硬判定——所以放宽：4xx 且报错里出现图片/模态相关词就算。
         * 有的网关只回一句笼统的 400，那就落到普通失败分支，用户照样能手动去掉图片重发。
         */
        fun looksLikeImageRejection(code: Int, detail: String): Boolean =
            // 413 = 请求体过大：图片场景最典型的失败，也算图片类，用户才有「去掉图片重发」这条路
            (code in 400..422 || code == 413) &&
                IMAGE_REJECT_HINTS.any { detail.contains(it, ignoreCase = true) }

        /**
         * 派生纪律（见 `AppHttp` 注释）：只覆盖超时，不动连接池。
         *
         * 三个数字的来历（与 OkHttp 那三档一一对应）：
         * - socket 90s：只管**两次读的间隔**——流式响应会持续吐字节，不会触发它；
         * - request 600s：兜「模型长时间不吐字」的总时长（原先 callTimeout）；
         * - connect 15s：与其余模块一致。
         *
         * 注意 request 超时在 Ktor 里是 `HttpRequestTimeoutException`（它继承 `IOException`，
         * **不是** `CancellationException`），所以不会被「用户点停止」那条分支吞掉。
         */
        fun create(imageEncoder: (Any) -> String? = { null }): AiScheduleClient = AiScheduleClient(
            http = SharedHttp.create(
                requestTimeoutSeconds = 600,
                connectTimeoutSeconds = 15,
                socketTimeoutSeconds = 90,
            ),
            imageEncoder = imageEncoder,
        )
    }
}
