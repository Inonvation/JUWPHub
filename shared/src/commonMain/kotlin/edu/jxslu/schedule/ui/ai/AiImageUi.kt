package edu.jxslu.schedule.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AI 会话图片附件的**存储**（DESIGN §4.39；会话级、不留痕）。
 *
 * ## 为什么是一条缝而不是直接调 `AiImageStore`
 *
 * `AiImageStore`（`:app` 的 `data/repo`）是 `android.net.Uri` + `BitmapFactory` +
 * `ImageImport` 的实现，`commonMain` 一个都用不了。搬页面时按 `design/ios-port.md` §11.2 ④
 * 把它收成「可空对象」：**`null` = 本平台没有落盘与解码能力**，输入区的截图整行不渲染
 * （给一个点了没反应的按钮比没有更糟）。
 *
 * ## 待发送元素为什么是 `Any`
 *
 * `AiScheduleClient.chatStream(images: List<Any>)` 的元素由装配根注入的编码器转成 data URI
 * （见那个类的注释第 6 条），Android 传的是 `java.io.File`。这里照同一条口径，
 * 共享层不认「图片」是什么类型。
 */
interface AiImageAttachments {

    /** 单次最多几张（`AiImageStore.MAX_IMAGES` 同值：4）。每张图都是 token 大户。 */
    val maxImages: Int

    /**
     * 导入一张平台侧选中的图（Android 传 `android.net.Uri`）：压缩转正后落盘。
     *
     * 返回落盘文件名；任何失败返回 `null`（调用方给一次性提示，不静默丢）。
     */
    suspend fun import(source: Any): String?

    /** 删除一张已落盘的图。文件不在也不报错（与 `File.delete()` 同口径）。 */
    suspend fun delete(fileName: String)

    /**
     * 清空全部：文件 + 内存里的缩略图缓存。
     *
     * 内存缓存也归这里：新会话 / 页面销毁 / 冷启动三处都要一起清（见 `AiImageStore` 类注释），
     * 拆成两条缝会出现「文件清了、缩略图还挂在内存里」的半清状态。
     */
    suspend fun clear()

    /** 交给 `AiScheduleClient.chatStream(images = …)` 的元素；图不在盘上返回 `null`。 */
    fun payload(fileName: String): Any?
}

/**
 * AI 会话图片的平台 UI（缩略图 / 全屏看图 / 选图 / 粘图）。
 *
 * 与 [AiImageAttachments] 分开：那个是 ViewModel 的数据面，这个是页面渲染与入口。
 * 两者由宿主用**同一份** `AiImageStore` 建起来（Android 是 `rememberAiImageUi()` 一行），
 * 收成一个对象是为了让「只给一半」在调用点就写不出来（同 `AttachmentImageUi` 的理由）。
 *
 * **全屏查看器的状态归宿主**（`openViewer` 只是「要看这张」的通知）：查看器本身是平台件
 * （Android 的 `ImageViewerDialog`），弹窗状态留在能画它的那一层，页面就不必为
 * 「宿主有没有画弹窗」操心。
 */
class AiImageUi(
    /** 文件存储；宿主把这个实例同时传给 `AiChatViewModel`。 */
    val store: AiImageAttachments,
    /** 缩略图（Android 走 `AiThumb`：采样解码 + 12MB LRU）。 */
    val thumb: @Composable (
        fileName: String,
        modifier: Modifier,
        contentDescription: String?,
    ) -> Unit,
    /** 打开全屏查看器（宿主持状态并画弹窗）。 */
    val openViewer: (String) -> Unit,
    /** 相册多选（结果逐张交给 `AiChatViewModel.addImage`）；`null` = 没有选图入口。 */
    val pickImages: (() -> Unit)? = null,
    /** 从系统剪贴板取图（截图后直接粘）；`null` = 没有这条入口。 */
    val pasteImages: (() -> List<Any>)? = null,
)

/**
 * API Key 的读写（DESIGN §4.39：落 `EncryptedSharedPreferences`，不进 DataStore / 备份）。
 *
 * ## 为什么是三个回调而不是一个接口
 *
 * `AiCredentialStore`（`:app` 的 `data/ai`）没有接口，也不该为一次页面搬移去改它
 * （它绑着 `EncryptedSharedPreferences` 与 `secure_ai.xml` 的备份排除规则）。
 * 页面只需要「读 / 写 / 清」三件事，收成可空对象即可。
 *
 * **`null` = 本平台没有加密凭证存储**：配置页的「API Key」与「测试连接」两行、
 * 主页的「还没有配置」引导卡据此不渲染。iOS 要等 Keychain 实现，
 * 升级条件写在 `IosServices` 的同名注释里。
 */
class AiApiKeys(
    val read: () -> String?,
    val save: (String) -> Unit,
    val clear: () -> Unit,
)
