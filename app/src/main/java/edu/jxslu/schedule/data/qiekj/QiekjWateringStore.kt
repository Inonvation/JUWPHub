package edu.jxslu.schedule.data.qiekj

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import edu.jxslu.schedule.domain.AppClock

/**
 * 进行中的开水状态（DESIGN §3.18 / §4.10，2026-10-21）。
 *
 * 为什么不是普通偏好而是 [StateFlow]：读它的有两处独立 ViewModel——今日页那挂在
 * `MainActivity` 上，开水页是二级页窗口的另一份实例；`UnlockFlowState.Working`
 * 只活在各自的 VM 里，跨窗口互相看不见（从开水页开水，今日页卡片不出现；从侧栏面板
 * 开水，开水页看不到流程态）。把「正在出水」的事实收进一条落盘的进程内流，
 * 两处都订阅它，展示口径才一致（与 [edu.jxslu.schedule.data.qzxy.QzxyWateringStore] 同构）。
 *
 * 普通 `SharedPreferences` 不上加密：开阀时刻与设备名不是凭证。
 *
 * ## 介质留在这里，会话类型搬去了共享层（iOS 移植 V2，2026-11-25）
 *
 * [QiekjWateringSession] 与它的 KDoc 现在在 `shared/commonMain`（今日页服务卡要在共享层读
 * 那几个字段）；**store 本身仍留在 `:app`**：介质是 `SharedPreferences`，换介质等于让老用户
 * 设备上正在出水的那条记录读不出来。共享层要的那一面是 `QiekjWateringSessions`（写 + 读流），
 * 由 `GraphServices` 把本类的 [watering] 转过去——两条路指向同一个实例，没有第二份真相源。
 */
class QiekjWateringStore(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _watering = MutableStateFlow(readInitial())

    /** 非空 = 本机登记过一次「出水中」（过期残留已在读时清掉）。 */
    val watering: StateFlow<QiekjWateringSession?> = _watering.asStateFlow()

    /** 服务端确认出水时登记（流程首次进入 Working）。重复调用按最新一次覆盖。 */
    fun begin(session: QiekjWateringSession) {
        prefs.edit()
            .putLong(KEY_STARTED_AT, session.startedAtMillis)
            .putString(KEY_NAME, session.deviceName)
            .putString(KEY_GOODS, session.goodsId)
            .apply()
        _watering.value = session
    }

    /**
     * 结算完成 / 流程失败 / 165 秒超时收尾时清掉。
     * 结算晚到（超时后 Success 重新落卡）时再清一次是空操作。
     */
    fun clear() {
        prefs.edit().clear().apply()
        _watering.value = null
    }

    private fun readInitial(): QiekjWateringSession? {
        val started = prefs.getLong(KEY_STARTED_AT, 0L)
        if (started <= 0L) return null
        val session = QiekjWateringSession(
            startedAtMillis = started,
            deviceName = prefs.getString(KEY_NAME, "").orEmpty(),
            goodsId = prefs.getString(KEY_GOODS, null),
        )
        if (session.isStale(AppClock.nowMillis())) {
            // 过期残留：进程死在流程中，服务端早已超时关阀。静默清掉（展示口径，
            // 不涉及金额记录——账单由流程回报，见 QiekjWateringSession KDoc）
            prefs.edit().clear().apply()
            return null
        }
        return session
    }

    private companion object {
        const val FILE_NAME = "qiekj_watering"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_NAME = "device_name"
        const val KEY_GOODS = "goods_id"
    }
}
