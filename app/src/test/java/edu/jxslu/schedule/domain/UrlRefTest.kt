package edu.jxslu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RFC 3986 §5.2 相对引用解析（[UrlRef]）的钉子——原先由 okhttp 的 `HttpUrl.resolve` 承担，
 * 摘掉 okhttp3 后语义不能变：
 * ① §5.4.1 的经典相对引用（base = `http://a/b/c/d;p?q`）；
 * ② 教务处官网列表页 href / 文章页 orisrc 里真实出现的两种形态；
 * ③ base 解析不出来时返回 null（[CampusCalendarSchoolRules] 的容错口径依赖它）。
 */
class UrlRefTest {

    /** RFC 3986 §5.4.1 的范例 base。 */
    private val base = "http://a/b/c/d;p?q"

    private fun resolve(reference: String): String? = UrlRef.resolve(base, reference)

    @Test
    fun `rfc 3986 normal relative references`() {
        assertEquals("http://a/b/c/g", resolve("g"))
        assertEquals("http://a/b/c/g", resolve("./g"))
        assertEquals("http://a/b/c/g/", resolve("g/"))
        assertEquals("http://a/g", resolve("/g"))
        assertEquals("http://a/b/g", resolve("../g"))
        assertEquals("http://a/g", resolve("../../g"))
        // 越过根的 .. 不许爆栈/报错：夹到根为止
        assertEquals("http://a/g", resolve("../../../g"))
    }

    @Test
    fun `rfc 3986 dot segments authority query and fragment`() {
        assertEquals("http://a/b/", resolve(".."))
        assertEquals("http://a/b/", resolve("../"))
        assertEquals("http://a/", resolve("../.."))
        assertEquals("http://g", resolve("//g"))
        assertEquals("http://a/b/c/d;p?y", resolve("?y"))
        assertEquals("http://a/b/c/g?y", resolve("g?y"))
        assertEquals("http://a/b/c/d;p?q#s", resolve("#s"))
        assertEquals("http://a/b/c/g#s", resolve("g#s"))
        assertEquals("http://a/b/c/d;p?q", resolve(""))
    }

    @Test
    fun `rfc 3986 abnormal examples keep the containing directory`() {
        assertEquals("http://a/b/c/g/", resolve("./g/."))
        assertEquals("http://a/b/c/g/h", resolve("g/./h"))
        assertEquals("http://a/b/c/h", resolve("g/../h"))
        // query / fragment 里出现 ".." 不许被当 dot-segment 消掉
        assertEquals("http://a/b/c/g?y/../x", resolve("g?y/../x"))
        assertEquals("http://a/b/c/g#s/../x", resolve("g#s/../x"))
    }

    @Test
    fun `five shapes the project relies on`() {
        // 绝对 URL：原样返回，不重排
        assertEquals("https://a/b.htm", UrlRef.resolve(base, "https://a/b.htm"))
        assertEquals("http://a/b.htm", UrlRef.resolve(base, "http://a/b.htm"))
        // 协议相对：继承 base 的 scheme
        assertEquals("http://host/path", UrlRef.resolve(base, "//host/path"))
        // 绝对路径：替换路径，丢掉 base 的 query
        assertEquals("http://a/__local/x.png", UrlRef.resolve(base, "/__local/x.png"))
        // 相对路径：接到 base 目录之后
        assertEquals("http://a/b/c/a/b.htm", UrlRef.resolve(base, "a/b.htm"))
        // 只带 query / fragment / 空串
        assertEquals("http://a/b/c/d;p?q=1", UrlRef.resolve(base, "?q=1"))
        assertEquals("http://a/b/c/d;p?q#frag", UrlRef.resolve(base, "#frag"))
        assertEquals("http://a/b/c/d;p?q", UrlRef.resolve(base, ""))
    }

    @Test
    fun `resolves the real school site shapes`() {
        // 列表页 href="../info/1040/10376.htm" 必须回到站根再进 info（fixture 见 CampusCalendarSchoolRulesTest）
        assertEquals(
            "https://jw.juwp.edu.cn/info/1040/10376.htm",
            UrlRef.resolve("https://jw.juwp.edu.cn/xxgk/tzgg.htm", "../info/1040/10376.htm"),
        )
        // 文章页 orisrc / src 都是站根绝对路径
        assertEquals(
            "https://jw.juwp.edu.cn/__local/abc.png",
            UrlRef.resolve("https://jw.juwp.edu.cn/info/1040/10376.htm", "/__local/abc.png"),
        )
        // BASE 本身没有路径时也按站根合并
        assertEquals(
            "https://jw.juwp.edu.cn/xxgk/tzgg.htm",
            UrlRef.resolve("https://jw.juwp.edu.cn", "xxgk/tzgg.htm"),
        )
    }

    @Test
    fun `unparsable base or foreign scheme returns null`() {
        assertNull(UrlRef.resolve("", "a.htm"))
        assertNull(UrlRef.resolve("jw.juwp.edu.cn/xxgk/tzgg.htm", "../info/1040/10376.htm"))
        assertNull(UrlRef.resolve("not a url", "a.htm"))
        // 非 http(s) 的绝对引用：同 okhttp，不收
        assertNull(UrlRef.resolve("https://jw.juwp.edu.cn/info/1040/1.htm", "javascript:void(0)"))
    }
}
