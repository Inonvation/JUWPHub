package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import edu.jxslu.schedule.data.local.CourseEntity
import edu.jxslu.schedule.data.local.DB_VERSION
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.local.juwDatabasePath
import edu.jxslu.schedule.ui.LinkValidationScreen
import edu.jxslu.schedule.ui.theme.JuwTheme
import edu.jxslu.schedule.ui.week.WeekGridPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.UIKit.UIViewController

/**
 * iOS 侧入口（P3，2026-11-24）。
 *
 * Swift 侧通过 `MainViewControllerKt.MainViewController()` 拿到这个 UIViewController，
 * 再用 `UIViewControllerRepresentable` 塞进 SwiftUI。这是 Compose Multiplatform 在 iOS 上
 * 的标准接法：Compose 负责画，Swift 只负责窗口与生命周期。
 *
 * 现在挂的是两块验收面板（见 [IosRoot]），不是产品界面。真做产品界面时把 [IosRoot]
 * 换成对应页面即可——Swift 侧不需要改。
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    IosRoot()
}

/**
 * 当前 IPA 挂什么。
 *
 * 上半 [WeekGridPreview]：共享课表网格的渲染验收，判「搬进 commonMain 的 UI 画得对不对」。
 * 下半 [LinkValidationScreen]：定点小数的期望/实际对照，判「iOS 算术与 Android 是否逐位一致」
 * ——这是目前唯一已知的**运行期**风险，`domain` 层宁可留一个 expect/actual 也不手写取整。
 *
 * 两块都是只读面板，没有「进入」的概念，所以用上下分栏而不是导航。比例给 3:2，
 * 网格在 iPhone 竖屏下能完整放下 7 列，小数表在下半自己滚。
 */
@Composable
private fun IosRoot() {
    JuwTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                Modifier
                    .weight(3f)
                    .fillMaxWidth(),
            ) {
                WeekGridPreview()
            }
            DbSelfTestStrip()
            SectionLabel("定点小数校验　iOS 与 Android 对照")
            Box(
                Modifier
                    .weight(2f)
                    .fillMaxWidth(),
            ) {
                LinkValidationScreen()
            }
        }
    }
}

/**
 * 数据库自检条（iOS 移植 P2 验收，2026-11-24）。
 *
 * 做的是一次**真实读写**：开库 → 写入一门探针课 → 按 id 读回 → 删掉 → 再数一遍。
 * 只显示「编译过了」没有意义，Room KMP 的真正风险在运行时：KSP 生成的实现、BundledSQLiteDriver
 * 能否在本机 ABI 上跑、Application Support 目录是否可写、suspend DAO 的查询上下文对不对。
 *
 * 探针用固定 `timetableId = 999999` 且写完即删，不会污染任何真实数据；iOS 侧目前也没有真实数据。
 *
 * 这一条是整个「能不能用」的分水岭：通了，后面的仓库 / ViewModel / 页面才有地基。
 */
@Composable
private fun DbSelfTestStrip() {
    var status by remember { mutableStateOf(DbSelfTestStatus.Running) }
    LaunchedEffect(Unit) {
        status = withContext(Dispatchers.Default) {
            runCatching {
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
                    DbSelfTestStatus.Ok(
                        "DB v$DB_VERSION · 写入并读回 1 门 · 清理后剩 $left 门",
                    )
                } else {
                    DbSelfTestStatus.Failed(
                        "读写不一致：读回「${readBack?.name}」，残留 $left 门",
                    )
                }
            }.getOrElse { e ->
                DbSelfTestStatus.Failed("开库或读写失败：" + (e.message ?: e::class.simpleName))
            }
        }
    }
    val tone = when (status) {
        DbSelfTestStatus.Running -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        is DbSelfTestStatus.Ok -> Color(0xFF2E7D32)
        is DbSelfTestStatus.Failed -> Color(0xFFC62828)
    }
    val line = when (val s = status) {
        DbSelfTestStatus.Running -> "数据库自检：进行中…"
        is DbSelfTestStatus.Ok -> "数据库自检：通过 · ${s.detail}"
        is DbSelfTestStatus.Failed -> "数据库自检：失败 · ${s.reason}"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(text = line, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tone)
        Text(
            text = juwDatabasePath(),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private sealed interface DbSelfTestStatus {
    data object Running : DbSelfTestStatus
    data class Ok(val detail: String) : DbSelfTestStatus
    data class Failed(val reason: String) : DbSelfTestStatus
}

/** 分栏之间的细线 + 一行说明；纯装饰，不承载交互。 */
@Composable
private fun SectionLabel(text: String) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(outline.copy(alpha = 0.45f)),
        )
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
