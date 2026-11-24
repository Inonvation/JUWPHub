package edu.jxslu.schedule.ui.campus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.repo.CampusCalendarStore
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.InlineNoticeRow
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.launch

/**
 * 我的 → 校园服务 → 校历（DESIGN §4.34）。
 *
 * 整页就是一张校历图：**进页即整图**（满宽、按真实比例、可点开全屏手势缩放），
 * 没有任何要登录的东西。图源——已下载的校历图（教务处官网直取为主、仓库镜像兜底）
 * → 内置兜底图 → 骨架占位；
 * 刷新 = 顶栏按钮（force 绕闸门；进行中显进度圈、结果给 snackbar——已更新 / 已是最新 /
 * 失败。2026-10-06 补：原「成功全静默」被用户当成「点刷新没反应」），平时冷启动静默刷
 * （7 天闸门）。
 *
 * 点图开全屏查看器：全屏黑底 + 双指缩放/拖动，与笔记附件看大图同一组件。
 *
 * iOS 移植 W2（2026-11-25）：本页搬进共享层，只把「画图 + 全屏查看器」留给宿主
 * （[CampusCalendarImageUi]，null = 本平台画不出图，整块不渲染）。刷新、闸门、状态流、
 * 文案全部在这里，两端同一份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusCalendarScreen(
    onBack: () -> Unit,
    images: CampusCalendarImageUi? = null,
) {
    val store = Services.current.campusCalendarStore()
    val state by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var viewerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校历") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!refreshing) {
                                scope.launch {
                                    refreshing = true
                                    val result = store.refresh(force = true)
                                    refreshing = false
                                    // 手动刷新给结果反馈（2026-10-06）：成功不再全静默——
                                    // 「点刷新没反应」就是被静默坑的；冷启动仍静默（见 KDoc）
                                    val year = store.state.value.year
                                    when (result) {
                                        CampusCalendarStore.RefreshResult.Updated ->
                                            snackbar.showNotice(
                                                AppNoticeVisuals(
                                                    if (year.isBlank()) "校历已更新" else "校历已更新到 $year 学年",
                                                    tone = NoticeTone.Success,
                                                ),
                                            )
                                        CampusCalendarStore.RefreshResult.Unchanged ->
                                            snackbar.showNotice(
                                                AppNoticeVisuals(
                                                    if (year.isBlank()) "已是最新" else "已是最新（$year 学年）",
                                                    tone = NoticeTone.Info,
                                                ),
                                            )
                                        CampusCalendarStore.RefreshResult.Failed ->
                                            snackbar.showNotice(
                                                AppNoticeVisuals("刷新失败，请检查网络后重试", tone = NoticeTone.Warning),
                                            )
                                        CampusCalendarStore.RefreshResult.Skipped -> Unit
                                    }
                                }
                            }
                        },
                        enabled = !refreshing,
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(AppIcons.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(state = snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 图源：已下载镜像图优先，没有走内置兜底；两者都是本地解码，进页不卡网络。
            // 宿主没给图片能力时整块不渲染（只留下面两行说明）。
            images?.image(state.localFile, state.year) { viewerOpen = true }

            // 年次与来源说明：镜像没下到时只说内置图的归属学年，不编造
            Text(
                text = if (state.year.isNotBlank()) "$state.year 学年 · 图片来自学校公开发布，每学年自动更新"
                else "图片为内置版本，联网后自动更新到最新学年",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            // 边界说明只留一行（与快趣账号页同款口径）
            InlineNoticeRow(
                message = "校历内容以学校官方发布为准（非官方整理，可能滞后）。",
                tone = NoticeTone.Info,
            )
        }
    }

    // 全屏看图（手势缩放）。镜像图没下载到（内置兜底状态）也照开——
    // 宿主的查看器解不到文件时回退打包资源图，点击放大永远可用
    images?.viewer(state.localFile, viewerOpen) { viewerOpen = false }
}
