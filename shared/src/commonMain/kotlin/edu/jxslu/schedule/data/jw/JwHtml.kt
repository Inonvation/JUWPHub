package edu.jxslu.schedule.data.jw

import edu.jxslu.schedule.domain.TermNaming

/**
 * 教务壳页 HTML 的学期下拉解析（考试 / 课表 / 选课 / 教学日历共用）。
 *
 * 优先按 `id` / `name` 收窄到指定 select，避免整页多个下拉时串值；
 * `selectId = null` 只用于确认页面上只有一个 select 的壳页。
 * value 属性优先、option 文本兜底；`requireTermPattern` 时用 [JwUrls.TERM_PATTERN] 校验。
 *
 * public 而非 internal：本层已搬进 `:shared`，而 `ExamSync` / `ScheduleSync` /
 * `ScheduleTermImport` 留在 `:app`，跨模块看不见 internal。
 */
object JwHtml {

    fun selectedTerm(
        html: String,
        selectId: String? = null,
        requireTermPattern: Boolean = true,
    ): String? {
        val scope = selectScope(html, selectId) ?: return null
        for (option in OPTION_RE.findAll(scope)) {
            val attrs = option.groupValues[1]
            if (!attrs.contains("selected", ignoreCase = true)) continue
            // value 优先、文本兜底：value 存在但格式不对时也要再试文本（旧调用点的口径）
            val text = option.groupValues[2].trim().takeIf { it.isNotEmpty() }
            for (candidate in listOfNotNull(valueOf(attrs), text).distinct()) {
                if (!requireTermPattern || JwUrls.TERM_PATTERN.matches(candidate)) {
                    return candidate
                }
            }
        }
        return null
    }

    /**
     * 学期下拉的**全部**选项（学期号升序，非法值丢弃）。
     *
     * 归档链（DESIGN §4.17）靠它拿候选学期：下拉由教务按学籍生成，实测 12 项、
     * 起点就是入学学年，所以「下拉里最早的一项」天然等于大一上，不必用学号推入学年。
     * 没有下拉 / 全是不合法值时返回空列表，调用方按「拿不到学期清单」报失败。
     */
    fun termOptions(html: String, selectId: String? = null): List<String> {
        val scope = selectScope(html, selectId) ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (option in OPTION_RE.findAll(scope)) {
            val text = option.groupValues[2].trim().takeIf { it.isNotEmpty() }
            for (candidate in listOfNotNull(valueOf(option.groupValues[1]), text).distinct()) {
                if (JwUrls.TERM_PATTERN.matches(candidate)) {
                    out += candidate
                    break
                }
            }
        }
        return TermNaming.sortedAscending(out)
    }

    /** 页面上的学期下拉在不在（有下拉但没选中项 = 教务没开放该学期，见归档探测）。 */
    fun hasTermSelect(html: String, selectId: String? = null): Boolean =
        selectScope(html, selectId) != null

    private fun selectScope(html: String, selectId: String?): String? {
        if (selectId == null) return html
        val selectRe = Regex(
            """(?is)<select[^>]*(?:id|name)\s*=\s*["']?${Regex.escape(selectId)}["']?[^>]*>([\s\S]*?)</select>""",
        )
        return selectRe.find(html)?.groupValues?.getOrNull(1)
    }

    private fun valueOf(attrs: String): String? =
        Regex("""value\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(attrs)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private val OPTION_RE = Regex("""(?is)<option\b([^>]*)>([\s\S]*?)</option>""")
}
