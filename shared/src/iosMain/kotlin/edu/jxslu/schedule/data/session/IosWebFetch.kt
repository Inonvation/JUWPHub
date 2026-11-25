package edu.jxslu.schedule.data.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 「在已登录的 `WKWebView` 里同源取页」的桥（2026-11-25 晚，用户报「导入说成功但课表是空的」之后）。
 *
 * ## 为什么不能只靠 Cookie
 *
 * 上一版的抓取链是「从 `WKWebsiteDataStore` 读 Cookie → 交给 Ktor 发 HTTP」。这条路在
 * 2026-11-25 真机上被证伪：WebView 里明明已经登进教务（页面上是「个人中心」），
 * 但 `IosJwReadSession.ensureValid()` 读到的 Cookie 头里没有可用的会话，导入直接判
 * 「还没有登录教务」。可能是 Cookie 名、域过滤或读时机三者之一，**在没有真机调试通道的
 * 前提下继续猜是浪费**——而 WebView 自己知道怎么带着登录态取页。
 *
 * 所以主路径换成：在**当前这个已经登录的页面**里跑一段异步 `XMLHttpRequest`，同源取回 HTML。
 * Cookie、UA、Referer、TLS 全由 WebKit 负责，与「用户自己点开那一页」逐字节一致。
 * Ktor 那条路保留为兜底（WebView 已销毁时，比如从成绩页触发的同步）。
 *
 * ## 为什么不用同步 XHR
 *
 * `open(..., false)` 会把主线程堵在 JS 里等网络，WKWebView 上等于整屏卡死甚至死锁。
 * 这里用异步 XHR + **轮询**：起手脚本把结果写进 `window.__juwFetch[token]`，Kotlin 侧每
 * [POLL_INTERVAL_MS] 问一次「好了没」。轮询不阻塞任何线程，超时也能干净退出。
 *
 * ## 线程
 *
 * 宿主安装进来的 [runScript] 自己负责切主队列（`WKWebView` 只能在主线程调）。本对象
 * 只在协程里 await，不假设自己跑在哪个线程。
 */
@OptIn(ExperimentalForeignApi::class)
object IosWebFetch {

    /** 轮询间隔：一次 `evaluateJavaScript` 往返通常几毫秒，120ms 足够密且不烧 CPU。 */
    private const val POLL_INTERVAL_MS = 120L

    /** 起手脚本的回执超时：连脚本都执行不了，就别等 20 秒了。 */
    private const val START_TIMEOUT_MS = 3_000L

    /** 结果分隔符（U+0001，HTML 里不会出现）。JS 侧写 `'\u0001'`，两边是同一个字符。 */
    private const val SEP = "\u0001"

    private const val WAIT = "WAIT"

    /** 令牌序号：同一页面里可能连续取两次（理论页 + 实验页），令牌不能撞。 */
    private var seq = 0

    /**
     * 由 WebView 宿主安装：跑一段 JS，把字符串结果回给回调。
     *
     * **必须**在视图销毁时置空——否则本对象会把请求发进一个已经死掉的视图，调用方只能等超时。
     */
    var runScript: ((script: String, onResult: (String?) -> Unit) -> Unit)? = null

    /** 有没有活着的 WebView 可以借用（false = 调用方该走 Cookie + Ktor 兜底）。 */
    val available: Boolean get() = runScript != null

    /**
     * 在页面里同源取 [url]，回 `(HTTP 状态码, 响应体)`；视图不在、超时、脚本报错都回 null。
     *
     * 状态码为 0 表示 XHR 被拦（跨域 / 网络层失败），调用方按失败处理。
     */
    suspend fun fetch(url: String, timeoutMs: Long = 20_000L): Pair<Int, String>? {
        if (runScript == null) return null
        seq += 1
        val token = "juw$seq"

        val started = withTimeoutOrNull(START_TIMEOUT_MS) { runJs(startScript(token, url)) }
        if (started == null) return null

        var result: Pair<Int, String>? = null
        withTimeoutOrNull(timeoutMs) {
            while (result == null) {
                delay(POLL_INTERVAL_MS)
                val raw = runJs(pollScript(token)) ?: return@withTimeoutOrNull
                if (raw == WAIT) continue
                val cut = raw.indexOf(SEP)
                if (cut <= 0) continue
                val status = raw.substring(0, cut).toIntOrNull() ?: -1
                result = status to raw.substring(cut + 1)
            }
        }
        return result
    }

    /** 跑一段 JS 等它的返回值；视图在跑之前被销毁就回 null（不会永远挂着）。 */
    private suspend fun runJs(script: String): String? = suspendCancellableCoroutine { cont ->
        val run = runScript
        if (run == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        run(script) { value -> if (cont.isActive) cont.resume(value) }
    }

    /** 起手脚本：开异步 XHR，结果写进 `window.__juwFetch[token]`。 */
    private fun startScript(token: String, url: String): String = """
(function(){
  var t='$token', u='${escapeJs(url)}';
  window.__juwFetch = window.__juwFetch || {};
  window.__juwFetch[t] = {d:false, s:0, x:''};
  try{
    var r = new XMLHttpRequest();
    r.open('GET', u, true);
    r.onreadystatechange = function(){
      if (r.readyState === 4){
        window.__juwFetch[t] = {d:true, s:r.status, x:(r.responseText || '')};
      }
    };
    r.send(null);
    return 'ok';
  }catch(e){
    window.__juwFetch[t] = {d:true, s:-1, x:String(e)};
    return 'err';
  }
})()
"""

    /** 轮询脚本：没好回 `WAIT`，好了回 `"状态码\u0001正文"` 并把暂存删掉。 */
    private fun pollScript(token: String): String = """
(function(){
  var t='$token';
  var o = window.__juwFetch && window.__juwFetch[t];
  if (!o || !o.d) return '$WAIT';
  delete window.__juwFetch[t];
  return String(o.s) + '\u0001' + o.x;
})()
"""

    /** 拼进单引号 JS 字符串：反斜杠与单引号转义，换行折成空格（URL 里不该有）。 */
    private fun escapeJs(text: String): String =
        text.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")
            .replace("\r", " ")
}
