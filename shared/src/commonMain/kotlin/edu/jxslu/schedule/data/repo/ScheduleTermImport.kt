package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.DefaultData
import edu.jxslu.schedule.data.jw.JwHtml
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.session.CasFetchResult
import edu.jxslu.schedule.data.session.JwReadSession
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.AutoSyncRules
import edu.jxslu.schedule.domain.ExamMapper
import edu.jxslu.schedule.domain.InboxKind
import edu.jxslu.schedule.domain.InboxKeys
import edu.jxslu.schedule.domain.SemesterConfig
import edu.jxslu.schedule.domain.TermNaming
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 学期归档与新学期探测（DESIGN §4.17，2026-11-19）。
 *
 * 归档 = 把教务学期下拉里的每个学期各导入成一张课表：
 * - **候选学期只认教务下拉**（`select#xnxq01id`）：实测 12 项、按学籍生成、起点就是入学
 *   学年，所以不必用学号推入学年，也不必用「连续 N 个空学期」猜边界；
 * - 只抓**不晚于教务当前学期**的项（未来学期还没排课，抓了也是空表）；
 * - 每个学期先看库里有没有同 term 的课表：有就跳过（**永不覆盖**，历史学期不做重导）；
 * - 用户主动删掉的学期（[DisplayPrefsStore.scheduleDismissedTerms]）自动链跳过，手动入口
 *   （[manual] = true）清掉标记后重建——「删除后可重新导入」走这条路；
 * - 命名用 [TermNaming]：最早有课学期 = 大一上，写入 `schedule_first_term` 作为基准。
 *
 * 探测 = 看下一个学期（库里最大 term 的下一学期）是否已排课（[probeNextTerm]）：
 * 学期下拉里没有该学期（无选中项）或两页都 0 条 → 未出，静默；有课就新建课表并导入。
 *
 * 两条链都走 [ScheduleSync.fetch]，与手动导入同一套解析；请求之间一律
 * [AutoSyncRules.interRequestDelayMs] 间隔，撞上在跑的那次直接让位（[syncLocked]）。
 */
