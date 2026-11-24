package edu.jxslu.schedule.data.repo

import kotlinx.coroutines.sync.Mutex

/**
 * 自动导入类同步的**撞车让位**闸门（2026-11-18 收编 8 份同构样板）。
 *
 * 八个 `*Sync` / `LessonLoader` 都遵守同一条纪律：**同一时刻只允许一次抓取**，
 * 撞上已在跑的那次就直接让位，不排队重抓一遍（排队意味着两个触发点各打一轮教务，
 * 白撞风控；让位之后先跑完的那次抓的东西两处都能用）。
 *
 * 用法固定成一行：
 * ```
 * suspend fun sync(...): Result = mutex.let { it.tryLocked { syncLocked(...) } } ?: Result.InProgress
 * ```
 * 或更直接的 `mutex.tryLocked { ... } ?: Result.InProgress`。
 *
 * 为什么不把八个 `Result` 也合成一个泛型类型：各自的 `Updated` / `Loaded` / `Applied` 载荷
 * 不同（学期、条数、变更明细、合并数），合并后 42 处限定名引用与十来个文件里的
 * `when` 分支都要改成泛型解包，收益是几十行 `data object` 声明——得不偿失。
 * 闸门是真正重复的那部分，所以只抽闸门。
 *
 * iOS 移植 S4（2026-11-24）：本文件与八个使用方一起搬进 `shared/commonMain`，但
 * `InternshipBasesLoader` 仍留在 `:app`（它的 JSON 快照仓储离不开 `Context` + `File`），
 * 跨模块看不见 `internal`，所以 `tryLocked` 提 public——全仓只有这一份实现。
 */
suspend fun <T> Mutex.tryLocked(block: suspend () -> T): T? {
    if (!tryLock()) return null
    try {
        return block()
    } finally {
        unlock()
    }
}
