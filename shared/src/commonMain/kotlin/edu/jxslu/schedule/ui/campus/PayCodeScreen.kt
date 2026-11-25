package edu.jxslu.schedule.ui.campus

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.jxslu.schedule.domain.MoneyFormat
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.BarcodeMatrixImage
import edu.jxslu.schedule.ui.common.SkeletonBox
import edu.jxslu.schedule.ui.common.lineHeightDp
import edu.jxslu.schedule.ui.common.postNotice
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.icon.AppIcons

/**
 * 校园卡付款码页（DESIGN §3.10）：进页自动取码，大 QR + Code128 双展示。
 *
 * **防截屏与亮度拉满**：由宿主注入 [windowGuard]（Android 走 `FLAG_SECURE` +
 * `screenBrightness`，付款码等同现金；食堂/超市扫码枪对亮度敏感）。按
 * `design/ios-port.md` §11.2 ④，**null = 本平台没有这两项能力**——iOS 这版既没接
 * `UIScreen.brightness` 也没做录屏防护，传 null 时只是不加这两道保护，码本身照常显示。
 * 不给空实现：那会让人以为做了防护。
 *
 * iOS 移植 Y1（2026-11-25）搬进 `shared/commonMain`。两处变化都写在对应位置：
 * ViewModel 由宿主建（§11.2 ①），码图改由 [BarcodeMatrixImage] 用 Compose 画布画
 * （不再经过 `android.graphics.Bitmap`，理由见那份渲染件的 KDoc）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayCodeScreen(
    viewModel: PayCodeViewModel,
    onBack: () -> Unit = {},
    /** 跳消费流水页（DESIGN §4.19 B4） */
    onOpenStatement: () -> Unit = {},
    /** 防截屏 + 亮度拉满；**null = 本平台没有这两项能力**（见类注释）。 */
    windowGuard: PayCodeWindowGuard? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val matrices by viewModel.matrices.collectAsStateWithLifecycle()
    val dark by viewModel.dark.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val balanceLoaded by viewModel.balanceLoaded.collectAsStateWithLifecycle()
    val payment by viewModel.detectedPayment.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()

    // 进页自动取码
    LaunchedEffect(Unit) { viewModel.load() }

    // 检测到扣款：这一页的使命结束——自动退出，由退出后的页面弹「支付成功」
    // （结果经 PayCodeResultBus 转交，本页不弹：弹在这里会随窗口一起消失）
    LaunchedEffect(payment) {
        if (payment != null) onBack()
    }

    // 事件出口（换批失败等）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // 事件流用 postNotice 单开协程发：收集协程不卡在「等这条提示收场」上，
                // 事件成串来时新的提示才能当场顶掉旧的（见 AppNotice.kt）
                is PayCodeEvent.Notice -> scope.postNotice(snackbar, event.text, event.tone)
            }
        }
    }

    // 防截屏 + 亮度拉满：离开即恢复（成对副作用由 [windowGuard] 的实现方收口）
    DisposableEffect(windowGuard) {
        windowGuard?.enter()
        onDispose { windowGuard?.exit() }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("水宝宝一卡通") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PayCodeBody(
                state = state,
                matrices = matrices,
                dark = dark,
                onNext = {
                    haptics.tap()
                    viewModel.next()
                },
                onRetry = {
                    haptics.tap()
                    viewModel.load(force = true)
                },
            )

            // 余额行 + 流水入口（DESIGN §4.19 B3/B4）：取到才显示余额；流水入口恒在
            BalanceRow(balance, balanceLoaded = balanceLoaded, onOpenStatement = {
                haptics.tap()
                onOpenStatement()
            }, onRefresh = { viewModel.refreshBalance() })

            Text(
                text = "付款码等同现金，请勿截图或分享给他人。每个码仅可消费一次，" +
                    "用掉后服务端自动递补下一个。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 余额行：卡余额（+电费账户非零时附带）+ 「消费流水」入口 + 点击刷新余额。 */
@Composable
private fun BalanceRow(
    balance: PayCodeViewModel.BalanceSnapshot?,
    balanceLoaded: Boolean,
    onOpenStatement: () -> Unit,
    onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onRefresh)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (balance == null) {
                Text(
                    text = if (balanceLoaded) "余额暂不可用（点击重试）" else "余额获取中…（点击刷新）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            } else {
                Text(
                    text = "正式卡 ${MoneyFormat.fromFen(balance.cardFen)}" +
                        if (balance.accountFen > 0) " · 电子账户 ${MoneyFormat.fromFen(balance.accountFen)}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onOpenStatement) {
            Text("消费流水")
        }
    }
}

@Composable
private fun PayCodeBody(
    state: PayCodeUiState,
    matrices: PayCodeMatrices?,
    dark: Boolean,
    onNext: () -> Unit,
    onRetry: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val error = state as? PayCodeUiState.Error
    val success = state as? PayCodeUiState.Success
    val showCode = success != null && matrices != null
    val loading = !showCode && error == null
    // 两行文本位的高度取自样式本身，系统字体调大时骨架跟着长，不会又差一截
    val infoLine = lineHeightDp(MaterialTheme.typography.bodySmall)
    val codeLine = lineHeightDp(MaterialTheme.typography.titleMedium)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. QR 位：1:1 常驻。空着是描边空框，取码中是流光骨架块，出码原地换图
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (error != null) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        AppIcons.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                matrices != null -> BarcodeMatrixImage(
                    matrix = matrices.qr,
                    dark = dark,
                    contentDescription = "校园卡付款码二维码",
                    modifier = Modifier.fillMaxSize(),
                )

                else -> SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(14.dp))
            }
        }

        // 2. 条码位：6:1（码图 720×120），几何与 QR 一样占死
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(6f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            when {
                error != null -> Unit

                matrices != null -> BarcodeMatrixImage(
                    matrix = matrices.barcode,
                    dark = dark,
                    contentDescription = "校园卡付款码条形码",
                    modifier = Modifier.fillMaxSize(),
                )

                else -> SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(8.dp))
            }
        }

        // 3. 信息行：固定一行高。取码中这行放加载文案（它就是这一行的占位），出错留空
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(infoLine),
            contentAlignment = Alignment.Center,
        ) {
            when {
                success != null -> Text(
                    text = "${success.accountMasked} · 第 ${success.index + 1}/${success.codes.size} 个 · " +
                        "约 ${success.expiresSeconds / 3600} 小时内有效",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                loading -> Text(
                    text = if (state is PayCodeUiState.Success) {
                        "正在生成付款码…"
                    } else {
                        "正在登录校园卡，连接水宝宝并获取付款码…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                else -> Unit
            }
        }

        // 4. 卡号行：固定一行高（取码中是流光骨架条）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(codeLine),
            contentAlignment = Alignment.Center,
        ) {
            when {
                success != null -> Text(
                    text = success.current,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                )

                loading -> SkeletonBox(
                    Modifier
                        .width(220.dp)
                        .height(codeLine),
                    RoundedCornerShape(codeLine / 2),
                )

                else -> Unit
            }
        }

        // 5. 按钮行：常显，不可用置灰。**不做条件渲染**——「有下一个码」切换时行出现或消失
        // 会把下方余额行顶动（同生活页出码位那套「按钮常显」口径）
        val actionLabel = if (error != null) "重试" else "下一个码"
        val actionEnabled = if (error != null) error.canRetry else (success != null && success.hasMore)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = if (error != null) onRetry else onNext,
                enabled = actionEnabled,
            ) {
                Text(actionLabel)
            }
        }
    }
}
