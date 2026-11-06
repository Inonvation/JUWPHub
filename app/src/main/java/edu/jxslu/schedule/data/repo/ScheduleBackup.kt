package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.data.local.courseKindFromName
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.domain.CurriculumCourse
import edu.jxslu.schedule.domain.Homework
import edu.jxslu.schedule.domain.Note
import edu.jxslu.schedule.domain.OverrideKind
import edu.jxslu.schedule.domain.ScholarCourse
import edu.jxslu.schedule.domain.ScholarCourseStatus
import edu.jxslu.schedule.domain.ScholarGroup
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.domain.ScheduleOverride
import edu.jxslu.schedule.domain.ScoreRecord
import edu.jxslu.schedule.domain.SemesterConfig
import edu.jxslu.schedule.domain.Textbook
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.TimeSlotRules
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CourseJson(
    val id: Long = 0,
    val name: String,
    val teacher: String = "",
    val position: String = "",
    val day: Int,
    val startSection: Int,
    val endSection: Int,
    val weeks: List<Int> = emptyList(),
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val colorIndex: Int = 0,
    /** [CourseKind] 的小写名。带默认值，保证加字段前导出的旧 JSON 仍能读进来。 */
    val kind: String = "theory",
    /** 课程备注（DESIGN §4.3，2026-09-21，可选键）：旧文件缺省为空串，新版多写的键拾光侧忽略。 */
    val remark: String = "",
) {
    fun toDomain(): Course = Course(
        id = id,
        name = name,
        teacher = teacher,
        position = position,
        day = day,
        startSection = startSection,
        endSection = endSection,
        weeks = weeks.toSet(),
        isCustomTime = isCustomTime,
        customStartTime = customStartTime,
        customEndTime = customEndTime,
        colorIndex = colorIndex,
        kind = courseKindFromName(kind),
        remark = remark,
    )
}

