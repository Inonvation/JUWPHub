package edu.jxslu.schedule.domain

/**
 * RFC 3986 §5.2「相对引用解析」的纯 Kotlin 实现，等价于 okhttp 的 `HttpUrl.resolve`。
 *
 * 只为一件事存在：`domain/` 要能进 iOS（Kotlin Multiplatform）产物，而原先做这件事的是
 * okhttp 的 `HttpUrl.resolve`——JVM 专有库，iOS 侧没有。
 * 容错口径与它一致：base 不是一个能解析的绝对 http(s) URL 时返回 null，参考串解析不出来
 * 也返回 null，**不抛异常**（调用方 `parseArticleLinks` / `parseImageUrl` 依赖这一点）。
 *
 * 覆盖面 = 本项目实际会遇到的 5 类形态：
 * 1. 绝对 URL（`https://a/b.htm`）——原样返回，不重排、不规范化；
 * 2. 协议相对（`//host/path`）——继承 base 的 scheme；
 * 3. 绝对路径（`/__local/x.png`）——替换 base 的路径；
 * 4. 相对路径（`../info/1040/10376.htm`、`a/b.htm`）——§5.2.3 合并 + §5.2.4 消 dot-segment；
 * 5. 只带 query / fragment / 空串（`?q=1`、`#frag`、空串）。
 *
 * **只覆盖这 5 类，不是通用 URI 实现**：不解析 query / fragment 内部结构、不做百分号编解码、
 * 不做 host 小写 / 默认端口 / 重复斜杠之类的规范化。因此个别畸形输入的结果可能与 okhttp
 * 逐字不同（`UrlRefTest` 里钉住的就是本项目在用的那几条），取数链路只用到「能点开的绝对地址」。
 */
object UrlRef {

