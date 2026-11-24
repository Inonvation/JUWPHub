package edu.jxslu.schedule.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AttachmentImageUi
import edu.jxslu.schedule.ui.common.EmptyHint
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.icon.AppIcons

/**
 * 某课程的笔记列表（DESIGN §3.11）。
 *
 * 每条是 [NoteItemBlock]（2026-10-21）：标题（空标题用正文首行兜底）+ 更新时间 +
 * 2 行摘要 + 图片缩略图；点条目进详情，点铅笔直接编辑。新建入口在顶栏
 * （不用 FAB，列表页窄屏遮挡小）。
 *
 * [images] 是宿主给的附件图能力（解码 + 全屏查看器都在 `:app`）；
 * **null = 本平台没有附件图能力**，缩略图整行不渲染（iOS 移植 S13）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCourseScreen(
    courseName: String,
    onBack: () -> Unit,
    onOpenNote: (id: Long, startEditing: Boolean) -> Unit,
    /** 附件图能力；null = 本平台没有。 */
    images: AttachmentImageUi? = null,
) {
    val repo = remember { Services.current.noteRepository() }
    val notes by repo.observeForCourse(courseName).collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(courseName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenNote(0L, false) }) {
                        Icon(AppIcons.Add01, contentDescription = "新建笔记")
                    }
                },
            )
        },
    ) { padding ->
        val list = notes
        when {
            list == null -> LoadingHint("正在读取笔记", Modifier.fillMaxSize().padding(padding))

            list.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyHint(
                    title = "这门课还没有笔记",
                    body = "点右上角「+」写下第一条；文字支持 Markdown 与 \$ 公式，也能插图片。",
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { note ->
                    AppCard(contentPadding = PaddingValues(0.dp)) {
                        NoteItemBlock(
                            note = note,
                            onClick = { onOpenNote(note.id, false) },
                            onEdit = { onOpenNote(note.id, true) },
                            images = images,
                            embedded = false,
                        )
                    }
                }
            }
        }
    }
}
