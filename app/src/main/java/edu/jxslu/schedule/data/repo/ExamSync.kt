package edu.jxslu.schedule.data.repo

import android.util.Log
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.jw.ExamScheduleParser
import edu.jxslu.schedule.data.jw.JwHtml
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.session.AutoSyncRules
import edu.jxslu.schedule.data.session.CasEnsureResult
import edu.jxslu.schedule.data.session.CasSession
import edu.jxslu.schedule.domain.ExamChangeDetector
import edu.jxslu.schedule.domain.ExamChangeText
import edu.jxslu.schedule.domain.ExamMapper.ExamEntry
import edu.jxslu.schedule.domain.InboxKind
import edu.jxslu.schedule.domain.InboxKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/**
 * 考试安排自动检查（DESIGN §4.33）：考试变动提醒的数据链。
 *
 * 走 [CasSession.fetchHtml]（已存凭证 + OkHttp），不开窗口：缺省学期以教务壳页为准（同
 * `scripts/fetch_exams.py` 的做法），再 GET 数据接口 `/jsxsd/xsks/xsksap_list`
 * 翻页抓全量，解析用 [ExamScheduleParser.parseFetchJson]——与教务 WebView 导入
 * **同一份解析口径**，只是传输层换成 OkHttp（同 `ScoreSync` 与成绩页的关系）。
 *
 * **检测出的变动会写课表**（2026-11-03 起）：检测到变动且「考试变动自动合并」开着时，
 * 把最新考试列表交给 [ScheduleRepository.mergeExamCourses] 并进当前课表（补/改考试行，
 * 不删、不动普通课程）。开关关着 = 只随 [Result.Updated] 交给提醒层发通知，
 * 点通知落考试页，用户仍可手动导入。**不再有「绝不写课程表」的红线**：用户 2026-11-03
 * 要求默认开自动合并，写库前没有确认弹窗——由开关与学期守卫兜住。
 *
 * 闸门与基线纪律：
 * - 提醒与自动合并任一开着才跑（提醒关、只开合并也要能并表）；两个都关直接 Skipped，
 *   一行网络请求都不发；
 * - 间隔 + `exam_check_millis`（失败不写时刻，下个周期自动补查）；`eager` 只由
 *   冷启动补查传入（阈值减半，见 [AutoSyncRules.shouldAttemptAt]），周期任务不传；
 * - 壳页学期缓存（[AutoSyncRules.shellTermCacheUsable]）：TTL 内复用上次抓到的学期，
 *   省一个壳页 GET；force（手动更新按钮）永远真抓；
 * - 基线为空（首跑）或学期切换 = 只存基线**不通知也不并表**，防首跑/换学期整表刷屏；
 * - 撤考忽略（[ExamChangeDetector] 的口径），宁可少打扰。
 */
