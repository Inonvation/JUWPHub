package edu.jxslu.schedule.ui.week

import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.domain.ScheduleDiff
import edu.jxslu.schedule.domain.TermNaming
import edu.jxslu.schedule.ui.common.NoticeTone

/**
 * 课表页「快速检测」的一次结果（DESIGN §4.17，2026-11-18）。
 *
 * [hasChanges] 单独给出来而不是让 UI 从文案里猜：提示条据此决定要不要挂「查看」
 * （落调课处置页），文案怎么改都不该影响那个动作。
 */
data class QuickCheckFeedback(
    val text: String,
    val tone: NoticeTone,
    val hasChanges: Boolean,
)

/**
 * 快速检测的三条链结果 → 一句提示（纯函数，`QuickCheckTextTest` 钉住）。
 *
 * 三条链各说一句、用「；」连接：一次点击动了四份数据（理论课表 / 实验课表 / 考试 /
 * 下一学期课表），只报其中一份，用户会以为另一份没查。考试那一路尤其不能吞——
 * 「考试变动自动合并」开着时它**当场写课表**，不说就是静默改课。
 *
 * 新学期探测（2026-11-19）只在真的命中时占一句：没排课是常态，每次都写「下学期还没出」
 * 会让这条提示变成噪音。
 *
 * 语气：任一路失败 = Error；发现差异 = Warning（要用户处置）；否则 Info。
 *
 * **public 而不是 internal**（iOS 移植 M1）：迁进 `:shared` 之后，钉它的 `QuickCheckTextTest`
 * 还留在 `:app`（那边 JVM 单测环境齐备），而 `internal` 跨模块不可见。等测试也搬过来再收窄。
 */
fun quickCheckFeedback(
    /** null = 本平台没有这一路（iOS M1 还没有教务直取链）：那一句整条不出现。 */
    schedule: ScheduleSync.CheckResult?,
    exam: ExamSync.Result?,
    probe: ScheduleTermImport.ProbeResult? = null,
    firstTerm: String? = null,
): QuickCheckFeedback {
    // 计数单位是「一次调课」而不是行级差异（与处置页 / 通知 / hub 入口行同口径）
    val groups = (schedule as? ScheduleSync.CheckResult.Ok)
        ?.let { ScheduleDiff.group(it.changes).size }
        ?: 0
    val failed = schedule is ScheduleSync.CheckResult.Failed || exam is ExamSync.Result.Failed
    val examChanged = (exam as? ExamSync.Result.Updated)?.changes?.isNotEmpty() == true
    val newTerm = (probe as? ScheduleTermImport.ProbeResult.Found)?.let {
        val label = TermNaming.gradeLabel(it.term, firstTerm) ?: it.term
        "新学期课表已出：${label}（${it.courseCount} 门，已导入，可在课表管理里切换）"
    }

    val parts = buildList {
        when (schedule) {
            is ScheduleSync.CheckResult.Ok ->
                add(if (groups > 0) "发现 $groups 处调课差异" else "课表无变动")
            is ScheduleSync.CheckResult.Failed -> add("课表检测失败（${schedule.reason}）")
            ScheduleSync.CheckResult.Skipped -> add("课表未比对（没有带学期号的课表，先从教务导入一次）")
            // 本平台没有这一路（iOS M1）：不出一句，说「未比对」会让人以为哪里配错了
            null -> Unit
        }
        when (exam) {
            is ExamSync.Result.Updated -> add(
                when {
                    exam.changes.isEmpty() -> "考试无变动"
                    else -> "考试 ${exam.changes.size} 条变动" +
                        if (exam.merged > 0) "（已并入课表）" else ""
                },
            )
            is ExamSync.Result.Failed -> add("考试检测失败（${exam.reason}）")
            // force 下不会走到这两个分支，留着是因为 Result 是公开类型
            ExamSync.Result.InProgress -> add("考试检查正在进行")
            ExamSync.Result.Skipped -> add("考试未检查")
            null -> Unit
        }
        newTerm?.let { add(it) }
    }

    return QuickCheckFeedback(
        // 「已检查」只在两路都真跑完时才说：任一路失败 / 跳过还挂这句，读起来像整轮成功
        text = (if (failed || schedule is ScheduleSync.CheckResult.Skipped) "" else "已检查：") +
            parts.joinToString("；"),
        tone = when {
            failed -> NoticeTone.Error
            groups > 0 || examChanged -> NoticeTone.Warning
            else -> NoticeTone.Info
        },
        hasChanges = groups > 0,
    )
}
