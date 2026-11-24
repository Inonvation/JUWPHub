package edu.jxslu.schedule.ui.homework

import android.content.Context
import android.content.Intent
import android.net.Uri
import edu.jxslu.schedule.startActivityOutsideApp

/**
 * 详情里打开外链的兜底（iOS 移植 V4，2026-11-25）。
 *
 * 作业详情页已搬进 `shared/commonMain`，那里拿不到 `Intent`；正文里的链接改由宿主注入
 * （共享页的 `onOpenLink: ((String) -> Boolean)?`，返回是否成功，失败由页面给一句提示）。
 * 作业库 / 课程页 / 作业中心三处也在用同一份，所以它留在 `:app` 的这个包里，
 * 三处与详情的 import 都不用改。
 */
internal fun openLink(context: Context, url: String): Boolean =
    runCatching {
        context.startActivityOutsideApp(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.isSuccess
