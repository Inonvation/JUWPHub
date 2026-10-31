package edu.jxslu.schedule.ui.tile

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import edu.jxslu.schedule.SubpageRequest
import edu.jxslu.schedule.WaterAutoStart
import edu.jxslu.schedule.subpageLaunchIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 快捷设置磁贴基类（DESIGN §3.32 / §4.50）。
 *
 * 点击链路：磁贴 → 本地就绪判定 →（开水磁贴）一次性令牌 → [subpageLaunchIntent]
 * → MainActivity 跳板 → SubpageActivity。**不直接起 SubpageActivity**：那会让二级页
 * 成为新 task 的根（按返回退桌面、之后再点桌面图标另开一个 task），理由见
 * `SubpageStack.kt` 里 `subpageLaunchIntent` 的 KDoc。
 *
 * 状态：`onStartListening` 每次下拉通知栏都会触发，在那里异步读本地凭证 / 会话并
 * 刷新磁贴底色；`onClick` 只用缓存值，避免点下去才卡一下。**不读网络**，也不在
 * 主线程读 EncryptedSharedPreferences。
 */
abstract class JuwTileService : TileService() {

    protected abstract val kind: TileKind

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    @Volatile
    private var cached: TileAction? = null

    override fun onStartListening() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val action = withContext(Dispatchers.IO) {
                TileTargets.action(kind, readTileSignals(applicationContext, kind))
            }
            cached = action
            val tile = qsTile ?: return@launch
            // 三个磁贴都是纯动作入口，底色一律 INACTIVE（不表示凭证 / 登录状态）。
            // 不用 UNAVAILABLE——灰色仍可点，用户才到得了配置 / 登录页。
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        // onStartListening 一定先于点击发生；缓存缺失只可能是极快的首次点击。
        // 这时同步补读一次本地状态（毫秒级），宁可慢一点也不要落错页。
        val action = cached
            ?: TileTargets.action(kind, readTileSignals(applicationContext, kind))
        // 自动开水只在磁贴被点击时武装；一次性令牌由 SubpageActivity 首帧消费，
        // 窗口链恢复不会重放（否则会在用户不知情时开阀计费）。
        if (action.autoStart) WaterAutoStart.arm(action.request.screen)
        startActivityAndCollapseCompat(action.request)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startActivityAndCollapseCompat(request: SubpageRequest) {
        val intent = subpageLaunchIntent(applicationContext, request)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 34+：Intent 重载已废弃，targetSdk 34+ 在后台调用会抛异常。
            // 每个磁贴用不同 requestCode —— PendingIntent 匹配不看 extras，
            // 三个磁贴共用 requestCode 会互相顶掉，点哪个都开同一页。
            val pending = PendingIntent.getActivity(
                applicationContext,
                kind.ordinal + 1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
