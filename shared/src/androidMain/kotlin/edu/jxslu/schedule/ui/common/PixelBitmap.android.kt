package edu.jxslu.schedule.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * [PixelBitmap] 的 Android 实现（iOS 移植 P2，2026-11-24）：**原样持有** `android.graphics.Bitmap`。
 *
 * 不复制像素、不改密度、不 recycle：这一层只是给共享层一个不认识 `Bitmap` 的句柄，
 * 保存到相册时再把它取回来交给 `compress`。所以内存占用与改造前完全一样。
 *
 * 构造器 `internal`：只有同模块的 [decodeBitmap] 与 `BitmapSave.android.kt` 用得上，
 * 共享层外面拿不到「凭空造一个」的口子（见 `PixelBitmap.kt` 的最小面说明）。
 */
actual class PixelBitmap internal constructor(internal val nativeBitmap: Bitmap) {
    actual val width: Int get() = nativeBitmap.width
    actual val height: Int get() = nativeBitmap.height
}

/**
 * 解码图片字节：`BitmapFactory.decodeByteArray`，解不出给 null。
 *
 * 与 `BitmapFactory` 的默认行为一致（不指定 `inPreferredConfig`，让它自己选）——
 * 改造前 `QuickScan` / 相册选图那条链路也是这么解的，换 `ARGB_8888` 会让大图内存翻倍。
 */
actual fun decodeBitmap(bytes: ByteArray): PixelBitmap? {
    if (bytes.isEmpty()) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::PixelBitmap)
}
