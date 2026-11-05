package edu.jxslu.schedule.ui.common

import android.os.SystemClock

/**
 * 进程级防连点：**同一个目标**在 500ms 内只放行第一次。
 *
 * 只用于「双击会开出两个相同窗口」的入口（二级页、设置）。页面内的业务按钮
 * 仍用各自的 busy/防抖状态，不要拿这个替代。
 *
 * 为什么按目标分槽（2026-11-05）：此前只有一个全局时刻，**任意两次**窗口启动挨在
 * 500ms 内都会吞掉第二次。U净「侧栏扫一扫 → 进 U净 页 → 识别成功即跳下单页」正是
 * 这条形态：进页那次记下的时刻把紧随其后的下单页导航吃掉，用户看到的是「跳到了
 * U净 页但没识别」——识别其实成功了（草稿已开），写操作链断在导航上且没有任何提示。
 * 分槽后「同一页连点两次」仍被挡，「A 页开完立刻开 B 页」不再受影响。
 *
 * 状态是进程级单例且无同步，**只在主线程调用**（现有调用方都在 UI 线程）。
 */
object LaunchGate {
    private const val WINDOW_MS = 500L
    private var lastKey: String? = null
    private var lastAt = 0L

    /**
     * [key] 是目标标识（二级页用 `SubpageScreen.name`，设置用 `"settings"`）。
     * 同 key 在 [WINDOW_MS] 内重复调用返回 false。
     *
     * [now] 只是给单测注入时钟的口子，生产调用不要传。
     */
    fun allow(key: String, now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (key == lastKey && now - lastAt < WINDOW_MS) return false
        lastKey = key
        lastAt = now
        return true
    }
}
