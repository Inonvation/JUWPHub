package edu.jxslu.schedule.domain

/**
 * 文件体积文案：`B` / `KB` / `MB`（成绩单最近导出、地图缓存共用）。
 *
 * 小数点固定为 `.`（[TextFmt.decimals] 走 `Locale.ROOT`），避免某些区域把「1.5 MB」写成「1,5 MB」。
 */
object SizeText {

    fun format(bytes: Long): String = when {
        bytes >= 1L shl 20 -> TextFmt.decimals(bytes / 1024.0 / 1024.0, 1) + " MB"
        bytes >= 1024L -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
