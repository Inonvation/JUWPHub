package edu.jxslu.schedule.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * 当前 schema 版本，唯一出处：`@Database(version = …)` 与迁移链测试共用。**公开**是因为
 * `:app` 的 `JuwMigrationChainTest` 跨模块读它。
 *
 * 加表/加列时必须同时做三件事：① 实体改字段；② 在 `:app` 的 `JuwDatabaseAndroid.kt` 里加一条
 * `MIGRATION_(N)_(N+1)` 并加进 `ALL_MIGRATIONS`；③ 把这里 +1。`JuwMigrationChainTest` 会挡住
 * 「只改版本不写迁移」——那种错在开发机的全新安装上永远不报，只在**升级设备**上开库即崩。
 */
const val DB_VERSION = 21

/**
 * 课表应用的本地数据库（iOS 移植 P2 起在 commonMain，2026-11-24）。
 *
 * ## 为什么在 commonMain，但迁移链留在一个平台
 *
 * 实体、DAO、`@Database` 声明是纯 Kotlin + Room 注解，两端逐字相同，放共享层才能让 iOS 侧
 * 复用同一套表结构与查询。真正分叉的只有两件事：**怎么打开库**（文件路径 API 不同）和
 * **迁移链**（`androidx.sqlite.db.SupportSQLiteDatabase` 是 Android 专属 API）。
 *
 * 所以拆分是：
 * - 这里：注解、DAO 访问器、[DB_VERSION]、[JuwDatabaseConstructor]。
 * - `:app` 的 `JuwDatabaseAndroid.kt`：v1→v21 的迁移链 + `Room.databaseBuilder(context, …)`
 *   （Android 路径一个字没改，见那里的说明）。
 * - `shared/src/iosMain` 的 `JuwDatabase.ios.kt`：`BundledSQLiteDriver` + Application Support
 *   目录，**不带迁移**——iOS 用户没有历史库，全新安装走 `createAllTables`。
 *
 * ## `@ConstructedBy` 与那个 expect object
 *
 * Room 从 2.7 起支持 KMP，要求 `@Database` 类配一个 `expect object : RoomDatabaseConstructor<T>`，
 * 由 Room 的 KSP 生成各平台的 `actual`（它就是原来的 `JuwDatabase_Impl` 实例化入口）。
 * 这不是可选的装饰——iOS 侧没有反射，`Room.databaseBuilder<JuwDatabase>(name)` 只能靠它拿到实现。
 */
@Database(
    entities = [
        TimetableEntity::class,
        CourseEntity::class,
        TimeSlotEntity::class,
        SemesterConfigEntity::class,
        ScoreEntity::class,
        YktTurnoverEntity::class,
        NoteEntity::class,
        HomeworkEntity::class,
        PowerReadingEntity::class,
        ScholarGroupEntity::class,
        ScholarCourseEntity::class,
        TextbookEntity::class,
        RideRecordEntity::class,
        CourseSelectionEntity::class,
        InboxMessageEntity::class,
        DateOverrideEntity::class,
        CurriculumCourseEntity::class,
        KqcxOrderEntity::class,
    ],
    version = DB_VERSION,
    // 2026-11-06 起导出 schema（`shared/schemas/`）并入库：Room 的运行时校验只在升级设备上跑，
    // 导出后「实体改了忘写迁移」会在 code review 里以 schema diff 的形式暴露。
    exportSchema = true,
)
@ConstructedBy(JuwDatabaseConstructor::class)
abstract class JuwDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun courseDao(): CourseDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun semesterConfigDao(): SemesterConfigDao
    abstract fun scoreDao(): ScoreDao
    abstract fun yktTurnoverDao(): YktTurnoverDao
    abstract fun noteDao(): NoteDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun powerReadingDao(): PowerReadingDao
    abstract fun scholarProgressDao(): ScholarProgressDao
    abstract fun textbookDao(): TextbookDao
    abstract fun rideRecordDao(): RideRecordDao
    abstract fun courseSelectionDao(): CourseSelectionDao
    abstract fun inboxDao(): InboxDao
    abstract fun dateOverrideDao(): DateOverrideDao
    abstract fun curriculumDao(): CurriculumDao
    abstract fun kqcxOrderDao(): KqcxOrderDao
}

/**
 * Room 生成的数据库实现入口（见 [JuwDatabase] 的 KDoc）。`actual` 由 Room 的 KSP 逐平台生成，
 * 源码里看不到——所以这里必须 `@Suppress` 掉「expect 没有 actual」的告警。
 */
@Suppress("KotlinNoActualForExpect")
expect object JuwDatabaseConstructor : RoomDatabaseConstructor<JuwDatabase> {
    override fun initialize(): JuwDatabase
}
