package edu.jxslu.schedule.ui.jwvw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.jxslu.schedule.data.session.IosJwSessionFlag
import edu.jxslu.schedule.data.jw.JwAutoLogin
import edu.jxslu.schedule.data.jw.JwImportDiagnosis
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.data.session.SessionCredentials
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.platform.AppServices
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * iOS 的教务网页窗口（2026-11-29）：把 Android `JwImportScreen` 的能力按 iOS 的现实收成一份。
 *
 * ## 和 Android 那份的关系
 *
 * 同一个窗口概念：**登录 → 落到学生端 → 按模式做一件事**。Android 因为要解析六套页面（理论课表 /
 * 实验课表 / 考试 / 成绩 / 选课 / 浏览），页面里有上千行解析与重试；iOS 的抓取全在共享层
 * （`ScheduleTermImport` / `ExamSync` 走 Ktor + `JwReadSession`），网页这边只需要三件：
 *
 * | 能力 | Android | 这里 |
 * |---|---|---|
 * | 登录 | CAS 表单 + 保存的凭证自动填 | 同（`JwAutoLogin.fillJs`，凭证走 Keychain） |
 * | 手机 / 电脑版 | 顶栏开关，落盘 `jwDesktopMode` | 同（`JwWebFit` 同一份脚本，当场重注入不重载） |
 * | 落到学生端之后 | 抓页面解析入库 | 调共享层：归档课表 / 同步考试（含自动合并） |
 *
 * ## 为什么"浏览"也要留着
 *
 * 教务有些页 iOS 没有对应业务页（评教、培养方案明细之类），用户仍需要一个**能登录的浏览器**
 * 去核对与操作；考试页的「在教务网页核对」也走这里（模式 = [IosJwTask.SyncExams]，
 * 开在考试查询页，登录后自动同步一次）。
 *
 * ## 自动填表只做一次
 *
 * 页面级 `autoLoginTried` 挡同一页反复注入；进程级 [IosJwAutoLoginGate] 挡"密码改过以后
 * 每开一次窗口撞一次 CAS"——与 Android `SessionStatus.tryAcquireAutoLogin` 同一条纪律
 * （那边 5 分钟窗口，这里同值）。脚本回 `no-form` 时把闸门退回去，别的窗口仍可自动登录。
 */
internal enum class IosJwTask {

    /** 只浏览，不做任何抓取。 */
    Browse,

    /** 落到学生端后归档全部学期课表（原 `JwLoginScreen` 的行为，默认）。 */
    ImportSchedule,

    /** 落到学生端后跑一次考试同步（含「考试变动自动合并课表」）。 */
    SyncExams,
}

