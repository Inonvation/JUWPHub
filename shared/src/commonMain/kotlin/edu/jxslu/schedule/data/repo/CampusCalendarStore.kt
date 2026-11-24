package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.data.local.JsonFileStore
import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.CampusCalendarIndex
import edu.jxslu.schedule.domain.CampusCalendarRules
import edu.jxslu.schedule.domain.CampusCalendarSchoolRules
import edu.jxslu.schedule.platform.Logger
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.concurrent.Volatile

/**
 * 校历的落盘缓存与远程刷新（DESIGN §4.34）。
 *
 * 数据源（2026-10-07 起主次换位）= ① **教务处官网**（`jw.juwp.edu.cn` 通知公告，
 * 免登录直取高清海报原图；规则在 [CampusCalendarSchoolRules]）；② 仓库镜像
 * `docs-public/calendar/`（多源，已停日常维护，仅主源不可达时兜底；规则在
 * [CampusCalendarRules]）。刷新 = 找最新「学年校历」→ 比对本地学年 + 文件名 →
 * 变了才下载图，**原子落盘**（临时文件写完改名，中断不留半张图）。
 *
 * 为什么不用 Room/Not：就两样东西（一张图 + 一个学年标签），一份小 JSON + 一个目录
 * 足够；StateFlow 只为「后台刷完页内立即换图」（同 `QzxyWateringStore` 的理由）。
 *
 * 失败静默（与 ScoreSync 同纪律）：校历是锦上添花的信息，拉不到就继续用本地缓存，
 * 连内置兜底都没有才是问题——兜底图在 `res/drawable-nodpi/ic_campus_calendar.webp`，
 * 由 UI 层直接兜，本类不感知。无任何凭证与用户数据，镜像 URL 是公开仓库。
 *
 * ## iOS 移植 W2（2026-11-25）换掉的三样东西
 *
 * | 搬移前 | 现在 | 为什么 |
 * |---|---|---|
 * | `Context` + `SharedPreferences`（学年 / 文件名 / 上次成功时刻） | [JsonFileStore] 一份小 JSON（`appDataDir()` 下，文件名 [STATE_FILE]） | `commonMain` 拿不到 `SharedPreferences`；`JsonFileStore` 的落点与 `AppCacheCleaner` 认的 `filesDir` 是同一个 |
 * | `java.io.File` | okio 的 [FileSystem] + [Path]，目录仍是 `appDataDir()/campus_calendar` | 与 `JsonFileStore` 同一介质，`AppCacheCleaner` 的 `CALENDAR_DIR` 不用改 |
 * | OkHttp（`AppHttp.base` 派生） | Ktor（[SharedHttp.create]，超时口径逐项照抄） | OkHttp 不是 KMP 库 |
 * | `BitmapFactory.inJustDecodeBounds` 内容探测 | 容器魔数探测（[looksLikeImage]） | 见 [tryDownloadImage] 的注释 |
 *
 * **唯一的用户可见差异**：老设备上「学年 + 文件名」存在旧的 `SharedPreferences` 里，
 * 换成 JSON 后读不到 → 首次刷新会重下一张图（期间显示内置兜底图）。图文件本身在同一路径，
 * 不重复占用空间（旧的被 [applyNewImage] 的清理逻辑按新名字对不上而留下一个残留文件，
 * 「清除缓存」会一并清掉）。
 */
