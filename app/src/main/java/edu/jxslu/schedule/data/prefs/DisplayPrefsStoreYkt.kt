package edu.jxslu.schedule.data.prefs

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

/** 一卡通相关偏好（DESIGN §4.19 / §4.38）：从 [DisplayPrefsStore] 拆出，按功能聚在一处。 */

// 上次已知的正式卡余额（分，2026-11-06）：余额变化 → 补拉一页消费流水（新消费入收件箱）。
// 落盘而不是只放内存，是为了进程被杀后第一次取余额也能判出「这段时间扣过钱」——
// 否则「打开 App 直接看收件箱」这条最常用的路径又回到原样。
private val KEY_YKT_LAST_BALANCE_FEN = longPreferencesKey("ykt_last_balance_fen")

/**
 * 上次已知的正式卡余额（分）；null = 从未记录过。
 *
 * 只作「两次余额观察之间有没有发生交易」的比较基线（`domain/YktBalanceChange`），
 * 不是展示值、不参与页面渲染——展示口径仍是各页面的余额快照。
 */
suspend fun DisplayPrefsStore.yktLastBalanceFen(): Long? =
    context.displayDataStore.data.first()[KEY_YKT_LAST_BALANCE_FEN]?.takeIf { it >= 0 }

suspend fun DisplayPrefsStore.setYktLastBalanceFen(fen: Long) {
    context.displayDataStore.edit { it[KEY_YKT_LAST_BALANCE_FEN] = fen }
}
