package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * iOS 侧：[AppBackHandler] 是**空实现**（不是占位）。
 *
 * iOS 没有系统返回键，CMP 1.7.3 也没有把滑动返回手势暴露成公共 API——页面只能从顶栏
 * 返回键退出。所以「多选模式下返回先退多选」这条在 iOS 上不存在触发路径；等 CMP 升到
 * 1.8+ 接上 `ui-backhandler` 之后，改这一处即可，调用方不动。
 *
 * 参数故意保留：签名必须与 expect 一致，`enabled` / `onBack` 在本平台就是没有消费者的。
 */
@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // 本平台无系统返回输入，无事可做。
}
