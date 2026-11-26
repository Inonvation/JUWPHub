package edu.jxslu.schedule.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 背景图的**路径口径**（`ScheduleBackgroundFiles` 的公共部分）。
 *
 * 这三个值（目录名、目录路径、文件名 → 绝对路径）是 `delete` / `exists` / 读图三处的唯一拼接口径：
 * Android 那一份在 `ScheduleBackgroundStore`（`File(dir, name)`），iOS 那一份走这里再 `toPath()`。
 * 拼错不会有异常，只会让「查存在」永远返回 false、把偏好里的名字清掉——用户看到的现象是
 * 「背景图自己没了」。所以字面值要钉死，跨端恢复备份（备份里带 `bgImageName`）才对得上文件。
 */
class ScheduleBackgroundFilePathTest {

    private val dataDir = "/data/user/0/edu.jxslu.schedule/files"

    /** 目录名与 `:app` 的 `ScheduleBackgroundStore.DIR_NAME` 逐字相同，改名等于让老背景图失联。 */
    @Test
    fun dirNameIsPinned() {
        assertEquals("schedule_bg", SCHEDULE_BACKGROUND_DIR_NAME)
    }

    /** 目录 = `<dataDir>/schedule_bg`，文件 = 目录 + 文件名，中间只有一个分隔符。 */
    @Test
    fun pathIsDataDirPlusDirPlusName() {
        assertEquals("$dataDir/schedule_bg", scheduleBackgroundDirPath(dataDir))
        assertEquals(
            "$dataDir/schedule_bg/bg_20260922_101500_1a2b.jpg",
            scheduleBackgroundFilePath(dataDir, "bg_20260922_101500_1a2b.jpg"),
        )
    }

    /** null / 名字非法（手改偏好、跨版本残留）一律 null：调用方据此返回 false，不会去 stat 一个拼出来的怪路径。 */
    @Test
    fun invalidNamesGiveNull() {
        assertNull(scheduleBackgroundFilePath(dataDir, null))
        assertNull(scheduleBackgroundFilePath(dataDir, ""))
        assertNull(scheduleBackgroundFilePath(dataDir, "   "))
        assertNull("上跳目录", scheduleBackgroundFilePath(dataDir, "../notes_img/a.jpg"))
        assertNull("子目录", scheduleBackgroundFilePath(dataDir, "sub/a.jpg"))
        assertNull("反斜杠", scheduleBackgroundFilePath(dataDir, "sub\\a.jpg"))
        assertNull("上级目录", scheduleBackgroundFilePath(dataDir, ".."))
        assertNull("超长", scheduleBackgroundFilePath(dataDir, "a".repeat(65) + ".jpg"))
    }

    /** iOS 的沙箱路径形态（多段、无结尾分隔符）同样只做拼接，不做规范化。 */
    @Test
    fun iosSandboxPathIsPlainConcatenation() {
        val sandbox =
            "/var/mobile/Containers/Data/Application/1F2E3D4C/Documents"
        assertEquals(
            "$sandbox/schedule_bg/bg_1.jpg",
            scheduleBackgroundFilePath(sandbox, "bg_1.jpg"),
        )
    }
}