class ScheduleTermImport(
    private val cas: JwReadSession,
    private val repo: ScheduleRepository,
    private val prefs: DisplayPrefsStore,
    private val inbox: InboxRepository,
    private val sync: ScheduleSync,
    private val nowMillis: () -> Long = AppClock::nowMillis,
    private val today: () -> LocalDate = AppClock::today,
) {

    /** 归档冷启动补跑的退避窗口：失败也记时刻，6 小时内不再打一轮教务。 */
    companion object {
        /** 归档失败后的重试退避。 */
        const val ARCHIVE_RETRY_WINDOW_MS = 6 * 60 * 60 * 1000L

        /** 下一学期探测的固定间隔（不跟用户的差异检测间隔走，避免被调成一周）。 */
        const val NEW_TERM_PROBE_INTERVAL_HOURS = 24
        const val NEW_TERM_PROBE_INTERVAL_MS = NEW_TERM_PROBE_INTERVAL_HOURS * 60L * 60L * 1000L

        /** 课表页学期下拉的 id（与 `ScheduleSync.TERM_SELECT` 同一份口径）。 */
        private const val TERM_SELECT = ScheduleSync.TERM_SELECT
    }

    private val mutex = Mutex()

    sealed interface Result {
        /**
         * [created] = 本次新建的课表，[skipped] = 已有同学期课表或用户已删除的学期数，
         * [failed] = 学期到失败原因。全部跳过时 [created] 为空。
         */
        data class Archived(
            val createdTerms: List<String>,
            val skipped: Int,
            val failed: List<Pair<String, String>>,
            val firstTerm: String?,
            val currentTerm: String?,
            /** 本次新建课表里的课程总数（结果弹窗展示「共 N 门」）。 */
            val courseCount: Int = 0,
        ) : Result

        /** 正在跑另一次归档，本次让位（不排队，防重复打教务）。 */
        data object InProgress : Result

        data class Failed(val reason: String) : Result
    }

    /** 新学期探测的结论。 */
    sealed interface ProbeResult {
        /** 新学期已出并导入成功（[timetableId] 是新建的课表）。 */
        data class Found(val term: String, val courseCount: Int, val timetableId: Long) : ProbeResult

        /** 下一学期还没排课 / 教务没开放 / 已有课表，[reason] 只用于日志。 */
        data class NotYet(val term: String, val reason: String) : ProbeResult

        /** 连学期基准都拿不到（没有带 term 的课表，或探测闸门未到）。 */
        data class Skipped(val reason: String) : ProbeResult

        data class Failed(val reason: String) : ProbeResult
    }

    /**
     * 全量归档。入口三处：首启引导登录成功、老用户升级后的首次冷启动、设置页手动入口。
     *
     * [manual] = true 表示用户显式要求（清掉「已删除学期」标记、不受退避闸门限制）；
     * 自动链（false）尊重标记。
     * [onProgress] 每抓完一个学期回调一次，供手动入口显示进度。
     */
    suspend fun archive(
        manual: Boolean = false,
        onProgress: (done: Int, total: Int, term: String) -> Unit = { _, _, _ -> },
    ): Result = mutex.tryLocked { archiveLocked(manual, onProgress) } ?: Result.InProgress

    private suspend fun archiveLocked(
        manual: Boolean,
        onProgress: (done: Int, total: Int, term: String) -> Unit,
    ): Result {
        prefs.setScheduleArchiveAttemptAt(nowMillis())
        val html = when (val fetched = cas.fetchHtml(JwUrls.SCHEDULE_LIST)) {
            is CasFetchResult.Ok -> fetched.body
            is CasFetchResult.Failed -> return Result.Failed("没取到课表页，${fetched.reason}")
        }
        val currentTerm = JwHtml.selectedTerm(html, TERM_SELECT)
        val options = JwHtml.termOptions(html, TERM_SELECT)
        if (options.isEmpty()) return Result.Failed("课表页没有可用的学期下拉（教务结构可能已变化）")
        currentTerm?.let { prefs.setScheduleJwCurrentTerm(it) }

        val currentKey = TermNaming.sortKey(currentTerm) ?: TermNaming.sortKey(options.last())
        val wanted = options.filter { term ->
            val key = TermNaming.sortKey(term)
            key != null && currentKey != null && key <= currentKey
        }
        if (wanted.isEmpty()) return Result.Failed("教务没有可归档的学期")

        val dismissed = prefs.scheduleDismissedTerms()
        val candidates = if (manual) wanted else wanted.filter { it !in dismissed }

        val fetchedTerms = LinkedHashMap<String, ScheduleSync.FetchResult.Ok>()
        val failed = mutableListOf<Pair<String, String>>()
        var skipped = 0
        var done = 0
        // 会话被顶 / 网络断了：后面每个学期都会以同样理由失败，继续打完只是白撞教务
        // （强智是单会话，连续两次取页失败基本就是会话真挂了）。剩下的学期这轮不碰；
        // 因为 failed 非空，归档标记不会落，下次冷启动会补。
        var consecutiveNetworkFailures = 0
        for (term in candidates) {
            if (repo.timetableByTerm(term) != null) {
                // 已有该学期的课表：永不覆盖（历史学期不做重导）
                skipped++
                continue
            }
            onProgress(done, candidates.size, term)
            when (val result = sync.fetch(term)) {
                is ScheduleSync.FetchResult.Ok -> {
                    consecutiveNetworkFailures = 0
                    when {
                        // 学期在教务里但一门课都没有（未排课 / 假期）：不建空表
                        result.result.courses.isEmpty() -> skipped++
                        // 实验页结构不可信时按失败报：静默导入会把实验课整批漏掉，
                        // 而用户看不到分项条数（同 import-jw.md「实验页 0 条不是失败」的分界）
                        !result.labReliable -> failed +=
                            term to "实验课表页未识别到课表（页面结构可能已变化），请手动导入一次核对"
                        else -> fetchedTerms[term] = result
                    }
                }
                is ScheduleSync.FetchResult.Failed -> {
                    failed += term to result.reason
                    consecutiveNetworkFailures = if (result.kind == ScheduleSync.FailureKind.Network) {
                        consecutiveNetworkFailures + 1
                    } else {
                        0
                    }
                }
            }
            done++
            onProgress(done, candidates.size, term)
            if (consecutiveNetworkFailures >= 2) break
            delay(AutoSyncRules.interRequestDelayMs())
        }

        val createdTerms = mutableListOf<String>()
        if (fetchedTerms.isNotEmpty()) {
            val earliest = fetchedTerms.keys.minByOrNull { TermNaming.sortKey(it) ?: Int.MAX_VALUE }
            // 基准学期取「已有基准」与「本次最早有课学期」里更早的那个：老用户先归档过，
            // 后来又补了更早的学期时，命名基准跟着往前挪。
            val firstTerm = listOfNotNull(prefs.scheduleFirstTerm(), earliest)
                .minByOrNull { TermNaming.sortKey(it) ?: Int.MAX_VALUE }
            prefs.setScheduleFirstTerm(firstTerm)

            // 首启那张默认空表（唯一一张、无 term、零课程）拿来装教务当前学期，避免留一张空表
            var reusableId = emptyDefaultTimetableId()
            for ((term, result) in fetchedTerms) {
                val existing = repo.timetableByTerm(term)
                if (existing != null) {
                    skipped++
                    continue
                }
                val targetId = if (term == currentTerm && reusableId != null) {
                    reusableId.also { id ->
                        repo.renameTimetable(id, uniqueName(TermNaming.timetableName(term, firstTerm), term))
                        repo.setTimetableTerm(id, term)
                        reusableId = null
                    }
                } else {
                    val id = repo.createTimetable(uniqueName(TermNaming.timetableName(term, firstTerm), term))
                    repo.setTimetableTerm(id, term)
                    id
                }
                repo.importParsedCourses(
                    courses = result.result.courses,
                    merge = false,
                    timetableId = targetId,
                    term = term,
                )
                // 历史学期用估算开学日（教学日历只校准当前学期，避免每次归档多 N 次请求）
                val startDate = ExamMapper.estimatedTermStart(term)?.toString()
                    ?: repo.semesterFor(targetId)?.startDate
                    ?: DefaultData.defaultSemester.startDate
                val weeks = repo.semesterFor(targetId)?.totalWeeks ?: DefaultData.defaultSemester.totalWeeks
                repo.updateSemesterFor(targetId, SemesterConfig(startDate, weeks))
                createdTerms += term
            }
            // 基线是单快照：显式指向 term 最大的那张，别让最后写入的历史学期抢走
            repo.latestTermTimetable()?.let { repo.refreshBaselineFromDb(it.id) }
        }

        if (manual) {
            // 用户显式要求归档：把「已删除」标记清掉，之前删掉的学期从此可以回来
            prefs.removeScheduleDismissedTerms(candidates)
        }
        // 有学期失败就不落「已完成」（2026-11-19 复核）：此前无条件落，一次会话被顶掉
        // 让所有学期都失败，也会把归档标记成完成——以后再也不自动补，用户只能手动重导。
        // 失败时保持未完成，靠 attempt 的 6 小时退避兜住重试频率（已成功的学期幂等跳过）。
        if (failed.isEmpty()) prefs.setScheduleArchiveDoneAt(nowMillis())
        // 默认名改学期标签（「我的课表」→「大三上」）；失败那轮不动，避免用不准的基准命名
        if (failed.isEmpty()) normalizeTimetableNames()
        repo.syncCurrentTimetableToDate(today(), currentTerm)

        if (createdTerms.isNotEmpty()) {
            postArchiveInbox(createdTerms, failed.size)
        }
        return Result.Archived(
            createdTerms = TermNaming.sortedAscending(createdTerms),
            skipped = skipped,
            failed = failed,
            firstTerm = prefs.scheduleFirstTerm(),
            currentTerm = currentTerm,
            courseCount = fetchedTerms.values.sumOf { it.result.courses.size },
        )
    }

    /**
     * 把「用户从没起过名」的课表改成学期标签（2026-11-19 用户口径：当前那张「我的课表」
     * 应该叫「大三上」）。
     *
     * 三条边界：
     * - **只认两个默认名**（[ScheduleRepository.DEFAULT_TIMETABLE_NAMES]）：用户自己起的名字
     *   一律不动，也不判断「改过又改回来」这种没法判断的事；
     * - 没有学期号（手建表）不改；没有入学基准（`first_term`）也不改，等归档拿到基准；
     * - 幂等、纯本地、零网络：归档成功后调一次，冷启动也调（已归档完成的老设备重启即改名）。
     *
     * 返回改了几张。
     */
    suspend fun normalizeTimetableNames(): Int {
        val firstTerm = prefs.scheduleFirstTerm() ?: return 0
        val all = repo.allTimetables()
        if (all.isEmpty()) return 0
        val used = all.map { it.name }.toMutableSet()
        var changed = 0
        for (timetable in all) {
            if (timetable.name !in ScheduleRepository.DEFAULT_TIMETABLE_NAMES) continue
            val term = timetable.term?.takeIf { TermNaming.parse(it) != null } ?: continue
            val base = TermNaming.timetableName(term, firstTerm)
            val name = when {
                base !in used -> base
                "$base $term" !in used -> "$base $term"
                else -> "$base $term (${timetable.id})"
            }
            if (name == timetable.name) continue
            repo.renameTimetable(timetable.id, name)
            used -= timetable.name
            used += name
            changed++
        }
        return changed
    }

    /**
     * 探测下一学期是否已出（DESIGN §4.17 变动检测第一段）。
     *
     * 基准 = 库里 term 最大的课表；目标 = 它的下一学期。库里已有该学期课表 → 不探测。
     * [respectInterval] = false 时不吃 24 小时间隔（手动快速检测 / 归档后立刻看一次）。
     */
    suspend fun probeNextTerm(force: Boolean = false): ProbeResult {
        val latest = repo.latestTermTimetable() ?: return ProbeResult.Skipped("没有带学期号的课表")
        val target = TermNaming.next(latest.term) ?: return ProbeResult.Skipped("学期号无法推算下一学期")
        if (repo.timetableByTerm(target) != null) {
            return ProbeResult.NotYet(target, "该学期课表已在库里")
        }
        if (!force) {
            val last = prefs.scheduleNewTermProbeAt()
            if (nowMillis() - last < NEW_TERM_PROBE_INTERVAL_MS) {
                return ProbeResult.Skipped("距上次探测未满 24 小时")
            }
        }
        // 毕业 / 长期休学：最后一个学期结束 180 天后不再探测，免得永久每 24 小时打两次教务。
        // 手动导入新学期会把 max term 推上去，探测自动恢复。
        // iOS 移植 S4：`LocalDate` 换成 kotlinx-datetime 之后没有 `isAfter`，用 `>`（Comparable）。
        val latestStart = ExamMapper.estimatedTermStart(latest.term)
        if (latestStart != null &&
            today() > latestStart.plus(DatePeriod(days = 20 * 7 + 180))
        ) {
            return ProbeResult.Skipped("最后一个学期已结束超过 180 天")
        }
        if (target in prefs.scheduleDismissedTerms()) {
            return ProbeResult.NotYet(target, "用户删除过该学期的课表")
        }
        val result = when (val fetched = sync.fetch(target)) {
            is ScheduleSync.FetchResult.Failed -> return when (fetched.kind) {
                // 学期不在下拉里 = 教务还没开放这一学期：这是**权威空态**，必须落探测时刻，
                // 否则每次冷启动（enqueueCheck 不受 24 小时闸门约束）都会再打一次教务
                ScheduleSync.FailureKind.TermNotOpen -> {
                    prefs.setScheduleNewTermProbeAt(nowMillis())
                    ProbeResult.NotYet(target, "教务尚未开放该学期")
                }
                else -> ProbeResult.Failed(fetched.reason)
            }
            is ScheduleSync.FetchResult.Ok -> fetched
        }
        prefs.setScheduleNewTermProbeAt(nowMillis())
        if (result.result.courses.isEmpty()) {
            return ProbeResult.NotYet(target, "学期已开放但还没有课程")
        }
        val timetableId = createTimetableForTerm(target)
        repo.importParsedCourses(
            courses = result.result.courses,
            merge = false,
            timetableId = timetableId,
            term = target,
        )
        val startDate = ExamMapper.estimatedTermStart(target)?.toString()
            ?: repo.semesterFor(timetableId)?.startDate
            ?: DefaultData.defaultSemester.startDate
        repo.updateSemesterFor(
            timetableId,
            SemesterConfig(startDate, repo.semesterFor(timetableId)?.totalWeeks ?: DefaultData.defaultSemester.totalWeeks),
        )
        // 基线跟着最新学期走（单快照，见 ScheduleBaselineStore）
        repo.refreshBaselineFromDb(timetableId)
        repo.syncCurrentTimetableToDate(today(), prefs.scheduleJwCurrentTerm())
        inbox.post(
            kind = InboxKind.SCHEDULE_NEW_TERM,
            dedupeKey = InboxKeys.SCHEDULE_TERM_PREFIX + target,
            title = newTermTitle(target),
            body = newTermBody(target, result.result.courses.size),
            route = SubpageScreen.TIMETABLE_MANAGE.name,
        )
        return ProbeResult.Found(target, result.result.courses.size, timetableId)
    }

    private suspend fun createTimetableForTerm(term: String): Long {
        val firstTerm = prefs.scheduleFirstTerm()
            ?: repo.allTimetables().mapNotNull { it.term }.minByOrNull { TermNaming.sortKey(it) ?: Int.MAX_VALUE }
        val name = uniqueName(TermNaming.timetableName(term, firstTerm), term)
        val id = repo.createTimetable(name)
        repo.setTimetableTerm(id, term)
        return id
    }

    /**
     * 课表名去重：同名（用户手建过「大二下」）时追加学期号，避免课表管理里两行同名。
     */
    private suspend fun uniqueName(base: String, term: String): String {
        val used = repo.allTimetables().map { it.name }.toSet()
        if (base !in used) return base
        val withTerm = "$base $term"
        if (withTerm !in used) return withTerm
        var index = 2
        while ("$withTerm ($index)" in used) index++
        return "$withTerm ($index)"
    }

    /** 首启那张默认空表：唯一一张、term 为空、零课程。其余情况返回 null（不复用别人的表）。 */
    private suspend fun emptyDefaultTimetableId(): Long? {
        val all = repo.allTimetables()
        if (all.size != 1) return null
        val only = all.first()
        if (!only.term.isNullOrBlank()) return null
        return only.id.takeIf { repo.timetableCourseCount(it) == 0 }
    }

    private suspend fun postArchiveInbox(createdTerms: List<String>, failedCount: Int) {
        val sorted = TermNaming.sortedAscending(createdTerms)
        val earliest = sorted.first()
        val latest = sorted.last()
        val key = "${InboxKeys.SCHEDULE_ARCHIVE_PREFIX}$earliest|$latest|${sorted.size}"
        val range = if (sorted.size == 1) termLabel(earliest) else "${termLabel(earliest)}至${termLabel(latest)}"
        inbox.post(
            kind = InboxKind.SCHEDULE_ARCHIVE,
            dedupeKey = key,
            title = "已归档 ${sorted.size} 个学期课表",
            body = buildString {
                append("$range（$earliest ~ $latest）已从教务导入，可在课表管理里切换查看。")
                if (failedCount > 0) append("另有 $failedCount 个学期导入失败，可稍后重新导入。")
            },
            route = SubpageScreen.TIMETABLE_MANAGE.name,
        )
    }

    private suspend fun newTermTitle(term: String): String {
        val label = TermNaming.gradeLabel(term, prefs.scheduleFirstTerm())
        // 注意 `${label}`：中文是合法的 Kotlin 标识符字符，写成 `$label课表已出`
        // 会被当成一个叫 `label课表已出` 的变量（编译不过）
        return if (label != null) "${label}课表已出" else "$term 课表已出"
    }

    private suspend fun newTermBody(term: String, count: Int): String {
        val label = TermNaming.gradeLabel(term, prefs.scheduleFirstTerm())
        val head = if (label != null) "教务已发布 ${label}（$term）课表" else "教务已发布 $term 课表"
        return "$head，共 $count 门课程，已自动导入课表「${label ?: term}」，可在课表管理里查看。"
    }

    private suspend fun termLabel(term: String): String =
        TermNaming.gradeLabel(term, prefs.scheduleFirstTerm()) ?: term
}
