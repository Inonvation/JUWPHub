package edu.jxslu.schedule.ui.life

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.jxslu.schedule.domain.AccountMask
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.MoneyFormat
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.ui.common.BrandMark
import edu.jxslu.schedule.ui.common.rememberSheetDismisser
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.theme.Motion

/**
 * 侧栏的**内容件**（iOS 移植 U1，2026-11-25）：头部 / 钱包区 / 行 / 段 / 弹层。
 *
 * ## 为什么从这里切开
 *
 * `SideDrawer.kt`（`:app`）里混着两类东西：一类是「这一行写什么字、点开什么」的**内容**，
 * 另一类是 `StackedDrawerState` 的手势视差、`Graph` 装配、`viewModel()` 与品牌位图资源 id
 * 这些**宿主**才有的东西。前者原样搬到这里，后者留在 `:app` 的 `SideDrawerContent`。
 *
 * 判据是机械的：这个 Composable 收的参全是「字符串 + 布尔 + 回调」就搬；一旦要
 * `LocalContext` / `Graph` / `R` / `viewModel()` 就留。搬过来的部分**一个平台 API 都没有**，
 * 所以 iOS 的抽屉壳（还没写，见 `design/ios-port.md` §12.3）可以直接复用这套内容件。
 *
 * ## 品牌位图为什么还能用 `Int`
 *
 * 「江水生活」五行用的是宿主模块的 `ic_brand_*` 位图（`R.drawable` 只有 Android 有）。
 * `ui/common/BrandMark` 就是为这件事立的缝：参数是资源 id，iOS 侧渲染为空。
 * 所以 [DrawerRow] 的 `brandIcon: Int?` 原样保留，不改成 `Painter?` 槽位——
 * 改了会让五个调用点各写一段 `Image(painterResource(...))`，等于把位图逻辑抄五遍。
 */

/** 钱包详情弹层的两项（校园卡 / 寝室电费）。 */
enum class WalletDetail { Campus, Power }

/**
 * 四行服务的「进行中」标记（2026-10-21）：侧栏行描边高亮的依据。
 * 由调用侧从各真相源收集（快趣骑行/免费计时、胖乖 Working、趣智 watering、U净 在案订单），
 * 侧栏只消费布尔——数据口径与今日页「进行中服务卡」同源，两处展示不另立判据。
 */
data class ServiceActiveFlags(
    val ride: Boolean = false,
    val water: Boolean = false,
    val qzxy: Boolean = false,
    val ujing: Boolean = false,
)

/**
 * 侧栏头部身份区：头像 + 姓名 + 遮罩学号 + 班级（整块进学籍信息页），右侧独立「扫一扫」。
 *
 * ## 为什么三行文案由这里拼（2026-11-25 iOS 抽屉对齐）
 *
 * 搬进来之前这三个参数是已经拼好的字符串（`title` / `subtitle` / `avatarText`），拼接逻辑
 * 留在 `:app` 的 `SideDrawerContent` 里。iOS 抽屉要同一块身份区，就得把「姓名 + 遮罩学号
 * 拼成一行、班级做次标题、空值退到哪句兜底文案」再抄一遍——那是同一个事实的第二份拷贝。
 * 现在只传原始值（[name] / [studentId] / [className]），文案规则只在这里一处。
 *
 * 参数全是「字符串 + 回调」，没有平台 API：两端同一个 Composable。
 *
 * ## 头像为什么是文字而不是位图槽位
 *
 * Android 侧的头像一直是**姓名首字的圆形文字头像**（`profileName.firstOrNull()`，
 * 取不到时「水」），全仓没有任何位图解码实现（`app` 与 `shared` 里 `avatar` 只有这一处）。
 * 所以这里不给可空的 `Painter` 槽位：两端渲染的是同一份文字头像，观感本来就一致。
 * 真要接真实头像位图时再引入槽位——现在加就是给不存在的调用方留参数。
 *
 * @param name 姓名（教务学籍卡导入）。空串 → 整行退到「尚未配置」。
 * @param studentId 学号（原样传，内部走 [AccountMask]；遮不住的不显示，见那个函数的 KDoc）。
 * @param className 班级（教务学籍卡导入）。空串 → 「登录教务后自动填班级」。
 * @param onOpenStudentInfo 头像 / 姓名 / 班级整块点击：进学籍信息页。
 * @param onScan 扫一扫。**null = 本平台没有扫码能力，那一枚整颗不渲染**
 *   （`design/ios-port.md` §11.2 ④：留一个点了没反应的入口比没有更糟）。
 */
