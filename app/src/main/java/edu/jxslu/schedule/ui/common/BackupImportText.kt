package edu.jxslu.schedule.ui.common

import edu.jxslu.schedule.data.repo.ImportPreview
import edu.jxslu.schedule.data.repo.ImportResult

/**
 * 备份导入的**知情文案**：确认弹窗的 `note` 与成功提示。
 *
 * 为什么收口在这里（2026-11-06 复核）：导入入口有两个——「我的 → 数据」与「课表页 → 导入 JSON」。
 * 后者原先既不给 `note`、成功后也只报课程数，于是从课表页导一份备份会**静默整体替换**
 * 用户的成绩/教材/笔记/作业，用户完全不知情（P0 同源的知情缺口）。两处各写一份文案必然漂移，
 * 所以文案与判定都放这里，两个入口只调函数。
 *
 * 文案必须与 `ScheduleRepository.importJson` 的实际行为逐条对齐（改那边就要回来改这里）：
 *
 * | 段 | 覆盖导入 | 合并导入 |
 * |----|----------|----------|
 * | 成绩 / 学业完成 / 培养方案 / 教材 | 整体替换（全局归属，与课表无关） | **同样整体替换** |
 * | 笔记 / 作业 | 整体替换现有（图片不随文件） | **完全不动** |
 * | 调休 / 停课 | 替换目标课表的 | 追加到目标课表 |
 * | 学期配置 / 作息 | 覆盖目标课表的 | 覆盖目标课表的 |
 *
 * 因为弹窗是先看文案再选「合并/覆盖」，文案不能只讲一种模式——两种模式的后果都要说清。
 */
fun importPreviewNote(preview: ImportPreview.Ok): String? {
    val parts = mutableListOf<String>()
    if (preview.scores.isNotEmpty()) {
        parts += "${preview.scores.map { it.term }.distinct().size} 个学期共 ${preview.scores.size} 条成绩，" +
            "导入时会整体替换现有成绩（合并导入也一样）。"
    }
    if (preview.scholarCourseCount > 0) {
        parts += "另有 ${preview.scholarCourseCount} 门课程的学业完成情况，导入时会整体替换。"
    }
    if (preview.curriculumCourseCount > 0) {
        parts += "另有 ${preview.curriculumCourseCount} 门培养方案课程设置，导入时会整体替换。"
    }
    if (preview.textbookCount > 0) {
        parts += "另有 ${preview.textbookCount} 本教材，导入时会整体替换现有教材记录。"
    }
    // 笔记/作业是用户自己写的东西，两种模式的后果不同，必须分开说
    if (preview.noteCount > 0) {
        parts += "另有 ${preview.noteCount} 条笔记：覆盖导入会整体替换现有笔记" +
            "（笔记里的图片不随文件，需重新配图），合并导入不改动笔记。"
    }
    if (preview.homeworkCount > 0) {
        parts += "另有 ${preview.homeworkCount} 条作业：覆盖导入会整体替换现有作业，合并导入不改动作业。"
    }
    if (preview.overrideCount > 0) {
        parts += "另有 ${preview.overrideCount} 条调休 / 停课安排：覆盖导入会替换目标课表的，" +
            "合并导入会追加。"
    }
    val config = buildList {
        if (preview.semester != null) add("学期配置")
        if (preview.slotCount > 0) add("作息 ${preview.slotCount} 节")
    }
    if (config.isNotEmpty()) {
        parts += "并将恢复目标课表的${config.joinToString("与")}。"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

/**
 * 导入成功后的提示：除了课程数，还要报出「顺手恢复了多少」。
 *
 * 原先只报课程数，用户以为只动了课表——实际成绩、笔记、作业、调休都换了。
 * 段为空时不提该段（避免「恢复了 0 条笔记」这种噪音）。
 */
fun importSuccessText(result: ImportResult.Success): String {
    val head = if (result.merge) {
        "合并完成：新增 ${result.added} / 文件共 ${result.total} 门课"
    } else {
        "已覆盖导入 ${result.total} 门课"
    }
    val extras = buildList {
        if (result.restoredScores > 0) add("成绩 ${result.restoredScores} 条")
        if (result.restoredScholarCourses > 0) add("学业 ${result.restoredScholarCourses} 门")
        if (result.restoredCurriculumCourses > 0) add("培养方案 ${result.restoredCurriculumCourses} 门")
        if (result.restoredTextbooks > 0) add("教材 ${result.restoredTextbooks} 本")
        if (result.restoredNotes > 0) add("笔记 ${result.restoredNotes} 条")
        if (result.restoredHomework > 0) add("作业 ${result.restoredHomework} 条")
        if (result.restoredOverrides > 0) add("调休 ${result.restoredOverrides} 条")
        if (result.restoredSlots > 0) add("作息 ${result.restoredSlots} 节")
        if (result.restoredSemester) add("学期配置")
    }
    return if (extras.isEmpty()) head else "$head，同时恢复${extras.joinToString("、")}"
}
