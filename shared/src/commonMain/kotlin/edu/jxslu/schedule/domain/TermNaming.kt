package edu.jxslu.schedule.domain

/**
 * 学期号（`2026-2027-1`）的推算与命名（DESIGN §4.17）。
 *
 * 存在的理由：归档要把教务的学期号变成用户看得懂的「大一上 / 大二下」，而**不能按
 * 导入序号数**——休学一学期时学期号仍然连续（学校按学年编），序号会整体错位一位。
 * 推算只用学期号本身：`(学年差 × 2 + 学期位差) + 1`。
 *
 * 基准学期（大一上）由调用方给：首选用教务学期下拉里最早的那一项（下拉按学籍生成，
 * 起点就是入学学期），拿不到时用库里已有课表的最小 term。基准本身落在 `-2` 时按
 * 「大一下」起算——学生显然已经上过大一上，只是库里没有那份数据。
 *
 * 纯 JVM，无 Android 依赖；`TermNamingTest` 钉住跨学年、休学与非法输入。
 */
object TermNaming {

    private val PATTERN = Regex("""(\d{4})-(\d{4})-([12])""")

    /** 本科最长按 4 年算；超出（专升本 / 留级）不猜年级，调用方退回学期号当名字。 */
    private const val MAX_GRADE_INDEX = 8

    private val GRADE_WORDS = listOf("大一", "大二", "大三", "大四")

    /** 学期号拆解结果。[half] 是 1（上）或 2（下）。 */
    data class Parsed(val startYear: Int, val endYear: Int, val half: Int)

    fun parse(term: String?): Parsed? {
        val m = PATTERN.matchEntire(term?.trim().orEmpty()) ?: return null
        val start = m.groupValues[1].toIntOrNull() ?: return null
        val end = m.groupValues[2].toIntOrNull() ?: return null
        // 学年必须连续（2026-2027），否则是脏值；half 由正则限定为 1/2
        if (end != start + 1) return null
        return Parsed(start, end, m.groupValues[3].toInt())
    }

    /** 下一个学期：`2026-2027-1` → `2026-2027-2`，`2026-2027-2` → `2027-2028-1`。 */
    fun next(term: String?): String? {
        val p = parse(term) ?: return null
        return if (p.half == 1) {
            "${p.startYear}-${p.endYear}-2"
        } else {
            "${p.startYear + 1}-${p.endYear + 1}-1"
        }
    }

    /** 上一个学期（与 [next] 对称，归档从当前学期往前回溯时用）。 */
    fun previous(term: String?): String? {
        val p = parse(term) ?: return null
        return if (p.half == 2) {
            "${p.startYear}-${p.endYear}-1"
        } else {
            "${p.startYear - 1}-${p.endYear - 1}-2"
        }
    }

    /** 排序键：学年序 × 2 + 学期位。非法学期返回 null（调用方丢掉，不猜位置）。 */
    fun sortKey(term: String?): Int? {
        val p = parse(term) ?: return null
        return p.startYear * 2 + (p.half - 1)
    }

    /** 学期号升序（旧 → 新）；非法项被丢弃。 */
    fun sortedAscending(terms: Iterable<String>): List<String> =
        terms.mapNotNull { t -> sortKey(t)?.let { it to t } }
            .sortedBy { it.first }
            .map { it.second }

    /**
     * 以 [firstTerm] 为大一上时的学期序号（1 = 大一上 … 8 = 大四下）。
     *
     * [firstTerm] 落在 `-2` 时基准记作大一下（序号 2）。早于基准学期或任一侧无法
     * 解析都返回 null——宁可让调用方用学期号当名字，也不猜。
     */
    fun gradeIndex(term: String?, firstTerm: String?): Int? {
        val t = parse(term) ?: return null
        val f = parse(firstTerm) ?: return null
        val base = if (f.half == 1) 0 else 1
        val index = (t.startYear - f.startYear) * 2 + (t.half - f.half) + 1 + base
        return index.takeIf { it >= 1 && it <= MAX_GRADE_INDEX }
    }

    /**
     * 「大一上」这类年级标签；推不出来返回 null（调用方退回学期号）。
     *
     * 超过本科 4 年的学期（留级 / 专升本）同样返回 null：写「大五上」比写
     * `2028-2029-1` 更容易让人以为 App 算错了。
     */
    fun gradeLabel(term: String?, firstTerm: String?): String? {
        val index = gradeIndex(term, firstTerm) ?: return null
        val grade = GRADE_WORDS.getOrNull((index - 1) / 2) ?: return null
        return grade + if (index % 2 == 1) "上" else "下"
    }

    /**
     * 课表显示名：能推年级就用「大二下」，否则用学期号本身。
     * 调用方负责重名去重（见 `ScheduleTermImport.uniqueName`）。
     */
    fun timetableName(term: String, firstTerm: String?): String =
        gradeLabel(term, firstTerm) ?: term
}