@Serializable
data class CourseExport(
    val courses: List<CourseJson> = emptyList(),
    /** 数据所属学年学期（如 2026-2027-1），来自教务/脚本导出；旧文件或手工编辑没有则缺省。 */
    val term: String? = null,
    /**
     * 成绩备份段（DESIGN §4.3/§4.15）：换机/重装随课表一起恢复。
     * 读取侧 ignoreUnknownKeys——旧版 App 忽略该段、新版读旧文件缺省为空，两版互不破坏。
     */
    val scores: List<ScoreBackupJson> = emptyList(),
    /**
     * 学业完成情况备份段（DESIGN §4.3/§4.29）：换机/重装随课表一起恢复。
     * 与成绩同理，旧版 App 忽略该段、新版读旧文件缺省为空，两版互不破坏。
     *
     * 培养方案名不在备份里：它是展示性字段，恢复后为空只是少显示一行，
     * 下次抓取就有了；为此把它塞进 [DisplayPrefs] 反而会牵动整套偏好默认值契约。
     */
    val scholarGroups: List<ScholarGroupBackupJson> = emptyList(),
    val scholarCourses: List<ScholarCourseBackupJson> = emptyList(),
    /**
     * 培养方案课程设置备份段（DESIGN §4.3/§4.41）：换机/重装随备份一起恢复。
     * 与学业段同理，旧版 App 忽略该段、新版读旧文件缺省为空，两版互不破坏。
     * 方案名不进备份（展示性字段，恢复后下次抓取即补，同学业段的口径）。
     */
    val curriculumCourses: List<CurriculumCourseBackupJson> = emptyList(),
    /**
     * 教材备份段（DESIGN §4.31）：换机/重装随备份一起恢复。与成绩同理，
     * 旧版 App 忽略该段、新版读旧文件缺省为空，两版互不破坏。
     */
    val textbooks: List<TextbookBackupJson> = emptyList(),
    /**
     * 笔记备份段（2026-11-06，审计 D2）。**用户自写数据，本机再没有第二份**——
     * 之前它不在备份里，换机/重装就永久丢失，而教务可重取的教材/成绩反而被带走了。
     * 旧文件没有此键 → 空列表 → **导入侧不动现有笔记**（见 [validateBackup] 的空段口径）。
     */
    val notes: List<NoteBackupJson> = emptyList(),
    /**
     * 作业备份段（2026-11-06，审计 D2）。归属与备份口径同 [notes]：用户自写、没有第二份。
     *
     * 笔记/作业的**图片附件不随备份**（`filesDir/notes_img/` 的二进制不塞进 JSON）：
     * 正文里的 `![](img:…)` 引用会保留，但换机后图片缺失，界面按「图片加载失败」降级。
     */
    val homework: List<HomeworkBackupJson> = emptyList(),
    /**
     * 学期配置备份段（DESIGN §4.3）：导出当前课表、导入恢复到目标课表。
     * 旧文件没有此键 → 不动目标课表的学期配置。
     */
    val semester: SemesterBackupJson? = null,
    /**
     * 作息备份段（DESIGN §4.3）：一条一个 40 分钟小节（小节号口径 §3.5）。
     * 旧文件没有此键 → 不动目标课表的作息。
     */
    val timeSlots: List<TimeSlotBackupJson> = emptyList(),
    /**
     * 日期覆盖备份段（调休 / 停课，DESIGN §4.40；2026-11-06 审计 D2 补）。
     *
     * 之前它**既不在备份里、又会被覆盖导入删掉**（旧逻辑见 `importJson`）：换机后调休安排
     * 凭空消失，而它多半是 AI 从学校通知里落下的、用户不会重录一遍。
     * 与 [semester] / [timeSlots] 同口径：按课表归属，恢复到导入目标课表；旧文件没有此键 →
     * 空列表 → 不动目标课表已有的覆盖。
     */
    val dateOverrides: List<DateOverrideBackupJson> = emptyList(),
    /**
     * 备份文件格式版本（2026-11-06 审计 B3）。当前恒为 1。
     *
     * **只作识别用：读取侧不校验、不按它拒绝。** 这是本条唯一的硬约束，别「顺手」加一道
     * `require(version <= 1)` 之类的闸——用户装回旧版、或从别人那里拿到一份新版导出的文件时，
     * 那会把「文件其实是好的、只是导出的 App 更新」变成「导入失败，什么也没恢复」。
     * 格式兼容靠的从来是别的机制：`ignoreUnknownKeys = true` 让旧版忽略新段（自首个提交就在），
     * 新段一律带默认值让新版读旧文件降级为空，[validateBackup] 的空段口径保证「旧文件没有
     * 这一段的键」不会去动本机数据。version 将来要起作用，只能是**新增能力**（比如提示
     * 「这份备份来自更新的版本，可能少恢复 N 项」），不能是拒绝。
     *
     * 真正需要留意的降级风险不在版本号上，在**旧版 App 的行为**里：10-06 之后、本轮之前的
     * 覆盖导入会**无条件删 `date_overrides`**（本轮已改成「空段不动作」）。所以拿新版备份
     * 降级安装到那批版本再导入，调休会被删掉且不回填——这只能靠升级解决，读到 version 也拦不住
     * （拦下来反而是更糟的「导入失败」）。
     *
     * 放在字段末尾而不是开头：`CourseExport(courses = …)` 这类位置参数调用遍布测试，
     * 加在首位会把它们全部变成类型不匹配；JSON 按名序列化，位置不影响文件格式。
     * 写出侧不用显式传参——`CourseJsonFormat.encodeDefaults = true`（见本文件末尾）保证
     * 等于默认值的键也会写进文件，所以导出的备份里一定有 `"version": 1`。
     */
    val version: Int = 1,
)

/** 备份文件里的学期配置段（DESIGN §4.3）：全带默认值，异构文件缺字段时降级而不是解码失败。 */
@Serializable
data class SemesterBackupJson(
    /** yyyy-MM-dd。 */
    val startDate: String = "",
    val totalWeeks: Int = 20,
    val firstDayOfWeek: Int = 1,
)

/** 备份文件里的作息段：一条一个 40 分钟小节（小节号口径见 DESIGN §3.5）。 */
@Serializable
data class TimeSlotBackupJson(
    val number: Int,
    val startTime: String = "",
    val endTime: String = "",
)

/**
 * 备份文件里的单条成绩。字段与 [ScoreRecord] 对齐、全带默认值：
 * 手工编辑或旧格式缺字段时降级为空值而不是解码失败。
 */
@Serializable
data class ScoreBackupJson(
    val term: String = "",
    val courseNo: String = "",
    val name: String = "",
    val unit: String = "",
    val credit: Double = 0.0,
    val hours: Double = 0.0,
    val examForm: String = "",
    val courseAttr: String = "",
    val category: String = "",
    /** 数值分；等级制成绩为 null。 */
    val score: Double? = null,
    val scoreStr: String = "",
    val gradePoint: Double? = null,
    val status: String = "",
    val pendingReview: Boolean = false,
)