@Composable
fun DrawerHeader(
    name: String,
    studentId: String,
    className: String,
    onOpenStudentInfo: () -> Unit,
    onScan: (() -> Unit)? = null,
) {
    // 2026-10-20 用户口径：学号跟在姓名后面，班级单独做次标题。
    val masked = AccountMask.maskStudentId(studentId).orEmpty()
    val title = buildList {
        if (name.isNotBlank()) add(name)
        if (masked.isNotBlank()) add(masked)
    }.joinToString(" ").ifBlank { "尚未配置" }
    val subtitle = className.ifBlank { "登录教务后自动填班级" }
    val avatarText = name.firstOrNull()?.toString() ?: "水"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像 + 姓名 + 班级整块可点：进「学籍信息」（2026-10-21 用户口径，
        // 该入口从教务 Tab 的服务列表挪到这里）。扫一扫是独立按钮，不包进这块。
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = "学籍信息", onClick = onOpenStudentInfo)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = avatarText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // 右侧留白由外层 `weight(1f)` 吃掉，扫一扫缺席时身份块自然铺到右缘
        if (onScan != null) ScanCircle(onClick = onScan)
    }
}

@Composable
private fun ScanCircle(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "drawerScanScale")
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = "扫一扫",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.QrCodeScan,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 账号与登录：紧跟头像信息，不再占底部位置。 */
@Composable
fun AccountRow(onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "账号与登录", onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("账号与登录", style = MaterialTheme.typography.labelMedium, color = muted)
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * 钱包卡的三格文案（校园卡余额 / 电子账户 / 寝室电费），由 [walletCardTexts] 产出。
 *
 * 单独成类型而不是三个 `String` 参数：三格是**一组**（水位、失败、未开启三种状态各自成句），
 * 分开传会让调用点每次重复凑三段 `when`。
 */
data class WalletCardTexts(
    val balance: String,
    val account: String,
    val power: String,
)

/**
 * 钱包卡「写什么字」的唯一实现（2026-11-25 随 iOS 抽屉下沉）。
 *
 * ## 为什么要从 `:app` 挪进来
 *
 * 这三格的口径（未开启 / 读到值 / 读完了但没有 / 还在读 / 读失败）原先写在
 * `SideDrawerContent` 里，各是一段 `when`。iOS 抽屉要同一张钱包卡，照抄就是同一份事实的
 * 第二份拷贝——和 [DrawerHeader] 三行文案当初下沉是同一个理由。
 *
 * ## 三条别改坏
 *
 * 1. **顺序即优先级**：`noCredentials / roomNotBound` 是终态，压在旧读数之上——那份读数
 *    已经不属于当前房间；`loading` 压在 `error` 之上，刷新中不喊上一次的失败。
 * 2. **有旧读数时不退回「读取中…」**：侧栏展开每次都静默刷一轮，把已显示的电量抹掉再填
 *    回来，读起来像在闪。
 * 3. **小数用 [TextFmt.decimals] 不用 `"%.1f".format`**：后者是 JVM 专有 API，
 *    `commonMain` 编不过；两者的等价性由 `TextFmtTest` 全量比对钉住。
 *
 * @param campusEnabled 一卡通总开关；关掉时余额格写「未开启」，不看有没有旧快照。
 * @param cardFen 正式卡余额（分）；null = 还没读到。
 * @param accountFen 电子账户余额（分）；null → 「—」（它没有加载中文案，只有得手与否）。
 * @param balanceLoaded 余额状态是否已确定（尝试过、或压根没得加载）。
 * @param power 寝室电费卡状态机（`LifeViewModel` 那份，字段语义见 [PowerCardState]）。
 */
fun walletCardTexts(
    campusEnabled: Boolean,
    cardFen: Long?,
    accountFen: Long?,
    balanceLoaded: Boolean,
    power: PowerCardState,
): WalletCardTexts {
    val balance = when {
        !campusEnabled -> "未开启"
        cardFen != null -> MoneyFormat.fromFen(cardFen)
        balanceLoaded -> "—"
        else -> "读取中…"
    }
    val account = accountFen?.let { MoneyFormat.fromFen(it) } ?: "—"
    val remain = power.snapshot?.meter?.remain
    val powerText = when {
        power.noCredentials || power.roomNotBound -> "—"
        remain != null -> "${TextFmt.decimals(remain, 1)} 度"
        power.loading -> "读取中…"
        power.error != null -> "读取失败"
        else -> "—"
    }
    return WalletCardTexts(balance = balance, account = account, power = powerText)
}

/**
 * 钱包卡：校园卡余额大字 + 充值按钮 + 「电子账户 / 寝室电费」并排一行。
 *
 * @param onRecharge 充值入口。**null = 本平台没有这条充值路径，按钮整颗不渲染**
 *   （`design/ios-port.md` §11.2 ④）；iOS 抽屉就是这一档——两个充值弹层的宿主
 *   （`RechargeHost` / `PowerBankPayScreen`）还在 `:app`。
 */
@Composable
fun WalletHero(
    balanceText: String,
    accountValue: String,
    powerText: String,
    /** 有充值等待到账（2026-10-22）：「校园卡余额」标签让位给状态文案，恢复旧钱包卡口径。 */
    arrivalWatching: Boolean,
    onCampusDetail: () -> Unit,
    onPowerDetail: () -> Unit,
    onRecharge: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = onSurface.copy(alpha = 0.55f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp),
    ) {
        // 等待到账时小标题行让位给状态文案（旧生活页钱包卡同款口径），正常时还原标签；
        // animateContentSize 已挂在 Column 上，切换不跳。转圈 + 主色小字与
        // 校园卡设置页的等待行同族。
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (arrivalWatching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "有充值正在确认到账…",
                    style = MaterialTheme.typography.labelSmall,
                    color = primary,
                )
            } else {
                Text("校园卡余额", style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = balanceText,
                // 进 state 档、退 tap 档：新内容淡入要看得清，旧内容让位要快——
                // 两个方向同长会读成"两行字互相叠着糊一下"。这不是随手取的差值，
                // 别把两者统一成同一档。
                transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.exit()) },
                label = "drawerBalance",
                modifier = Modifier.weight(1f),
            ) { text ->
                // 金额按长度分三档：¥ 开头最多 7 字保持 34sp，8–9 字降 28sp，
                // 再长降 24sp——大字号或四位数余额不会把充值按钮挤走。
                val balanceSize = when {
                    !text.startsWith("¥") -> 20.sp
                    text.length <= 7 -> 34.sp
                    text.length <= 9 -> 28.sp
                    else -> 24.sp
                }
                Text(
                    text = text,
                    // tnum：余额刷新时数字宽度不抖
                    style = MaterialTheme.typography.bodyLarge
                        .copy(fontFeatureSettings = "tnum"),
                    fontSize = balanceSize,
                    lineHeight = when (balanceSize) {
                        34.sp -> 40.sp
                        28.sp -> 34.sp
                        else -> 30.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "校园卡详情", onClick = onCampusDetail)
                        .padding(vertical = 2.dp),
                )
            }
            // 与余额数字同一行、垂直居中：上一版挂在「校园卡余额」小标题那一行，看着没对齐。
            // onRecharge 为 null（本平台没有充值路径）时整颗不渲染，余额大字自己吃掉这一行。
            if (onRecharge != null) {
                RechargeButton(
                    onClick = onRecharge,
                    // 一卡通充值有到账等待时锁入口（2026-10-22）：等待中再下一单会覆盖基线
                    // 与 pendingRecharge，第一笔的到账判定就丢了——与设置页「充值」按钮的
                    // enabled = watching == null 同一道闸。
                    enabled = !arrivalWatching,
                )
            }
        }
        // 电子账户与寝室电费并成一行（2026-10-21 用户口径）：两项都是可点的余额，
        // 各占一半、中间一道细分隔线；从原来的两行（小字 + 电费行）压到一行，
        // 也收掉了钱包区与「生活」段之间的空档。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(onSurface.copy(alpha = 0.04f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WalletStat(
                label = "电子账户",
                value = accountValue,
                modifier = Modifier.weight(1f),
                onClick = onCampusDetail,
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(onSurface.copy(alpha = 0.08f)),
            )
            WalletStat(
                label = "寝室电费",
                value = powerText,
                modifier = Modifier.weight(1f),
                onClick = onPowerDetail,
            )
        }
    }
}

