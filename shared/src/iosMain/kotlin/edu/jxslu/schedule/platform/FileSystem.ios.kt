package edu.jxslu.schedule.platform

import okio.FileSystem

/**
 * iOS 侧落点：okio 的 Apple 目标自带 posix 实现（`nativeMain` 里的
 * `PosixFileSystem`，`FileSystem.SYSTEM` 就是指它），不需要 `NSFileManager` 那套。
 *
 * 目录来自 [appDataDir]（`NSFileManager` 的 Application Support，实测落到沙箱
 * `Documents/`），本行只决定「怎么碰文件」。
 *
 * **本机（Windows）编不了这一行**：iOS target 的编译要 macOS + Xcode，本任务只做到
 * 「写法与 okio 的 Apple 源集一致」，真机首次编译时再确认一遍。
 */
actual fun platformFileSystem(): FileSystem = FileSystem.SYSTEM
