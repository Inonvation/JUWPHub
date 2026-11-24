package edu.jxslu.schedule.data.local

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「内存 StateFlow 是真值 + 磁盘是镜像」基类（2026-11-06 审计 C2 第二轮）的契约。
 *
 * 三条最容易被后人改坏的：构造期同步读（首帧真值）、写盘失败不回滚内存、
 * 写盘失败不抛。前两条直接对应 `AiApplyHistoryStore.append` 注释里那个「日期覆盖
 * 重复插入」的历史坑。
 *
 * iOS 移植 S7（2026-11-24）起基类只收文件名 + 目录，夹具改成临时目录（同 `JsonFileStoreTest`）。
 */
class MirroredJsonStoreTest {

    @Serializable
    private data class Row(val id: Int = 0, val name: String = "")

    private class TestStore(dir: String) : MirroredJsonStore<Row>(
        fileName = FILE,
        serializer = Row.serializer(),
        empty = Row(),
        dir = dir,
    ) {
        suspend fun set(name: String) = mutate { it.copy(name = name, id = it.id + 1) }

        suspend fun wipe() = clearStored()
    }

    private fun tempDir(): File = File.createTempFile("mirrored", "").also { it.delete(); it.mkdirs() }

    private fun fileIn(dir: File): File = File(dir, FILE)

    /** 构造期就得是真值：不能「先空表、再跳一下」。 */
    @Test
    fun `构造期同步读拿到真值`() = runBlocking {
        val dir = tempDir()
        try {
            TestStore(dir.absolutePath).set("第一份")
            val fresh = TestStore(dir.absolutePath)
            assertEquals(Row(id = 1, name = "第一份"), fresh.state.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 内存先更新、磁盘整份覆盖写；新实例读到的就是这份。 */
    @Test
    fun `mutate 立即更新内存并落盘`() = runBlocking {
        val dir = tempDir()
        try {
            val store = TestStore(dir.absolutePath)
            assertEquals(Row(), store.state.value)
            store.set("甲")
            assertEquals("甲", store.state.value.name)
            assertEquals(1, store.state.value.id)
            assertEquals(store.state.value, TestStore(dir.absolutePath).state.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * 写盘必然失败（目标其实是个**非空目录**：改名覆盖不了、删不掉、也写不进去），
     * 内存必须照常更新且不抛——否则调用方（多在落库事务之后）会把整次操作判成失败，
     * 用户重试一遍就把业务做两次。
     */
    @Test
    fun `写盘失败不回滚内存也不抛`() = runBlocking {
        val dir = tempDir()
        try {
            val blocker = fileIn(dir).also { it.mkdirs() }
            File(blocker, "occupied").writeText("占位")
            val store = TestStore(dir.absolutePath)
            assertEquals(Row(), store.state.value)
            store.set("写不进去也得记住")
            assertEquals("写不进去也得记住", store.state.value.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 换账号那种「连磁盘一起清」：内存回 empty，新实例也读不到旧值。 */
    @Test
    fun `clearStored 清磁盘与内存`() = runBlocking {
        val dir = tempDir()
        try {
            val store = TestStore(dir.absolutePath)
            store.set("甲")
            store.wipe()
            assertEquals(Row(), store.state.value)
            assertEquals(Row(), TestStore(dir.absolutePath).state.value)
            assertTrue("磁盘那份要真的删掉：${fileIn(dir).absolutePath}", !fileIn(dir).exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private companion object {
        const val FILE = "mirrored.json"
    }
}
