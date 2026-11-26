package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import edu.jxslu.schedule.ui.life.DrawerSectionHeader
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 「全部功能」页（2026-11-26，用户要求抽屉对齐 Android 之后新增）。
 *
 * ## 为什么要这一页
 *
 * 2026-11-25 那一版把 iOS 抽屉做成了**站点地图**：把 `SubpageScreen` 白名单按段全列出来，
 * 六段 36 行。Android 的侧栏只是一条短面板（身份卡 / 我的·设置 / 钱包卡 / 江水生活 / 付款码），
 * 于是真机上「拉开抽屉」看到的是一屏目录，和 Android 完全是两套信息架构。
 *
 * 但直接删掉那六段会让 30 多个页面在 iOS 上**再也点不到**（电费、消费流水、收件箱、宿舍报修、
 * 请假、校历、学籍信息…这些在 Android 侧栏里本来就有）。所以整份清单搬到这里：
 * 抽屉回到短面板，二级页一个不丢。
 *
 * ## 与抽屉的关系
 *
 * 条目数据仍是唯一的 [iosDrawerEntries]，分组仍是那个 `section` 字段——**不新增第二份清单**。
 * 抽屉那一侧现在只渲染「江水生活」段里的二级页（洗衣账单），其余段全在这里。
 *
 * ## 为什么不新增 `SubpageScreen` 枚举值
 *
 * 这一页是 iOS 专属的容器，Android 没有对应页面。加一个枚举值会让 `:app` 的
 * `SubpageActivity` 也要跟着多一个分支（那是纯噪音）。所以它走**本地覆盖层**：
 * `IosRoot` 里一个 `showAllFeatures` 布尔，与首启登录页（`jwOpen`）同一做法。
 */
@Composable
internal fun IosAllFeaturesScreen(
    onBack: () -> Unit,
    onPick: (IosRoute) -> Unit,
) {
    // 与抽屉同一份过滤口径：快捷入口（由抽屉上半区承担）不进这份清单，已经下线的页面也不进。
    val sections = remember {
        iosDrawerEntries()
            .filterNot { it.route.screen in IOS_DRAWER_QUICK_SCREENS }
            .groupBy { it.section }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
        ) {
            item(key = "title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("返回") }
                    Text(
                        text = "全部功能",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            sections.forEach { (section, rows) ->
                item(key = "h-$section") { DrawerSectionHeader(section) }
                items(rows, key = { it.route.screen.name }) { entry ->
                    CompactDrawerRow(entry = entry, onClick = { onPick(entry.route) })
                }
            }
        }
    }
}
