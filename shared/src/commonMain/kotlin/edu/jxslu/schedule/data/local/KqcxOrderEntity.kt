package edu.jxslu.schedule.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * 快趣服务端订单缓存（2026-11-05，表 `kqcx_orders`，DB v21）。
 *
 * 为什么落库：超时费统计要「全部历史」，而 `queryOrderList` 一次只查一个月，全量
 * 扫描是 20–40 次请求。缓存之后，统计在本地算（进页即可出数），网络只负责增量刷新
 * （[edu.jxslu.schedule.data.kqcx.KqcxOrderCache] 由账号页与历史订单页共同写入）。
 *
 * **主键 [key] 由业务键拼出**（`i:<id>` / `n:<订单号>` / `c:<车号>:<还车时间>`）：
 * 服务端没有稳定自增主键的保证，同一笔订单在不同月份的查询里必须落到同一行，
 * 否则统计会把同一单重复累加（2026-11-05 实测踩过，金额被放大到 ¥1245）。
 *
 * 可空列（订单号 / 车号 / 车型 / 金额 / 待支付）**实体不给 Kotlin 默认值**，迁移建表
 * 照写裸 `TEXT` / `INTEGER`——给默认值就必须写 `DEFAULT`，否则迁移校验崩溃
 * （v7→v8 / v11→v12 / v14→v15 的老坑，见 AGENTS.md「工程实况」）。
 */
@Entity(tableName = "kqcx_orders")
data class KqcxOrderEntity(
    /** 业务键，见类注释。 */
    @PrimaryKey val key: String,
    val orderNum: String?,
    val carNum: String?,
    /** 1 = 电单车，2 = 单车。 */
    val deviceType: Int?,
    /** 还车时间原文（`yyyy-MM-dd HH:mm:ss` 形态）；进行中的订单为 null。 */
    val endAt: String?,
    val totalSeconds: Long?,
    /** 实付合计（分）。 */
    val payMoneyCents: Long?,
    /** 骑行费（分）——超时费统计的唯一来源。 */
    val orderMoneyCents: Long?,
    val dispatchMoneyCents: Long?,
    val needPay: Boolean?,
    /** 写入时刻（epoch 毫秒），排查"这份缓存是什么时候拉的"。 */
    val syncedAt: Long,
)

@Dao
interface KqcxOrderDao {

    /** 按业务键覆盖写：同一笔订单反复拉到只更新一行。 */
    @Upsert
    suspend fun upsert(rows: List<KqcxOrderEntity>)

    /** 全量读出（统计用；万级以下都是内存里算，不分页）。 */
    @Query("SELECT * FROM kqcx_orders")
    suspend fun all(): List<KqcxOrderEntity>

    @Query("SELECT COUNT(*) FROM kqcx_orders")
    suspend fun count(): Int

    /** 最近一次写入时刻（增量刷新的节流依据：新鲜就别再发请求）。 */
    @Query("SELECT MAX(syncedAt) FROM kqcx_orders")
    suspend fun maxSyncedAt(): Long?

    /** 退出登录 / 切换账号时清空（订单属于某个手机号，不能串号）。 */
    @Query("DELETE FROM kqcx_orders")
    suspend fun clear()
}
