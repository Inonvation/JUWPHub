package edu.jxslu.schedule.ui.reminder

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.jxslu.schedule.data.qiekj.QiekjDailySignInResult
import edu.jxslu.schedule.domain.QiekjSignInNotice
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.postNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 胖乖「打开软件自动签到」本次真签到成功 → 主窗口底部提示卡（2026-10-24）。
 *
 * 自动签到挂在 `MainActivity.onResume` 的 `Graph.appScope` 协程里，而底部提示卡的
 * `SnackbarHostState` 活在壳层组合内，两头没有共同的引用——用进程内单例流转发
 * （同 [edu.jxslu.schedule.ui.campus.PayCodeResultBus] 的口径）：发布方只管发，
 * [QiekjSignInNoticeHost] 取值即消费再 `postNotice`。取值即消费防转屏重建后重弹
 * （同 2026-09-22 充值到账弹窗的教训）。
 *
 * 只报「本次真签到成功」；「今天已签」与失败都不发（失败下次打开还会重试，不制造噪音）。
 */
object QiekjSignInNoticeBus {

    private val _freshSignIn = MutableStateFlow<QiekjDailySignInResult?>(null)
    val freshSignIn: StateFlow<QiekjDailySignInResult?> = _freshSignIn.asStateFlow()

    fun publish(result: QiekjDailySignInResult) {
        _freshSignIn.value = result
    }

    fun consume() {
        _freshSignIn.value = null
    }
}

/**
 * 底部提示宿主：挂在壳层 `shellSnackbar` / `shellScope` 的所在处——与根层
 * `AppSnackbarHost` 同一条通道，抽屉盖着主卡、充值弹层开在侧栏上都看得见。
 */
@Composable
fun QiekjSignInNoticeHost(snackbar: SnackbarHostState, scope: CoroutineScope) {
    val fresh by QiekjSignInNoticeBus.freshSignIn.collectAsStateWithLifecycle()
    LaunchedEffect(fresh) {
        fresh?.let { result ->
            QiekjSignInNoticeBus.consume()
            scope.postNotice(
                snackbar,
                QiekjSignInNotice.text(result.gainedIntegral, result.totalIntegral),
                NoticeTone.Success,
            )
        }
    }
}
