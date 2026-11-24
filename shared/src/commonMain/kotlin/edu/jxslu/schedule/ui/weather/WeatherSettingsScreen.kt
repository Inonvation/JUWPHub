package edu.jxslu.schedule.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.weather.WeatherApi
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsNote
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.me.MeViewModel

/**
 * 设置 → 功能 → 天气（DESIGN §3.29）。
 *
 * **默认关**：关着时今日页顶栏不占位、一次网络请求都不发；开启后顶栏日期下面出现
 * 一行 emoji + 一句话，点那行进天气详情页。
 *
 * 开关写 [MeViewModel]（DataStore `weather_enabled`），与其它功能开关同一层。
 *
 * ## 为什么 ViewModel 是必填参数（iOS 移植 V4，2026-11-25）
 *
 * `viewModel()` 那个 Composable 入口要 Compose 1.9，共享层拿不到
 * （`design/ios-port.md` §11.1）。所以由宿主建好传进来：Android 是
 * `viewModel { MeViewModel(...) }`（转屏不重建），iOS 是 `remember { ... }`。
 * 本页没有别的平台动作——`onOpenWeather` 是普通导航回调，两端都给得起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSettingsScreen(
    viewModel: MeViewModel,
    onBack: () -> Unit,
    /** 查看天气详情（开关关着时也有入口，先看再决定开不开）。 */
    onOpenWeather: () -> Unit,
) {
    val enabled by viewModel.weatherEnabled.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("天气") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(title = "今日页提示") {
                SettingSwitchRow(
                    title = "在今日页显示天气",
                    subtitle = "顶栏日期下一行显示天气与提醒，点它看详情",
                    checked = enabled,
                    onCheckedChange = viewModel::setWeatherEnabled,
                    icon = AppIcons.Cloudy,
                )
                SettingItem(
                    title = "查看天气",
                    subtitle = "实时状态 · 今天与明天提醒 · 未来 7 天",
                    icon = AppIcons.SunCloud01,
                    onClick = onOpenWeather,
                )
                SettingsNote("18:00 前讲今天，18:00 起讲明天，方便提前准备")
            }

            FooterDisclaimer(
                "天气数据来自 **Open-Meteo（CC BY 4.0）**；查询点固定为校区" +
                    "（${WeatherApi.CAMPUS_LABEL}），**不读手机定位**。**预报仅供参考**。",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
