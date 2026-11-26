package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.session.WebCookies
import edu.jxslu.schedule.platform.IosKeychain
import edu.jxslu.schedule.platform.IosLocalNotifications
import edu.jxslu.schedule.platform.Logger
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.ui.common.IosSharePresenter
import edu.jxslu.schedule.ui.me.MeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 启动自检屏（2026-11-26，闪退第三次排查专用）。
 *
 * ## 为什么要有它
 *
 * 到这一刻为止，「打开就闪退」已经三次，而每次都拿不到任何现场：包体事实全绿
 * （架构 / minos / rpath / 依赖库 / Info.plist / Assets.car），用户侧没有 Console、没有 Xcode，
 * `logs/app.log` 又因为沙箱目录那条 bug 自己先断过。上一版加的整屏报错只能接住**组合期**的
 * Kotlin 异常——如果崩在系统框架里（ObjC 断言 / 野指针），连一句提示都不会有。
 *
 * 所以这一屏改个思路：**把启动路径拆成十几个独立的探针，一条一条跑，结果直接画在屏幕上**。
 *
 * | 现象 | 说明 |
 * |---|---|
 * | 屏幕上一行都没有 | 崩在 Compose 首帧之前（CMP 运行时 / dyld / 包体），与业务代码无关 |
 * | 停在「… 第 N 条」 | 就是第 N 条把它打死的；这一条要么抛了 ObjC 异常，要么野指针 |
 * | 某条变红 `FAIL` | 那是 Kotlin 异常，文字里带原因，按它修 |
 * | 全绿且点「进入主界面」能进 | 启动链路没问题，问题在页面组合 |
 *
 * 「… 第 N 条」先上屏再跑，中间插了 `delay`，就是为了让进程被打死之前那一行**已经画出来**。
 *
 * ## 这一屏是临时的
 *
 * 保留到定位为止；定位后 `MainViewController()` 改回直接 `IosRoot(services)`，
 * 本文件与 `IosBootGuard.kt` 里的探针部分一起删（留 `IosBootCrashScreen` 那条兜底即可）。
 */
@Composable
internal fun IosBootSelfTest() {
    val lines = remember { mutableStateListOf<String>() }
    var services by remember { mutableStateOf<IosServices?>(null) }
    var showUi by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        probe(lines, "1 沙箱目录 appDataDir()") { appDataDir() }
        probe(lines, "2 写日志（NSLog + okio 落盘）") {
            Logger.i(TAG_BOOT, "自检：写一条日志")
            "已写"
        }
        services = probe(lines, "3 服务装配 IosServices()") {
            IosServices().also { Services.install(it) }
        }
        val svc = services
        if (svc == null) {
            lines.add("→ 第 3 条失败，后面全部跳过")
            return@LaunchedEffect
        }
        probe(lines, "4 偏好库 DisplayPrefsStore.themeMode") {
            svc.displayPrefs().themeMode.first().name
        }
        probe(lines, "5 偏好库 onboardingSeen") {
            svc.displayPrefs().onboardingSeen.first().toString()
        }
        probe(lines, "6 课表仓库 ensureDefaults（Room 写）") {
            svc.scheduleRepository().ensureDefaults()
            "已写"
        }
        probe(lines, "7 课表仓库 courses（Room 读）") {
            "库里 ${svc.scheduleRepository().courses.first().size} 门课"
        }
        probe(lines, "8 一卡通凭证存储（Keychain 句柄）") {
            if (svc.yktCredentialStore() == null) "null（本平台没有）" else "非空"
        }
        probe(lines, "9 Keychain 读写删往返") {
            val key = "selftest"
            IosKeychain.set("probe", key, "v1")
            val read = IosKeychain.get("probe", key)
            IosKeychain.delete("probe", key)
            "读到 $read"
        }
        probe(lines, "10 教务 Cookie 库（WKWebsiteDataStore）") {
            val header = WebCookies.headerFor(JwUrls.SCHEDULE_LIST)
            if (header == null) "无 cookie（正常：还没登录）" else "有 cookie（${header.length} 字）"
        }
        probe(lines, "11 通知中心代理（UNUserNotificationCenter）") {
            IosLocalNotifications.installForegroundPresentation()
            "已派发"
        }
        probe(lines, "12 我的页 ViewModel（MeViewModel）") {
            MeViewModel(svc.scheduleRepository()).let { "已建" }
        }
        probe(lines, "13 抽屉条目 iosDrawerEntries()") {
            "共 ${iosDrawerEntries().size} 条"
        }
        lines.add("全部通过。点下面的按钮进主界面；点完就退出，说明崩在页面组合。")
    }

    val ui = services
    if (showUi && ui != null) {
        // 与正式根组合的顺序一致：先装分享宿主，再排提醒，最后才是页面。
        IosSharePresenter()
        LaunchedEffect(ui) {
            delay(1_200)
            runCatching { ui.warmUpReminders() }
        }
        // 页面组合里再崩，就是 Kotlin 异常走 IosBootCrashScreen、系统框架崩则整屏消失。
        IosRoot(services = ui)
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF7FAF7)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("启动自检（诊断用）", style = TextStyle(color = Color.Black, fontSize = 17.sp))
            Text(
                text = "屏幕停在哪一行，就是哪一步把 App 打死了。请整屏截图发给开发者。",
                style = TextStyle(color = Color(0xFF555555), fontSize = 12.sp),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = TextStyle(
                        color = when {
                            line.startsWith("FAIL") -> Color(0xFF8C1D18)
                            line.startsWith("OK") -> Color(0xFF1B5E20)
                            else -> Color(0xFF333333)
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            if (ui != null) {
                Button(
                    onClick = { showUi = true },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text("进入主界面")
                }
            }
        }
    }
}

/**
 * 跑一条探针：先上屏「… 名字」，等一帧真的画出来，再执行、再把这一行改成 OK / FAIL。
 *
 * 那个 `delay` 不是装饰：不打它，同步的探针（Keychain / WebKit）会和上屏挤在同一帧里，
 * 进程被打死时屏幕上还看不到是哪一条。
 */
private suspend fun <T> probe(
    lines: MutableList<String>,
    name: String,
    block: suspend () -> T,
): T? {
    lines.add("… $name")
    delay(220)
    val result = runCatching { block() }
    lines[lines.lastIndex] = when {
        result.isFailure -> "FAIL $name → ${result.exceptionOrNull()}".take(400)
        else -> "OK $name → ${result.getOrNull()}".take(400)
    }
    delay(120)
    return result.getOrNull()
}
