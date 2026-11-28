package edu.jxslu.schedule.ui.onboarding

import edu.jxslu.schedule.ui.common.IosBrandAssets

/**
 * 首启引导在 iOS 侧的品牌位图 id（2026-11-27）。
 *
 * ## 为什么只有这一样东西
 *
 * 引导页其余四个槽位（原生教务登录、去导入页登录、返回、退场）各有自己的 iOS 装配文件，
 * 这里补的只是**素材**这一格：`OnboardingBrandIcons` 原先在 iOS 传 null，引导每屏的步骤头
 * 因此「留一块与图标等大的空白」（见该类的 KDoc）。图进了 bundle 之后这一格能填真 id。
 *
 * 映射与 Android 的 `AndroidOnboarding.kt` 逐项对齐，**包括那条容易被误判的历史现状**：
 * `wenqu` 同时服务第 3 步「一卡通 · 电费」与完成页那一行（2026-10-24 起就这样），
 * 移植时不顺手「修正」成别的图，否则「行为与视觉不变」这条验收说不清。
 *
 * 用法（`MainViewController.kt` 的 `OnboardingScreen(`）：加 `brands = iosOnboardingBrandIcons`。
 */
internal val iosOnboardingBrandIcons: OnboardingBrandIcons = OnboardingBrandIcons(
    school = IosBrandAssets.SCHOOL_BADGE,
    wenqu = IosBrandAssets.WENQU,
    pangguai = IosBrandAssets.PANG_GUAI,
    qzxy = IosBrandAssets.QZXY,
    ujing = IosBrandAssets.UJING,
    kqcx = IosBrandAssets.KQCX,
)
