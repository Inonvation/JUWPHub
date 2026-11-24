package edu.jxslu.schedule.platform

import okio.FileSystem

/**
 * 本机文件系统（iOS 移植 S7，2026-11-24）。
 *
 * ## 为什么 commonMain 不能直接用 `FileSystem.SYSTEM`
 *
 * okio 的 `FileSystem` 在 commonMain 里是 `expect abstract class`，但它的 companion 里
 * **只声明了 `SYSTEM_TEMPORARY_DIRECTORY`**；`SYSTEM` 这个成员是各平台源集里各自的
 * `actual companion object` 加上去的（JVM/Android 是 nio 实现，Apple 目标是 `PosixFileSystem`）。
 * 也就是说 commonMain 的元数据里根本没有 `FileSystem.SYSTEM` 这个名字——
 * 写进去 `:shared:metadataCommonMainClasses` 当场报 unresolved reference。
 * 与 `AppDirs` / `Logger` 同一个办法：commonMain 只认这条缝，用哪个实现由平台源集决定。
 *
 * ## 两端的落点
 *
 * | 平台 | 实现 | 底层 |
 * |---|---|---|
 * | Android | `FileSystem.SYSTEM` | okio-jvm 的 `JvmSystemFileSystem`（`java.nio.file`） |
 * | iOS | `FileSystem.SYSTEM` | okio Apple 目标的 `PosixFileSystem`（`appleMain` 的 actual） |
 *
 * 两端恰好同名，但**名字的可见性是按源集算的**：这一层必须由 actual 提供，不能提前到
 * commonMain 写死——那就是这段缝存在的唯一理由。
 *
 * ## 目录不在这条缝里
 *
 * 「文件放哪」由 [appDataDir] 回答（Android 就是 `filesDir`，与搬移前同一路径，老用户
 * 的缓存原地可读）；这条缝只回答「怎么碰文件」。单测要临时目录时**不要动这条缝**，
 * 把目录当参数传给 `JsonFileStore` 之类即可。
 */
expect fun platformFileSystem(): FileSystem
