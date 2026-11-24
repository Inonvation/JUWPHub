package edu.jxslu.schedule.data.ai

import edu.jxslu.schedule.platform.Logger
import java.io.File
import okio.ByteString.Companion.toByteString

/**
 * `:app` 侧的 [AiScheduleClient] 构造入口（iOS 移植 S15，2026-11-24）。
 *
 * ## 为什么需要它
 *
 * 客户端已搬进 `:shared`，那里没有 `java.io.File`。而唯一的调用点
 * `ui/ai/AiChatViewModel` 传的是 `imageStore.fileFor(...)` 出来的 `List<File>`，
 * 那条页面本轮**不许动**。共享层的 `chatStream` 因此把 `images` 声明成 `List<Any>`
 * （`List<File>` 按协变可直接塞进去），把「元素 → data URI」这一步用构造参数注入。
 * 读文件、base64 编码就留在这里。
 *
 * ## 口径（与迁移前逐字一致）
 *
 * - mime 固定 `image/jpeg`：落盘时已强制转 JPEG（`AiImageStore` 走
 *   `ImageImport(forceJpeg = true)`），不需要再嗅探；
 * - 单张读不到就**跳过这一张**（不整批失败），只记一条 warn；
 * - 不是 `File` 的元素一律跳过（缺省编码器就是恒 null，iOS 侧要接时另给一份）。
 *
 * shortcut: 只覆盖 ui/ai 这一条调用点；ui/ai 搬进共享层时删掉本文件，
 * 装配根直接给共享层一份「字节 → data URI」的编码器（那时入参也不再是 `java.io.File`）。
 */
fun createAiScheduleClient(): AiScheduleClient = AiScheduleClient.create(::fileDataUri)

/** 一张图片文件 → `data:image/jpeg;base64,…`；读不到返回 null。 */
private fun fileDataUri(source: Any): String? {
    val file = source as? File ?: return null
    if (!file.isFile) return null
    return runCatching { "data:image/jpeg;base64," + file.readBytes().toByteString().base64() }
        .onFailure { Logger.w(TAG, "encode image failed: ${it::class.simpleName}") }
        .getOrNull()
}

private const val TAG = "AiScheduleClient"
