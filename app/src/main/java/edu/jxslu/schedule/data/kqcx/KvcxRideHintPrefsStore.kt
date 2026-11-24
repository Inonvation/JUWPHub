package edu.jxslu.schedule.data.kqcx

import android.content.Context

/**
 * 「上一次进程退出时还有在案骑行」的落盘提示（2026-11-18）。
 *
 * 用途与判据见共享层的 [KvcxRideHintStore]（接口在 `:shared`，iOS 将来用
 * NSUserDefaults 另写一份）。
 *
 * 存普通 SharedPreferences 不上加密：只是一枚布尔，不含车号 / 订单号；凭证仍在
 * `secure_kqcx` 里。与 [edu.jxslu.schedule.data.qzxy.QzxyWateringStore] /
 * [edu.jxslu.schedule.data.ujing.UjingOrderStore] 同口径。
 *
 * iOS 移植 S12（2026-11-24）：类名从 `KvcxRideHintStore` 改为
 * `KvcxRideHintPrefsStore`——接口占用了原名，实现名点明它落在 SharedPreferences。
 */
internal class KvcxRideHintPrefsStore(context: Context) : KvcxRideHintStore {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 上一次进程退出时是否还有在案骑行。 */
    override val hadRide: Boolean get() = prefs.getBoolean(KEY_HAD_RIDE, false)

    /** 有骑行 → true、无骑行 → false。同值跳写（每次查询都会走到这里）。 */
    override fun setHadRide(active: Boolean) {
        if (hadRide == active) return
        prefs.edit().putBoolean(KEY_HAD_RIDE, active).apply()
    }

    private companion object {
        const val FILE_NAME = "kvcx_ride_hint"
        const val KEY_HAD_RIDE = "had_ride"
    }
}
