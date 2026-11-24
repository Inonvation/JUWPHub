package edu.jxslu.schedule.data.local

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * [inTransaction] 的 iOS 实现：连接绑定 + `immediateTransaction`。
 *
 * iOS 侧的库由 `BundledSQLiteDriver` 打开（`JuwDatabase.ios.kt`），`useWriterConnection`
 * 是 Room KMP 在该驱动上的标准写法：把写连接绑到当前协程，块内的 suspend DAO 调用复用
 * 同一条连接，因此整块是一个事务。
 *
 * 用 IMMEDIATE 而不是 DEFERRED：仓库层的写批次几乎都是「先读一遍再写」，DEFERRED 事务里
 * 的首次写要从读锁升级到写锁，并发时直接 `SQLITE_BUSY`；IMMEDIATE 一开始就拿写锁。
 *
 * ## 为什么需要嵌套闸门
 *
 * SQLite 不允许 `BEGIN` 套 `BEGIN`，而调用链里**确实有嵌套**：
 * `ScheduleRepository.importBackup` 的外层事务里调 `mergeCourses` / `replaceAllCourses`，
 * 那两个方法自己也开事务（Android 侧一直是靠 Room `withTransaction` 的嵌套复用活下来的，
 * 见那边的注释）。所以这里用协程上下文里的标记模拟同一件事：已经在一个事务里就**直接执行**
 * 块体，不再 `BEGIN`——语义与 Room 的复用完全相同，块体仍在外层事务里。
 */
internal actual suspend fun <R> JuwDatabase.inTransaction(block: suspend () -> R): R {
    if (currentCoroutineContext()[InTransactionKey] != null) return block()
    return useWriterConnection { transactor ->
        transactor.immediateTransaction {
            withContext(InTransactionMarker) { block() }
        }
    }
}

private object InTransactionKey : CoroutineContext.Key<InTransactionMarker>

private object InTransactionMarker : AbstractCoroutineContextElement(InTransactionKey)
