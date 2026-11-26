package edu.jxslu.schedule.platform

import edu.jxslu.schedule.domain.ScheduleBackground

/**
 * 课表页背景图的**文件能力**（iOS 移植 S17，2026-11-25；2026-11-26 补 [exists]）。
 *
 * ## 为什么只有这两个方法
 *
 * 背景图这条链在 `:app` 里分三段：弹系统选图器（`PickVisualMedia`）→ 解码压缩落盘
 * （`ImageImport` + `ScheduleBackgroundStore.importUri`）→ 写偏好、删旧文件（`MeViewModel`）。
 * 前两段都是 Android 专有物（`ContentResolver` / `BitmapFactory` / `EXIF`），第三段是纯逻辑。
 * 搬进 `commonMain` 的只有第三段，它需要平台代劳的只有两件：**把旧文件删掉**、
 * **确认新名字对应的文件真的在盘上**。写入本身仍在平台动作里完成（文件名从平台动作传回 VM）。
 *
 * 所以这里只放这两个：**多一个方法而没有调用方，就是给两条装配根各留一半的实现机会**
 * （见 [AppServices] 的形状说明）。
 *
 * ## null 的语义
 *
 * [AppServices.scheduleBackgroundFiles] 返回 null = 本平台没接背景图落盘：
 * 选图入口整组不渲染、VM 里也不删文件（没有文件可删）。
 * 两端现在都给真实现（Android 是 `FilesDir/schedule_bg/`，iOS 是 `appDataDir()/schedule_bg/`），
 * 但**这个可空性不能收掉**：JVM 单测不装装配根（`MeViewModel` 里用 `runCatching` 兜的就是这一档），
 * 而将来若再多一个目标平台，它的第一版实现同样可能什么都没有。
 */
interface ScheduleBackgroundFiles {

    /**
     * 删掉一个背景图文件；[fileName] 为 null 或文件不存在都当无事发生。
     *
     * 不抛异常：删不掉只会在磁盘上留一个孤儿文件，冷启动的 `sweep` 会扫掉
     * （`JuwApplication.ensureDefaults` 那条链），不该因为删不掉而让设置页报错。
     */
    suspend fun delete(fileName: String?)

    /**
     * 背景图文件是否还在磁盘上（`fileName` 为 null 或名字不合法一律 false）。
     *
     * **为什么不是 suspend**：这是一次 stat，两端实现都是同步 IO（Android 是 `File.isFile`，
     * iOS 是 okio 的 `metadataOrNull`）；而唯一调用点的形状是
     * `WeekScreen(backgroundExists = (String) -> Boolean)`——一个普通 lambda
     * （`LaunchedEffect` 里换名时调一次），做不成 suspend 回调，硬要 suspend 就得在 UI 层
     * `runBlocking` 读盘。宁可让这一次 stat 留在调用线程上。
     *
     * 不抛异常：查不出来一律 false（与 [delete] 同一失败口径）——把「查不动」当成「没有」，
     * 最坏结果是把偏好里的名字清掉、降级成无背景，而不是让课表页因为磁盘异常崩掉。
     */
    fun exists(fileName: String): Boolean
}

/**
 * 背景图目录名（`<appDataDir>/schedule_bg`）。
 *
 * **两端逐字相同**，`:app` 那一份是 `ScheduleBackgroundStore.DIR_NAME`：偏好里只存文件名，
 * 读图 / 删图 / 孤儿清扫都靠「名字 + 目录」拼路径，目录名换一处就跨端对不上文件
 * （备份里带 `bgImageName`，恢复后背景静默消失）。
 */
const val SCHEDULE_BACKGROUND_DIR_NAME = "schedule_bg"

/**
 * 背景图目录的绝对路径（`<dataDir>/schedule_bg`）。
 *
 * [dataDir] 是平台的应用私有目录（`appDataDir()` 两端都不带结尾分隔符），这里只做字符串拼接，
 * 不做规范化——真正的路径解析交给 okio / `java.io.File`。
 */
fun scheduleBackgroundDirPath(dataDir: String): String = "$dataDir/$SCHEDULE_BACKGROUND_DIR_NAME"

/**
 * 文件名 → 绝对路径；[fileName] 为 null 或名字不合法（手改偏好、跨版本残留）返回 null。
 *
 * 合法性白名单在 [ScheduleBackground.isValidFileName]——只放行字母、数字与 `_` `-` `.`，
 * 所以 `../`、`/`、`\` 这些能越出私有目录的取值全部被挡下，不需要再单独判一遍。
 * 与 `:app` 的 `ScheduleBackgroundStore.fileFor` 是同一个判据。
 */
fun scheduleBackgroundFilePath(dataDir: String, fileName: String?): String? {
    val name = fileName ?: return null
    if (!ScheduleBackground.isValidFileName(name)) return null
    return scheduleBackgroundDirPath(dataDir) + "/" + name
}
