package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.domain.Homework
import edu.jxslu.schedule.domain.Note
import edu.jxslu.schedule.domain.OverrideKind
import edu.jxslu.schedule.domain.ScheduleOverride
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidationTest {

    private fun course(name: String = "高等数学") = CourseJson(
        name = name,
        day = 1,
        startSection = 1,
        endSection = 2,
        weeks = listOf(1, 2, 3),
    )

    private fun valid() = CourseExport(courses = listOf(course()))

    @Test
    fun validBackupPasses() {
        assertTrue(validateBackup(valid()) is BackupCheck.Ok)
    }

    @Test
    fun emptyCoursesRejected() {
        assertTrue(validateBackup(CourseExport()) is BackupCheck.Error)
    }

    @Test
    fun scoreMissingTermRejected() {
        val export = valid().copy(scores = listOf(ScoreBackupJson(name = "高等数学")))
        assertTrue(validateBackup(export) is BackupCheck.Error)
    }

    @Test
    fun textbookMissingTitleRejected() {
        val export = valid().copy(
            textbooks = listOf(TextbookBackupJson(term = "2026-2027-1", courseName = "高等数学")),
        )
        assertTrue(validateBackup(export) is BackupCheck.Error)
    }

    @Test
    fun invalidSemesterDateRejected() {
        val export = valid().copy(semester = SemesterBackupJson(startDate = "not-a-date"))
        assertTrue(validateBackup(export) is BackupCheck.Error)
    }

    /**
     * 节次上界必须挡住（2026-11-06 复核）：`endSection = 999` 能通过旧的「≥1 且不小于 start」
     * 校验，落库后在课表网格里因为「行号只画到作息表的节数」而**静默不可见**——用户以为
     * 备份导进来了，其实那门课永远看不见。
     *
     * 上界取 40 而不是作息表的 11 节：作息表可自定义（DESIGN §3.5），一天排 12+ 节是合法的。
     */
    @Test
    fun oversizedEndSectionRejected() {
        val export = valid().copy(courses = listOf(course().copy(startSection = 41, endSection = 999)))
        assertTrue("endSection 超界必须拒绝", validateBackup(export) is BackupCheck.Error)
        // 40 节以内仍然放行（自定义作息可能比默认 11 节多）
        val ok = valid().copy(courses = listOf(course().copy(startSection = 12, endSection = 40)))
        assertTrue("12–40 节是合法自定义作息", validateBackup(ok) is BackupCheck.Ok)
    }

    /**
     * 备份里的课程行 id 必须归零（2026-11-06 审计 D1）：原样写回库会撞上本机**其它课表**的行，
     * `INSERT OR REPLACE` 会删掉那行重插并把 timetableId 改成导入目标 → 另一张课表静默少课。
     * 单课表 + 覆盖导入因清表恰好不复现，所以这条只能靠断言钉住。
     */
    @Test
    fun courseIdsAreZeroed() {
        val export = valid().copy(
            courses = listOf(course().copy(id = 7), course("线性代数").copy(id = 9)),
        )
        val check = validateBackup(export)
        assertTrue("应通过校验：$check", check is BackupCheck.Ok)
        val courses = (check as BackupCheck.Ok).value.courses
        assertTrue("导入链必须归零行 id，实际：${courses.map { it.id }}", courses.all { it.id == 0L })
        // 归零不能连带丢掉其它字段
        assertTrue(courses.any { it.name == "线性代数" })
    }

    @Test
    fun invalidSlotRejected() {
        val export = valid().copy(
            timeSlots = listOf(TimeSlotBackupJson(number = 1, startTime = "25:00", endTime = "26:00")),
        )
        assertTrue(validateBackup(export) is BackupCheck.Error)
    }

    // ---- 笔记 / 作业 / 日期覆盖备份段（2026-11-06 审计 D2）----

    private val note = Note(
        id = 42, // 故意带 id：备份不许把它写出去（跨设备没有意义，且会撞本机行）
        courseName = "高等数学",
        title = "第一章 极限",
        body = "## 定义\n![](img:20261106_101500_ab12.jpg)",
        createdAt = 1_760_000_000_000,
        updatedAt = 1_760_000_100_000,
    )

    private val homework = Homework(
        id = 7,
        courseName = "高等数学",
        detail = "- [ ] 习题 1.1\n第二行正文",
        dueDate = LocalDate.of(2026, 11, 9),
        done = false,
        doneAt = null,
        createdAt = 1_760_000_000_000,
        updatedAt = 1_760_000_050_000,
    )

    private val dateOverride = ScheduleOverride(
        id = 3,
        fromDate = "2026-11-08",
        toDate = "2026-11-08",
        kind = OverrideKind.FOLLOW,
        sourceWeek = 5,
        sourceWeekday = 1,
        note = "周日补第五周周一的课",
    )

    /**
     * 三段用户自写数据导出→解码→校验后逐字段还在，且 id 归零。
     *
     * 走真实 `CourseJsonFormat` 序列化再解码（不是直接拿对象），这样字段改名/类型改动、
     * `encodeDefaults` 被关掉都会红——只断言对象相等挡不住落盘格式漂移。
     */
    @Test
    fun userDataSegmentsRoundTrip() {
        val export = valid().copy(
            notes = listOf(note.toBackupJson()),
            homework = listOf(homework.toBackupJson()),
            dateOverrides = listOf(dateOverride.toBackupJson()),
        )
        val text = CourseJsonFormat.encodeToString(CourseExport.serializer(), export)
        val back = CourseJsonFormat.decodeFromString(CourseExport.serializer(), text)
        val check = validateBackup(back)
        assertTrue("应通过校验：$check", check is BackupCheck.Ok)
        val value = (check as BackupCheck.Ok).value
        assertEquals(listOf(note.copy(id = 0)), value.notes)
        assertEquals(listOf(homework.copy(id = 0)), value.homework)
        assertEquals(listOf(dateOverride.copy(id = 0)), value.overrides)
    }

    /** 三段都不能把行 id 写进备份：源设备主键跨设备无意义，写回去会撞本机其它课表的行（同 D1）。 */
    @Test
    fun userDataSegmentsCarryNoRowId() {
        val text = CourseJsonFormat.encodeToString(
            CourseExport.serializer(),
            valid().copy(
                notes = listOf(note.toBackupJson()),
                homework = listOf(homework.toBackupJson()),
                dateOverrides = listOf(dateOverride.toBackupJson()),
            ),
        )
        val root = CourseJsonFormat.parseToJsonElement(text) as JsonObject
        for (key in listOf("notes", "homework", "dateOverrides")) {
            val first = (root[key] as JsonArray).first() as JsonObject
            assertFalse("$key 段不能带行 id", first.containsKey("id"))
        }
    }

    /**
     * 空段契约：文件不带这三段时，校验产物必须是空列表——写库侧（`importJson`）据此跳过，
     * 「空段不清库」由那条 `isNotEmpty()` 守卫落地。这里钉住的是校验层交出去的东西，
     * 一旦有人把空段也塞成「空对象」或改成 null 列表，写库侧的守卫就会失效。
     */
    @Test
    fun emptyUserDataSegmentsStayEmpty() {
        val check = validateBackup(valid())
        assertTrue(check is BackupCheck.Ok)
        val value = (check as BackupCheck.Ok).value
        assertTrue(value.notes.isEmpty())
        assertTrue(value.homework.isEmpty())
        assertTrue(value.overrides.isEmpty())
    }

    /** 旧备份（本版之前导出，没有 notes/homework/dateOverrides 三个键）仍能导入，其它段不受影响。 */
    @Test
    fun legacyBackupWithoutUserDataSegmentsStillImports() {
        val legacy = """
            {"courses":[{"name":"高数","day":1,"startSection":1,"endSection":2,"weeks":[1]}],
             "scores":[{"term":"2025-2026-2","name":"高数","scoreStr":"90"}]}
        """.trimIndent()
        val export = CourseJsonFormat.decodeFromString(CourseExport.serializer(), legacy)
        assertTrue("旧文件没有这三段 → 空列表", export.notes.isEmpty() && export.homework.isEmpty())
        assertTrue(export.dateOverrides.isEmpty())
        val check = validateBackup(export)
        assertTrue("旧备份必须仍能导入：$check", check is BackupCheck.Ok)
        assertEquals(1, (check as BackupCheck.Ok).value.scores.size)
    }

    /** 认不出的覆盖类型必须拒整个文件：猜成 SUSPENDED 会把课静默停掉。 */
    @Test
    fun unknownOverrideKindRejected() {
        val export = valid().copy(
            dateOverrides = listOf(
                DateOverrideBackupJson(fromDate = "2026-11-08", toDate = "2026-11-08", kind = "WHATEVER"),
            ),
        )
        assertTrue(validateBackup(export) is BackupCheck.Error)
    }

    /** FOLLOW 缺源槽位 = 不知道该上哪天的课，拒。 */
    @Test
    fun followOverrideWithoutSourceSlotRejected() {
        val export = valid().copy(
            dateOverrides = listOf(
                DateOverrideBackupJson(fromDate = "2026-11-08", toDate = "2026-11-08", kind = "FOLLOW"),
            ),
        )
        assertTrue(validateBackup(export) is BackupCheck.Error)
    }

    /** 结束日期早于起始日期 = 脏数据（多半是手改坏了），拒而不是默默接受一个空区间。 */
    @Test
    fun reversedOverrideRangeRejected() {
        val export = valid().copy(
            dateOverrides = listOf(
                DateOverrideBackupJson(
                    fromDate = "2026-11-09",
                    toDate = "2026-11-08",
                    kind = "SUSPENDED",
                ),
            ),
        )
        assertTrue(validateBackup(export) is BackupCheck.Error)
    }

    /** 作业截止日期坏值要拒：落成 null 等于把用户的截止日期悄悄抹掉。 */
    @Test
    fun homeworkWithBadDueDateRejected() {
        val export = valid().copy(
            homework = listOf(
                HomeworkBackupJson(courseName = "高等数学", detail = "习题 1.1", dueDate = "11月9日"),
            ),
        )
        assertTrue(validateBackup(export) is BackupCheck.Error)
    }

    /** 空笔记/空作业写进去也没人看得到，直接在信任边界拒掉（与 day=9 同一条纪律）。 */
    @Test
    fun blankNoteAndHomeworkRejected() {
        assertTrue(
            validateBackup(valid().copy(notes = listOf(NoteBackupJson(courseName = "")))) is BackupCheck.Error,
        )
        assertTrue(
            validateBackup(
                valid().copy(notes = listOf(NoteBackupJson(courseName = "高数"))),
            ) is BackupCheck.Error,
        )
        assertTrue(
            validateBackup(
                valid().copy(homework = listOf(HomeworkBackupJson(courseName = "高数", detail = " "))),
            ) is BackupCheck.Error,
        )
    }
}
