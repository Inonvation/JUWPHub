package edu.jxslu.schedule.ui.week

import edu.jxslu.schedule.domain.ScheduleBackground
import edu.jxslu.schedule.platform.ScheduleBackgroundFiles
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath

/**
 * [ScheduleBackgroundFiles] 的 iOS 实现（iOS 移植 M4，2026-11-26）：`appDataDir()/schedule_bg/`
 * 里的那一个文件。
 *
 * ## 目录名为什么必须与 `:app` 一致
 *
 * 偏好里只存文件名（`DisplayPrefs.bgImageName`），读图、删图、孤儿清扫三处都靠「名字 + 目录」
 * 拼路径。目录名换成别的，跨端恢复备份（备份里带 `bgImageName`）就对不上文件——用户的背景
 * 静默消失。`:app` 那一份是 `ScheduleBackgroundStore.DIR_NAME = "schedule_bg"`，这里逐字相同。
 *
 * `appDataDir()` 在 iOS 上解析到沙箱 `Documents/`（见 `platform/AppDirs.ios.kt` 的实测记录），
 * 所以背景图会进 iCloud 备份，也在「文件」App 里可见——与日志、导出件同一层，不是缺陷。
 *
 * ## 只有 `delete` 的原因
 *
 * 与 `:app` 的装配根同一个理由（见 `commonMain/platform/ScheduleBackgroundFiles.kt` 的 KDoc）：
 * 写入在平台动作里已经完成（选图 → 解码压缩 → 落盘，文件名从平台动作回传给 `MeViewModel`），
 * 搬进共享层的只有「删旧文件」这一步。**别在这里补 `exists` / `write`**：多一个方法而当前
 * 没有调用方，只是给两条装配根各留一半实现机会。
 *
 * ## 失败一律不抛
 *
 * 删不掉只会在磁盘上留一个孤儿文件，冷启动的清扫链会扫掉；让设置页因为「删不掉」弹一句红字，
 * 是把磁盘问题转嫁给用户。所以 `delete` 全程 `runCatching`。
 *
 * okio 的 Apple 目标自带 posix 实现（`platformFileSystem()` = `FileSystem.SYSTEM`），
 * 不需要 `NSFileManager` 那一套——写法与 `data/repo/ScheduleShareStore.kt` 同形。
 */
internal class IosScheduleBackgroundFiles : ScheduleBackgroundFiles {

    override suspend fun delete(fileName: String?) {
        // null / 名字非法都当无事发生：前者是「本来就没图」，后者只会来自手改过的偏好
        val target = fileName?.let { scheduleBackgroundPath(it) } ?: return
        withContext(Dispatchers.Default) {
            // mustExist = false：目录里本来就没有这个文件时不报错（重复点「移除背景」会走到这里）
            runCatching { platformFileSystem().delete(target, mustExist = false) }
        }
    }
}

/**
 * 背景图目录（`<appDataDir>/schedule_bg`）。目录不一定存在，写之前 `createDirectories`。
 *
 * 读图（[scheduleBackgroundPath] → 渲染层）与删图（[IosScheduleBackgroundFiles]）共用这一处，
 * 别在调用方各拼一遍字符串。
 */
internal fun scheduleBackgroundDir(): Path = (appDataDir() + "/" + DIR_NAME).toPath()

/**
 * 文件名 → 绝对路径；名字不合法（手改偏好、跨版本残留）返回 null。
 *
 * 合法性白名单在共享层的 [ScheduleBackground.isValidFileName]——只放行字母、数字与 `_` `-` `.`，
 * 所以 `../`、`/`、`\` 这些能越出私有目录的取值全部被挡下，不需要再单独判一遍。
 * 与 `:app` 的 `ScheduleBackgroundStore.fileFor` 是同一个判据、同一份实现。
 */
internal fun scheduleBackgroundPath(fileName: String): Path? =
    if (ScheduleBackground.isValidFileName(fileName)) scheduleBackgroundDir() / fileName else null

/** 目录名与 `:app` 的 `ScheduleBackgroundStore.DIR_NAME` 逐字相同（见类 KDoc 第一段）。 */
private const val DIR_NAME = "schedule_bg"
