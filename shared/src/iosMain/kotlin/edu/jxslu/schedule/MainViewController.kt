package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.local.CourseEntity
import edu.jxslu.schedule.data.local.DB_VERSION
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.local.juwDatabasePath
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.DataSelfTestResult
import edu.jxslu.schedule.ui.DataSelfTestStrip
import edu.jxslu.schedule.ui.theme.JuwTheme
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import edu.jxslu.schedule.ui.week.WeekScreen
import edu.jxslu.schedule.ui.week.WeekViewModel
import platform.UIKit.UIViewController

/**
 * iOS 侧入口（P3，2026-11-24；M1 起是产品页面）。
 *
 * Swift 侧通过 `MainViewControllerKt.MainViewController()` 拿到这个 UIViewController，
 * 再用 `UIViewControllerRepresentable` 塞进 SwiftUI。这是 Compose Multiplatform 在 iOS 上的
 * 标准接法：Compose 负责画，Swift 只负责窗口与生命周期。
 *
 * ## 现在的页面结构
 *
 * | 区域 | 内容 | 说明 |
 * |---|---|---|
 * | 主体 | [WeekScreen]（共享层同一个页面） | 真实 Room 数据；空库时显示「去导入课表」 |
 * | 底部 | [DbSelfTestStrip] 一行 | 读写探针，**保留是因为它是「数据链路通了」的唯一屏上证据** |
 * | 覆盖层 | [JwLoginScreen] | 点「去导入」进来的教务登录 WebView（WKWebView） |
 *
 * 搬掉的两个旧面板：`WeekGridPreview`（样例网格）与 `LinkValidationScreen`（定点小数对照）。
 * 它们的验收目的都已达成——真网格取代样例网格，小数口径由 2418 条 JVM 单测继续钉住。
 *
 * ## 装配
 *
 * [IosServices] 必须在**任何**共享 ViewModel 构造之前安装，所以放在
 * `ComposeUIViewController` 之前——那是 Swift 调用进来的第一行。
 * 与 Android 侧 `JuwApplication.onCreate` 里的 `Services.install(GraphServices(this))` 对称。
 */
fun MainViewController(): UIViewController {
    val services = IosServices()
    Services.install(services)
    // 首启把作息表与默认学期写进库（Android 在 JuwApplication.ensureDefaults 里做同一件事）
    services.warmUp()
    return ComposeUIViewController { IosRoot(services) }
}

@Composable
private fun IosRoot(services: IosServices) {
    JuwTheme {
        var jwOpen by remember { mutableStateOf(false) }
        if (jwOpen) {
            JwLoginScreen(onClose = { jwOpen = false })
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // ViewModel 挂在组合上即可：iOS 侧没有 Android 的配置变更重建，
                    // `remember` 的生命周期就等于这一屏的生命周期。
                    val viewModel = remember { WeekViewModel(services.scheduleRepository()) }
                    WeekScreen(
                        viewModel = viewModel,
                        // 空态的「去导入课表」→ 教务登录页（M2 第一步：先把登录页跑起来）
                        onOpenJwImport = { jwOpen = true },
                        // 其余平台动作在 iOS 侧都还没有实现，留空即可——
                        // 传 null / 空 lambda 的入口本来就不渲染（见 WeekScreen 头部参数表）。
                    )
                }
                DbSelfTestStrip()
            }
        }
    }
}

/**
 * 教务登录页（M2 第一步，2026-11-25）：用共享的 [WebContentView] 装一个 WKWebView。
 *
 * ## 这一版做到哪儿
 *
 * 只做到「能把统一身份认证页打开、能登录、能读回当前 URL」。
 * **还没有接**：把 WebView 里的 Cookie 交给共享层的 Ktor 会话去抓课表——那一步要
 * Ktor 版的 `JwReadSession`（`:app` 那份走 OkHttp），属于 M2 的后半段。
 *
 * 所以现在从这一页登录成功后返回，课表仍然是空的。这不是坏掉，是**还没接完**。
 *
 * ## 为什么顶栏自己做
 *
 * iOS 侧没有 `SubpageActivity` 那套返回栈，这一页由 [IosRoot] 的布尔状态控制进出，
 * 顶栏就是一个返回按钮 + 当前地址。等 M2 接完导入流程，这里会换成完整的一页。
 */
@Composable
private fun JwLoginScreen(onClose: () -> Unit) {
    val controller = remember { WebViewController() }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // 两个回调挂在**控制器**上而不是组合上：控制器比组合活得久（视图销毁后仍在），
    // 所以用 remember 装一次即可；`by remember` 写法会让它在每次重组时被换掉。
    remember(controller) {
        controller.onPageFinished = { url -> currentUrl = url }
        controller.onError = { message -> error = message }
        controller
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("返回课表") }
            Text(
                text = error ?: currentUrl ?: JwUrls.ENTRY,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            WebContentView(
                controller = controller,
                url = JwUrls.ENTRY,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 数据库自检（P2 验收，2026-11-24）：真实写入一门探针课 → 按 id 读回 → 删掉 → 再数一遍。
 *
 * 界面与状态机在 `ui/DataSelfTestStrip.kt`（commonMain），本机门禁能编；这里只留平台相关的
 * 十几行。探针用固定 `timetableId = 999999` 且写完即删，不会污染真实数据。
 */
@Composable
private fun DbSelfTestStrip() {
    DataSelfTestStrip(
        probe = {
            val dao = juwDatabase().courseDao()
            val probe = CourseEntity(
                timetableId = 999_999,
                name = "iOS 数据自检",
                teacher = "-",
                position = "-",
                day = 1,
                startSection = 1,
                endSection = 1,
                weeksCsv = "1",
            )
            val id = dao.upsert(probe)
            val readBack = dao.getById(id)
            dao.delete(probe.copy(id = id))
            val left = dao.getForTimetable(999_999).size
            if (readBack?.name == probe.name && left == 0) {
                DataSelfTestResult(ok = true, text = "DB v$DB_VERSION · 写入并读回 1 门 · 清理后剩 $left 门")
            } else {
                DataSelfTestResult(ok = false, text = "读写不一致：读回「${readBack?.name}」，残留 $left 门")
            }
        },
        footnote = juwDatabasePath(),
        emptyHint = "数据库自检：进行中…",
    )
}
