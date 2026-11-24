package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.jw.JwHtml
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.jw.OneClickImport
import edu.jxslu.schedule.data.jw.OneClickResult
import edu.jxslu.schedule.data.jw.ImportSource
import edu.jxslu.schedule.data.jw.ImportParseResult
import edu.jxslu.schedule.data.jw.QiangzhiScheduleParser
import edu.jxslu.schedule.data.jw.SyjxScheduleParser
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.session.CasEnsureResult
import edu.jxslu.schedule.data.session.CasFetchResult
import edu.jxslu.schedule.data.session.CasSession
import edu.jxslu.schedule.domain.AutoSyncRules
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.domain.InboxKind
import edu.jxslu.schedule.domain.InboxKeys
import edu.jxslu.schedule.domain.ScheduleChangeRow
import edu.jxslu.schedule.domain.ScheduleCourseRow
import edu.jxslu.schedule.domain.ScheduleDiff
import edu.jxslu.schedule.domain.SchedulePendingChanges
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

/**
 * 课表直取与变动检测（DESIGN §4.17，2026-10-04 重立）。
 *
 * 走 [CasSession.fetchHtml]（已存凭证 + OkHttp）依次抓学期理论课表与实验课表两页，
 * 解析用 [QiangzhiScheduleParser.parseFromHtml] / [SyjxScheduleParser.parseFromHtml]——
 * 与教务 WebView 导入**同一套判定算法**（当年为单测写的备用路径），只是传输层换成
 * OkHttp（同 `ScoreSync` 与成绩页的关系）。合成口径直接复用 [OneClickImport.combine]，
 * 产物与 WebView 一键导入同型（`OneClickResult`）。
 *
 * 两个入口、两种用途：
 * - [fetch]：纯读。理论页学期以页面学期下拉为准；请求带了学期时校验
 *   「请求学期 = 返回学期」，不一致直接报错（同 `scripts/fetch_courses.py` 纪律——
 *   拿错学期的数据当基线会刷一屏假差异）；
 * - [check]：变动检测的差异段。抓最新教务课表与基线比对，差异落「待处置缓存」并交给
 *   提醒层发通知。基线**不在检查后更新**（这点与 `ExamSync` 相反）：课表基线的
 *   语义是「上次与用户达成一致的状态」，只有合并成功 / 重新导入才推进；
 *   重复打扰由「已阅指纹 + 待处置指纹」双闸挡住。
 *
 * 首启与历史学期的整批导入走 `ScheduleTermImport`（2026-11-19 起），它复用 [fetch]，
 * 不再有「只为当前课表空表导一次」的第二条写库路径。
 *
 * 闸门纪律同成绩/考试：开关关着不发请求、间隔内不抓、失败静默且不落检查时刻、
 * 失败不重试（防撞风控）；两页之间 [AutoSyncRules.interRequestDelayMs] 随机间隔。
 */
