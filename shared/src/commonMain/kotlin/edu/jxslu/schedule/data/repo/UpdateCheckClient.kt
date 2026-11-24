package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.domain.UpdateCheck
import edu.jxslu.schedule.domain.UpdateManifest
import edu.jxslu.schedule.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * 检查更新的网络执行（DESIGN §3.3）：拉仓库镜像 latest.json → 解析 → 比对版本。
 *
 * 两个触发点（2026-10-08）：关于页「检查更新」行（手动）与冷启动自动检查
 * （默认开、可关、可在弹窗上「忽略此次更新」——忽略按版本号存偏好，见
 * `DisplayPrefsStore.ignoredUpdateVersion`）。本身无状态、不落盘、没有
 * 「上次检查时刻」闸门——自动检查就是每次冷启动一次。无凭证、无日志拦截器
 * （清单是公开仓库文件，不含任何用户数据，但纪律一致）。
 *
 * 多源顺序见 [edu.jxslu.schedule.domain.RepoMirror]（gh-proxy 加速 → jsdelivr → raw
 * 兜底）：逐源尝试、首个成功即用；单源失败只记日志换下一源，全挂才返回失败。
 *
 * 2026-11-24（iOS 移植 P1）：搬进 `:shared`，三处平台依赖一并拆掉——
 * `android.util.Log` → [Logger]；OkHttp → Ktor（[SharedHttp]）；
 * `BuildConfig.VERSION_NAME` 改成构造参数（`:app` 侧调用点传进来）。
 * `Dispatchers.IO` 那层 `withContext` 去掉：Ktor 的 `get` 本就挂起不占线程。
 */
class UpdateCheckClient(private val currentVersionName: String) {

    // 每源一个总上限：正常源 ~1s 命中；被静默丢包的源最多烧满 10s 就换下一源
    // （默认 base 的 15s connect / 20s read 对「点完等着看结果」太宽，收紧到 8s/10s）
    private val http: HttpClient =
        SharedHttp.create(requestTimeoutSeconds = 10, connectTimeoutSeconds = 8)

    /**
     * 执行一次检查。逐源轮询（第一个成功即用），**任何失败都不抛**——
     * 返回 [UpdateCheckResult.Failed] 由 UI 给提示。
     */
    suspend fun check(): UpdateCheckResult {
        val latest = fetchFirstAvailableManifest()
        return when {
            latest == null -> UpdateCheckResult.Failed(UpdateCheckResult.FailReason.Network)
            !UpdateCheck.isNewer(latest.version, currentVersionName) -> UpdateCheckResult.UpToDate(latest)
            else -> UpdateCheckResult.UpdateAvailable(latest)
        }
    }

    /** 逐源拉清单；单源不可达 / 非 2xx / 脏清单都换下一源，全挂返回 null。 */
    private suspend fun fetchFirstAvailableManifest(): UpdateManifest? {
        for (url in UpdateCheck.LIST_URLS) {
            val manifest = fetchManifest(url)
            if (manifest != null) return manifest
        }
        return null
    }

    /**
     * 拉单个清单地址并过校验；网络失败记日志后返回 null（交调用方换源）。
     *
     * `catch` 兜的是 `Exception` 而不是原来的 `IOException`：Ktor 的异常层次与 OkHttp
     * 不同（超时是 `HttpRequestTimeoutException`、连接失败是引擎侧异常），只抓
     * `IOException` 会漏。清单解析仍在 `try` 之外，脏清单的异常照旧向上抛，口径没变。
     */
    private suspend fun fetchManifest(url: String): UpdateManifest? {
        val text = try {
            val response = http.get(url)
            if (!response.status.isSuccess()) return null
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "manifest source unreachable: $url", e)
            return null
        }
        return UpdateCheck.parseManifest(text)
    }

    private companion object {
        private const val TAG = "UpdateCheck"
    }
}

/** 一次检查的结论（UI 按此给提示与弹窗）。 */
sealed interface UpdateCheckResult {

    /** 已是最新（含解析到的清单，弹窗/Snackbar 可展示「当前 vX.Y.Z 已是最新」）。 */
    data class UpToDate(val latest: UpdateManifest) : UpdateCheckResult

    /** 有新版本：UI 据此弹「双通道下载」弹窗。 */
    data class UpdateAvailable(val latest: UpdateManifest) : UpdateCheckResult

    /** 检查失败。 */
    data class Failed(val reason: FailReason) : UpdateCheckResult

    /** 失败原因细分（文案由 UI 出；当前实际只有网络一类——清单源至少两个域名）。 */
    enum class FailReason {
        /** 所有清单源都不可达 / 内容不合法。 */
        Network,
    }
}
