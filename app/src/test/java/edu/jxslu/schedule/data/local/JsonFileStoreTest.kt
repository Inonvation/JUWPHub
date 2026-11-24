package edu.jxslu.schedule.data.local

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享落盘实现（2026-11-06 审计 C2）的契约：读不到/坏了回 `null`、写失败不抛、
 * 临时文件不留在目录里、重复写以最后一次为准。
 *
 * 为什么要单独钉这几条：八份缓存/快照都靠它，`ZoneCacheStoreTest` 只覆盖了其中一份
 * 的往返；「写两次」这条更是只有在这里才跑得到——Windows 上「改名覆盖已存在文件」
 * 会失败，走的正是 `writeBlocking` 里那条「删掉再改名 / 直接写」的兜底分支。
 *
 * iOS 移植 S7（2026-11-24）起它不再收 `File`/`Context`，只收文件名 + 目录：所以夹具改成
 * 「一个临时目录 + 固定文件名」，与生产路径（`appDataDir()/<文件名>`）同构。
 */
class JsonFileStoreTest {

    @Serializable
    private data class Row(
        val id: Int = 0,
        val name: String = "",
        val tags: List<String> = emptyList(),
    )

    private fun tempDir(): File = File.createTempFile("json-store", "").also { it.delete(); it.mkdirs() }

    private fun fileIn(dir: File): File = File(dir, FILE)

    private fun storeOf(dir: File) = JsonFileStore(FILE, Row.serializer(), dir = dir.absolutePath)

    @Test
    fun `往返保留字段`() = runBlocking {
        val dir = tempDir()
        try {
            val store = storeOf(dir)
            val row = Row(id = 7, name = "高等数学", tags = listOf("a", "b"))
            assertTrue("写应成功", store.write(row))
            assertEquals(row, store.read())
            assertTrue("写完要占字节（缓存统计用）", store.bytes() > 0)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `缺文件与坏文件都回 null 且不抛`() = runBlocking {
        val dir = tempDir()
        try {
            val store = storeOf(dir)
            assertNull("文件不存在 → null", store.read())
            fileIn(dir).writeText("{不是 JSON")
            assertNull("坏 JSON → null", store.read())
            fileIn(dir).writeText("""{"id":"类型不对"}""")
            assertNull("类型对不上 → null", store.read())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `重复写以最后一次为准`() = runBlocking {
        val dir = tempDir()
        try {
            val store = storeOf(dir)
            assertTrue(store.write(Row(id = 1, name = "旧")))
            assertTrue(store.write(Row(id = 2, name = "新")))
            assertEquals(Row(id = 2, name = "新"), store.read())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `删除后回 null 且目录里不留临时文件`() = runBlocking {
        val dir = tempDir()
        try {
            val store = storeOf(dir)
            store.write(Row(id = 1))
            store.delete()
            assertNull("删掉即失效", store.read())
            assertEquals("删掉后不再占字节", 0L, store.bytes())
            val leftovers = dir.listFiles()?.filter { it.name == FILE + ".tmp" }.orEmpty()
            assertTrue("临时文件必须被改名消耗掉：$leftovers", leftovers.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * 目录不存在时要自己建（iOS 首启、用户清过沙箱后都会遇到）：写仍然返回 `true`，
     * 而**建目录失败也只是返回 `false`**，不抛——这条与「写失败不打断业务」同一个口径。
     */
    @Test
    fun `目录不存在时自动创建`() = runBlocking {
        val parent = tempDir()
        val dir = File(parent, "nested")
        try {
            val store = storeOf(dir)
            assertTrue("缺目录时写应成功", store.write(Row(id = 3)))
            assertEquals(Row(id = 3), store.read())
        } finally {
            parent.deleteRecursively()
        }
    }

    private companion object {
        /** 落盘文件名（与生产一样由调用方给，不随测试变）。 */
        const val FILE = "store.json"
    }
}