    /** `scheme:` 前缀；字符集按 RFC 3986 §3.1。 */
    private val SCHEME_RE = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):""")

    /**
     * 把 [reference] 按 [base] 解析成绝对 URL；解析不出来返回 null。
     *
     * @param base 绝对 URL（`https://jw.juwp.edu.cn/xxgk/tzgg.htm`）。空串、缺 scheme、
     *   非 http(s) 协议、没有 host 的一律返回 null。
     * @param reference 页面里的 href / src，可以是上面 5 类形态中的任意一种。
     */
    fun resolve(base: String, reference: String): String? {
        val origin = parseAbsolute(base) ?: return null

        // ① 参考串自带 scheme = 绝对 URL，原样返回。§5.2.2 还会对它跑一遍 dot-segment，
        //    这里跳过：调用方要的就是页面里写的那个地址，重排反而可能改动真实链接。
        //    协议范围与 base 保持一致（okhttp 也只收 http/https）。
        val refScheme = SCHEME_RE.find(reference)?.groupValues?.get(1)?.lowercase()
        if (refScheme != null) {
            return if (refScheme == "http" || refScheme == "https") reference else null
        }

        // ② 先切 fragment、再切 query，剩下的当路径（`?` / `#` 之后不再细分）。
        val hash = reference.indexOf('#')
        val fragment = if (hash >= 0) reference.substring(hash + 1) else null
        val withoutFragment = if (hash >= 0) reference.substring(0, hash) else reference
        val mark = withoutFragment.indexOf('?')
        val query = if (mark >= 0) withoutFragment.substring(mark + 1) else null
        var path = if (mark >= 0) withoutFragment.substring(0, mark) else withoutFragment

        // ③ `//` 开头 = 参考串自带 authority，其余形态沿用 base 的。
        val authorityFromRef = path.startsWith("//")
        val authority: String
        if (authorityFromRef) {
            val end = path.indexOf('/', startIndex = 2).let { if (it < 0) path.length else it }
            authority = path.substring(2, end)
            if (authority.isEmpty()) return null // `//` 后面没有 host，同 okhttp
            path = path.substring(end)
        } else {
            authority = origin.authority
        }

        // ④ §5.2.2：目标路径与 query。
        val targetPath: String
        val targetQuery: String?
        if (authorityFromRef) {
            // `//host` 形态：路径与 query 只取参考串的，不继承 base（`//g` 就是 `https://g`）
            targetPath = removeDotSegments(path)
            targetQuery = query
        } else if (path.isEmpty()) {
            // 空路径（`?q=1` / `#frag` / 空串）：路径沿用 base，query 只在参考串没给时才沿用。
            targetPath = origin.path
            targetQuery = query ?: origin.query
        } else {
            targetPath = removeDotSegments(if (path.startsWith("/")) path else merge(origin.path, path))
            targetQuery = query
        }

        return buildString {
            append(origin.scheme).append("://").append(authority).append(targetPath)
            if (targetQuery != null) append('?').append(targetQuery)
            if (fragment != null) append('#').append(fragment)
        }
    }

    /** 绝对 URL 的四个部件；path 为空或以 `/` 开头（否则就是没 authority，[parseAbsolute] 已挡掉）。 */
    private class Uri(
        val scheme: String,
        val authority: String,
        val path: String,
        val query: String?,
    )

    /**
     * 解析绝对 URL，只认 `http(s)://host...`。
     *
     * 不做规范化：scheme 统一小写（拼回去时用），authority / path / query 原样保留——
     * okhttp 会把 host 小写、去掉默认端口、把空路径补成 `/`，那几项对取数没有意义。
     */
    private fun parseAbsolute(url: String): Uri? {
        val match = SCHEME_RE.find(url) ?: return null
        val scheme = match.groupValues[1].lowercase()
        if (scheme != "http" && scheme != "https") return null
        val rest = url.substring(match.range.last + 1)
        if (!rest.startsWith("//")) return null
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#'), startIndex = 2)
            .let { if (it < 0) rest.length else it }
        val authority = rest.substring(2, authorityEnd)
        if (authority.isEmpty()) return null // `http:///a` 之类：没有 host
        val withoutFragment = rest.substring(authorityEnd).substringBefore('#')
        val mark = withoutFragment.indexOf('?')
        val path = if (mark >= 0) withoutFragment.substring(0, mark) else withoutFragment
        val query = if (mark >= 0) withoutFragment.substring(mark + 1) else null
        return Uri(scheme, authority, path, query)
    }

    /**
     * RFC 3986 §5.2.3 的 `merge`：相对路径接到 base 路径最后一个 `/` 之后。
     *
     * base 路径为空（`https://host` 这种写法）时从根开始；非空则必定以 `/` 开头，
     * 由 [parseAbsolute] 保证，所以 `lastIndexOf('/')` 一定命中。
     */
    private fun merge(basePath: String, refPath: String): String =
        if (basePath.isEmpty()) {
            "/$refPath"
        } else {
            basePath.substring(0, basePath.lastIndexOf('/') + 1) + refPath
        }

    /**
     * RFC 3986 §5.2.4 的 `remove_dot_segments`：按段消掉 `.` 与 `..`。
     *
     * 逐条照搬算法原文：不合并重复斜杠、不解码、`.` 段的判定只认「完整路径段」
     * （`g.` / `..g` 这类不是 dot-segment，原样留着）。
     */
    private fun removeDotSegments(path: String): String {
        val output = StringBuilder()
        var rest = path
        while (rest.isNotEmpty()) {
            rest = when {
                rest.startsWith("../") -> rest.substring(3)
                rest.startsWith("./") -> rest.substring(2)
                // "/./x" → "/x"：只吃掉 "/."，留着那个 "/" 给下一轮搬走
                rest.startsWith("/./") -> rest.substring(2)
                // 末尾的 "/." / "/.." 换成 "/"（RFC 原文如此，末尾斜杠要保住）
                rest == "/." -> "/"
                rest.startsWith("/../") -> {
                    dropLastSegment(output)
                    rest.substring(3)
                }
                rest == "/.." -> {
                    dropLastSegment(output)
                    "/"
                }
                rest == "." || rest == ".." -> ""
                else -> {
                    // 搬走首个路径段（有前导 `/` 时连它一起搬；没有则是整个首段）
                    val end = rest.indexOf('/', startIndex = if (rest[0] == '/') 1 else 0)
                    output.append(rest, 0, if (end < 0) rest.length else end)
                    if (end < 0) "" else rest.substring(end)
                }
            }
        }
        return output.toString()
    }

    /** 弹掉 [output] 末尾的最后一个路径段（含它前面的 `/`）。 */
    private fun dropLastSegment(output: StringBuilder) {
        val slash = output.lastIndexOf("/")
        if (slash >= 0) output.setLength(slash) else output.setLength(0)
    }
}