class CampusCalendarStore(
    // 镜像图 1.2MB，慢网读得更久（30s）；连接超时收紧——多源轮询下「死源」最多烧 8s
    // 连接就换下一源，不让冷启动后台刷新卡在死源上。与搬移前
    // `AppHttp.base.newBuilder()` 的两项一一对应。
    private val http: HttpClient = SharedHttp.create(
        requestTimeoutSeconds = 60,
        connectTimeoutSeconds = 8,
        socketTimeoutSeconds = 30,
    ),
    /** 落盘根目录，缺省 [appDataDir]；**只有单测会传**（临时目录）。 */
    dir: String = appDataDir(),
) {

    private val fs: FileSystem = platformFileSystem()

    private val imageDir: Path = dir.toPath() / DIR_NAME

    private val stateStore = JsonFileStore(STATE_FILE, Stored.serializer(), tag = TAG, dir = dir)

    /** 学年 / 文件名 / 上次成功时刻的真值（内存），磁盘只是镜像。 */
    @Volatile
    private var stored: Stored = stateStore.readBlocking() ?: Stored()

    /** 一次刷新一把锁：冷启动与页内手动撞上时后者让位（语义同 ScoreSync）。 */
    private val mutex = Mutex()

    private val _state = MutableStateFlow(stateOf(stored))

    /** 当前校历状态（学年 + 本地图片文件；图未下载过时 file 为 null，UI 走内置兜底）。 */
    val state: StateFlow<CampusCalendarState> = _state.asStateFlow()

    /**
     * 刷新：[force] = 页内手动按钮，绕过 7 天闸门；否则闸门未到直接空跑返回。
     * 每一步失败都静默返回（不打断调用方；调用方也不该为此弹任何提示——
     * 后台刷新成功与否用户无感，页内手动失败由 UI 层按返回值给行内提示）。
     */
    suspend fun refresh(force: Boolean = false): RefreshResult = mutex.withLock {
        val now = AppClock.nowMillis()
        if (!force && !CampusCalendarRules.shouldRefresh(stored.lastSuccess, now)) {
            return RefreshResult.Skipped
        }
        return withContext(Dispatchers.Default) {
            runCatching {
                // 1) 主源：教务处官网（免登录直取，2026-10-07 起）
                val school = refreshFromSchool(now)
                if (school == RefreshResult.Updated || school == RefreshResult.Unchanged) {
                    return@runCatching school
                }
                // 2) 兜底：仓库镜像（多源；已停日常维护，仅应急/兼容）
                val index = fetchIndex() ?: return@runCatching RefreshResult.Failed
                val current = _state.value
                if (CampusCalendarRules.matchesLocal(current.year, current.localFile, index) &&
                    exists(CampusCalendarRules.localFileName(index))
                ) {
                    markSuccess(now)
                    return@runCatching RefreshResult.Unchanged
                }
                val name = downloadImage(
                    CampusCalendarRules.imageUrls(index),
                    CampusCalendarRules.localFileName(index),
                ) ?: return@runCatching RefreshResult.Failed
                applyNewImage(index.year, name, now)
                RefreshResult.Updated
            }.getOrElse {
                Logger.w(TAG, "calendar refresh failed", it)
                RefreshResult.Failed
            }
        }
    }

    /**
     * 主源：教务处官网。扫通知公告（页序 = 时间倒序，第一处命中页即含最新一条）→ 取
     * 学年最大的「学年校历」→ 学年/文件名都对上且图在 = Unchanged，否则下载官网高清原图。
     * 任何一步失败返回 [RefreshResult.Failed]（由 [refresh] 落镜像兜底）。
     */
    private suspend fun refreshFromSchool(now: Long): RefreshResult {
        val hits = mutableListOf<Pair<String, String>>()
        for (pageUrl in CampusCalendarSchoolRules.listUrls()) {
            val html = fetchText(pageUrl) ?: continue
            val found = CampusCalendarSchoolRules.parseArticleLinks(html, pageUrl)
            if (found.isNotEmpty()) {
                hits += found
                break
            }
        }
        val (articleUrl, year) = CampusCalendarSchoolRules.latest(hits) ?: return RefreshResult.Failed
        val articleHtml = fetchText(articleUrl) ?: return RefreshResult.Failed
        val imageUrl = CampusCalendarSchoolRules.parseImageUrl(articleHtml, articleUrl)
            ?: return RefreshResult.Failed
        Logger.i(TAG, "school source: $year $articleUrl")

        val name = CampusCalendarSchoolRules.localFileName(year, imageUrl)
        val current = _state.value
        val indexLike = CampusCalendarIndex(year = year, file = name)
        if (CampusCalendarRules.matchesLocal(current.year, current.localFile, indexLike) && exists(name)) {
            markSuccess(now)
            return RefreshResult.Unchanged
        }
        val file = downloadImage(listOf(imageUrl), name) ?: return RefreshResult.Failed
        applyNewImage(year, file, now)
        return RefreshResult.Updated
    }

    /** 拉一个页面文本；失败（网络 / 非 2xx）记日志返回 null。官网页无 charset 头，按 UTF-8 解。 */
    private suspend fun fetchText(url: String): String? = try {
        val response = http.get(url)
        if (!response.status.isSuccess()) null else response.bodyAsText()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.w(TAG, "source unreachable: $url", e)
        null
    }

    /** 落好图后统一收口：换状态、记时刻、清旧图（只保留当前一张，同背景图口径）。 */
    private fun applyNewImage(year: String, fileName: String, now: Long) {
        val previous = _state.value.localFile
        _state.value = CampusCalendarState(year = year, localFile = fileName)
        stored = Stored(year = year, image = fileName, lastSuccess = now)
        stateStore.writeBlocking(stored)
        if (previous != null && previous != fileName) {
            runCatching { fs.delete(imageDir / previous, mustExist = false) }
        }
    }

    /** 只是「学年没变」也记一次成功时刻，否则 7 天闸门永远停在旧值上、每次进页都打官网。 */
    private fun markSuccess(now: Long) {
        stored = stored.copy(lastSuccess = now)
        stateStore.writeBlocking(stored)
    }

    /**
     * 本地图片文件的**绝对路径**；文件名只能来自本类自己写下的状态（外来的名字不认）。
     *
     * 返回 `String` 而不是 `File`：共享层没有 `java.io.File`，解码由宿主按路径自己做
     * （Android 走 `decodeFileSampled`，iOS 待接）。
     */
    fun fileFor(name: String?): String? {
        if (name.isNullOrBlank()) return null
        if (!name.startsWith("calendar_")) return null
        val path = imageDir / name
        return if (fs.exists(path)) path.toString() else null
    }

    private fun exists(name: String): Boolean = fileFor(name) != null

    /** 拉 index.json 并过校验；逐源尝试，网络失败 / 脏清单都换下一源，全挂返回 null。 */
    private suspend fun fetchIndex(): CampusCalendarIndex? {
        for (url in CampusCalendarRules.indexUrls()) {
            val text = try {
                val response = http.get(url)
                if (!response.status.isSuccess()) null else response.bodyAsText()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.w(TAG, "index source unreachable: $url", e)
                null
            }
            val index = text?.let { CampusCalendarRules.parseIndex(it) }
            if (index != null) return index
        }
        return null
    }

    /** 逐源下载校历图，临时文件写完原子改名；内容探测保它真是张图。返回落好的文件名。 */
    private suspend fun downloadImage(urls: List<String>, targetName: String): String? {
        for (url in urls) {
            val name = tryDownloadImage(url, targetName)
            if (name != null) return name
        }
        return null
    }

    /**
     * 单源下载：失败（网络 / 非 2xx / 内容探测不过 / 落盘失败）清掉临时文件返回 null。
     *
     * **内容探测换成容器魔数**（[looksLikeImage]），不再用 `BitmapFactory.inJustDecodeBounds`：
     * 后者的目的是「别把一张 HTML 错误页当成校历图存下来」，而解码探测为了量尺寸要把整张
     * 1.2MB 海报解一遍，`commonMain` 也没有这个 API。魔数判据更严（HTML / JSON 一定不过），
     * 代价是不再验证「像素能不能解出来」——那件事本来就由页面的解码兜底（解不出 → 内置图）。
     */
    private suspend fun tryDownloadImage(url: String, targetName: String): String? {
        val bytes = try {
            val response = http.get(url)
            if (!response.status.isSuccess()) null else response.readBytes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.w(TAG, "image source unreachable: $url", e)
            null
        }
        if (bytes == null || !looksLikeImage(bytes)) return null

        val tmp = imageDir / "$targetName$TMP_SUFFIX"
        val target = imageDir / targetName
        val ok = runCatching {
            fs.createDirectories(imageDir)
            fs.write(tmp) { write(bytes) }
            fs.atomicMove(tmp, target)
        }.isSuccess
        if (!ok) {
            runCatching { fs.delete(tmp, mustExist = false) }
            return null
        }
        return targetName
    }

    /**
     * 字节是不是一张常见的位图（JPEG / PNG / GIF / WebP）。
     *
     * 只认容器头四个字节，不读像素：这里唯一的用途是「别把错误页当图落盘」，
     * 而校历图只有这四种格式（官网给 JPEG/PNG，仓库镜像是 WebP/JPEG）。
     */
    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        fun at(offset: Int, magic: String): Boolean =
            magic.indices.all { bytes[offset + it] == magic[it].code.toByte() }
        return (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) || // JPEG
            (bytes[0] == 0x89.toByte() && at(1, "PNG")) || // PNG
            at(0, "GIF8") || // GIF87a / GIF89a
            (at(0, "RIFF") && at(8, "WEBP")) // WebP
    }

    private fun stateOf(raw: Stored): CampusCalendarState {
        if (raw.year.isBlank() || raw.image.isBlank()) return CampusCalendarState()
        // 文件被系统清掉（存储紧张）时标签作废，回内置兜底
        if (fileFor(raw.image) == null) return CampusCalendarState()
        return CampusCalendarState(year = raw.year, localFile = raw.image)
    }

    /** 页面展示用的状态。[year] 空 = 本地没有任何已下载的校历（UI 走内置兜底图）。 */
    data class CampusCalendarState(
        val year: String = "",
        val localFile: String? = null,
    )

    /** 刷新结果（UI 层手动刷新时给行内提示用；后台静默刷新不看它）。 */
    enum class RefreshResult {
        /** 拉到新学年并已落盘。 */
        Updated,

        /** 学年没变（或已是最新），没下载。 */
        Unchanged,

        /** 闸门未到，零网络空跑。 */
        Skipped,

        /** 网络失败或镜像内容坏了。 */
        Failed,
    }

    /**
     * 落盘的状态（学年 / 图片文件名 / 上次成功时刻）。
     *
     * 三者写在一份 JSON 里：搬移前它们就是同一个 `SharedPreferences` 文件里的三个键，
     * 分开存只会多出「学年更新了、文件名没更新」的中间态。
     */
    @Serializable
    private data class Stored(
        val year: String = "",
        val image: String = "",
        val lastSuccess: Long = 0L,
    )

    private companion object {
        private const val TAG = "CampusCalendar"

        /** 状态文件（`appDataDir()` 下）。搬移前是 `SharedPreferences("campus_calendar")`。 */
        private const val STATE_FILE = "campus_calendar_state.json"

        /**
         * 图片目录名。**不能改**：`AppCacheCleaner.CALENDAR_DIR` 按这个名字统计 / 清理，
         * 改名等于用户那边多出一份谁也管不到的残留（与 `InternshipSnapshotStore` 同一条纪律）。
         */
        private const val DIR_NAME = "campus_calendar"

        private const val TMP_SUFFIX = ".tmp"
    }
}
