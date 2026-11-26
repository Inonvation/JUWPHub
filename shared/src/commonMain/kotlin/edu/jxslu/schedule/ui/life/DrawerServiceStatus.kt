package edu.jxslu.schedule.ui.life

import edu.jxslu.schedule.data.ujing.UjingHouseStore
import edu.jxslu.schedule.data.ujing.UjingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 侧栏「U净」行右侧的空闲摘要（`空闲 3/12` / `暂无空闲`）。
 *
 * 2026-11-26 晚从 `:app/ui/life/SideDrawer.kt` 搬进共享层：iOS 抽屉现在也要显示这一格，
 * 两端必须同一份算法——否则同一天同一家洗衣房在两台手机上会给出不同的数字。
 *
 * 口径（与 U净 页看板同源）：`stores/near?keyword=<店名>` 的门店统计，多店求和
 * （free / total）。`devices/reserve` 在本校已被服务端关闭，不要换回去。
 *
 * 返回 null 的三种情况：未登录、没收藏店、没有任何一家拿到数据——此时行右侧**不显示**状态，
 * 整行点击直接进页（不显示优于显示一个错误的 0/0）。
 *
 * 单店失败只算这一家不参与求和：`runCatching` 会把 `CancellationException` 一起吞掉，
 * 所以这里手写 catch 并原样上抛取消——抽屉收起时必须能正常取消。
 */
suspend fun ujingFreeText(
    repo: UjingRepository,
    houseStore: UjingHouseStore,
): String? {
    if (!repo.loggedIn.value) return null
    val houses = houseStore.list()
    if (houses.isEmpty()) return null
    val limiter = Semaphore(UJING_FREE_CONCURRENCY)
    val lines = coroutineScope {
        houses.map { house ->
            async {
                try {
                    val groups = limiter.withPermit { repo.storeCounts(house.name) }
                    repo.boardLine(groups)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll()
    }.filterNotNull()
    val free = lines.sumOf { it.free }
    val total = lines.sumOf { it.total }
    if (lines.isEmpty() || total <= 0) return null
    return if (free > 0) "空闲 $free/$total" else "暂无空闲"
}

/** 侧栏聚合的并发上限（与 U净 页看板同口径，别一次打满收藏店）。 */
private const val UJING_FREE_CONCURRENCY = 4
