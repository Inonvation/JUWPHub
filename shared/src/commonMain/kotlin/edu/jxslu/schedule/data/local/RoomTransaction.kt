package edu.jxslu.schedule.data.local

/**
 * 跨平台事务块（iOS 移植 S4，2026-11-24）。
 *
 * ## 为什么需要这条缝
 *
 * `androidx.room.withTransaction` **只在 Android 变体里**：Room 2.7.1 的 KMP 布局把
 * `withTransaction` 放在 `RoomDatabase.android.kt`（`shared` 的 commonMain 元数据编译
 * 直接报 `Unresolved reference 'withTransaction'`，2026-11-24 实测）。所以仓库层一旦搬进
 * `commonMain`，那 24 处 `db.withTransaction { … }` 全部失去符号。
 *
 * ## 为什么不统一用 `useWriterConnection { it.immediateTransaction { } }`
 *
 * 那条路在 commonMain 里是**能编过**的（`RoomDatabaseKt__RoomDatabaseKt` /
 * `TransactorKt` 都在 common 源集）。但它在 Android 上不是 `withTransaction` 的等价物：
 * 后者走 `startTransactionCoroutine` + 事务专用 dispatcher，前者走连接绑定 + `BEGIN IMMEDIATE`。
 * 本仓 Android 侧的库是 `Room.databaseBuilder(context, …)`（非 driver 路径）打开的，
 * 换实现的语义差与运行期风险都没有测试能钉住——`app/src/test` 里没有一条用例真开过 Room
 * （只有迁移链的静态断言）。所以这里**只在 iOS 用新路径，Android 保持逐字不变**。
 *
 * ## 实现口径
 *
 * - Android：`androidx.room.withTransaction`（与搬家前逐字一致）；
 * - iOS：`useWriterConnection { it.immediateTransaction { … } }`——iOS 侧库由
 *   `BundledSQLiteDriver` 打开，这是 Room KMP 文档给的写法；写批次用 IMMEDIATE
 *   避免「读到写」升级锁时撞 `SQLITE_BUSY`。
 *
 * 调用方不要自己再套一层 `withContext(Dispatchers.IO)`：两种实现都已在库自己的
 * 事务上下文里调度。
 */
internal expect suspend fun <R> JuwDatabase.inTransaction(block: suspend () -> R): R
