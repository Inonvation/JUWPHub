package edu.jxslu.schedule.ui.week

import edu.jxslu.schedule.platform.ScheduleBackgroundFiles
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import edu.jxslu.schedule.platform.scheduleBackgroundDirPath
import edu.jxslu.schedule.platform.scheduleBackgroundFilePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath

/**
 * [ScheduleBackgroundFiles] 的 iOS 实现（iOS 移植 M4，2026-11-26）：`appDataDir()/schedule_bg/`
 * 里的那一个文件。
 *
 * ## 目录名与拼路径为什么不在这里写
 *
 * 偏好里只存文件名（`DisplayPrefs.bgImageName`），读图、删图、查存在、孤儿清扫四处都靠
 * 「名字 + 目录」拼路径。目录名与拼接规则收在共享层的
 * `scheduleBackgroundDirPath` / `scheduleBackgroundFilePath` 一处（判据是
 * `ScheduleBackground.isValidFileName`），`:app` 那一份是
 * `ScheduleBackgroundStore.DIR_NAME` + `fileFor`，两者逐字同源——目录名各写一份，
 * 跨端恢复备份（备份里带 `bgImageName`）就对不上文件，用户的背景静默消失。
 *
 * `appDataDir()` 在 iOS 上解析到沙箱 `Documents/`（见 `platform/AppDirs.ios.kt` 的实测记录），
 * 所以背景图会进 iCloud 备份，也在「文件」App 里可见——与日志、导出件同一层，不是缺陷。
 *
 * ## 只有 `delete` / `exists` 的原因
 *
 * 与 `:app` 的装配根同一个理由（见 `commonMain/platform/ScheduleBackgroundFiles.kt` 的 KDoc）：
 * 写入在平台动作里已经完成（选图 → 解码压缩 → 落盘，文件名从平台动作回传给 `MeViewModel`），
 * 搬进共享层的只有「删旧文件」与「文件还在不在」两步。**别在这里补 `write`**：多一个方法而当前
 * 没有调用方，只是给两条装配根各留一半实现机会。
 *
 * ## 失败一律不抛
 *
 * 删不掉只会在磁盘上留一个孤儿文件，冷启动的清扫链会扫掉；让设置页因为「删不掉」弹一句红字，
 * 是把磁盘问题转嫁给用户。所以 `delete` 全程 `runCatching`。`exists` 同口径：查不动就当没有
 * （最坏结果是清掉偏好里的名字、降级成无背景，而不是让课表页崩）。
 *
 * okio 的 Apple 目标自带 posix 实现（`platformFileSystem()` = `FileSystem.SYSTEM`），
 * 不需要 `NSFileManager` 那一套——写法与 `data/repo/ScheduleShareStore.kt` 同形，
 * `metadataOrNull(path)?.isRegularFile` 与 `ui/me/hub/IosPermissionsStorage.kt` 的 iOS 侧写法一致。
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

    /**
     * 判「普通文件」而不是「路径存在」：目录、符号链接都算「没有这张图」
     * （与 `:app` 的 `ScheduleBackgroundStore.exists` 里 `file.isFile` 同一判据）。
     */
    override fun exists(fileName: String): Boolean {
        val target = scheduleBackgroundPath(fileName) ?: return false
        return runCatching {
            platformFileSystem().metadataOrNull(target)?.isRegularFile == true
        }.getOrDefault(false)
    }
}

/**
 * 背景图目录（`<appDataDir>/schedule_bg`）。目录不一定存在，写之前 `createDirectories`。
 *
 * 读图（[scheduleBackgroundPath] → 渲染层）、删图 / 查存在（[IosScheduleBackgroundFiles]）
 * 与落盘（`IosScheduleBackgroundImport`）共用这一处，别在调用方各拼一遍字符串。
 */
internal fun scheduleBackgroundDir(): Path = scheduleBackgroundDirPath(appDataDir()).toPath()

/**
 * 文件名 → 绝对路径；名字不合法（手改偏好、跨版本残留）返回 null。
 *
 * 判据与目录拼接都在共享层（[scheduleBackgroundFilePath]），这里只是换成 okio 的 [Path]。
 */
internal fun scheduleBackgroundPath(fileName: String): Path? =
    scheduleBackgroundFilePath(appDataDir(), fileName)?.toPath()
