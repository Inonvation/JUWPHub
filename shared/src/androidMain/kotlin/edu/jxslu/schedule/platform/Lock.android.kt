package edu.jxslu.schedule.platform

/** Android 侧：就是 `kotlin.synchronized`，与搬迁前逐字等价。 */
actual fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)
