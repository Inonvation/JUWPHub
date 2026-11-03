package edu.jxslu.schedule.domain

import edu.jxslu.schedule.domain.ExamMapper.ExamEntry

/**
 * 考试变动自动合并（DESIGN §4.33，2026-11-03）：把教务**最新考试列表**并进当前课表的计划计算。
 *
 * 为什么按整份列表对账、而不是只并这次变动的那几条：考试是考前数周逐门录入的，
 * 首次检查只建基线不并表；只并之后的变动会让课表里只有零星几场、其余考试一直缺着。
 * 整份列表对账一次就能把课表的考试部分拉齐（缺的补、变了的原地改）。
 *
 * 匹配口径（与 [ExamChangeDetector] 的身份键不是一回事：那边是「考试 ↔ 考试」，
 * 这里是「课表行 ↔ 考试」）：
 * 1. 同名 + 同一教学周 + 同一天 = 同一场考试，内容变了就**原地改**（保 id / 配色 / 备注）；
 * 2. 同名但日期已不在最新列表里 = **改期**，复用旧行（不另插一行，也不留旧日期的幽灵）；
 * 3. 其余 = 新增。
 *
 * **不删行**：撤考（旧有新无）在 [ExamChangeDetector] 里本就忽略，自动写库也只做「补」和「改」，
 * 删考试留给用户自己动手。
 *
 * 只做纯计算，落库在 `ScheduleRepository.mergeExamCourses`（学期守卫、配色、事务都在那边）。
 */
object ExamMerge {

    /**
     * 一次合并要写的两类行。
     * [updates] 的第二项已带好旧行的 id / 配色 / 备注，直接 upsert 即可原地替换。
     */
    data class Plan(
        val updates: List<Pair<Course, Course>>,
        val adds: List<Course>,
    ) {
        val isEmpty: Boolean get() = updates.isEmpty() && adds.isEmpty()

        /** 实际写入的行数（新增 + 原地改）。 */
        val size: Int get() = updates.size + adds.size
    }

    /**
     * [existing] 是目标课表的全部课程（非考试行会先被滤掉），[entries] 是教务最新考试列表。
     * 日期/时刻无法定位的考试（学期配置缺失、日期越界）由 [ExamMapper.toExamCourse] 跳过，
     * 与手动导入同一口径：宁可少写，不落错周。
     */
    fun plan(
        existing: List<Course>,
        entries: List<ExamEntry>,
        slots: List<TimeSlot>,
        config: SemesterConfig?,
        term: String?,
    ): Plan {
        val mapped = entries.mapNotNull { ExamMapper.toExamCourse(it, slots, config, term) }
        if (mapped.isEmpty()) return Plan(emptyList(), emptyList())

        val rows = existing.filter { it.kind == CourseKind.Exam }
        val fresh = mapped.mapTo(HashSet()) { identity(it) }
        val used = HashSet<Long>()
        val updates = mutableListOf<Pair<Course, Course>>()
        val adds = mutableListOf<Course>()

        for (exam in mapped) {
            val sameName = rows.filter { it.name == exam.name && it.id !in used }
            val target = sameName.firstOrNull { identity(it) == identity(exam) }
                ?: sameName.firstOrNull { identity(it) !in fresh }
            if (target == null) {
                adds += exam
                continue
            }
            used += target.id
            if (sameContent(target, exam)) continue
            updates += target to exam.copy(
                id = target.id,
                colorIndex = target.colorIndex,
                remark = target.remark,
            )
        }
        return Plan(updates, adds)
    }

    /**
     * 课表行 / 考试的身份：同名 + 同一教学周 + 同一天。考试行恒为单周（[ExamMapper] 的映射口径），
     * 取 `weeks` 的并集写法只为对用户手改过的多周行也不误判。
     */
    private fun identity(course: Course): String =
        course.name + "|" + course.day + "|" + course.weeks.sorted().joinToString(",")

    /** 内容是否一致（不比 id / 配色 / 备注：那三个是本地字段，合并时要原样保留）。 */
    private fun sameContent(a: Course, b: Course): Boolean =
        a.name == b.name &&
            a.teacher == b.teacher &&
            a.position == b.position &&
            a.day == b.day &&
            a.startSection == b.startSection &&
            a.endSection == b.endSection &&
            a.weeks == b.weeks &&
            a.isCustomTime == b.isCustomTime &&
            a.customStartTime == b.customStartTime &&
            a.customEndTime == b.customEndTime
}
