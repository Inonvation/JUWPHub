package edu.jxslu.schedule.data.local

import androidx.room.withTransaction

/**
 * [inTransaction] 的 Android 实现：直接用 Room 自带的 `withTransaction`。
 *
 * 搬进 `shared/commonMain` 之前，仓库层那 24 处事务调用就是这一句；这里刻意不换写法
 * （理由见 commonMain 的 `RoomTransaction.kt`：`useWriterConnection` 在 `Room.databaseBuilder`
 * 打开的库上语义不同，而 Android 侧没有用例能钉住那个差异）。
 */
internal actual suspend fun <R> JuwDatabase.inTransaction(block: suspend () -> R): R =
    withTransaction { block() }
