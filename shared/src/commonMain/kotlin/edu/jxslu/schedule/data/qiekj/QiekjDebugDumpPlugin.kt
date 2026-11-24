package edu.jxslu.schedule.data.qiekj

import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.platform.platformFileSystem
import io.ktor.client.call.save
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodedPath
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path

/**
 * debug 专用：把胖乖几个端点的**响应体原文**落盘（`filesDir/qiekj_debug/`），供补协议样本。
 *
 * 为什么在传输层落盘：`SyncData` / `DeviceItem` 只声明了少数字段，`QiekjJson` 又开了
 * `ignoreUnknownKeys`，服务端多给的字段在解码那一步就被丢掉。要看它究竟回了什么，只能拿原始字节。
 * 采集目标见 [isQiekjDumpTarget]；`user/reg` 的响应体带 token，不在名单里。
 *
 * 落盘失败静默：留档是副业，不该影响开水主流程。release 不装这个插件
 * （`Graph` 侧按 `BuildConfig.DEBUG` 传 null 目录），什么都不写。
 *
 * ## iOS 移植 S11（2026-11-24）：OkHttp `Interceptor` → Ktor `createClientPlugin`
 *
 * 选了「重写成等价插件」而不是「把落盘挪回 `:app`」：本仓有过「无头案全靠这一行日志」的教训，
 * 而 iOS 侧将来同样需要采样本（那边连 logcat 都没有）。commonMain 里**没有**引入 okhttp。
 *
 * 三处必须跟着换，写下来免得后来者以为只是换了个名字：
 *
 * 1. **读响应体不能再「peek」**：OkHttp 的 `peekBody` 是旁路拷贝，Ktor 的响应体是单次
 *    消费的 `ByteReadChannel`。这里用 [save]（`io.ktor.client.call.save`）把整份读进内存、
 *    包成可重读的 call，**并且把 saved 交回管线**——原 call 的体此时已经被读掉，
 *    不交回就是调用方拿到空体（这条错了只会在真机上表现为「胖乖所有接口都返回空」）。
 * 2. `java.io.File` → okio [Path] + [platformFileSystem]（commonMain 没有 `java.io`）。
 * 3. `SimpleDateFormat` → [AppClock] + 手写定宽拼接（commonMain 没有 `java.text`；
 *    时间戳定宽是为了让 [pruneQiekjDumps] 能直接按文件名字典序裁旧）。
 */
fun qiekjDebugDumpPlugin(dir: Path) = createClientPlugin("QiekjDebugDump") {
    on(Send) { request ->
        val call = proceed(request)
        val path = request.url.encodedPath.trim('/')
        if (!isQiekjDumpTarget(path)) return@on call
        // save() 失败（响应体读不出来）就原样返回原 call：留档是副业，不许因此让请求失败
        val saved = runCatching { call.save() }.getOrNull() ?: return@on call
        runCatching {
            dumpQiekjResponse(
                dir = dir,
                path = path,
                httpStatus = saved.response.status.value,
                // 与原先 peekBody(MAX_BODY_BYTES) 同口径：只留前 256K 字符
                body = saved.response.bodyAsText().take(MAX_BODY_CHARS),
            )
        }
        saved
    }
}

/** 单个响应体的落盘上限；胖乖这几个接口的返回都是几百字节的小 JSON。 */
private const val MAX_BODY_CHARS = 256 * 1024

/**
 * 采集白名单（只读查询与开水链路）。
 * 不放 `user/reg`（响应含 token）与 `user/balance`（余额与设备状态无关，少一份样本少一份隐私）。
 */
fun isQiekjDumpTarget(path: String): Boolean = path in QIEKJ_DUMP_PATHS