/**
 * 钱包区的一格余额（2026-10-21）：小字标签 + 右对齐的数值，整格可点开详情。
 * 数值走 [AnimatedContent]：异步加载时「—」→ 金额是渐变而不是跳字。
 */
@Composable
private fun WalletStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "$label 详情", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = onSurface.copy(alpha = 0.55f),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.exit()) },
            label = "drawerWalletStat",
            modifier = Modifier.weight(1f),
        ) { text ->
            Text(
                text = text,
                // tnum：金额 / 电量的数字宽度固定，刷新时不会把分隔线挤动
                style = MaterialTheme.typography.labelMedium
                    .copy(fontFeatureSettings = "tnum"),
                color = onSurface.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RechargeButton(
    onClick: () -> Unit,
    /** 一卡通到账等待中为 false（2026-10-22）：置灰而非隐藏，闸的理由写在调用点。 */
    enabled: Boolean = true,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "drawerRechargeScale")
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(99.dp))
            .background(if (enabled) primary.copy(alpha = 0.10f) else primary.copy(alpha = 0.05f))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClickLabel = "充值",
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AppIcons.Wallet01,
            contentDescription = null,
            tint = if (enabled) primary else muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "充值",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) primary else muted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailSheet(
    detail: WalletDetail,
    campusCardFen: Long?,
    campusAccountFen: Long?,
    campusAt: Long?,
    powerRemain: Double?,
    powerRoom: String?,
    powerAt: Long?,
    /** 一卡通到账等待中锁「充值」（2026-10-22）：与钱包区 RechargeButton 同一道闸。 */
    campusRechargeLocked: Boolean,
    onDismiss: () -> Unit,
    onOpenBill: () -> Unit,
    onRecharge: () -> Unit,
) {
    val isCampus = detail == WalletDetail.Campus
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    // 按钮动作先播完弹层退场再执行（账单页 / 充值弹层不会和这张弹层重叠一帧）
    val sheetState = rememberModalBottomSheetState()
    val dismissThen = rememberSheetDismisser(sheetState, onDismiss)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = if (isCampus) "校园卡" else "寝室电费",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isCampus) {
                    campusCardFen?.let { MoneyFormat.fromFen(it) } ?: "—"
                } else {
                    powerRemain?.let { "${TextFmt.decimals(it, 2)} 度" } ?: "—"
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (isCampus) {
                    "正式卡余额；电子账户另计"
                } else {
                    powerRemain?.let { "约 ${MoneyFormat.fromYuan(it * 0.62)}（按 0.62 元/度估算）" } ?: "以缴费平台账单为准"
                },
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            Spacer(Modifier.height(12.dp))
            if (isCampus) {
                DetailRow("正式卡", campusCardFen?.let { MoneyFormat.fromFen(it) } ?: "—")
                DetailRow("电子账户", campusAccountFen?.let { MoneyFormat.fromFen(it) } ?: "—")
                DetailRow("更新时间", formatTime(campusAt))
            } else {
                DetailRow("房间", powerRoom ?: "—")
                DetailRow("更新时间", formatTime(powerAt))
            }
            // 旧「生活」页的钱包说明随页面删除后挪到这里：详情弹层是余额/电费的
            // 唯一常驻落点，这两句用户要求保留的口径不能丢。
            Text(
                text = if (isCampus) BALANCE_NOTE else ELECTRICITY_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { dismissThen(onOpenBill) },
                    modifier = Modifier.weight(1f),
                ) { Text("账单") }
                Button(
                    onClick = { dismissThen(onRecharge) },
                    modifier = Modifier.weight(1f),
                    // 只锁一卡通那格的充值；电费充值没有到账轮询，不受影响
                    enabled = !isCampus || !campusRechargeLocked,
                ) { Text("充值") }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeChoiceSheet(
    onDismiss: () -> Unit,
    onPickCampus: () -> Unit,
    onPickPower: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val dismissThen = rememberSheetDismisser(sheetState, onDismiss)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("选择充值项目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "两个系统各充各的，请确认要充哪一个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RechargeTile(
                    title = "一卡通充值",
                    subtitle = "正式卡 / 电子账户",
                    icon = AppIcons.Wallet01,
                    modifier = Modifier.weight(1f),
                    // 退场播完再弹下一张，两张弹层不叠在同一帧
                    onClick = { dismissThen(onPickCampus) },
                )
                RechargeTile(
                    title = "寝室电费充值",
                    subtitle = "缴费平台 · 房间电表",
                    icon = AppIcons.Bolt,
                    modifier = Modifier.weight(1f),
                    onClick = { dismissThen(onPickPower) },
                )
            }
        }
    }
}

