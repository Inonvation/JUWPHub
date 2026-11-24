package edu.jxslu.schedule.platform

import okio.FileSystem

/**
 * Android 侧落点：okio-jvm 的 `FileSystem.SYSTEM`（`java.nio.file` 实现）。
 *
 * 与搬移前的 `java.io.File` 读写落到同一个目录、同一批文件名——路径本身由 [appDataDir]
 * 给（`context.filesDir`），这里只换「怎么读写」，不改「读写哪个文件」。
 */
actual fun platformFileSystem(): FileSystem = FileSystem.SYSTEM