private val QIEKJ_DUMP_PATHS = setOf(
    "goods/latestUsed",
    "goods/normal/details",
    "goods/normal/skus",
    "goods/water/sync",
    "goods/water/unlock",
    // 结算链路（2026-10-09 加）：设备侧「结算」关会话后 sync 回 data=null，收尾改走
    // 「建单读账单」——要确认这两步在会话关闭后的原始返回，只能把它们也落盘
    "order/afterPay/creating",
    "order/detail",
    // 2026-10-14 反编译补出：远程结束用水的返回形态只有真机落盘才能确认
    // （2026-10-16 已取到样本：4072「结束失败，请在设备端操作结束」）。
    // trade/check_device_status 已随「在线显示」整块删除（2026-10-16），不再采样。
    "goods/endWater",
    // 服务端账单历史（2026-10-14）：确认 OrderItemBean 的金额/时间字段口径
    "order/list",
)

/** 每个端点最多留多少份样本：轮询 sync 一秒一条，不设上限会堆出几千个文件。 */
const val QIEKJ_DUMP_KEEP_PER_ENDPOINT = 40

/** 落一份样本（文件名 `<端点>-<时间戳>.json`），随后按端点裁剪旧文件。 */
fun dumpQiekjResponse(dir: Path, path: String, httpStatus: Int, body: String) {
    val fs = platformFileSystem()
    fs.createDirectories(dir)
    val safe = path.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val stamp = dumpStamp(AppClock.nowMillis())
    val redacted = redactQiekjDump(body)
    val payload = buildJsonObject {
        put("at", stamp)
        put("path", path)
        put("httpStatus", httpStatus)
        // 正文按 JSON 原样嵌进来（方便直接看结构）；不是合法 JSON 时就当字符串存
        put("body", runCatching { QiekjJson.json.parseToJsonElement(redacted) }
            .getOrElse { JsonPrimitive(redacted) })
    }.toString()
    var file = dir / "$safe-$stamp.json"
    var seq = 1
    while (fs.exists(file)) {
        file = dir / "$safe-$stamp-${seq++}.json"
    }
    fs.write(file) { writeUtf8(payload) }
    pruneQiekjDumps(dir, safe, QIEKJ_DUMP_KEEP_PER_ENDPOINT)
}

/**
 * 落盘文件名里的时间戳：`yyyyMMdd-HHmmss-SSS`（定宽，字典序即时间序）。
 *
 * 用 [AppClock] 而不是 `SimpleDateFormat`（commonMain 没有 `java.text`）；时区取系统默认，
 * 与原实现 `new SimpleDateFormat(...).format(Date())` 同口径。
 */
private fun dumpStamp(millis: Long): String {
    val t = AppClock.dateTimeOf(millis)
    val ms = (millis % 1000).let { if (it < 0) it + 1000 else it }
    return buildString {
        append(t.year.toString().padStart(4, '0'))
        append(t.monthNumber.toString().padStart(2, '0'))
        append(t.dayOfMonth.toString().padStart(2, '0'))
        append('-')
        append(t.hour.toString().padStart(2, '0'))
        append(t.minute.toString().padStart(2, '0'))
        append(t.second.toString().padStart(2, '0'))
        append('-')
        append(ms.toString().padStart(3, '0'))
    }
}

/** 响应体里若回显凭证字段，落盘前替换掉；不匹配就原样写。 */
fun redactQiekjDump(text: String): String = text
    .replace(TOKEN_FIELD, """"token":"<redacted>"""")
    .replace(PHONE_FIELD, """"phone":"<redacted>"""")

private val TOKEN_FIELD = Regex(""""token"\s*:\s*"[^"]*"""")
private val PHONE_FIELD = Regex(""""phone"\s*:\s*"[^"]*"""")

/** 只保留该端点最新的 [keep] 份（文件名里的时间戳定宽，字典序即时间序）。 */
fun pruneQiekjDumps(dir: Path, safeEndpointPath: String, keep: Int) {
    val fs = platformFileSystem()
    runCatching {
        fs.list(dir)
            .filter { fs.metadataOrNull(it)?.isRegularFile == true }
            .filter { it.name.startsWith("$safeEndpointPath-") }
            .sortedBy { it.name }
            .dropLast(keep)
            .forEach { fs.delete(it, mustExist = false) }
    }
}