class ScheduleSync(
    private val cas: CasSession,
    private val repo: ScheduleRepository,
    private val prefs: DisplayPrefsStore,
    /** 收件箱留痕（DESIGN §4.38）：新差异落待处置缓存的同时入箱。 */
    private val inbox: InboxRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    /**
     * 抓取失败的**性质**（2026-11-19）。归档与新学期探测要区分「教务还没开放这一学期」
     * 与「真的出错了」：前者是正常空态（静默），后者要记日志等下一轮。字符串比对
     * 会把「页面结构变了」和「学期没开放」混在一起。
     */
    enum class FailureKind {
        /** 取页失败 / 网络 / 会话被顶后仍失败。 */
        Network,

        /**
         * 请求的学期在教务里还没有：理论页的下拉存在但没有选中项（实测请求
         * `2019-2020-1` 这种下拉外的学期返回完整网格但零选中），或返回学期与请求不一致。
         */
        TermNotOpen,

        /** 页面骨架认不出（缺学期下拉 / 缺课表网格）：结构可能变了。 */
        PageUnreliable,
    }

    sealed interface FetchResult {
        /**
         * [result] 与 WebView 一键导入同型；[theoryReliable] / [labReliable] 是两页的
         * 结构可信度（页面骨架在不在）。导入只要求理论页可靠（实验 0 条不是失败），
         * 调课检查要求两页都可靠——实验页结构变了却当「没课」，会把整批实验课报成
         * 「教务删除」。
         */
        data class Ok(
            val result: OneClickResult,
            val term: String,
            val theoryReliable: Boolean,
            val labReliable: Boolean,
        ) : FetchResult

        data class Failed(
            val reason: String,
            val kind: FailureKind = FailureKind.Network,
        ) : FetchResult
    }

    sealed interface CheckResult {
        /**
         * [changes] 是需要用户处置的差异（已滤掉「已阅」与「已提醒过未处置」的重复），
         * 为空 = 没动静或只是重复检查。[repeated] = 差异与上次提醒的相同（未处置/已忽略），
         * 提醒层据此不再发通知。
         */
        data class Ok(
            val term: String,
            val changes: List<ScheduleDiff.Change>,
            val repeated: Boolean,
        ) : CheckResult

        data object Skipped : CheckResult

        data class Failed(val reason: String) : CheckResult
    }

    /**
     * 直取两张课表页并解析。[term] 非空时带 `xnxq01id` 请求并校验返回学期；
     * 空 = 教务默认学期（返回页面学期下拉的选中项）。
     */
    suspend fun fetch(term: String? = null): FetchResult {
        when (val ensured = cas.ensureValid()) {
            is CasEnsureResult.Ready -> Unit
            else -> return FetchResult.Failed(ScholarProgressSync.describe(ensured))
        }

        val safeTerm = term?.takeIf { JwUrls.TERM_PATTERN.matches(it) }
        val theoryUrl = if (safeTerm != null) "${JwUrls.SCHEDULE_LIST}&xnxq01id=$safeTerm" else JwUrls.SCHEDULE_LIST
        val theoryHtml = when (val fetched = cas.fetchHtml(theoryUrl)) {
            is CasFetchResult.Ok -> fetched.body
            is CasFetchResult.Failed -> return FetchResult.Failed("没取到理论课表页，${fetched.reason}")
        }
        // 有下拉但没选中项 = 教务没开放这个学期（实测请求下拉外的学期返回完整网格但零选中），
        // 这是探测链的正常空态，不能报成「结构变了」（2026-11-19 实测）
        val pageTerm = termFromHtml(theoryHtml) ?: return FetchResult.Failed(
            reason = "课表页没有选中学期（教务尚未开放该学期）",
            kind = if (JwHtml.hasTermSelect(theoryHtml, TERM_SELECT)) FailureKind.TermNotOpen
            else FailureKind.PageUnreliable,
        )
        if (safeTerm != null && pageTerm != safeTerm) {
            return FetchResult.Failed(
                reason = "教务返回学期（$pageTerm）与请求学期（$safeTerm）不一致",
                kind = FailureKind.TermNotOpen,
            )
        }

        val theoryParsed = try {
            QiangzhiScheduleParser.parseFromHtml(theoryHtml)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FetchResult.Failed("理论课表解析失败：${e.message}", FailureKind.PageUnreliable)
        }
        val theoryReliable = theoryParsed is ImportParseResult.Success || "kbDataTd" in theoryHtml
        if (!theoryReliable) {
            return FetchResult.Failed("理论课表页未识别到课表网格（页面结构可能已变化）", FailureKind.PageUnreliable)
        }

        delay(AutoSyncRules.interRequestDelayMs())

        val labHtml = when (val fetched = cas.fetchHtml(JwUrls.labScheduleUrl(pageTerm))) {
            is CasFetchResult.Ok -> fetched.body
            is CasFetchResult.Failed -> return FetchResult.Failed("没取到实验课表页，${fetched.reason}")
        }
        // 实验页同样带学期下拉，必须与理论页一致（2026-10-07 复核补）：教务若忽略
        // 实验页的学期参数、按自己的默认学期渲染，直取会把两个学期的课混在一起比对。
        labTermError(pageTerm, labHtml)?.let {
            return FetchResult.Failed(
                reason = it,
                kind = if (JwHtml.hasTermSelect(labHtml, TERM_SELECT)) FailureKind.TermNotOpen
                else FailureKind.PageUnreliable,
            )
        }
        val labParsed = try {
            SyjxScheduleParser.parseFromHtml(labHtml)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FetchResult.Failed("实验课表解析失败：${e.message}", FailureKind.PageUnreliable)
        }
        val labReliable = labParsed is ImportParseResult.Success || LAB_TABLE_MARK in labHtml

        val combined = OneClickImport.combine(
            ImportSource(
                label = OneClickImport.THEORY_LABEL,
                courses = (theoryParsed as? ImportParseResult.Success)?.courses.orEmpty(),
                term = pageTerm,
                recognized = true,
            ),
            ImportSource(
                label = OneClickImport.LAB_LABEL,
                courses = (labParsed as? ImportParseResult.Success)?.courses.orEmpty(),
                term = pageTerm,
                recognized = labReliable,
            ),
        )
        return FetchResult.Ok(result = combined, term = pageTerm, theoryReliable = true, labReliable = labReliable)
    }

    /**
     * 变动检测：抓最新教务课表与基线比对。差异非空时落「待处置缓存」并随
     * [CheckResult.Ok] 交提醒层；指纹与已阅记录或待处置缓存相同的差异不重复上报。
     */
    suspend fun check(force: Boolean = false): CheckResult {
        if (!force) {
            if (!prefs.scheduleAlertEnabled.first()) return CheckResult.Skipped
            if (!AutoSyncRules.shouldAttemptAt(
                    prefs.scheduleCheckMillis(),
                    prefs.scheduleAlertIntervalHours.first(),
                    nowMillis(),
                )
            ) {
                return CheckResult.Skipped
            }
        }

        // 检测目标 = **term 最大的那张课表**，不是「当前课表」（2026-11-19 改）：
        // 新学期课表由探测自动建出来之后，后续教务继续排课 / 改课要继续以差异报出来；
        // 而用户此刻可能正翻看历史学期的课表。基线是单快照，也只跟着这张表走。
        val timetable = repo.latestTermTimetable() ?: return CheckResult.Skipped
        val ttId = timetable.id
        // 学期是唯一钥匙（抓取 URL 与基线对齐都靠它）：旧课表没有学期号就不检查，不猜
        val term = timetable.term?.takeIf { JwUrls.TERM_PATTERN.matches(it) }
            ?: return CheckResult.Skipped

        val fetchResult = fetch(term)
        val ok = fetchResult as? FetchResult.Ok ?: return CheckResult.Failed((fetchResult as FetchResult.Failed).reason)
        if (!ok.labReliable) {
            // 实验页结构不可信：硬着头皮比对会把整批实验课报成「教务删除」
            return CheckResult.Failed("实验课表页未识别到课表（页面结构可能已变化）")
        }
        if (ok.result.blocked != null) {
            // 理论页识别成功但 0 条 + 实验 0 条：可能真没课（假期），也可能数据异常。
            // 不动基线也不报差异，等下个周期再看
            return CheckResult.Ok(term = term, changes = emptyList(), repeated = false)
        }

        val baseline = repo.loadScheduleBaseline()
        if (baseline.timetableId != ttId || baseline.term != term) {
            // 首跑 / 换课表 / 换学期：只建基线不通知，防整表刷屏（同 ExamSync 闸门）
            repo.saveScheduleBaseline(ttId, term, ok.result.courses)
            prefs.setScheduleCheckMillis(nowMillis())
            return CheckResult.Ok(term = term, changes = emptyList(), repeated = false)
        }
        // 整类课解析为 0 而基线里有该类课 = 解析器与教务页面脱节的可疑信号，
        // 硬比对会把整类课报成「教务删除」——这轮放弃，不落检查时刻（下个周期自动重试）
        vanishingKindError(ok.result.courses, baseline.courses)?.let { return CheckResult.Failed(it) }
        val changes = ScheduleDiff.detect(baseline.courses.map { it.toCourse() }, ok.result.courses)
        prefs.setScheduleCheckMillis(nowMillis())

        if (changes.isEmpty()) {
            // 无差异 = 待处置缓存里那批差异已不存在（教务改回 / 基线刚被重新导入推进过）。
            // 缓存是派生数据，留着只会让处置页一直挂着已经不存在的变动，而「合并」会把
            // 它们照指纹套到当前课表上（静默删课）——2026-10-07 用户报障的「重新导入后
            // 仍显示有差异 / 与教务对不上」有一半是这里。只清属于本次检查课表的缓存。
            if (prefs.schedulePendingChanges()?.timetableId == ttId) {
                prefs.setSchedulePendingChanges(null)
            }
            return CheckResult.Ok(term = term, changes = emptyList(), repeated = false)
        }
        val fingerprint = ScheduleDiff.fingerprintOf(changes)
        if (fingerprint == prefs.scheduleIgnoredFingerprint() ||
            fingerprint == prefs.schedulePendingChanges()?.fingerprint
        ) {
            // 已忽略 / 已提醒过还没处置：不再重复打扰，待处置缓存原样保留
            return CheckResult.Ok(term = term, changes = emptyList(), repeated = true)
        }
        prefs.setSchedulePendingChanges(
            SchedulePendingChanges(
                timetableId = ttId,
                term = term,
                changes = changes.map { ScheduleChangeRow.of(it) },
                fingerprint = fingerprint,
                savedAt = nowMillis(),
            ),
        )
        // 收件箱留痕（DESIGN §4.38）：去重键 = 差异指纹，与双闸同源——同一批差异
        // 重投只会刷新那一条；合并/忽略后处置页会把这类消息标为已读。
        inbox.post(
            kind = InboxKind.SCHEDULE_CHANGE,
            dedupeKey = InboxKeys.SCHEDULE_PREFIX + fingerprint,
            title = "课程安排有变动",
            body = "教务课表检测到 ${ScheduleDiff.group(changes).size} 处差异，逐项确认后合并或忽略。",
            route = SubpageScreen.SCHEDULE_CHANGES.name,
        )
        return CheckResult.Ok(term = term, changes = changes, repeated = false)
    }

    companion object {
        /**
         * 实验课表页的骨架标记（与 `SyjxScheduleParser.EXTRACT_JS` 的 container 判据同源）：
         * 页面渲染了周次表骨架 = 「这张表在」，只是本学期没有课块。
         */
        private const val LAB_TABLE_MARK = "qz-weeklyTable"

        /** 课表页学期下拉的 id（理论与实验页同一个）。 */
        internal const val TERM_SELECT = "xnxq01id"

        /**
         * 从理论课表页 HTML 抽 `select#xnxq01id` 的当前选中项。
         * 先定位到该 select 块再找 selected option——页面可能有多个下拉，
         * 整页搜 `<option selected>` 会串（同 [ExamSync.termFromShell] 的教训）。
         * value 与文本兜底各一次，抽不到返回 null（调用方报失败，不猜学期）。
         */
        internal fun termFromHtml(html: String): String? =
            JwHtml.selectedTerm(html, selectId = TERM_SELECT)

        /**
         * 实验课表的学期校验（2026-10-07 复核补）：实验页也带 `select#xnxq01id`
         * （实测 56 个选项、选中项即服务端渲染的学期），必须与理论页一致——教务若忽略
         * 学期参数、按自己的默认学期渲染实验页，直取就会把两个学期的课混为一批。
         * 抽不到下拉同样视为不可信（结构变了就别猜）。返回 null = 校验通过。
         */
        internal fun labTermError(theoryTerm: String, labHtml: String): String? {
            val labTerm = termFromHtml(labHtml)
                ?: return "实验课表页没有学期下拉（教务结构可能已变化）"
            return if (labTerm == theoryTerm) null
            else "实验课表页返回学期（$labTerm）与理论课表页（$theoryTerm）不一致"
        }

        /**
         * 「整类课解析为 0」的可疑判定（2026-10-07 复核补）：教务页骨架都在（否则
         * fetch 已经失败），但理论 / 实验有一整类课解析为 0 条，而**基线里该类课有货**
         * ——大概率是解析器与页面脱节（改版 / 字段改名）或教务整表异常，硬比对会把
         * 整类课报成「教务删除」，勾选合并就是批量删课。返回人话描述（给提醒层与
         * logcat），正常时 null。
         *
         * 只守「有 → 零」这一侧：基线没有、教务新有的课（0 → N）是正常新增，照报。
         */
        internal fun vanishingKindError(
            remote: List<Course>,
            baselineRows: List<ScheduleCourseRow>,
        ): String? {
            val baseTheory = baselineRows.count { it.kind == CourseKind.Theory.name }
            val baseLab = baselineRows.count { it.kind == CourseKind.Lab.name }
            val remoteTheory = remote.count { it.kind == CourseKind.Theory }
            val remoteLab = remote.count { it.kind == CourseKind.Lab }
            val gone = buildList {
                if (baseTheory > 0 && remoteTheory == 0) add("理论课 ${baseTheory}→0")
                if (baseLab > 0 && remoteLab == 0) add("实验课 ${baseLab}→0")
            }
            if (gone.isEmpty()) return null
            return "教务课表解析异常（${gone.joinToString("、")}），本轮放弃比对，下个周期自动重试"
        }
    }
}
