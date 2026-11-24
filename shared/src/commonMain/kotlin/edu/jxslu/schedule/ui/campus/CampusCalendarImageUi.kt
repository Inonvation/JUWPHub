package edu.jxslu.schedule.ui.campus

import androidx.compose.runtime.Composable

/**
 * 校历页的图片能力（iOS 移植 W2，2026-11-25）。
 *
 * ## 为什么是槽
 *
 * 校历页整页就是一张图，而「把文件解成能画的位图」两件都还在 `:app`：降采样解码是
 * `android.graphics.BitmapFactory` 的两遍解码（`decodeFileSampled`），全屏手势缩放是
 * `ImageViewerDialog`。`commonMain` 拿不到位图 API，iOS 侧也还没写解码器——所以**只把
 * 画图交给宿主**：刷新、7 天闸门、失败文案、状态流全部留在共享层，两端同一份。
 *
 * **null = 本平台画不出校历图**：`CampusCalendarScreen` 据此整块不渲染（口径同
 * `design/ios-port.md` §11.2 ④）。留一个点了没反应的图比没有更糟。
 *
 * ## 为什么给的是**文件名**而不是绝对路径
 *
 * 两个成员都要「按名字找图」：解码要知道文件在哪，全屏查看器（Android 的
 * `ImageViewerDialog`）自己也会拿名字去同一个 store 里查。传文件名，宿主各自解析，
 * 共享层就不用知道「应用私有目录在哪」；`null` = 还没下载到（或已被系统清掉），
 * 宿主自己兜内置图。
 */
class CampusCalendarImageUi(
    /**
     * 整页校历图（满宽、按真实比例、圆角、可点）。
     *
     * [fileName] 为 null 时宿主必须给出**内置兜底图**——校历页进页就要有东西看，
     * 不能因为「还没联网刷下来」显示空白。
     */
    val image: @Composable (
        fileName: String?,
        year: String,
        onClick: () -> Unit,
    ) -> Unit,

    /**
     * 全屏看图弹层。调用方在 [visible] 为 true 时挂它，[onDismiss] 里把状态关掉。
     *
     * 做成「由调用方给 visible」而不是宿主内部状态：弹层与页面同生命周期，
     * 宿主每次重组都会重新执行这段，内部持有 `remember` 反而会和页面的开关打架。
     */
    val viewer: @Composable (
        fileName: String?,
        visible: Boolean,
        onDismiss: () -> Unit,
    ) -> Unit,
)
