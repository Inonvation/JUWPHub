package edu.jxslu.schedule.data.local

import androidx.room.migration.Migration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 迁移链护栏（2026-11-06 审计 D4）。
 *
 * 挡的是这一类错：改了实体 / 加了表，把 `DB_VERSION` 加一，却忘了写迁移 —— Room 的迁移校验
 * **只在升级设备上跑**，开发机全新安装走 `createAllTables` 永远绿，到用户设备上就是
 * `IllegalStateException: Migration didn't properly handle`，用户唯一出路是清数据。
 *
 * 这里不验 DDL 内容（那需要真 SQLite / 仪器测试），只验「链是完整的」：
 * v1..v(DB_VERSION-1) 每一级恰好一条 `v → v+1`，不重复、不跳级、末端落在当前版本。
 * 每次加迁移，只要漏了 [JuwDatabase.ALL_MIGRATIONS]，这条用例就会红。
 *
 * **它挡不住的两类**（别误以为护栏很厚）：
 * 1. 迁移写了但 DDL 错 / 空实现 —— 只有 `MigrationTestHelper` + 真 SQLite 能验；
 * 2. 「改了实体、没加版本号」—— 链形状不变，本用例全绿。这类只能靠
 *    `exportSchema = true` 生成的 `app/schemas/…/<版本>.json` 在 git diff 里露出来。
 *
 * 另外：`@Database(version = …)` 是 `RetentionPolicy.CLASS`，JVM 单测**读不到注解**，
 * 所以「注解必须引用 [DB_VERSION] 常量」这条只能靠约定 —— 别把注解改回字面量数字。
 */
class JuwMigrationChainTest {

    private val migrations: List<Migration> = JuwDatabase.ALL_MIGRATIONS

    @Test
    fun chainCoversEveryVersionStep() {
        val steps = migrations.map { it.startVersion to it.endVersion }
        assertEquals("迁移不能有重复的起止版本：$steps", steps.size, steps.toSet().size)
        for (v in 1 until DB_VERSION) {
            assertEquals(
                "缺少 v$v → v${v + 1} 的迁移（DB_VERSION 加了但没写迁移？）",
                1,
                steps.count { it == v to v + 1 },
            )
        }
    }

    @Test
    fun chainStartsAtOneAndEndsAtCurrentVersion() {
        assertTrue("迁移链不能为空", migrations.isNotEmpty())
        assertEquals("迁移链必须从 v1 开始", 1, migrations.minOf { it.startVersion })
        assertEquals(
            "最后一条迁移必须落在 DB_VERSION 上（否则升级设备开库即崩）",
            DB_VERSION,
            migrations.maxOf { it.endVersion },
        )
    }

    /** 一次跳两级会漏掉中间版本的 DDL —— 本项目要求逐级 `ALTER TABLE`。 */
    @Test
    fun stepsAreSingleVersionEach() {
        migrations.forEach {
            assertEquals(
                "迁移必须逐级（${it.startVersion} → ${it.endVersion}）",
                it.startVersion + 1,
                it.endVersion,
            )
        }
    }
}
