package edu.jxslu.schedule.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.JwImportActivity
import edu.jxslu.schedule.SubpageActivity
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.repo.SelectionCenterClient
import edu.jxslu.schedule.ui.jwvw.JwImportMode
import edu.jxslu.schedule.ui.reminder.ScoreAlertReminder
import edu.jxslu.schedule.ui.reminder.SelectionAlertReminder

/**
 * 选课四页要宿主代劳的平台动作（iOS 移植 S13，2026-11-25）。
 *
 * ## 为什么在这一侧
 *
 * `ui/selection` 已整包搬进 `shared/commonMain`，那里没有 `Context` / `Activity`，也没有
 * `AlarmManager`（选课提醒是系统闹钟 + 通知）。按本仓已定的口径（见
 * `ui/week/WeekScreenPlatform.kt` / `ui/score/ScoreScreenPlatform.kt`），这些动作收成一组
 * 回调交给共享层；共享页面对 null 的处理是「入口整行不渲染」，所以这里的每个字段都必须是
 * **真的能执行**的，不要为了凑参数塞空 lambda。
 *
 * 与成绩页那组的差别：本组多了「直达某个轮次」（`focusItemId` 通道）与「教务网页核对」。
 */
class SelectionScreenActions(
    /** 进应用内选课中心；[roundId] 非空时直达该轮次（快照里认不出就退回自动挑选）。 */
    val openCenter: (roundId: String?) -> Unit,
    /** 已选课程（记录列表页）。 */
    val openRecords: () -> Unit,
    /** 预选清单。 */
    val openWishes: () -> Unit,
    /** 顶栏「在教务网页核对」：开教务网页窗口，只读浏览。 */
    val openJwWeb: () -> Unit,
    /** 「功能」区的教务网页备用入口：直接开「学生选课中心」列表。 */
    val openJwPage: () -> Unit,
    /**
     * 提醒开关 / 间隔改动后重排：清检查时刻 + 即时核对（[ScoreAlertReminder.onSettingsChanged]）
     * + 重排轮次闹钟（[SelectionAlertReminder.scheduleNext]）。后者要读一次轮次快照文件，
     * 所以是 suspend。
     */
    val alertsChanged: suspend () -> Unit,
)

/**
 * 组合期一次性取齐本组动作。
 *
 * `remember` 只按 `context` 记忆：闭包读的是 Activity 实例，换 Activity 就是换一套
 * （`SubpageActivity` 每次进页都是新实例，记忆键跟着换才不会拿旧实例去 startActivity）。
 */
@Composable
fun rememberSelectionScreenActions(): SelectionScreenActions {
    val context = LocalContext.current
    return remember(context) {
        SelectionScreenActions(
            openCenter = { roundId ->
                SubpageActivity.start(
                    context,
                    SubpageScreen.SELECTION_CENTER,
                    focusItemId = roundId,
                )
            },
            openRecords = { SubpageActivity.start(context, SubpageScreen.SELECTION_RECORDS) },
            openWishes = { SubpageActivity.start(context, SubpageScreen.SELECTION_WISHES) },
            openJwWeb = { JwImportActivity.startBrowse(context, "选课", JwUrls.SELECTION_CENTER) },
            openJwPage = { JwImportActivity.start(context, JwImportMode.Selection) },
            alertsChanged = {
                ScoreAlertReminder.onSettingsChanged(context)
                SelectionAlertReminder.scheduleNext(context)
            },
        )
    }
}

/**
 * 选课接口客户端（选课中心 / 预选清单共用）。
 *
 * 真身 [edu.jxslu.schedule.data.repo.SelectionCenterHttpClient] 还在 `:app`（OkHttp +
 * `CasSession` + debug 落盘），共享层的页面只认接口 [SelectionCenterClient]，所以由这里注入。
 * iOS 侧传 null（没有会话链），页面对 null 的处理是「说明一句，不画筛选面」。
 */
@Composable
fun rememberSelectionCenterClient(): SelectionCenterClient {
    val context = LocalContext.current
    return remember(context) { Graph.selectionCenterClient(context) }
}
