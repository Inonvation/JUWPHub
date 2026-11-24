package edu.jxslu.schedule.data.kqcx

import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.domain.BikeFailure
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * 快趣出行「附近车辆」接口客户端（DESIGN §4.23）。一条 form POST，
 * 只做请求与失败分类；响应怎么解析交给 `domain/BikeNearby`（纯可测）。
 *
 * 红线：接口不需要鉴权，因此**没有任何凭证**可发；不代客开锁、不触碰计费与签到。
 * 请求里也只有坐标与 `deviceType`，不含账号或设备标识。
 *
 * iOS 移植 P1（2026-11-24）：搬进 `:shared`，OkHttp → Ktor。
 * **顺带删掉了一整层机制**：原实现用 `suspendCancellableCoroutine` + `okhttp3.Callback`
 * 手工桥接「可取消的请求」——那是因为 OkHttp 的阻塞 `execute()` 中断不了，
 * 拖动地图时前一批撒点还占着连接、新查询排在后面。Ktor 的请求本身就是挂起且可取消的，
 * 协程一取消即断连，所以那 30 行回调桥接连同 `invokeOnCancellation` 全部不需要了。
 */
class KqcxBikeClient private constructor(private val http: HttpClient) {

    companion object {
        const val ENDPOINT =
            "https://api.kvcoogo.com/ManagerApi/api/v1.0.0/queryNearbyCar"

        /** 车队类型：实测只有 `1`（电单车）在返回车辆。 */
        private const val DEVICE_TYPE = "1"

        /** 上游参考实现用的标识。UA 不影响鉴权，写自己包名，别冒充别人的客户端。 */
        private const val UA = "edu.jxslu.schedule"

        fun create(): KqcxBikeClient = KqcxBikeClient(
            // 原 OkHttp 侧 connect 10s / read 15s；Ktor 的 request 超时取同一个 15s 量级，
            // connect 单独给 10s。请求都很小，不需要更长。
            SharedHttp.create(requestTimeoutSeconds = 15, connectTimeoutSeconds = 10),
        )

        /**
         * 异常 → 失败类别。纯函数，可测。
         *
         * 判断顺序不能反：超时若先落进「其它」就变成「网络不可达」，用户按提示反复检查网络
         * 也修不好——这几条就是在钉这个顺序。
         *
         * 为什么不再判 `java.io.IOException`：那是 JVM 专有类型，iOS 上没有。现在超时由本类
         * 在 [queryNearbyJson] 里归一成 [KqcxTimeoutException]，非 2xx 抛 [ServiceHttpException]，
         * 其余（连接被拒、DNS 失败、引擎异常）统一落到最后一条按网络失败处理。
         */
        fun classify(error: Throwable): BikeFailure = when (error) {
            is KqcxTimeoutException -> BikeFailure.Timeout
            is ServiceHttpException -> BikeFailure.Service
            // 意料之外的异常按网络失败提示：用户至少知道"没查到"并能重试。
            // 真出这种事是代码 bug，不该悄悄吞掉（VM 侧只提示、不上报，日志里能看到堆栈）
            else -> BikeFailure.Network
        }
    }

    /**
     * 查 [lat] / [lng] 附近的车辆，返回响应原文。
     *
     * 超时抛 [KqcxTimeoutException]、非 2xx 抛 [ServiceHttpException]、其余网络异常原样抛，
     * 由调用方经 [classify] 归类；不自动重试——地图页有刷新按钮，重试交给用户。
     *
     * `// shortcut: Ktor 的 OkHttp 引擎默认「同域名 5 并发」，原实现调到 12 是为了一次拖动
     * 撒 8 个采样点被分成两批；要恢复得在 androidMain 里给引擎配 preconfigured client。
     * 等真机验证拖动撒点确实变慢再改。`
     */
    suspend fun queryNearbyJson(lat: Double, lng: Double): String {
        try {
            val response = http.post(ENDPOINT) {
                header("User-Agent", UA)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("lat=$lat&lng=$lng&deviceType=$DEVICE_TYPE")
            }
            if (!response.status.isSuccess()) throw ServiceHttpException(response.status.value)
            return response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServiceHttpException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw KqcxTimeoutException(e)
        } catch (e: ConnectTimeoutException) {
            throw KqcxTimeoutException(e)
        }
    }

    /** 服务端返回了非 2xx。 */
    class ServiceHttpException(val httpCode: Int) : Exception("附近车辆接口返回 $httpCode")

    /**
     * 请求超时。
     *
     * 以前直接靠 `java.net.SocketTimeoutException` 判别，但那是 JVM 类型；现在由
     * [queryNearbyJson] 把 Ktor 的两种超时（请求超时 / 连接超时）统一归一再抛出来。
     */
    class KqcxTimeoutException(cause: Throwable?) : Exception("附近车辆接口超时", cause)
}
