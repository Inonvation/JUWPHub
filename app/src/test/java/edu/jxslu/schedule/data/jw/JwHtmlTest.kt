package edu.jxslu.schedule.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JwHtmlTest {

    @Test
    fun scopedSelectIgnoresOtherSelects() {
        val html = """
            <select id="other"><option value="2024-2025-1" selected>2024-2025-1</option></select>
            <select id="xnxq01id"><option value="2026-2027-1" selected>2026-2027-1</option></select>
        """.trimIndent()
        assertEquals("2026-2027-1", JwHtml.selectedTerm(html, "xnxq01id"))
    }

    @Test
    fun textFallbackWhenValueMissing() {
        val html = """<select id="xnxqid"><option selected>2026-2027-2</option></select>"""
        assertEquals("2026-2027-2", JwHtml.selectedTerm(html, "xnxqid"))
    }

    @Test
    fun textFallbackWhenValueInvalid() {
        // 强智个别壳页 value 是内部 id，文本才是学期号
        val html = """<select id="xnxqid"><option value="0" selected>2026-2027-1</option></select>"""
        assertEquals("2026-2027-1", JwHtml.selectedTerm(html, "xnxqid"))
    }

    @Test
    fun invalidTermRejectedWhenPatternRequired() {
        val html = """<select id="xnxqid"><option value="not-a-term" selected>not-a-term</option></select>"""
        assertNull(JwHtml.selectedTerm(html, "xnxqid"))
        assertEquals("not-a-term", JwHtml.selectedTerm(html, "xnxqid", requireTermPattern = false))
    }

    @Test
    fun fullPageFallbackForSingleSelectShells() {
        val html = """<select><option value="2025-2026-2" selected>2025-2026-2</option></select>"""
        assertEquals("2025-2026-2", JwHtml.selectedTerm(html, selectId = null))
    }

    /** 归档链的候选学期：下拉全量、按学期升序、非法值丢弃（实测教务降序输出 12 项）。 */
    @Test
    fun termOptionsSortedAscendingAndFiltered() {
        val html = """
            <select id="xnxq01id">
              <option value="2026-2027-2">2026-2027-2</option>
              <option value="2026-2027-1" selected>2026-2027-1</option>
              <option value="0">请选择</option>
              <option value="2024-2025-1">2024-2025-1</option>
            </select>
        """.trimIndent()
        assertEquals(
            listOf("2024-2025-1", "2026-2027-1", "2026-2027-2"),
            JwHtml.termOptions(html, "xnxq01id"),
        )
    }

    /** 有下拉但没选中项 = 教务没开放该学期（探测链据此判「未开放」，不是结构异常）。 */
    @Test
    fun selectWithoutSelectedOptionIsStillPresent() {
        val html = """<select id="xnxq01id"><option value="2019-2020-1">2019-2020-1</option></select>"""
        assertNull(JwHtml.selectedTerm(html, "xnxq01id"))
        assertEquals(listOf("2019-2020-1"), JwHtml.termOptions(html, "xnxq01id"))
        assertEquals(true, JwHtml.hasTermSelect(html, "xnxq01id"))
        assertEquals(false, JwHtml.hasTermSelect(html, "nope"))
    }
}
