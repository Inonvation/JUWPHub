package edu.jxslu.schedule.data.qiekj

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer

/**
 * 本地订单快照存储（DESIGN §4.10）。
 *
 * 为什么不进 Room：订单快照是胖乖模块私有的追加型小数据（上限 50 条），无查询/关联需求，
 * SharedPreferences + JSON 与参考实现一致且足够；接口侧没有可靠的订单列表端点，
 * 快照是订单历史的唯一来源，随 token 清理而清空。
 *
 * 与 `data/local/JsonFileStore` 的差异（2026-11-06 审计 C2 收尾）：介质是 SharedPreferences
 * 里的一段 kotlinx JSON 文本，而且**没有内存真值**——没有 StateFlow 镜像、每次 `list()`
 * 现读现解码，套共享实现只会多一层没有收益的间接。
 *
 * **留在 `:app`**（iOS 移植 S11，2026-11-24）：仓库搬进 `:shared` 后只认 [QiekjOrderStore]
 * 这个接口，本类是实现。**没有**顺手改成 `JsonFileStore`（`appDataDir()` + JSON 文件）：
 * 介质一换，老用户设备上那最多 50 笔本地账单当场读不出来，而这类快照没有服务端兜底
 * （`order/list` 是分页账单，不还原本地口径）。
 */
class QiekjOrderHistoryStore(context: Context) : QiekjOrderStore {
    private val prefs = context.getSharedPreferences("qiekj_order_history", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(OrderHistoryItem.serializer())

    override fun list(): List<OrderHistoryItem> {
        val raw = prefs.getString(KEY_ORDERS, null) ?: return emptyList()
        return runCatching { QiekjJson.json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
    }

    override fun add(item: OrderHistoryItem) {
        val next = (list().filterNot { it.orderNo == item.orderNo } + item)
            .sortedByDescending { it.completedAt }
            .take(QiekjOrderStore.MAX_ITEMS)
        prefs.edit().putString(KEY_ORDERS, QiekjJson.json.encodeToString(serializer, next)).apply()
    }

    override fun clearAll() {
        prefs.edit().remove(KEY_ORDERS).apply()
    }

    private companion object {
        const val KEY_ORDERS = "orders"
    }
}
