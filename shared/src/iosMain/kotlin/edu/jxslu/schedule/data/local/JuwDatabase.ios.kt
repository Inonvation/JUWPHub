package edu.jxslu.schedule.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import edu.jxslu.schedule.platform.appDataDir

/**
 * iOS 侧的开库入口（iOS 移植 P2，2026-11-24）。
 *
 * 与 Android 的两处**有意差异**：
 *
 * 1. **不带迁移链**。iOS 用户没有历史库，全新安装走 `createAllTables`；迁移只在「老库 + 新版本」
 *    时才会被 Room 调用。Android 那 21 条迁移用的是 `androidx.sqlite.db.SupportSQLiteDatabase`
 *    （Android 专属 API），Kotlin/Native 上连编译都过不去，所以那条链只留在 `:app`。
 *    `// shortcut: iOS 没有迁移链。DB_VERSION 下次 +1 时要么给这边补一条，要么在 builder 上开
 *    fallbackToDestructiveMigration（会清库），别什么都不做直接升版本。`
 * 2. **库文件优先放 Application Support**，不放 Documents：Documents 会进 iCloud 备份，
 *    一个可以从教务重新导入的本地缓存没必要占用户的备份配额。**成功路径与这一条一致**；
 *    只有 `URLForDirectory` 在沙箱里回 null 时才会退到 Documents（见下）。
 *
 * 用 `BundledSQLiteDriver` 而不是系统 SQLite：iOS 各版本自带 SQLite 的编译选项不一致
 * （有的版本没开 FTS / 某些 PRAGMA 行为不同），带一份自己的最稳；代价是二进制大 1.5~3MB。
 *
 * 查询协程上下文没有显式设置——Room 默认 `Dispatchers.IO`。`Dispatchers.IO` 在 Kotlin/Native
 * 上是否可用**本机无法验证**（Windows 编不了 iosMain）；真机上若崩在这里，改成
 * `.setQueryCoroutineContext(Dispatchers.Default)` 即可，别去猜 API 名。
 *
 * ## 2026-11-26：路径不再自己算，也不再抛
 *
 * 原来这里有一份 `URLForDirectory(ApplicationSupport, …, create = true)` +
 * `requireNotNull(dir?.path)`。它与 `platform/AppDirs.ios.kt` 的 `appDataDir()` 是**同一段表达式
 * 的第二份拷贝**，而那份在 11-26 已经证实会回 null 并被改成三级降级——这边却没有跟着改。
 * 结果是同一次失败两条路：日志侧静默失效，**开库侧直接抛 `IllegalArgumentException`**。
 * 而 `juwDatabase()` 在根组合里就被调用（`MeViewModel` → `ScheduleRepository`），
 * 那一下抛出去就是「一开就闪退」。
 *
 * 现在统一走 [appDataDir]：成功路径逐字不变（它的首选档就是这个表达式），失败时降到
 * `Documents/` 而不是抛异常。**别再在这里重新拼一遍目录**——目录只有 `appDataDir()` 一处实现。
 */
fun juwDatabase(): JuwDatabase = DatabaseHolder.instance

/** 库文件绝对路径（验收屏要把它显示出来，证明真的落在 Application Support 里）。 */
internal fun juwDatabasePath(): String = DatabaseHolder.path

private object DatabaseHolder {
    // 不在构造里做 IO：第一次真正用到 DAO 时才开库。
    val instance: JuwDatabase by lazy {
        Room.databaseBuilder<JuwDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    /** 库文件绝对路径；`lazy` 里与外面对外都读同一个值，避免两处各拼一次路径。 */
    val path: String by lazy { appDataDir() + "/juw_schedule.db" }
}
