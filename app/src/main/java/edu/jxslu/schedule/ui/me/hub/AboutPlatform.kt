package edu.jxslu.schedule.ui.me.hub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import edu.jxslu.schedule.R
import edu.jxslu.schedule.startActivityOutsideApp
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.GallerySaveResult
import edu.jxslu.schedule.ui.common.saveBitmapToGallery

/**
 * 「我的 → 关于」这一组页面搬进共享层之后留在 `:app` 的平台件（iOS 移植 W1，2026-11-25）。
 *
 * 共享层那三页（`AboutScreens.kt` / `SupportDevScreen.kt` / `UpdateAvailableDialogHost.kt`）
 * 把平台能力收成可空槽位，本文件就是 Android 侧的三个实现：
 *
 * | 成员 | 喂给谁 |
 * |---|---|
 * | [openUrl] | `AboutUpdateScreen.onOpenUrl` 与 `UpdateAvailableDialogHost.onOpenUrl` |
 * | [saveRewardQrToGallery] | `SupportDevScreen.onSaveToGallery` |
 * | [REWARD_FILE_NAME] / [REWARD_QR_RES] | 上面那条落盘的文件名与打包资源 |
 *
 * 为什么不搬：`ACTION_VIEW` 拉起外部应用前要先把本窗口的推入/滑出过渡换成静止
 * （[startActivityOutsideApp]，2026-09-26 修「页面跳动」），那是 Android 的窗口事务；
 * 赞赏码是 `res/drawable-nodpi` 里的打包位图，落相册走 `MediaStore`。共享层都没有对应物。
 */

/** 赞赏码打包资源（res/drawable-nodpi/ic_reward_qr.webp），固定 1037×1037；改图要同步这行。 */
const val REWARD_QR_RES: Int = R.drawable.ic_reward_qr

/** 赞赏码落相册的文件名（`Pictures/JUWPHub`，目录口径见 domain/GalleryPaths）。 */
private const val REWARD_FILE_NAME = "JUWPHub-赞赏码.png"

/**
 * 打开站外链接（下载页 / 开源仓库）；失败返回提示文案，成功 null。
 *
 * 共享层两页共用这一份：`AboutUpdateScreen` 的「开源仓库」行与
 * `UpdateAvailableDialogHost` 的下载按钮。
 */
internal fun openUrl(context: Context, url: String): String? = try {
    context.startActivityOutsideApp(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    null
} catch (_: ActivityNotFoundException) {
    "没有可打开链接的应用"
}

/**
 * 把赞赏码写进系统相册，结果翻成共享层的 [GallerySaveOutcome]。
 *
 * 调用方（`SupportDevScreen`）已经在后台调度器上等它，这里不再套一层 `withContext`。
 * `decodeResource` 可能命中资源缓存返回共享位图，不要 recycle。
 */
suspend fun saveRewardQrToGallery(context: Context): GallerySaveOutcome {
    val bitmap = BitmapFactory.decodeResource(context.resources, REWARD_QR_RES)
    return when (
        val result = saveBitmapToGallery(
            context = context,
            bitmap = bitmap,
            displayName = REWARD_FILE_NAME,
            format = Bitmap.CompressFormat.PNG,
            quality = 100,
        )
    ) {
        is GallerySaveResult.Saved -> GallerySaveOutcome.Saved(result.location)
        is GallerySaveResult.Failed -> GallerySaveOutcome.Failed(result.message)
    }
}