class ExamSync(
    private val cas: CasSession,
    private val prefs: DisplayPrefsStore,
    private val snapshotStore: ExamSnapshotStore,
    /** 收件箱留痕（DESIGN §4.38）：检测出的变更顺手入箱，与发不发系统通知无关。 */
    private val inbox: InboxRepository,
    /** 考试变动自动合并（DESIGN §4.33）：检测到变动后写当前课表的考试行。 */
    private val repo: ScheduleRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    /** 抓取互斥（口径同 `ScoreSync`）：撞上了直接让位，不排队重抓一遍。 */
    private val mutex = Mutex()

    sealed interface Result {
        /**
         * [changes] 是检测到的变更（可能为空 = 抓到了但没变动）；[term] 是教务实际返回的学期。
         * [merged] 是本次自动合并写进当前课表的行数（开关关着 / 没变动 / 学期守卫挡下时都是 0）。
         */
        data class Updated(
            val term: String,
            val count: Int,
            val changes: List<ExamChangeDetector.Change> = emptyList(),
            val merged: Int = 0,
        ) : Result

        data object Skipped : Result

        /** 已经有一次抓取在跑，这次让位。 */
        data object InProgress : Result

        data class Failed(val reason: String) : Result
    }

    suspend fun sync(force: Boolean = false, eager: Boolean = false): Result {
        if (!mutex.tryLock()) return Result.InProgress
        try {
            return syncLocked(force, eager)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun syncLocked(force: Boolean, eager: Boolean): Result {
        val alertOn = prefs.examAlertEnabled.first()
        val autoMergeOn = prefs.examAutoMergeEnabled.first()
        val intervalHours = prefs.examAlertIntervalHours.first()
        val now = nowMillis()
        if (!force) {
            // 提醒关着、但自动合并开着时仍要检查：合并靠这趟检查发现变动
            if (!alertOn && !autoMergeOn) return Result.Skipped
            if (!AutoSyncRules.shouldAttemptAt(prefs.examAlertCheckMillis(), intervalHours, now, eager)) {
                return Result.Skipped
            }
        }

        when (val ensured = cas.ensureValid()) {
            is CasEnsureResult.Ready -> Unit
            else -> return Result.Failed(ScholarProgressSync.describe(ensured))
        }

        // 缺省学期以教务为准：优先用壳页学期缓存（TTL 内，省一次壳页 GET），
        // 过期 / 首跑 / force 才真抓壳页；抓不到不猜测
        val cachedTerm = prefs.examShellTerm()
        val term: String = if (cachedTerm != null &&
            AutoSyncRules.shellTermCacheUsable(cachedTerm, prefs.examShellTermAtMillis(), force, now)
        ) {
            cachedTerm
        } else {
            val shell = cas.fetchHtml(JwUrls.EXAM_QUERY)
                ?: return Result.Failed("没取到考试安排页面（会话可能已失效）")
            termFromShell(shell)
                ?.also { prefs.setExamShellTerm(it, nowMillis()) }
                ?: return Result.Failed("考试安排页没有学期下拉（教务结构可能变了）")
        }

        val exams = mutableListOf<ExamEntry>()
        var count = 0
        var page = 1
        do {
            if (page > 1) delay(AutoSyncRules.interRequestDelayMs())
            val body = cas.fetchHtml(listUrl(term, page))
                ?: return Result.Failed("没取到考试数据（会话可能已失效）")
            val parsed = try {
                ExamScheduleParser.parseFetchJson(body)
            } catch (e: CancellationException) {
                throw e // 协程被取消，不能吞成解析失败
            } catch (e: Exception) {
                return Result.Failed(e.message ?: "考试解析失败")
            }
            count = parsed.count
            exams += parsed.exams
            page++
        } while (exams.size < count && page <= MAX_PAGES)

        // 与上次基线比对。基线为空（首跑）或学期切换 = 只建基线不通知，防整表刷屏
        val snapshot = snapshotStore.load()
        val baselineUsable = snapshot.term == term && snapshot.exams.isNotEmpty()
        val changes = if (baselineUsable) {
            ExamChangeDetector.detect(snapshot.exams, exams)
        } else {
            emptyList()
        }

        // 写盘失败不推进检查时间：基线没更新却记下「刚查过」，下一轮会拿旧基线比对，
        // 把同一批变动重复报一遍。
        if (snapshotStore.save(ExamSnapshotStore.Snapshot(term = term, exams = exams))) {
            prefs.setExamAlertCheckMillis(nowMillis())
        }
        // 收件箱留痕（DESIGN §4.38）：手动「更新」与周期检查同源，谁先发现都入箱。
        // 去重键 = 考试身份（courseNo|date），同一场考试反复 sync 只刷新那一条；
        // 时间/考场再变 = 内容刷新成最新状态，不堆历史消息。
        if (changes.isNotEmpty()) {
            for (change in changes) {
                inbox.post(
                    kind = InboxKind.EXAM,
                    dedupeKey = InboxKeys.EXAM_PREFIX + ExamChangeDetector.keyOf(change.entry),
                    title = if (change.kind == ExamChangeDetector.Kind.NEW) "新增考试安排" else "考试安排调整",
                    body = ExamChangeText.describe(change),
                    route = SubpageScreen.EXAMS.name,
                )
            }
        }
        // 自动合并（DESIGN §4.33，2026-11-03）：只在真有变动时写课表——没变动就别碰库。
        // 放在基线落盘之后：合并失败不能连累「基线推进 + 通知」这两件主事。
        // 失败只记日志不重试；下次真变动时按整份列表重新对账，这次漏掉的会一起补上。
        val merged = if (autoMergeOn && changes.isNotEmpty()) {
            try {
                repo.mergeExamCourses(exams, term)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "auto merge exams failed", e)
                0
            }
        } else {
            0
        }
        return Result.Updated(term = term, count = exams.size, changes = changes, merged = merged)
    }

    companion object {
        private const val TAG = "ExamSync"

        /** 翻页上限，同 [ScoreSync.MAX_PAGES]：挡住 count 异常时不至于空转。 */
        const val MAX_PAGES = 20

        /**
         * 从壳页 HTML 抽学期下拉的当前选中项（`<option selected>` 的 value 或文本）。
         * 正则而不是 DOM：教务壳页结构简单，和脚本侧 BeautifulSoup 的口径一致；
         * 抽不到返回 null（调用方报失败，不猜学期）。
         */
        fun termFromShell(html: String): String? =
            JwHtml.selectedTerm(html, selectId = "xnxqid")
                ?: JwHtml.selectedTerm(html, selectId = null)

        fun listUrl(term: String, page: Int): String =
            "${JwUrls.EXAM_LIST_API}?xnxqid=${java.net.URLEncoder.encode(term, "UTF-8")}&xqlb=&pageNum=$page&pageSize=${ExamScheduleParser.PAGE_SIZE}"
    }
}
