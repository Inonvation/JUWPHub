package edu.jxslu.schedule.data

import edu.jxslu.schedule.data.session.SessionExpirySink
import edu.jxslu.schedule.data.session.SessionExpiryStore
import edu.jxslu.schedule.data.session.SessionService

/**
 * [SessionExpirySink] 的 Android 实现（iOS 移植 S15，2026-11-24）：把共享层传下来的
 * **服务 id 字符串**换回 [SessionService] 枚举，再交给现有 [SessionExpiryStore]。
 *
 * 为什么不把 `SessionExpiryStore` 搬进共享层：它的介质是 `SharedPreferences` + `Context`，
 * 而那条标记的口径又只有「进程内一份」这一个要求——套一层适配器比把
 * `SharedPreferences` 抽象成跨平台键值库便宜得多。这条适配器是**唯一的桥**，
 * 一个进程一个实例，由 `Graph.sessionExpirySink(context)` 持有。
 *
 * 枚举里没有的 id 直接抛：口径漂移（共享层加了新 id、这里没跟上）必须当场炸，
 * 而不是静默丢一条「已过期」提示——那样用户只会看到一张登录表单，不知道为什么被登出。
 */
class AndroidSessionExpirySink(private val store: SessionExpiryStore) : SessionExpirySink {

    override fun mark(serviceId: String) {
        store.mark(service(serviceId))
    }

    override fun clear(serviceId: String) {
        store.clear(service(serviceId))
    }

    override fun isExpired(serviceId: String): Boolean = store.isExpired(service(serviceId))

    override fun consume(serviceId: String): Boolean = store.consume(service(serviceId))

    private fun service(id: String): SessionService =
        SessionService.entries.firstOrNull { it.id == id }
            ?: error("未知的会话服务 id：$id（SessionServiceIds 与 SessionService 口径不一致）")
}
