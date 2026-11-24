package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * [BrandMark] 的 Android 实现：按资源 id 取位图并画出来。
 *
 * **必须用 `Image` 而不是 `Icon(painter)`**：`Icon` 会把位图整体染成 `LocalContentColor`
 * （真机上呈黑色块）。`ContentScale.Fit` 保持位图自身的比例，品牌图的底色与轮廓是素材
 * 自带的，不要再套圆形底座。
 *
 * 这里的 `resId` 是**调用方模块**的 id（`edu.jxslu.schedule.R.drawable.*`），本模块不引用
 * 那个 `R`——`painterResource(Int)` 是运行期查表，编不出来只会在真机上崩，改调用点时留意。
 */
@Composable
actual fun BrandMark(resId: Int, size: Dp) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