@Composable
private fun RechargeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = title, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** `MM-dd HH:mm`（钱包详情弹层的「更新时间」）。时区跟系统走，与 `AppClock` 同口径。 */
internal fun formatTime(at: Long?): String {
    if (at == null) return "—"
    val t = AppClock.dateTimeOf(at)
    return "${TextFmt.pad(t.monthNumber)}-${TextFmt.pad(t.dayOfMonth)} " +
        "${TextFmt.pad(t.hour)}:${TextFmt.pad(t.minute)}"
}

/**
 * 侧栏一段的段头（生活 / 江水生活 / 学习 …）。
 *
 * 单独拆出来是因为 iOS 抽屉的分组列表在 `LazyColumn` 里，段头得是**一个 item**，
 * 不能跟着 [DrawerSection] 的 `ColumnScope` 走；两处共用这一份字级与间距，
 * 免得 iOS 侧再调一遍（`labelSmall` + onSurface 45% + 24/14/2dp）。
 */
@Composable
fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        // 2026-10-21 收窄：上一版段头上下 18/4dp，钱包区收成一行后仍显松，压到 14/2dp
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** 侧栏一段（生活 / 江水生活 / 学习）：段头 + 若干行。 */
@Composable
fun DrawerSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    DrawerSectionHeader(title)
    Column(content = content)
}

