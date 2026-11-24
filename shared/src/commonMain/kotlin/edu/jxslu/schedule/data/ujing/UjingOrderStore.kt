package edu.jxslu.schedule.data.ujing

import edu.jxslu.schedule.data.local.KeyValuePrefs
import edu.jxslu.schedule.data.qiekj.QiekjJson
import edu.jxslu.schedule.domain.UjingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 进行中的 U净 订单快照（DESIGN §4.37 P2）。
 *
 * 选型与 `QzxyWateringStore` 同一理由：读它的有两处独立 ViewModel 实例（页面与本页
 * 未来的今日页卡片 / 通知落点），普通键值存储的读时快照无法推送变化；
 * 落盘之外再挂一条进程内可订阅的流，`Graph` 保证同一进程一份实例。
 *
 * **为什么必须落盘**（社区实测教训）：App 进程重启后内存态 `orderId` 丢失 → 上报 /
 * 查询无单可查 → 用户在"看起来正常"的界面里反复点一个永远不会生效的按钮。订单号
 * 与机器绑定，必须跨进程存活。
 *
 * 不加密：订单号 / 状态 / 机器名不是凭证（凭证在 `secure_ujing`）。
 *
 * ## iOS 移植 S15（2026-11-24）：搬进 `:shared`
 *
 * 换掉的只有两样，**介质与键名都没动**（老用户的在案订单原地可读，不需要迁移）：
 * - `Context.getSharedPreferences` → [KeyValuePrefs]（Android 侧仍是同名 `SharedPreferences`
 *   文件 `ujing_order`，见 `AndroidPrefs`；iOS 侧换 `NSUserDefaults`）；
 * - `org.json` 手写编解码 → kotlinx-serialization 的 `JsonObject`。**JSON 字段名一个没改**
 *   （`orderId` / `orderNo` / …），所以老文件照样解得出来。
 *
 * 为什么不换到 `data/local/JsonFileStore`（换介质到 `filesDir` 下的 JSON 文件）：
 * 那等于把用户的在案订单丢掉一次（文件位置与格式都变）。这条缝只换「怎么读写」，
 * 不换「读写哪个文件」。
 */
class UjingOrderStore(private val prefs: KeyValuePrefs) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<UjingOrderSnapshot?> = _state.asStateFlow()

    /** 当前在案订单（无则 null）。 */
    fun current(): UjingOrderSnapshot? = _state.value

    fun save(snapshot: UjingOrderSnapshot) {
        prefs.putString(KEY_ORDER, encode(snapshot))
        _state.value = snapshot
    }

    /** 订单终结（完成 / 取消 / 失败）或用户主动放弃时清掉。 */
    fun clear() {
        prefs.remove(KEY_ORDER)
        _state.value = null
    }

    private fun read(): UjingOrderSnapshot? {
        val raw = prefs.getString(KEY_ORDER) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    private fun encode(s: UjingOrderSnapshot): String = buildJsonObject {
        put("orderId", s.orderId)
        put("orderNo", s.orderNo)
        put("deviceId", s.deviceId)
        put("deviceName", s.deviceName)
        put("modelId", s.modelId)
        put("modelName", s.modelName)
        put("dryer", s.dryer)
        put("priceFen", s.priceFen)
        put("status", s.status)
        put("statusRemark", s.statusRemark)
        put("remainSeconds", s.remainSeconds)
        put("snapshotAt", s.snapshotAt)
        put("paid", s.paid)
        put("createdAt", s.createdAt)
        put("durationSeconds", s.durationSeconds)
    }.toString()

    /** 解不出来（老数据被截断 / 不是对象 / 缺 orderId）回 null：与 `JSONObject` 抛异常后吞掉同口径。 */
    private fun decode(raw: String): UjingOrderSnapshot? {
        val obj = QiekjJson.json.parseToJsonElement(raw) as? JsonObject ?: return null
        val orderId = obj.text("orderId") ?: return null
        return UjingOrderSnapshot(
            orderId = orderId,
            orderNo = obj.textOrEmpty("orderNo"),
            deviceId = obj.textOrEmpty("deviceId"),
            deviceName = obj.textOrEmpty("deviceName"),
            modelId = obj.intOrZero("modelId"),
            modelName = obj.textOrEmpty("modelName"),
            dryer = obj.boolOrFalse("dryer"),
            priceFen = obj.intOrZero("priceFen"),
            status = obj.textOrEmpty("status"),
            statusRemark = obj.textOrEmpty("statusRemark"),
            remainSeconds = obj.intOrZero("remainSeconds"),
            snapshotAt = obj.longOrZero("snapshotAt"),
            paid = obj.boolOrFalse("paid"),
            createdAt = obj.longOrZero("createdAt"),
            durationSeconds = obj.intOrZero("durationSeconds"),
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.textOrEmpty(key: String): String = text(key).orEmpty()

    private fun JsonObject.intOrZero(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.longOrZero(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.boolOrFalse(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private companion object {
        /** 键名与搬移前逐字一致（老文件里就是这个键）。 */
        const val KEY_ORDER = "order"
    }
}

/**
 * 在案订单的快照。`status` / `remainSeconds` 是**最近一次服务端回包**的值，
 * 本地倒计时以 [snapshotAt] 为基准换算（不每秒请求）。
 */
data class UjingOrderSnapshot(
    val orderId: String,
    val orderNo: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    /** 用户选的模式（下单请求原样保存，重启后卡片还能说明"点的是什么"）。 */
    val modelId: Int = 0,
    val modelName: String = "",
    /**
     * 是否烘干机订单（下单时 `UjingState.isDryer` 判定）。今日页取衣提醒据此选文案
     * （「衣物已烘干」而不是「已洗完」）；旧版本快照没有该字段 → false。
     */
    val dryer: Boolean = false,
    /** 应付金额（分）。 */
    val priceFen: Int = 0,
    val status: String = "",
    /** 服务端状态文案（`detail.statusRemark`，非空时优先展示）；旧快照无此字段 → 空串。 */
    val statusRemark: String = "",
    /** 服务端报告的剩余秒数；未开始的订单为 0。 */
    val remainSeconds: Int = 0,
    /** 快照落定时刻（毫秒），本地倒计时基准。 */
    val snapshotAt: Long = 0,
    /** 支付是否已完成（detail.payFlag / lastPayStatus 的归一结果）。 */
    val paid: Boolean = false,
    /**
     * 下单时刻（毫秒）。**只在 createOrder 时写、刷新时原样保留**——
     * 服务端给未支付订单 2 分钟独占期，倒计时以此为准（snapshotAt 每次刷新都会动，
     * 拿它当窗口基准会越刷越长）。旧版本快照没有此字段（0），此时不显示窗口倒计时。
     */
    val createdAt: Long = 0,
    /**
     * 模式总时长（秒），下单时取所选模式的 `time`。洗涤进度条用它当分母；
     * 旧版本快照为 0，此时不显示进度条。
     */
    val durationSeconds: Int = 0,
) {
    val isTerminal: Boolean get() = UjingState.isTerminal(status)
}
