package edu.jxslu.schedule.data.qiekj

import edu.jxslu.schedule.data.local.JsonFileStore
import edu.jxslu.schedule.data.session.SessionExpirySink
import edu.jxslu.schedule.data.session.SessionServiceIds
import kotlinx.serialization.builtins.ListSerializer

/**
 * iOS 侧的胖乖订单快照与过期标记（2026-11-25）。
 *
 * ## 为什么这两份能让开水房在 iOS 上真的能用
 *
 * `QiekjRepository` 的构造要三样平台存储：凭证（Keychain，已接）、订单快照、过期标记。
 * 后两样都只是「一份 JSON + 一个布尔」，与 Android 的 `SharedPreferences` 没有本质区别，
 * 换成 `appDataDir()` 下的文件不丢任何语义。补齐这两份之后 `qiekjRepository()` 不再是
 * `null`，开水房（`SubpageScreen.WATER`）就能进——**胖乖走的是 HTTP，不是蓝牙**，
 * 这是 iOS 上唯一能真正开到水的那条链。
 *
 * ## 与 Android 实现的对齐点
 *
 * | 行为 | 对齐依据 |
 * |---|---|
 * | 最多 50 笔、按完成时间倒序、同 `orderNo` 覆盖 | `QiekjOrderStore.MAX_ITEMS` 与 `QiekjOrderHistoryStore` 同一口径 |
 * | 读不出来回空表（不抛） | `JsonFileStore.readBlocking()` 内部 `runCatching`，损坏文件当「没有快照」 |
 * | 落盘失败静默 | `JsonFileStore.writeBlocking()` 回 `false`，不抛 |
 * | 过期标记与问渠 / U净共用一张表 | 复用 `IosSessionExpirySink`（`NSUserDefaults`），键名带 `SessionServiceIds.QIEKJ` |
 *
 * ## 与 Android 的**唯一**差异
 *
 * 介质从 `SharedPreferences("qiekj_order_history")` 换成 `appDataDir()/qiekj_orders.json`。
 * 升级用户（只在 Android 上有过本地账单）换到 iOS 后看不到那 50 笔——文件不共享，
 * 服务端账单（`QiekjRepository.serverOrders`）仍能联网看到。
 */
internal class IosQiekjOrderStore : QiekjOrderStore {

    private val store = JsonFileStore(
        fileName = FILE,
        serializer = ListSerializer(OrderHistoryItem.serializer()),
        json = QiekjJson.json,
        tag = TAG,
    )

    override fun list(): List<OrderHistoryItem> = store.readBlocking().orEmpty()

    override fun add(item: OrderHistoryItem) {
        // 与 `QiekjOrderHistoryStore.add` 逐字同一套合并规则：同 orderNo 覆盖、按完成时间倒序、截断
        val next = (list().filterNot { it.orderNo == item.orderNo } + item)
            .sortedByDescending { it.completedAt }
            .take(QiekjOrderStore.MAX_ITEMS)
        store.writeBlocking(next)
    }

    override fun clearAll() {
        store.writeBlocking(emptyList())
    }

    private companion object {
        const val FILE = "qiekj_orders.json"
        const val TAG = "IosQiekjOrderStore"
    }
}

/**
 * 胖乖的「登录状态已过期」留痕：直接转给 [IosSessionExpirySink]。
 *
 * 不另建一份存储——账号中心显示「已失效」读的是同一张表，两套各写各的就会出现
 * 「开水页说过期了、账号中心说正常」。
 */
internal class IosQiekjExpiryMarker(
    private val sink: SessionExpirySink,
) : QiekjExpiryMarker {

    override fun markExpired() {
        sink.mark(SessionServiceIds.QIEKJ)
    }

    override fun clearExpired() {
        sink.clear(SessionServiceIds.QIEKJ)
    }
}
