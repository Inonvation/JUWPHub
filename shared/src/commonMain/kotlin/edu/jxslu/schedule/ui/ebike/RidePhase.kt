package edu.jxslu.schedule.ui.ebike

/**
 * 快趣出行页的三种状态（DESIGN §3.9）。
 *
 * 三态是**同一个页面在变形**，不是三次跳转：地图始终在上面，底部动作条换内容。
 * 状态由数据推导，不另存一份可变状态（推导见 `RideScreen` 里的 `phase`）——
 * 多一份状态就多一个能和真值不一致的地方。
 *
 * 「选中某辆车」「选中某个停车点」不在三态里：它们只是**找车态的两种形态**
 * （动作条换成车辆卡 / 停车点卡），换的是内容不是页面。
 *
 * 声明位置：iOS 移植 P4（2026-11-25）从 `RideScreen` 挪到共享层——`RidePanels` 先搬，
 * 它要按这一档决定动作区形态，而它比 `RideScreen` 先过编译。
 */
enum class RidePhase { Finding, Riding, Settled }
