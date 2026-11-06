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
 * 的往返；「写两次」这条更是只有在这里才跑得到——Windows 上 `renameTo` 覆盖已存在文件
 * 会失败，走的正是 `writeBlocking` 里那条「删掉再改名 / 直接写」的兜底分支。
 */
class JsonFileStoreTest {

    @Serializable
    private data class Row(
        val id: Int = 0,
        val name: String = "",
        val tags: List<String> = emptyList(),
    )

    private fun tempFile(): File = File.createTempFile("json-store", ".json").also { it.delete() }

    private fun storeOf(file: File) = JsonFileStore(file, Row.serializer())

    @Test
    fun `往返保留字段`() = runBlocking {
        val file = tempFile()
        try {
            val store = storeOf(file)
            val row = Row(id = 7, name = "高等数学", tags = listOf("a", "b"))
            assertTrue("写应成功", store.write(row))
            assertEquals(row, store.read())
            assertTrue("写完要占字节（缓存统计用）", store.bytes() > 0)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `缺文件与坏文件都回 null 且不抛`() = runBlocking {
        val file = tempFile()
        try {
            val store = storeOf(file)
            assertNull("文件不存在 → null", store.read())
            file.writeText("{不是 JSON")
            assertNull("坏 JSON → null", store.read())
            file.writeText("""{"id":"类型不对"}""")
            assertNull("类型对不上 → null", store.read())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `重复写以最后一次为准`() = runBlocking {
        val file = tempFile()
        try {
            val store = storeOf(file)
            assertTrue(store.write(Row(id = 1, name = "旧")))
            assertTrue(store.write(Row(id = 2, name = "新")))
            assertEquals(Row(id = 2, name = "新"), store.read())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `删除后回 null 且目录里不留临时文件`() = runBlocking {
        val file = tempFile()
        try {
            val store = storeOf(file)
            store.write(Row(id = 1))
            store.delete()
            assertNull("删掉即失效", store.read())
            assertEquals("删掉后不再占字节", 0L, store.bytes())
            val leftovers = file.parentFile?.listFiles()?.filter { it.name == file.name + ".tmp" }.orEmpty()
            assertTrue("临时文件必须被改名消耗掉：$leftovers", leftovers.isEmpty())
        } finally {
            file.delete()
        }
    }
}