/**
 * 侧栏的分隔线（1dp，`outlineVariant` 50%）。
 *
 * 只在 iOS 抽屉用：Android 侧栏靠段头与 16dp 留白分段，从来不用线；iOS 的
 * `ModalDrawerSheet` 底色是 `surfaceContainerLow`、没有落影，身份卡与列表之间要一条线才读得
 * 出层级。用 1dp 的 `Box` 而不是 `HorizontalDivider`：后者在不同 material3 小版本里名字与
 * 可用性都变过（`Divider` → `HorizontalDivider`），不值得为一条线赌一次构建。
 */
@Composable
fun DrawerDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/**
 * 侧栏一行入口（2026-10-21 双命中区）：
 * - **行本体**（图标 + 名称 + 空白）→ [onClick]，进对应二级页；
 * - **右侧状态 / 余额**（[trailing]，有 [onTrailingClick] 才可点）→ 开入口弹窗。
 *   命中区加大（垂直 24dp），别让状态文案小到点不准；
 * - 状态字号从 labelSmall 提到 **titleSmall + SemiBold + 主色**：余额 / 空闲数是
 *   这行最该被看到的信息，也是可点的目标，视觉上要立得住；
 * - 状态与箭头**收在同一列右缘**（2026-10-21）：金额一律右对齐、箭头始终在最右，
 *   各行扫下来是一条竖线（此前可点状态多一层 8dp 内边距，金额比不可点的行缩进一截）；
 *   右侧命中区**至少 48dp 宽**（U净未登录 / 无收藏店时只剩一枚箭头，16dp 宽点不准），
 *   内容仍贴右缘（`Arrangement.End`），箭头位置不因加宽而左移；
 * - [active] = 对应服务**正在进行中**（骑行 / 开水 / 用水 / 洗烘）：整行加主色描边 +
 *   浅 tonal 底（180ms 过渡，与 `AppCard.highlighted` 同族口径），展开侧栏一眼即见
 *   「这行有事在跑」——右侧状态数字只是信息，描边才是「现在就值得看」的信号；
 * - 按下整行轻微回缩（0.985，与扫一扫 / 付款码同族），松手回弹。
 */
