package edu.jxslu.schedule.data.ujing

import edu.jxslu.schedule.data.local.KeyValuePrefs
import edu.jxslu.schedule.data.qiekj.QiekjJson
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.UjingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * U净 历史账单（DESIGN §4.37，2026-10-02）。
 *
 * 口径：记**本 App 发起**的每一笔订单——下单即入账，刷新随状态更新，取消也算一笔
 * （状态标注「已取消」）。服务端的账单接口没有可用的协议样本（`docs/ujing-plan.md`
 * 的端点表里只有 `orders/running`），不猜端点，先落本地；等拿到协议样本再评估
 * 换成服务端账单源。
 *
 * 选型与 [UjingOrderStore] / [UjingHouseStore] 同一理由：键值存储里存 JSON 数组
 * （最多 [CAP] 条，新的在前），外挂一条进程内可订阅的流给两个页面（主页入口行的
 * 笔数、历史账单页的列表）。不加密：订单号 / 状态 / 金额不是凭证（凭证在 `secure_ujing`）。
 *
 * ## iOS 移植 S15（2026-11-24）：搬进 `:shared`
 *
 * 换掉的只有两样，**介质与键名都没动**（老账单原地可读）：`Context.getSharedPreferences`
 * → [KeyValuePrefs]（Android 侧仍是同名 `ujing_history`），`org.json` → kotlinx-serialization。
 * JSON 字段名一个没改（`orderId` / `orderNo` / …），所以老文件照样解得出来；
 * 换成 `data/local/JsonFileStore` 那种「换介质 + 换格式」的路子会让老用户账单全丢，不做。
 */
class UjingHistoryStore(private val prefs: KeyValuePrefs) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<List<UjingHistoryEntry>> = _state.asStateFlow()

    /** 新增或更新一笔（同 orderId 去重，最新在前），超量截断。 */
    fun upsert(entry: UjingHistoryEntry) {
        val updated = upsertInto(_state.value, entry)
        persist(updated)
        _state.value = updated
    }

    /** 清空账单（历史页的「清空」，二次确认在 UI）。 */
    fun clear() {
        prefs.remove(KEY_ENTRIES)
        _state.value = emptyList()
    }

    private fun persist(list: List<UjingHistoryEntry>) {
        prefs.putString(KEY_ENTRIES, encode(list))
    }

    private fun read(): List<UjingHistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES) ?: return emptyList()
        return runCatching { decode(raw) }.getOrDefault(emptyList())
    }

    private fun encode(list: List<UjingHistoryEntry>): String = buildJsonArray {
        list.forEach { e ->
            add(
                buildJsonObject {
                    put("orderId", e.orderId)
                    put("orderNo", e.orderNo)
                    put("deviceName", e.deviceName)
                    put("modelName", e.modelName)
                    put("priceFen", e.priceFen)
                    put("status", e.status)
                    put("paid", e.paid)
                    put("createdAt", e.createdAt)
                    put("updatedAt", e.updatedAt)
                },
            )
        }
    }.toString()

    private fun decode(raw: String): List<UjingHistoryEntry> {
        val array = QiekjJson.json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            UjingHistoryEntry(
                orderId = obj.textOrEmpty("orderId"),
                orderNo = obj.textOrEmpty("orderNo"),
                deviceName = obj.textOrEmpty("deviceName"),
                modelName = obj.textOrEmpty("modelName"),
                priceFen = obj.intOrZero("priceFen"),
                status = obj.textOrEmpty("status"),
                paid = obj.boolOrFalse("paid"),
                createdAt = obj.longOrZero("createdAt"),
                updatedAt = obj.longOrZero("updatedAt"),
            )
        }.filter { it.orderId.isNotBlank() }
    }

    private fun JsonObject.textOrEmpty(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.intOrZero(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.longOrZero(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun JsonObject.boolOrFalse(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    companion object {
        /** 键名与搬移前逐字一致（老文件里就是这个键）。 */
        private const val KEY_ENTRIES = "entries"

        /** 账单容量上限：本机消费记录，50 笔远超一学期的洗衣频次。 */
        const val CAP = 50

        /**
         * 纯列表逻辑（JVM 可测）：新条目顶到最前，同 [UjingHistoryEntry.orderId]
         * 去重（刷新更新原条目而不是叠一笔），超 [CAP] 截断。
         */
        fun upsertInto(
            current: List<UjingHistoryEntry>,
            entry: UjingHistoryEntry,
        ): List<UjingHistoryEntry> =
            (listOf(entry) + current.filterNot { it.orderId == entry.orderId }).take(CAP)
    }
}

/**
 * 一笔历史账单。`status` 是最近一次已知的**服务端订单状态**（枚举见
 * [UjingState.statusText]，取消在仓库里直接标注 `60`）；`paid` 与之一致地反映支付与否。
 */
data class UjingHistoryEntry(
    val orderId: String,
    val orderNo: String = "",
    val deviceName: String = "",
    val modelName: String = "",
    /** 应付金额（分）。 */
    val priceFen: Int = 0,
    val status: String = "",
    val paid: Boolean = false,
    /** 下单时刻（毫秒；来自订单快照，旧版本快照可能为 0，展示时退回 [updatedAt]）。 */
    val createdAt: Long = 0,
    /** 本条最近一次落定时刻（毫秒）。 */
    val updatedAt: Long = 0,
) {
    /** 列表展示用时间基准：下单时刻优先，缺失退回最近更新。 */
    val displayAt: Long get() = if (createdAt > 0) createdAt else updatedAt

    companion object {
        /** 订单快照 → 账单条目（[statusOverride] 给取消留痕用，非空时覆盖状态）。 */
        fun fromSnapshot(
            snapshot: UjingOrderSnapshot,
            statusOverride: String? = null,
            nowMillis: Long = AppClock.nowMillis(),
        ): UjingHistoryEntry = UjingHistoryEntry(
            orderId = snapshot.orderId,
            orderNo = snapshot.orderNo,
            deviceName = snapshot.deviceName,
            modelName = snapshot.modelName,
            priceFen = snapshot.priceFen,
            status = statusOverride ?: snapshot.status,
            paid = snapshot.paid,
            createdAt = snapshot.createdAt,
            updatedAt = nowMillis,
        )
    }
}