@Composable
internal fun IosJwBrowser(
    services: AppServices,
    startUrl: String,
    title: String,
    task: IosJwTask,
    onClose: () -> Unit,
    /** 教务凭证（Keychain）：自动填表用；null = 本机没存过密码，退回让用户手登。 */
    credentials: () -> SessionCredentials? = { null },
) {
    val controller = remember { WebViewController() }
    val scope = rememberCoroutineScope()
    val prefs = remember { services.displayPrefs() }

    var desktop by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var retries by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }

    // 电脑版开关读盘（默认开，与 Android 的 `jwDesktopMode` 初值一致）
    LaunchedEffect(Unit) {
        desktop = runCatching { prefs.jwDesktopMode.first() }.getOrDefault(true)
    }

    /** 顶栏切模式：**当场重注入，不重载页面**（与 Android 同一口径）。 */
    fun applyDesktop(mode: Boolean) {
        controller.evaluateJavascript(
            if (mode) JwWebFit.DESKTOP_JS else JwWebFit.DESKTOP_REVERT_JS,
        )
    }

    remember(controller) {
        fun retryOrReport(reason: String) {
            if (retries < MAX_AUTO_RETRY) {
                retries += 1
                error = null
                status = "$reason，正在重试（第 $retries 次）…"
                controller.loadUrl(JwImportDiagnosis.retryUrl(currentUrl) ?: startUrl)
            } else {
                status = null
                error = "$reason。点「刷新」重试"
            }
        }

        controller.onPageFinished = { url ->
            currentUrl = url
            error = null
            // 页面适配：课表页横向可滚、登录页表单收窄。桌面模式由同一个脚本参数决定。
            JwWebFit.fitScript(url, desktop)?.let { script -> controller.evaluateJavascript(script) }

            // 落在 CAS 登录页且手上有凭证：自动填一次（只一次，见类 KDoc）
            if (!IosJwAutoLoginGate.tried && JwUrls.isCasHost(url) && JwWebFit.isLoginLikeUrl(url)) {
                val cas = credentials()
                if (cas != null && IosJwAutoLoginGate.tryAcquire()) {
                    controller.evaluateJavascript(JwAutoLogin.fillJs(cas.username, cas.password)) { raw ->
                        // 只记结果，不记凭证
                        val result = raw?.trim()?.removeSurrounding("\"")
                        if (result?.startsWith(JwAutoLogin.OK_PREFIX) != true) {
                            IosJwAutoLoginGate.release()
                            status = "自动登录没走通，请在页面里手动登录一次"
                        }
                    }
                }
            }

            if (!done && isStudentSide(url)) {
                IosJwSessionFlag.markUsable()
                done = true
                when (task) {
                    IosJwTask.Browse -> status = "已登录，可以在这个窗口里核对教务页面"
                    IosJwTask.ImportSchedule -> {
                        busy = true
                        status = "已登录，正在从教务导入课表…"
                        scope.launch {
                            val result = services.scheduleTermImport().archive(manual = true) { d, t, term ->
                                status = "正在导入 $term（$d/$t）…"
                            }
                            status = runCatching { describeJwImport(result, services) }
                                .getOrElse { "导入结束（结果读取失败：${it.message ?: "未知"}）" }
                            busy = false
                        }
                    }

                    IosJwTask.SyncExams -> {
                        busy = true
                        status = "已登录，正在读取考试安排…"
                        scope.launch {
                            val sync = services.examSync()
                            val result = runCatching { sync?.sync(force = true) }
                                .getOrElse { null }
                            status = when (result) {
                                null -> "考试同步失败：教务没有响应，稍后在考试页点「更新」重试"
                                is ExamSync.Result.Updated ->
                                    "已同步 ${result.term} 共 ${result.count} 场" +
                                        if (result.merged > 0) "，已合并 ${result.merged} 场到课表" else ""

                                is ExamSync.Result.Failed -> "考试同步失败：${result.reason}"
                                else -> "这次没有需要同步的变动"
                            }
                            busy = false
                        }
                    }
                }
            } else if (task == IosJwTask.Browse || !isStudentSide(url)) {
                // 空白页判据：主文档加载完但正文一个字都没有（403 / 500 的空响应就是这样）
                controller.evaluateJavascript(BLANK_PROBE_JS) { result ->
                    if (result == "0") {
                        retryOrReport("页面是空白的")
                    } else if (result != null) {
                        retries = 0
                    }
                }
            }
        }
        controller.onError = { message -> retryOrReport("加载失败：$message") }
        controller
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("返回") }
            Text(
                // 有状态就说状态：导入中 / 同步结果比 URL 有用得多；都没有就显示窗口标题
                text = error ?: status ?: title,
                fontSize = 10.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    error != null -> MaterialTheme.colorScheme.error
                    status != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
            )
            // 手机版 / 电脑版（2026-11-29）：Android 顶栏同一只，落盘同一份偏好
            TextButton(
                onClick = {
                    desktop = !desktop
                    scope.launch { runCatching { prefs.setJwDesktopMode(desktop) } }
                    applyDesktop(desktop)
                    status = if (desktop) "已切到电脑版" else "已切到手机版"
                },
                enabled = !busy,
            ) { Text(if (desktop) "手机版" else "电脑版") }
            TextButton(onClick = { controller.reload() }, enabled = !busy) { Text("刷新") }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            WebContentView(
                controller = controller,
                url = startUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 自动重试上限：两次之后交给用户点「刷新」，真故障时不能打转。 */
private const val MAX_AUTO_RETRY = 2

/**
 * 是否已经落在**学生端**（CAS 回跳成功之后的页面）。
 *
 * 只认端口 8080 那一套部署（[JwUrls.XSD_BASE]），外加两个只有登录后才出现的学生端路径。
 * **不能用 `JwUrls.isJwHost`**：`sso.jsp` 本身就在教务域，预热页一加载就会被当成「已登录」。
 */
private fun isStudentSide(url: String?): Boolean {
    val u = url.orEmpty()
    if (!JwUrls.isJwHost(u)) return false
    return ":8080" in u || "xsMain" in u || "xskb" in u
}

/**
 * 把归档结果说成一句人话（从 `MainViewController` 的私有实现搬来，2026-11-29）。
 *
 * 为什么必须写清数字：只判「成功」会把「一个学期都没落地」也报成完成——用户看到的就是
 * 「说成功了但课表是空的」，比报错更糟。三种结局分开说，并把库里真实条数、开学日、
 * 当前周次与第一门课的例子一起打出来。
 */
internal suspend fun describeJwImport(
    result: ScheduleTermImport.Result,
    services: AppServices,
): String = when (result) {
    is ScheduleTermImport.Result.Failed -> "导入失败：${result.reason}"

    ScheduleTermImport.Result.InProgress -> "上一次导入还在进行，等它跑完再看"

    is ScheduleTermImport.Result.Archived -> {
        if (result.createdTerms.isEmpty() && result.failed.isNotEmpty()) {
            "导入失败：${result.failed.first().second}" +
                "（教务当前学期 ${result.currentTerm ?: "未识别"} · 跳过 ${result.skipped}）"
        } else {
            val repo = services.scheduleRepository()
            val courses = repo.courses.first()
            val semester = repo.semester.first()
            val week = ScheduleCalculator.todayWeekOf(semester, AppClock.today()) ?: 0
            val sample = courses.firstOrNull()?.let { c ->
                "${c.name.take(10)} ${weekdayNameOf(c.day)} ${c.startSection}-${c.endSection}节 " +
                    ScheduleCalculator.formatWeeks(c.weeks)
            }
            buildString {
                if (result.createdTerms.isEmpty()) {
                    append("导入完成：${result.skipped} 个学期库里已有，本次没有新建")
                } else {
                    append("导入完成：库里 ${courses.size} 门")
                    append(" · 新建 ${result.createdTerms.joinToString("、")}")
                }
                append(" · 开学 ${semester?.startDate ?: "—"} · 现第 $week 周")
                append(" · 教务 ${result.currentTerm ?: "未识别"}")
                if (result.failed.isNotEmpty()) append(" · ${result.failed.size} 个学期失败")
                if (sample != null) append("\n示例：$sample")
            }
        }
    }
}

/** 1..7 → 周一..周日（只用于上面那句示例，越界回「—」）。 */
private fun weekdayNameOf(day: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrNull(day - 1) ?: "—"

/** 空白页探针：返回正文字符数（去掉空白），`"0"` = 空白页。 */
private const val BLANK_PROBE_JS = """
(function(){
  try{
    var t = (document.body && document.body.innerText) ? document.body.innerText : '';
    return String(t.replace(/\s/g, '').length);
  }catch(e){ return 'err'; }
})()
"""

/**
 * 进程级自动登录闸门：5 分钟窗口（与 Android `SessionStatus.tryAcquireAutoLogin` 同值）。
 *
 * 为什么需要：页面级标记挡不住"关掉窗口再开一个"——密码改过而 Keychain 还是旧值时，
 * 每开一次都会撞一次 CAS，连续失败会触发账号锁定。
 */
private object IosJwAutoLoginGate {

    private const val WINDOW_MILLIS = 5 * 60 * 1000L

    private var lastAttemptMillis: Long = 0L

    var tried: Boolean = false
        private set

    fun tryAcquire(nowMillis: Long = nowMillis()): Boolean {
        if (nowMillis - lastAttemptMillis < WINDOW_MILLIS) return false
        lastAttemptMillis = nowMillis
        tried = true
        return true
    }

    /** 脚本没找到表单（页面还在渲染 / 不是真登录页）：退回闸门，别的窗口仍可试。 */
    fun release() {
        lastAttemptMillis = 0L
        tried = false
    }

    private fun nowMillis(): Long = edu.jxslu.schedule.domain.AppClock.nowMillis()
}