/**
 * 备份文件里的学业完成情况分组段（DESIGN §4.3 / §4.29）。
 *
 * 字段全部带默认值：手工编辑或旧格式缺字段时降级为空值，而不是整个文件解码失败。
 * 四个维度混在一张列表里，用 `dimension` 区分（与库里的存法一致）。
 */
@Serializable
data class ScholarGroupBackupJson(
    val dimension: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val requiredCredit: Double? = null,
    val earnedCredit: Double? = null,
    val ongoingCredit: Double? = null,
    val remainingCredit: Double? = null,
    val passed: Boolean? = null,
    val percent: String = "",
)

/** 备份文件里的学业完成情况课程明细段（DESIGN §4.3 / §4.29）。 */
@Serializable
data class ScholarCourseBackupJson(
    val dimension: String = "",
    val groupName: String = "",
    val sortOrder: Int = 0,
    val term: String = "",
    val courseNo: String = "",
    val name: String = "",
    val credit: Double = 0.0,
    val planned: Boolean? = null,
    val category: String = "",
    val attribute: String = "",
    val nature: String = "",
    val status: String = "",
    val scoreText: String = "",
    val remark: String = "",
    val degreeCourse: Boolean? = null,
)

/**
 * 备份文件里的培养方案课程段（DESIGN §4.3 / §4.41）：字段与 [CurriculumCourse] 对齐、
 * 全带默认值——手工编辑或旧格式缺字段时降级为空值，而不是整个文件解码失败。
 */
@Serializable
data class CurriculumCourseBackupJson(
    val term: Int = 0,
    val sortOrder: Int = 0,
    val courseNo: String = "",
    val name: String = "",
    val kind: String = "",
    val attr: String = "",
    val credit: Double? = null,
    val hoursTheory: String = "",
    val hoursPractice: String = "",
    val hoursLecture: String = "",
    val hoursExperiment: String = "",
    val hoursTotal: String = "",
    val tixi: String = "",
)

/** 备份文件里的单条教材（DESIGN §4.31）：字段与 [Textbook] 对齐、全带默认值。 */
@Serializable
data class TextbookBackupJson(
    val term: String = "",
    val courseName: String = "",
    val title: String = "",
    val author: String = "",
    val press: String = "",
    val edition: String = "",
    val isbn: String = "",
    val price: String = "",
)

fun Textbook.toBackupJson(): TextbookBackupJson = TextbookBackupJson(
    term = term,
    courseName = courseName,
    title = title,
    author = author,
    press = press,
    edition = edition,
    isbn = isbn,
    price = price,
)

fun TextbookBackupJson.toTextbook(): Textbook = Textbook(
    term = term,
    courseName = courseName,
    title = title,
    author = author,
    press = press,
    edition = edition,
    isbn = isbn,
    price = price,
)

/**
 * 备份文件里的单条笔记（2026-11-06，审计 D2）。
 *
 * **不写 `id`**：行 id 是源设备的自增主键，跨设备没有意义（同 [CourseJson] 的归零口径，
 * 见 `validateBackup` 的注释）。归属键 [courseName] 是原样字符串——课程行 id 在覆盖导入/
 * 调课/撤销里都会换，绑 id 必丢数据（DESIGN §4.20「归属」）。
 *
 * 全字段带默认值：手工编辑或旧格式缺字段时降级为空值，而不是整个文件解码失败。
 */
