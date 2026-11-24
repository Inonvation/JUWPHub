package edu.jxslu.schedule.ui.common

/**
 * 位图的中性类型（iOS 移植 P2，2026-11-24）。
 *
 * ## 为什么需要它
 *
 * `android.graphics.Bitmap` 是 Android 专有类型，既进不了 `commonMain`（元数据门禁看不见 JDK），
 * Kotlin/Native 上也没有。而「保存到相册」与「分享图片」这两条链路除了位图本身没有别的
 * 共同语言，所以这里的形状和 P0 立 `domain/BarcodeMatrix` 那次一模一样：
 * **只依赖 Kotlin 标准库的中性类型 + 两端 `actual` 包一层原生对象**。
 *
 * ## 最小面：只有宽高（这是刻意的）
 *
 * 现在只有两个使用者：保存到相册、分享文件。两者都不读像素——保存是把原生位图交给系统编码，
 * 分享是把文件句柄交给分享面板。所以这里**不暴露任何像素读写**：
 *
 * | 没提供 | 为什么现在不加 |
 * |---|---|
 * | ARGB 像素数组 | 水印 / 拼接 / 裁剪一个都还没搬；搬的时候真正需要的是「在原生画布上画」，不是逐像素搬运 |
 * | 构造器 | 只有 [decodeBitmap] 能造出它，调用方拿不到「从哪儿 new 一个」的口子 |
 * | `scale` / `density` | Android 的 `Bitmap` 自带密度、iOS 的 `UIImage` 用 `scale`，两套语义对不齐，等真有页面需要再讨论 |
 *
 * shortcut: 百宝箱的水印/拼接搬进 `commonMain` 时，按那时真正用到的成员扩这个类
 * （大概率是「画一块 ARGB 到指定位置」而不是「取整张像素」）。别提前设计成图形库。
 *
 * ## 两端的实际落点
 *
 * | 端 | 包装的原生类型 | 宽高口径 |
 * |---|---|---|
 * | Android | `android.graphics.Bitmap`（解码结果原样持有，不复制像素） | `Bitmap.width` / `.height`，即像素 |
 * | iOS | `UIImage` | `size × scale`，换算成像素 |
 *
 * 生命周期：GC 管。Android 侧不调 `recycle()`——改造前 `saveBitmapToGallery` 的调用方也没调，
 * 位图交给 GC 回收是这一版的口径；文档里不提，避免有人以为需要手动回收。
 */
expect class PixelBitmap {
    /** 像素宽。 */
    val width: Int

    /** 像素高。 */
    val height: Int
}

/**
 * 把编码过的图片字节（PNG / JPEG / WebP / GIF …）解成 [PixelBitmap]；解不出返回 null，**不抛**。
 *
 * 失败口径按调用方来：相册里挑到半截文件、网络下发的不是图片，都属于「解不出」而不是
 * 「程序出错」，所以返回 null 让调用方给文案。唯一不兜的是 OOM——超大图在两端都会直接
 * 杀进程，改造前也一样；`runCatching` 会把 `OutOfMemoryError` 一起吞掉，反而掩盖真实原因。
 */
expect fun decodeBitmap(bytes: ByteArray): PixelBitmap?
