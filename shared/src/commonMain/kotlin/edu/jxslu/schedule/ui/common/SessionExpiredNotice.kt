package edu.jxslu.schedule.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import edu.jxslu.schedule.data.session.SESSION_EXPIRED_NOTICE
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.platform.Services

/**
 * 进页提示：该功能在别处已判定登录过期时，给一句「登录状态已过期，请重新登录」。
 *
 * 标记由各仓库在**判定失效的那一刻**写入（`SessionExpirySink.mark`），这里只负责消费：
 * - 未登录且标记在 → 提示一次并取走标记（同一轮不重复弹）；
 * - 已登录（刚重登成功）→ 顺手清掉旧标记。
 *
 * **只在功能自己的页面调用**，不要在今日页的卡片里调：那会让「进这个界面」变成
 * 「一打开 App 就弹各家的过期提示」。用户主动退出登录走各仓库的 `logout()`，标记已清，
 * 不会误报。
 *
 * 依赖从 `Services.current.sessionExpirySink()` 取（与仓库层写的是同一份标记）；
 * 装配根还没装时静默跳过——本函数在 5 个功能页里都是纯副作用，不该因为测试环境
 * 没有装配根而炸掉整个页面。
 */
@Composable
fun SessionExpiredNotice(
    service: SessionService,
    loggedIn: Boolean,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(loggedIn) {
        val sink = runCatching { Services.current.sessionExpirySink() }.getOrNull()
            ?: return@LaunchedEffect
        if (loggedIn) {
            sink.clear(service.id)
        } else if (sink.consume(service.id)) {
            scope.postNotice(snackbar, SESSION_EXPIRED_NOTICE, NoticeTone.Warning)
        }
    }
}