@Serializable
data class NoteBackupJson(
    val courseName: String = "",
    val title: String = "",
    /** Markdown 源码；图片以 `![](img:文件名)` 内联引用（**图片文件本身不随备份**）。 */
    val body: String = "",
    /**
     * 创建时间（epoch 毫秒）。导出总会写真实值；手工编辑的备份若省略则落 0——
     * 不在这里补「当前时间」：那会让一份旧备份里的笔记全部显示成「今天创建」，
     * 比显示 1970 更误导（列表按 [updatedAt] 排序，最坏是沉到末尾，不影响可用性）。
     */
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

fun Note.toBackupJson(): NoteBackupJson = NoteBackupJson(
    courseName = courseName,
    title = title,
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun NoteBackupJson.toNote(): Note = Note(
    id = 0,
    courseName = courseName,
    title = title,
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * 备份文件里的单条作业（2026-11-06，审计 D2）。不写 `id`，理由同 [NoteBackupJson]。
 *
 * [dueDate] 存 `yyyy-MM-dd` 文本（与 `homework.dueDate` 列同口径，ISO 文本的字典序 = 时间序）；
 * 解析失败的脏值由 `validateBackup` 挡在写库之前，不在这里静默丢。
 */
@Serializable
data class HomeworkBackupJson(
    val courseName: String = "",
    /** Markdown 源码；作业没有独立标题，列表标题取正文第一行（见 `homeworkDisplayTitle`）。 */
    val detail: String = "",
    val dueDate: String? = null,
    val done: Boolean = false,
    /** 勾选完成的时间；null = 未完成。 */
    val doneAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

fun Homework.toBackupJson(): HomeworkBackupJson = HomeworkBackupJson(
    courseName = courseName,
    detail = detail,
    dueDate = dueDate?.toString(),
    done = done,
    doneAt = doneAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun HomeworkBackupJson.toHomework(): Homework = Homework(
    id = 0,
    courseName = courseName,
    detail = detail,
    // 脏日期在这里退回 null 而不是抛：写库路径不该因为一个字段崩掉，
    // 用户能看到的拒绝文案由 validateBackup 给（它在更前面就返回了）。
    dueDate = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    done = done,
    doneAt = doneAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/**
 * 备份文件里的单条日期覆盖（调休 / 停课，DESIGN §4.40；2026-11-06 审计 D2 补）。
 *
 * **不写 `id`，但顺序有意义**：`ScheduleOverrides.plan` 摊平时「后写的覆盖前面的」，
 * 而 DB 侧的顺序就是自增 id。备份列表按导出时的 id 升序排列（`getForTimetable` 的
 * `ORDER BY id ASC`），导入时按同一顺序插入 → 自增 id 单调递增 → **相对顺序被保住**。
 * 若改成按日期排序或乱序插入，同一槽位的多条覆盖谁赢就变了。
 *
 * [kind] 用 [OverrideKind] 的名字（`FOLLOW` / `SUSPENDED`）。认不出的名字在
 * `validateBackup` 里**拒绝整个文件**，不像读库路径那样退回 SUSPENDED——
 * 猜错方向的覆盖会把课「静默停掉」，比导入失败危险得多。
 */
@Serializable
data class DateOverrideBackupJson(
    /** yyyy-MM-dd */
    val fromDate: String = "",
    /** yyyy-MM-dd；单日时与 [fromDate] 相同。 */
    val toDate: String = "",
    val kind: String = "",
    /** FOLLOW 的源周次；其余为 null。 */
    val sourceWeek: Int? = null,
    /** FOLLOW 的源星期（1=周一 … 7=周日）；其余为 null。 */
    val sourceWeekday: Int? = null,
    val note: String = "",
)

fun ScheduleOverride.toBackupJson(): DateOverrideBackupJson = DateOverrideBackupJson(
    fromDate = fromDate,
    toDate = toDate,
    kind = kind.name,
    sourceWeek = sourceWeek,
    sourceWeekday = sourceWeekday,
    note = note,
)

fun DateOverrideBackupJson.toScheduleOverride(): ScheduleOverride = ScheduleOverride(
    id = 0,
    fromDate = fromDate,
    toDate = toDate,
    kind = OverrideKind.entries.firstOrNull { it.name == kind } ?: OverrideKind.SUSPENDED,
    sourceWeek = sourceWeek,
    sourceWeekday = sourceWeekday,
    note = note,
)

fun ScholarGroupBackupJson.toScholarGroup(): ScholarGroup = ScholarGroup(
    dimension = dimension,
    name = name,
    sortOrder = sortOrder,
    requiredCredit = requiredCredit,
    earnedCredit = earnedCredit,
    ongoingCredit = ongoingCredit,
    remainingCredit = remainingCredit,
    passed = passed,
    percent = percent,
)

fun ScholarGroup.toBackupJson(): ScholarGroupBackupJson = ScholarGroupBackupJson(
    dimension = dimension,
    name = name,
    sortOrder = sortOrder,
    requiredCredit = requiredCredit,
    earnedCredit = earnedCredit,
    ongoingCredit = ongoingCredit,
    remainingCredit = remainingCredit,
    passed = passed,
    percent = percent,
)

fun ScholarCourseBackupJson.toScholarCourse(): ScholarCourse = ScholarCourse(
    dimension = dimension,
    groupName = groupName,
    sortOrder = sortOrder,
    term = term,
    courseNo = courseNo,
    name = name,
    credit = credit,
    planned = planned,
    category = category,
    attribute = attribute,
    nature = nature,
    status = status.ifBlank { ScholarCourseStatus.Pending.label },
    scoreText = scoreText,
    remark = remark,
    degreeCourse = degreeCourse,
)

fun ScholarCourse.toBackupJson(): ScholarCourseBackupJson = ScholarCourseBackupJson(
    dimension = dimension,
    groupName = groupName,
    sortOrder = sortOrder,
    term = term,
    courseNo = courseNo,
    name = name,
    credit = credit,
    planned = planned,
    category = category,
    attribute = attribute,
    nature = nature,
    status = status,
    scoreText = scoreText,
    remark = remark,
    degreeCourse = degreeCourse,
)

fun CurriculumCourseBackupJson.toCurriculumCourse(): CurriculumCourse = CurriculumCourse(
    term = term,
    sortOrder = sortOrder,
    courseNo = courseNo,
    name = name,
    kind = kind,
    attr = attr,
    credit = credit,
    hoursTheory = hoursTheory,
    hoursPractice = hoursPractice,
    hoursLecture = hoursLecture,
    hoursExperiment = hoursExperiment,
    hoursTotal = hoursTotal,
    tixi = tixi,
)

fun CurriculumCourse.toBackupJson(): CurriculumCourseBackupJson = CurriculumCourseBackupJson(
    term = term,
    sortOrder = sortOrder,
    courseNo = courseNo,
    name = name,
    kind = kind,
    attr = attr,
    credit = credit,
    hoursTheory = hoursTheory,
    hoursPractice = hoursPractice,
    hoursLecture = hoursLecture,
    hoursExperiment = hoursExperiment,
    hoursTotal = hoursTotal,
    tixi = tixi,
)

fun ScoreBackupJson.toScoreRecord(): ScoreRecord = ScoreRecord(
    term = term.trim(),
    courseNo = courseNo,
    name = name,
    unit = unit,
    credit = credit,
    hours = hours,
    examForm = examForm,
    courseAttr = courseAttr,
    category = category,
    score = score,
    scoreStr = scoreStr,
    gradePoint = gradePoint,
    status = status,
    pendingReview = pendingReview,
)

fun ScoreRecord.toBackupJson(): ScoreBackupJson = ScoreBackupJson(
    term = term,
    courseNo = courseNo,
    name = name,
    unit = unit,
    credit = credit,
    hours = hours,
    examForm = examForm,
    courseAttr = courseAttr,
    category = category,
    score = score,
    scoreStr = scoreStr,
    gradePoint = gradePoint,
    status = status,
    pendingReview = pendingReview,
)

/**
 * 导入/导出共用的 JSON 配置。
 *
 * 提到顶层是为了让测试能复用**同一份**配置：之前测试只断言字符串里含某些键名，
 * 这里把 `ignoreUnknownKeys` 改掉、或者字段加/改默认值，测试都不会红。
 * 现在测试直接拿它 decode，配置漂移会被测出来。
 *
 * `encodeDefaults = true`：kotlinx 默认不写等于默认值的键，于是 `teacher=""`、`kind="theory"`
 * 这类字段会直接从导出文件里消失。读取侧有默认值兜底、我们自己来回转换不会出错，
 * 但文件是要给兼容工具互导的（见设置页说明），键时有时无会让对方必须猜默认值。
 * 全量写出后导出结果自解释，代价只是文件略大。
 */
internal val CourseJsonFormat: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 校验一条待导入的课程。
 * 返回 null 表示通过，否则返回给用户看的错误文案（含第几门课，便于对文件定位）。
 *
 * 与解码分离：`Json` 只保证结构能读出来，字段取值范围要靠这一层，
 * 否则 day=9 这种脏数据会一路写进库里，最后表现成「课程不见了」。
 */
internal fun validateCourseJson(index: Int, c: CourseJson): String? {
    val label = "第 ${index + 1} 门课"
    if (c.name.isBlank()) return "$label：缺少 name"
    if (c.day !in 1..7) return "$label「${c.name}」：day 应在 1–7，实际为 ${c.day}"
    if (c.startSection < 1) return "$label「${c.name}」：startSection 应 ≥1"
    if (c.endSection < c.startSection) {
        return "$label「${c.name}」：endSection(${c.endSection}) 不能小于 startSection"
    }
    // 上界 40 而不是作息表的 11 节：作息表可以自定义（DESIGN §3.5），一天排到 12+ 节是合法的；
    // 但 endSection 上千这种脏数据必须挡住——它会顺利写进库，然后在课表网格里因为
    // 「行号只画到 slots 的节数」而**静默不可见**（2026-11-06 复核发现，正是本函数要防的那类）
    if (c.endSection > 40) return "$label「${c.name}」：endSection 应在 1–40，实际为 ${c.endSection}"
    if (c.weeks.isEmpty()) return "$label「${c.name}」：缺少 weeks（如 [1,2,3]）"
    if (c.weeks.any { it !in 1..40 }) return "$label「${c.name}」：weeks 含非法周次"
    return null
}

/** 目标课表的导入统计：弹窗按所选目标实时展示（覆盖会清掉 existing 门，合并新增 newCount 门）。 */
data class ImportStats(val existing: Int, val newCount: Int)

/** 备份文件的统一校验产物：预览与真导入共用，避免两条校验链漂移。 */
internal data class ValidatedBackup(
    val courses: List<Course>,
    val scores: List<ScoreRecord>,
    val scholarGroups: List<ScholarGroup>,
    val scholarCourses: List<ScholarCourse>,
    val curriculum: List<CurriculumCourse>,
    val textbooks: List<Textbook>,
    val notes: List<Note>,
    val homework: List<Homework>,
    val semester: SemesterConfig?,
    val slots: List<TimeSlot>,
    val overrides: List<ScheduleOverride>,
)

internal sealed interface BackupCheck {
    data class Ok(val value: ValidatedBackup) : BackupCheck
    data class Error(val message: String) : BackupCheck
}

/**
 * 全量校验备份：任何一段有脏数据都整体拒绝，预览与真导入必须走同一份。
 *
 * **空段 = 不动本机数据**（口径统一在写库侧 `ScheduleRepository.importJson`，这里只负责
 * 「把空段原样交出去」）：成绩/学业/培养方案/教材/笔记/作业/日期覆盖 都是「段里有数据才
 * 整体替换」，空列表等于「这份文件不管这件事」。
 *
 * 为什么不能反过来（空段也清库）：本版之前导出的备份里根本没有 `notes`/`homework`/
 * `dateOverrides` 这三个键，解码后必然是空列表。若空段也执行「先删后插」，用户拿一份
 * 旧备份导入 = 把本机在这之后写的笔记、作业、调休安排全部删光——「恢复备份」变成
 * 数据毁灭。代价是「想用备份把某段清空」做不到，这是有意接受的：清空有各自的入口
 * （笔记库/作业库/课表设置），不该靠导出一份空段来实现。
 */
internal fun validateBackup(export: CourseExport): BackupCheck {
    if (export.courses.isEmpty()) return BackupCheck.Error("文件中没有课程（courses 为空）")
    export.courses.forEachIndexed { index, c ->
        validateCourseJson(index, c)?.let { return BackupCheck.Error(it) }
    }
    val scores = export.scores.map { it.toScoreRecord() }
    scores.forEachIndexed { index, s ->
        if (s.term.isBlank() || s.name.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条成绩缺少 term 或 name")
        }
    }
    val scholarGroups = export.scholarGroups.map { it.toScholarGroup() }
    val scholarCourses = export.scholarCourses.map { it.toScholarCourse() }
    scholarGroups.forEachIndexed { index, g ->
        if (g.dimension.isBlank() || g.name.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条学业分组缺少 dimension 或 name")
        }
    }
    scholarCourses.forEachIndexed { index, c ->
        if (c.dimension.isBlank() || c.name.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条学业课程缺少 dimension 或 name")
        }
    }
    val curriculum = export.curriculumCourses.map { it.toCurriculumCourse() }
    curriculum.forEachIndexed { index, c ->
        if (c.name.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条培养方案课程缺少 name")
        }
    }
    val textbooks = export.textbooks.map { it.toTextbook() }
    textbooks.forEachIndexed { index, b ->
        if (b.term.isBlank() || b.courseName.isBlank() || b.title.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条教材缺少 term、courseName 或 title")
        }
    }
    // 笔记/作业：用户自写数据，校验只挡「写进去也没人看得到」的空行。
    // 不校验 title 非空——作业根本没有标题字段，笔记也允许只有正文。
    val notes = export.notes.map { it.toNote() }
    notes.forEachIndexed { index, n ->
        if (n.courseName.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条笔记缺少 courseName")
        }
        if (n.title.isBlank() && n.body.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条笔记的标题与正文都是空的")
        }
    }
    val homework = export.homework.map { it.toHomework() }
    export.homework.forEachIndexed { index, h ->
        if (h.courseName.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条作业缺少 courseName")
        }
        if (h.detail.isBlank()) {
            return BackupCheck.Error("第 ${index + 1} 条作业缺少正文（detail）")
        }
        // 截止日期：坏值必须在这里拒，不能落成 null——那等于把用户的截止日期悄悄抹掉
        h.dueDate?.let { raw ->
            if (runCatching { LocalDate.parse(raw) }.isFailure) {
                return BackupCheck.Error("第 ${index + 1} 条作业的截止日期格式不正确：$raw")
            }
        }
    }
    // 日期覆盖（调休 / 停课）：猜错方向会把课静默停掉，所以认不出的类型/缺字段一律拒整个文件。
    // 注意模板要写 `${label}`：`$label的…` 会被 Kotlin 当成标识符 `label的…`（中文是合法标识符字符）。
    export.dateOverrides.forEachIndexed { index, o ->
        val label = "第 ${index + 1} 条日期覆盖"
        val from = runCatching { ScheduleCalculator.parseDate(o.fromDate) }.getOrNull()
            ?: return BackupCheck.Error("${label}的起始日期格式不正确：${o.fromDate}")
        val to = runCatching { ScheduleCalculator.parseDate(o.toDate) }.getOrNull()
            ?: return BackupCheck.Error("${label}的结束日期格式不正确：${o.toDate}")
        if (to.isBefore(from)) {
            return BackupCheck.Error("${label}的结束日期（${o.toDate}）早于起始日期（${o.fromDate}）")
        }
        if (OverrideKind.entries.none { it.name == o.kind }) {
            return BackupCheck.Error("${label}的类型无法识别：${o.kind}")
        }
        if (o.kind == OverrideKind.FOLLOW.name) {
            val week = o.sourceWeek
            val day = o.sourceWeekday
            if (week == null || week !in 1..40) {
                return BackupCheck.Error("${label}是「按另一天上课」，缺少合法的 sourceWeek")
            }
            if (day == null || day !in 1..7) {
                return BackupCheck.Error("${label}是「按另一天上课」，缺少合法的 sourceWeekday")
            }
        }
    }
    val overrides = export.dateOverrides.map { it.toScheduleOverride() }
    val semester = export.semester?.let { s ->
        val date = runCatching { ScheduleCalculator.parseDate(s.startDate) }.getOrNull()
            ?: return BackupCheck.Error("备份里的开学日期格式不正确：${s.startDate}")
        SemesterConfig(date.toString(), s.totalWeeks.coerceIn(1, 30), s.firstDayOfWeek)
    }
    val slots = export.timeSlots.map { TimeSlot(it.number, it.startTime, it.endTime) }
    if (slots.isNotEmpty()) {
        TimeSlotRules.validate(slots)?.let {
            return BackupCheck.Error("备份里的作息表有问题：$it")
        }
    }
    return BackupCheck.Ok(
        ValidatedBackup(
            // 行 id 是**源设备**的自增主键，跨设备/跨课表没有意义，必须归零让本机 DB 重新分配。
            // 原样写回库会撞上本机其它课表的行：`CourseDao.insertAll` 是 INSERT OR REPLACE，
            // 而 `replaceAllCourses` 只清**目标**课表（`Daos.kt:41`）——被撞的行会被删掉重插、
            // `timetableId` 被改成导入目标，另一张课表静默少课（2026-11-06 审计 D1）。
            // 撤销回滚走 `restoreCourses`（按原 id 恢复，有意为之），不经过这里。
            courses = export.courses.map { it.toDomain().copy(id = 0L) },
            scores = scores,
            scholarGroups = scholarGroups,
            scholarCourses = scholarCourses,
            curriculum = curriculum,
            textbooks = textbooks,
            notes = notes,
            homework = homework,
            semester = semester,
            slots = slots,
            overrides = overrides,
        ),
    )
}
