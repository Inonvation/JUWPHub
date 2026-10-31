package edu.jxslu.schedule.ui.tile

import android.content.Context
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.SubpageRequest
import edu.jxslu.schedule.SubpageScreen

/** 快捷设置磁贴种类（DESIGN §3.32）。 */
enum class TileKind {
    /** 一卡通付款码：有凭证进付款码页，没有则进校园卡设置。 */
    PAY_CODE,

    /** 胖乖生活开水：已登录进页并自动开水，未登录只进登录表单。 */
    QIEKJ_WATER,

    /** 趣智校园开水：已登录进页并自动开阀，未登录只进登录表单。 */
    QZXY_WATER,
}

/** 点击磁贴前可同步拿到的本地状态（零网络）。 */
internal data class TileSignals(
    val campusCardConfigured: Boolean = false,
    val qiekjLoggedIn: Boolean = false,
    val qzxyLoggedIn: Boolean = false,
)

/**
 * 一次磁贴点击的落点。
 *
 * [ready] 是**落点判据**（有凭证进付款码页、没凭证进设置页；开水磁贴决定是否自动开），
 * 不影响可点性——未配置时点下去要能进配置 / 登录页，不能做成不可点的 UNAVAILABLE。
 *
 * 三个磁贴都是纯动作入口，**底色一律 INACTIVE**（2026-10-29 用户拍板）：磁贴不表示
 * 凭证 / 登录状态，只看 [ready] 决定点下去去哪。
 */
internal data class TileAction(
    val request: SubpageRequest,
    val autoStart: Boolean,
    val ready: Boolean,
)

/**
 * 磁贴种类 → 落点的纯映射（DESIGN §3.32 / §4.50）。
 *
 * 抽成纯函数是为了单测：状态判定（凭证 / 登录）在 [readTileSignals] 里读 Android
 * 单例，落点与自动开水的组合全在这里，不依赖 Service 生命周期。
 */
internal object TileTargets {

    fun action(kind: TileKind, signals: TileSignals): TileAction = when (kind) {
        TileKind.PAY_CODE -> TileAction(
            request = SubpageRequest(
                screen = if (signals.campusCardConfigured) {
                    SubpageScreen.PAY_CODE
                } else {
                    SubpageScreen.CAMPUS_CARD_SETTINGS
                },
            ),
            autoStart = false,
            ready = signals.campusCardConfigured,
        )

        TileKind.QIEKJ_WATER -> TileAction(
            request = SubpageRequest(SubpageScreen.WATER),
            autoStart = signals.qiekjLoggedIn,
            ready = signals.qiekjLoggedIn,
        )

        TileKind.QZXY_WATER -> TileAction(
            request = SubpageRequest(SubpageScreen.QZXY),
            autoStart = signals.qzxyLoggedIn,
            ready = signals.qzxyLoggedIn,
        )
    }
}

/**
 * 读磁贴所需的本地状态。**只碰本地存储**：凭证走 EncryptedSharedPreferences、
 * 登录态走仓库内存流 / 会话文件，不发起任何网络请求。
 *
 * 调用方负责放到 IO 线程（`JuwTileService.onStartListening` 的协程里）。
 */
internal fun readTileSignals(context: Context, kind: TileKind): TileSignals {
    val app = context.applicationContext
    return when (kind) {
        TileKind.PAY_CODE -> TileSignals(
            campusCardConfigured = runCatching {
                Graph.yktCredentialStore(app).read() != null
            }.getOrDefault(false),
        )

        TileKind.QIEKJ_WATER -> TileSignals(
            qiekjLoggedIn = runCatching { Graph.qiekj(app).loggedIn.value }.getOrDefault(false),
        )

        TileKind.QZXY_WATER -> TileSignals(
            qzxyLoggedIn = runCatching { Graph.qzxy(app).localSession() != null }
                .getOrDefault(false),
        )
    }
}
