package edu.jxslu.schedule.data.wenqu

import edu.jxslu.schedule.platform.locked

/**
 * 问渠的**进程内 TTL 缓存**（DESIGN §4.45「进页不重复取数」）。
 *
 * 为什么需要它：问渠页是 `SubpageActivity` 上的独立窗口，**每次进页都会新建 ViewModel**，
 * 旧写法在 `init` 里订阅登录态、一订阅到 true 就取数——于是「进页 = 三次网络请求」
 * （我的记录 + 星级 + 活动列表），来回点两次就是六次。第三方系统没必要这么打。
 *
 * 为什么不做磁盘缓存：活动列表是平台公开内容、且随时在变；落盘要额外考虑备份排除与
 * 陈旧度展示，收益不抵成本。**进程内**已足够——仓库是 `Graph` 单例，页面进出、
 * 窗口重建都还在同一个进程里；冷启动重取一次是正确行为。
 *
 * 线程安全：锁实例。取数在后台线程、读缓存在主线程，两边都要进得来。
 * iOS 移植 S15（2026-11-24）起锁走 [locked] 而不是 `@Synchronized`——后者是 JVM 专有注解，
 * commonMain 编不过；`locked` 在 iOS 上目前是**不加锁**（见 `platform/Lock.kt` 的边界说明：
 * 那一条缝只给课表导入用，这里沿用同一实现，风险是 iOS 侧并发读缓存时可能看到中间态）。
 *
 * [now] 由调用方传（仓库默认取 [edu.jxslu.schedule.domain.AppClock.nowMillis]）——
 * 测试注入假时钟，不睡真时间。
 */
class WenquTtlCache<K : Any, V : Any>(
    private val ttlMs: Long,
    /** 最多留几条（LRU：最近写入的留在尾部，超了从头部丢）。 */
    private val maxEntries: Int = 8,
) {

    private class Entry<V>(val value: V, val fetchedAt: Long)

    private val lock = Any()

    /** `LinkedHashMap` 的迭代序 = 插入序，够当 LRU 用（命中不重排，只按写入时间淘汰）。 */
    private val entries = LinkedHashMap<K, Entry<V>>()

    /** 命中且未过期才返回值；过期即**顺手删掉**，不让它继续占位。 */
    fun get(key: K, now: Long): V? = locked(lock) {
        val entry = entries[key] ?: return@locked null
        if (now - entry.fetchedAt >= ttlMs) {
            entries.remove(key)
            return@locked null
        }
        entry.value
    }

    fun put(key: K, value: V, now: Long) {
        locked(lock) {
            // 先删再插：保证这条排到尾部（最近写入），且不会留旧值
            entries.remove(key)
            entries[key] = Entry(value, now)
            while (entries.size > maxEntries) {
                val oldest = entries.keys.firstOrNull() ?: break
                entries.remove(oldest)
            }
        }
    }

    fun clear() {
        locked(lock) { entries.clear() }
    }

    /** 单键作废（写操作改了一条数据时，别把整张表都清掉）。 */
    fun remove(key: K) {
        locked(lock) { entries.remove(key) }
    }
}