@Composable
fun DrawerRow(
    label: String,
    icon: ImageVector? = null,
    /** 官方品牌图标（drawable 位图，自带彩色底）：与 [icon] 二选一，brandIcon 优先。 */
    brandIcon: Int? = null,
    trailing: String? = null,
    trailingLoading: Boolean = false,
    /** 服务进行中：主色描边点亮（见 KDoc）。 */
    active: Boolean = false,
    onClick: () -> Unit,
    onTrailingClick: (() -> Unit)? = null,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = onSurface.copy(alpha = 0.78f)
    val rowShape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        label = "drawerRowPress",
    )
    // 高亮过渡与 AppCard.highlighted 同一族（都走 `Motion.state()`）：描边色 / 底色 / 描边宽
    // 三处一起动。2026-11-06 归并到 Motion 之后不再引 `AppCardDefaults.HighlightTransitionMillis`
    // ——那个常量现在只是它的别名，绕一层没有意义。
    val borderColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = Motion.state(),
        label = "drawerRowBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        } else {
            Color.Transparent
        },
        animationSpec = Motion.state(),
        label = "drawerRowContainer",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(rowShape)
            .background(containerColor)
            .border(1.dp, borderColor, rowShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = label,
                onClick = onClick,
            )
            .heightIn(min = 50.dp)
            // 纵向内边距 1dp：给右侧 48dp 的命中区留出空间，行高仍是 50dp
            .padding(horizontal = 12.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            // 品牌位图 24dp：彩色底自带视觉重量，比线性图标放大一档才不显小，
            // 但仍比文字行高小一截（M3 行高 56dp 的 43%），不把行撑高
            brandIcon != null -> BrandMark(brandIcon, 24.dp)
            else -> Icon(
                imageVector = icon!!,
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (trailingLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        // 状态 + 箭头是一组、钉在行的右缘：金额在左、箭头在右，所有行共用同一条收边。
        // 数值变化（余额 / 空闲数 / 工时异步到达）走淡入淡出，不跳字。
        val trailingSlot: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = trailing,
                    transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.exit()) },
                    contentAlignment = Alignment.CenterEnd,
                    label = "drawerRowTrailing",
                ) { text ->
                    if (text == null) {
                        Spacer(Modifier.size(0.dp))
                    } else {
                        Text(
                            text = text,
                            // tnum：余额 / 空闲数刷新时数字宽度不抖
                            style = MaterialTheme.typography.titleSmall
                                .copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (trailing != null) Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = if (onTrailingClick != null) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    } else {
                        onSurface.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (onTrailingClick != null) {
            Row(
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        onClickLabel = "$label 余额与入口",
                        onClick = onTrailingClick,
                    )
                    // 20sp 文本 + 上下 14dp = 48dp 命中区（行内容高度正好 48dp）
                    .padding(vertical = 14.dp),
                // 内容贴右缘：补出来的宽度只长在左侧，箭头仍与其它行对齐
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailingSlot()
            }
        } else {
            trailingSlot()
        }
    }
}

/** 左下角的设置入口：齿轮 + 文字，和右下付款码对称。 */
@Composable
fun SettingsEntry(onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .clickable(onClickLabel = "设置", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Settings01,
            contentDescription = null,
            tint = onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "设置",
            style = MaterialTheme.typography.labelLarge,
            color = onSurface.copy(alpha = 0.7f),
        )
    }
}

/**
 * 右下角的付款码图标入口：按下轻微缩放，松开回弹。
 *
 * @param onClick **null = 本平台没有付款码页，整颗不渲染**（`design/ios-port.md` §11.2 ④）。
 */
@Composable
fun PaymentCodeEntry(onClick: (() -> Unit)?) {
    if (onClick == null) return
    val primary = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        label = "drawerPayScale",
    )
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(99.dp))
            .background(primary.copy(alpha = 0.10f))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = "出示付款码",
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.QrCodeScan,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "付款码",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = primary,
        )
    }
}

/** 一卡通余额说明（钱包详情弹层，2026-09-24 口径；旧生活页删掉后挪到侧栏）。 */
private const val BALANCE_NOTE =
    "一卡通余额与流水都来自一卡通平台，服务端落账有延迟，刚发生的消费或充值可能暂时不计入。"

/** 电费说明（钱包详情弹层，2026-09-24 用户给的原话；只有这一处口径）。 */
private const val ELECTRICITY_NOTE = "受服务器影响，实际费用可能有延迟，电费不足时请及时缴费"
