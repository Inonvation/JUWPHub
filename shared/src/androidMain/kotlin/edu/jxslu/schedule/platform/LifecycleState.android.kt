package edu.jxslu.schedule.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle as lifecycleAware
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android 侧的实现就是官方那个 `androidx.lifecycle.compose.collectAsStateWithLifecycle`。
 *
 * 官方函数必须**别名导入**（[lifecycleAware]）：本文件的 actual 与它同名同接收者，
 * 不加别名就只能写成全限定前缀调用，而 Kotlin 不允许用全限定名调用扩展函数。
 * 行为与搬迁前逐字一致——STOPPED 时取消收集，回到 STARTED 重新订阅。
 */
@Composable
actual fun <T> StateFlow<T>.collectAsStateWithLifecycle(initialValue: T): State<T> =
    lifecycleAware(initialValue)

@Composable
actual fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T): State<T> =
    lifecycleAware(initialValue)
