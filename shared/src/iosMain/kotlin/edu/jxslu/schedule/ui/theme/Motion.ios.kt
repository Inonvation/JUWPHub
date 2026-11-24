package edu.jxslu.schedule.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * [rememberPlatformAnimationsEnabled] 的 iOS 实现：读系统的「减弱动态效果」。
 *
 * 与 Android 的对应关系是**语义对应、不是数值对应**：Android 看的是开发者选项里
 * 「动画程序时长缩放」是否为 0，iOS 没有这个开关，最接近的是
 * 设置 → 辅助功能 → 动态效果 → 减弱动态效果。用户打开它时，本 App 的无限循环动画
 * （地图脉冲、扫描波纹、倒计时环、骨架微光）会停在静止态，与 Android 行为对齐。
 *
 * 本分支未编译验证（需要 macOS），见文件头之外的迁移记录。
 */
@Composable
internal actual fun rememberPlatformAnimationsEnabled(): Boolean =
    remember { UIAccessibilityIsReduceMotionEnabled() }
