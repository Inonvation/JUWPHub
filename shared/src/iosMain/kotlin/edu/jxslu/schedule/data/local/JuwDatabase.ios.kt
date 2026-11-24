package edu.jxslu.schedule.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

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
 * 2. **库文件放 Application Support，不放 Documents**。Documents 会进 iCloud 备份，一个可以从
 *    教务重新导入的本地缓存没必要占用户的备份配额，也不该出现在「文件」App 里。
 *
 * 用 `BundledSQLiteDriver` 而不是系统 SQLite：iOS 各版本自带 SQLite 的编译选项不一致
 * （有的版本没开 FTS / 某些 PRAGMA 行为不同），带一份自己的最稳；代价是二进制大 1.5~3MB。
 *
 * 查询协程上下文没有显式设置——Room 默认 `Dispatchers.IO`。`Dispatchers.IO` 在 Kotlin/Native
 * 上是否可用**本机无法验证**（Windows 编不了 iosMain）；真机上若崩在这里，改成
 * `.setQueryCoroutineContext(Dispatchers.Default)` 即可，别去猜 API 名。
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
    val path: String by lazy { databasePath() }

    @OptIn(ExperimentalForeignApi::class)
    private fun databasePath(): String {
        val dir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        val base = requireNotNull(dir?.path) { "拿不到 Application Support 目录" }
        return "$base/juw_schedule.db"
    }
}
