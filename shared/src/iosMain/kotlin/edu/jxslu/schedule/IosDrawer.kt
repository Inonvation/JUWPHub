package edu.jxslu.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.campus.CampusCardViewModel
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.life.AccountRow
import edu.jxslu.schedule.ui.life.DrawerHeader
import edu.jxslu.schedule.ui.life.DrawerRow
import edu.jxslu.schedule.ui.life.DrawerSectionHeader
import edu.jxslu.schedule.ui.life.LifeViewModel
import edu.jxslu.schedule.ui.life.PaymentCodeEntry
import edu.jxslu.schedule.ui.life.SettingsEntry
import edu.jxslu.schedule.ui.life.WalletHero
import edu.jxslu.schedule.ui.life.lifeServiceRows
import edu.jxslu.schedule.ui.life.walletCardTexts

/**
 * iOS 抽屉宽度（2026-11-25 定 **300dp**，与 Android 侧栏同宽）。
 *
 * 为什么不用 `ModalDrawerSheet` 的默认值（M3 是 360dp）：
 *
 * 1. **内容是按 300dp 排的**。抽屉里的身份卡 / 钱包卡 / 服务行全是共享层的
 *    `ui/life/SideDrawerParts.kt`，与 Android 侧栏同一份代码，那套排版的口径就是 300dp；
 * 2. **主卡让位才读得出来**。展开时主卡整体右移「侧栏宽」并缩到 94%
 *    （`ui/common/DrawerCardTransform.kt`），侧栏占满 360dp 时 iPhone 上只剩一条 30pt 的
 *    卡片边缘，层级效果等于看不见；300dp 时能看见约 90pt，与 Android 一致。
 *
 * 这个值同时是主卡位移的上限，**两处必须是同一个数**：`MainViewController` 里
 * `rememberDrawerCardTransform(maxSlidePx = …)` 读的就是它。改宽度只改这一处。
 *
 * 宽度靠 `Modifier.width` 钉住：M3 在 `DrawerSheet` 里加的是
 * `sizeIn(min = MinimumDrawerWidth, max = MaximumDrawerWidth)`，把固定宽度套在外层即可
 * 收敛到 300dp（`sizeIn` 不再反抗）。抽屉的拖动锚点由 M3 自己按**实测宽度**更新，
 * 所以开到底时 `currentOffset` 正好是 −300dp，归一化不会越界。
 */
internal val IosDrawerWidth = 300.dp

/**
 * 抽屉里**由上面的卡片 / 行直接进**的页面，不再重复出现在下面的紧凑分组列表里。
 *
 * 判据是机械的：只要上面有一套更快的入口指向同一页（身份卡 → 学籍信息、账号与登录 →
 * 账号中心、钱包卡 → 消费流水 / 电费、服务行 → 一卡通账号 / 开水房 / U净、付款码按钮 →
 * 付款码），下面就不再列一份。Android 侧栏也是这个规矩：一个落点只有一处入口。
 *
 * 与 [iosDrawerEntries] 的关系是**减法**：白名单一行不动，40 多条仍然全在列表里——
 * 这里只是把其中已经在上面出现过的那几页滤掉。漏滤的表现是同一页出现两次（看得见），
 * 多滤的表现是点了找不到（一核对就露）——所以这两组名字必须一起改。
 */
internal val IOS_DRAWER_QUICK_SCREENS: Set<SubpageScreen> = setOf(
    SubpageScreen.STUDENT_INFO,
    SubpageScreen.ACCOUNTS,
    SubpageScreen.CAMPUS_STATEMENT,
    SubpageScreen.POWER_BILL,
    SubpageScreen.CAMPUS_CARD_SETTINGS,
    SubpageScreen.WATER,
    SubpageScreen.UJING,
    SubpageScreen.PAY_CODE,
)

/**
 * 左侧抽屉（iOS 版）：与 Android 的 `SideDrawer` **同一份内容、同一种交互**。
 *
 * ## 结构（2026-11-25 晚，按 Android 侧栏排版重排）
 *
 * | 位置 | 内容 | 部件 |
 * |---|---|---|
 * | 顶部 | 身份卡（头像 + 姓名 + 遮罩学号 + 班级，整块进学籍信息） | `DrawerHeader` |
 * | 其下 | 账号与登录 → 账号中心 | `AccountRow` |
 * | 其下 | 「全部功能」（二级页清单） | `DrawerRow` |
 * | 钱包卡 | 校园卡余额 / 电子账户 / 寝室电费 | `WalletHero` + `walletCardTexts` |
 * | 江水生活 | 一卡通账号 / 胖乖生活（开水）/ U净 + 白名单里同段的其余行 —— 缺服务的行整行不渲染 | `lifeServiceRows` + [CompactDrawerRow] |
 * | 二级页 | 白名单里「江水生活」段剩下的页（洗衣账单），接在服务行后面 | `LazyColumn` |
 * | 底部 | 左「设置」/ 右「付款码」，与 Android 两侧对称 | `SettingsEntry` + `PaymentCodeEntry` |
 *
 * 段头与行的字级、间距、描边全部来自共享层，和 Android 走同一份代码——这就是本轮要消灭的
 * 「iOS 专用仿制品」。**为什么单独一个文件**：`IosNavigation.kt` 里是路由白名单与
 * `IosRouteContent` 的渲染分支（那是另一条并行流的地盘），抽屉是纯排版组装，混在一起
 * 既超长又容易撞车。
 *
 * ## 2026-11-26 设置入口移到左下角（用户指定，与 Android 一致）
 *
 * 此前 iOS 把设置做成列表里的一行「我的 · 设置」（理由是「头像是我的」），Android 则是
 * 底部一颗齿轮。同一件事两种排法，用户从 Android 换过来要重新找。现在对齐成 Android 那条：
 * **设置只从底部左下角进**，列表里那一行删除——同一条落点只留一处入口，这也是 Android
 * 侧栏的规矩（`SideDrawer.kt` 底部 `SettingsEntry` + `PaymentCodeEntry` 一行两侧）。
 *
 * iOS 的「我的」不在底栏（三格只有今日 / 课表 / 教务），所以这颗齿轮是**唯一**入口；
 * 删行时必须同时把 `onOpenMe` 接到齿轮上，否则设置页在 iOS 上就不可达了。
 *
 * ## 2026-11-26 与 Android 侧栏逐项对齐（三处，都是「iOS 自己多出来的东西」）
 *
 * 1. **「江水生活」只起一次段头**。白名单里这一段的二级页（洗衣账单）原先在下面又开一段，
 *    Android 侧栏这一段只有一个段头（`lifeDrawerGroups` 的 `DrawerGroup("江水生活")`）；
 *    现在同段的行接在服务行后面，段头不重复。
 * 2. **列表首尾留白按 Android 的数**：上 0（`DrawerHeader` 自带 `top = 12dp`）、下 16
 *    （Android 收尾是 `Spacer(Modifier.height(16.dp))`）。原先上下各 8dp，头上多 8、尾上少 8。
 * 3. **删掉 `DrawerDivider`**：Android 侧栏没有分隔线（靠段头与留白分段），那是 iOS 侧自己
 *    加的一条线；`ModalDrawerSheet` 底色与 Android 一样是 `surfaceContainerLow`，不缺层级。
 *
 * ## 手势与遮罩
 *
 * 2026-11-25 晚改版：从「自己画遮罩 + 面板 + 点外部关闭」换成 Material3 的
 * [ModalDrawerSheet]，由 `ModalNavigationDrawer` 提供左缘右滑手势、遮罩渐隐、面板位移
 * 与无障碍语义。自己写那套手势，本机（Windows）一行都验不了，而它恰恰是最容易出错的地方。
 *
 * ## 身份区为什么只吃 `profileStudentId`
 *
 * 学号只取**学籍卡落库**那一份：iOS 没有 CAS 凭证保险箱（`design/ios-port.md` §12.3），
 * `casUsername` / `yktUsername` 那两档在 iOS 上恒为 null，所以这里与 Android 的
 * `casUsername ?: yktUsername ?: profileStudentId` 是**同一条链**，只是前两档在本平台不存在。
 *
 * `onScan` 传 null：iOS 还没有相机扫码实现，那一枚整颗不渲染（§11.2 ④）——比留一个
 * 点了没反应的圆钮好。
 *
 * ## 钱包卡的三格数从哪来
 *
 * 与 Android 侧栏**同一对 ViewModel**（都在共享层）：`CampusCardViewModel` 给余额 /
 * 电子账户，`LifeViewModel` 给寝室电费（`init` 里从本机读数种子顶起来，零网络）。
 * 文案规则不在这里写，走 [walletCardTexts]。
 *
 * 两处**刻意的差异**，都源于本平台没有充值这条链：
 *
 * 1. 余额取数点是「首次组合」而不是「抽屉展开」：Android 靠 `drawer.isVisible` 那道闸
 *    （60 秒节流），[IosDrawer] 拿不到抽屉可见性（签名固定三个参数，宿主没把 `DrawerState`
 *    传进来），所以首次组合静默拉一次（`force = false`，吃仓库自身 60 秒缓存）。代价是
 *    冷启动多一条一卡通余额请求；好过让卡片永远停在「读取中…」。
 * 2. `arrivalWatching` 恒 false：Android 那一档真，是因为侧栏的 `CampusCardViewModel` 与
 *    壳层 `RechargeHost` 是**同一个** `viewModel()` 实例；iOS 没有充值宿主
 *    （`RechargeHost` / `PowerBankPayScreen` 还在 `:app`），这份实例进不了
 *    `ArrivalState.Watching`，所以不订阅它。
 */
@Composable
internal fun IosDrawer(
    services: IosServices,
    onPick: (IosRoute) -> Unit,
    /**
     * 「我的 · 设置」= 底部左下角那颗齿轮（`SettingsEntry`）。
     *
     * 2026-11-26 起这里与 Android 完全同构：底栏只剩 今日 / 课表 / 教务 三格，设置首页
     * 从抽屉底部的左下角进（Android 那边也是这颗齿轮）。此前是列表里的一行「我的 · 设置」，
     * 两端的入口位置不一致，用户换个设备就要重新找。
     */
    onOpenMe: () -> Unit,
    /**「全部功能」那一行：整份二级页清单搬到 [IosAllFeaturesScreen]（2026-11-26 抽屉瘦身）。*/
    onOpenAllFeatures: () -> Unit,
) {
    val entries = remember { iosDrawerEntries() }
    // 「江水生活」是**唯一分成两半**的段：服务行（共享层的 `lifeServiceRows`）在钱包卡下面渲染，
    // 白名单里同段剩下的二级页（洗衣账单）接在同一段里。**不能交给下面的段循环**：
    // 那会再起一个同名段头，抽屉里看上去就是两段「江水生活」——Android 侧栏这一段只有
    // 一个段头（`lifeDrawerGroups` 的 `DrawerGroup("江水生活")`）。更不能「只跳过段头」：
    // `items` 的键在整条 LazyColumn 里必须唯一，同一个 `route.screen.name` 出两遍会崩在
    // 「key was already used」。
    val serviceSectionTitle = "江水生活"
    // 抽屉只剩「江水生活」段里的二级页（洗衣账单）：其余六段整份搬到「全部功能」页
    // （IosAllFeatures.kt，2026-11-26 抽屉瘦身）。**不要在这里把它们加回来**——
    // 抽屉要的是 Android 那条短面板，不是站点地图。
    val serviceSectionExtras = remember(entries) {
        entries
            .filterNot { it.route.screen in IOS_DRAWER_QUICK_SCREENS }
            .groupBy { it.section }[serviceSectionTitle].orEmpty()
    }
    // 身份区数据源与 Android 侧栏同一份 `DisplayPrefsStore`（iOS 落在沙箱私有目录的 DataStore）
    val prefs = remember { services.displayPrefs() }
    val profileName by prefs.profileName.collectAsStateWithLifecycle(initialValue = "")
    val profileClass by prefs.profileClass.collectAsStateWithLifecycle(initialValue = "")
    val profileStudentId by prefs.profileStudentId.collectAsStateWithLifecycle(initialValue = "")

    // ---- 钱包卡：与 Android 侧栏同一对 ViewModel ----
    val yktCredentials = remember { services.yktCredentialStore() }
    val campusVm = remember(yktCredentials) {
        yktCredentials?.let { store ->
            CampusCardViewModel(
                repo = services.yktRepository(),
                credentialStore = store,
                prefs = services.displayPrefs(),
                db = services.database(),
                // 与 Android 侧栏同一档：构造期不取数（下面那条 LaunchedEffect 才是取数点）
                autoFetch = false,
            )
        }
    }
    val lifeVm = remember {
        LifeViewModel(
            powerRepo = services.powerRepository(),
            // null = 本平台没有加密凭证存储 → 电费格落「—」（`PowerCardState.noCredentials`）
            credentialStore = yktCredentials,
            readingStore = services.powerReadingStore(),
        )
    }
    val power = lifeVm.uiState.collectAsStateWithLifecycle().value.power
    // 没有加密凭证存储（`yktCredentialStore()` 为 null）时一卡通整条链开不起来，
    // 三格按「未开启 / — / 电费照旧」渲染——与 Android 关掉开关时同一档，不另写一套。
    val walletTexts = if (campusVm == null) {
        walletCardTexts(
            campusEnabled = false,
            cardFen = null,
            accountFen = null,
            balanceLoaded = false,
            power = power,
        )
    } else {
        val balance = campusVm.balance.collectAsStateWithLifecycle().value
        walletCardTexts(
            campusEnabled = campusVm.enabled.collectAsStateWithLifecycle().value,
            cardFen = balance?.cardFen,
            accountFen = balance?.accountFen,
            balanceLoaded = campusVm.balanceLoaded.collectAsStateWithLifecycle().value,
            power = power,
        )
    }
    // 见 KDoc「钱包卡的三格数从哪来」第 1 条：iOS 拿不到抽屉可见性，取数点只能是首次组合
    LaunchedEffect(campusVm) {
        campusVm?.refreshBalance(force = false)
    }

    // 生活服务区：与 Android「江水生活」段**同一个构造器**。快趣（底图是高德 Android SDK）、
    // 趣智（蓝牙传输层）、问渠（精简版下线）在本平台没有服务 → 传 null，三行整行不渲染。
    // 右侧状态（开水余额 / U净 空闲数）也没有接：那两个数的聚合逻辑还在 `:app` 的
    // `SideDrawerContent` 里（`loadUjingFreeText` 等），本轮不搬——行照常可点，只是不带状态。
    val serviceRows = lifeServiceRows(
        onOpenCampusAccount = { onPick(IosRoute(SubpageScreen.CAMPUS_CARD_SETTINGS)) },
        onOpenWater = { onPick(IosRoute(SubpageScreen.WATER)) },
        onOpenUjing = { onPick(IosRoute(SubpageScreen.UJING)) },
        // 快趣出行（P4，2026-11-26）：页面搬完之后才有这一行。此前 `onOpenRide` 不传，
        // `lifeServiceRows` 按「本平台没有这一项」整行不渲染——与 Android 侧栏的
        // 「快趣出行」行同位同序（`SideDrawerGroups.kt` 的 `onOpenRide?.let { … }`）。
        onOpenRide = { onPick(IosRoute(SubpageScreen.RIDE)) },
    )

    // 面板铺满整屏高度（窗口已 ignoresSafeArea）：合上时看不见，展开时状态栏那一条
    // 也是面板底色，不会露出窗口白底。内容自己再让出安全区（下面那个 Column）。
    ModalDrawerSheet(
        modifier = Modifier.width(IosDrawerWidth).fillMaxHeight(),
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // 上半区（身份卡 / 账号与登录 / 我的·设置 / 钱包卡）也放进 `LazyColumn`：
            // Android 的侧栏是「整列滚动、底部付款码钉住」，段头与钱包区跟着内容一起滚。
            // 把它们钉在列表外面会让小屏上的列表只剩半屏——那是排版差异，不是平台差异。
            //
            // 首尾留白按 Android 的数：上 0（`DrawerHeader` 自带 `top = 12dp`，再垫一层就是
            // 凭空多出 8dp）；下 16（Android 收尾是 `Spacer(Modifier.height(16.dp))`，
            // 不是 8dp）。
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "header") {
                    // 身份区：头像 + 姓名 + 遮罩学号 + 班级，整块点开进学籍信息
                    //（与 Android 同一个回调语义）。
                    DrawerHeader(
                        name = profileName,
                        studentId = profileStudentId,
                        className = profileClass,
                        onOpenStudentInfo = { onPick(IosRoute(SubpageScreen.STUDENT_INFO)) },
                        onScan = null,
                    )
                }
                item(key = "account") {
                    // 账号与登录：Android 身份卡正下方那一行（这里进账号中心，与 Android 同落点）
                    AccountRow(onClick = { onPick(IosRoute(SubpageScreen.ACCOUNTS)) })
                }
                item(key = "all-features") {
                    // 「全部功能」：Android 的侧栏里没有这一行，但 iOS 需要它来承接原来那六段
                    // 站点地图（学习 / 教务 / 钱包 / 生活 / 设置 / 账号），见 IosAllFeatures.kt。
                    DrawerRow(
                        label = "全部功能",
                        icon = AppIcons.Menu01,
                        onClick = onOpenAllFeatures,
                    )
                }
                item(key = "wallet") {
                    // 钱包卡：三格文案走共享层的 `walletCardTexts`，视觉与 Android 逐像素同源。
                    // `onRecharge = null`：充值宿主还在 `:app`，本平台没有这条路径 →
                    // 充值按钮整颗不渲染（§11.2 ④）。
                    WalletHero(
                        balanceText = walletTexts.balance,
                        accountValue = walletTexts.account,
                        powerText = walletTexts.power,
                        arrivalWatching = false,
                        onCampusDetail = { onPick(IosRoute(SubpageScreen.CAMPUS_STATEMENT)) },
                        onPowerDetail = { onPick(IosRoute(SubpageScreen.POWER_BILL)) },
                        onRecharge = null,
                    )
                }
                // 生活服务区：段头与行都是共享部件，与 Android 同一个段名、同一种行
                item(key = "h-$serviceSectionTitle") { DrawerSectionHeader(serviceSectionTitle) }
                // 键用 label 前缀：这几行来自 `lifeServiceRows`（`DrawerEntry`，没有路由），
                // 与下面按 `route.screen.name` 做键的二级页列表不共用命名空间
                items(serviceRows, key = { "service:${it.label}" }) { entry ->
                    DrawerRow(
                        label = entry.label,
                        icon = entry.icon,
                        brandIcon = entry.brandResId,
                        trailing = entry.trailing,
                        trailingLoading = entry.trailingLoading,
                        active = entry.active,
                        onClick = entry.onClick,
                        onTrailingClick = entry.onTrailingClick,
                    )
                }
                // 同段剩下的二级页（洗衣账单）接在上面这一段里，段头不重复（见文件头第 1 条）
                items(serviceSectionExtras, key = { it.route.screen.name }) { entry ->
                    CompactDrawerRow(entry = entry, onClick = { onPick(entry.route) })
                }

            }
            // 底部一行：左「设置」右「付款码」，与 Android `SideDrawer.kt` 底部那一行
            // （`Arrangement.SpaceBetween` + 同一对部件）逐项同构。设置是 iOS 唯一的设置入口
            // （「我的」不在底栏），删掉列表里那一行之后必须接在这颗齿轮上。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsEntry(onClick = onOpenMe)
                PaymentCodeEntry(onClick = { onPick(IosRoute(SubpageScreen.PAY_CODE)) })
            }
        }
    }
}

/** 抽屉行按下的回缩比例：与 `ui/life/SideDrawerParts.kt` 的 `DrawerRow` 同值（2026-10-21 定的 0.985）。 */
private const val CompactRowPressScale = 0.985f

/**
 * 抽屉里「二级页」那一档的行：文字 + 箭头，没有图标。
 *
 * ## 为什么不用 `DrawerRow`
 *
 * `DrawerRow` 的 `icon` 在 `brandIcon` 缺席时是 `icon!!`（非空断言），而二级页这一档
 * 40 多页各挑一颗图标是一批新的视觉决定，不在本轮范围。所以这里只复刻**几何与手感**，
 * 不复用那个组件：
 *
 * | 项 | 值 | 与 `DrawerRow` |
 * |---|---|---|
 * | 行高 | `heightIn(min = 50.dp)` | 同 |
 * | 外缩进 / 内缩进 | 12dp / 12dp（文字起点 24dp） | 同（与段头同一条竖线） |
 * | 文字 | `bodyLarge` | 同 |
 * | 箭头 | `AppIcons.ChevronRight` 16dp，`onSurface` 30% | 同（`onTrailingClick == null` 那一档） |
 * | 圆角 | 12dp | 同 |
 * | 按下 | 整行缩到 98.5%，松手回弹 | 同 |
 *
 * ## 2026-11-26 补按下回缩
 *
 * 上一版这里是裸 `Modifier.clickable`：Android 侧栏的每一行按下都会缩一下
 * （`DrawerRow` 的 `pressScale`），iOS 抽屉的二级页行点了没反应——两边的「按下去有反馈」
 * 对不上。触感这一层两边都不发（`DrawerRow` 也不发）：侧栏行只给视觉回缩，振动归各页面里
 * 自己的可点元素，避免同一次点击震两下。
 */
@Composable
internal fun CompactDrawerRow(entry: IosDrawerEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) CompactRowPressScale else 1f,
        label = "iosDrawerRowPress",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = entry.label,
                onClick = onClick,
            )
            .heightIn(min = 50.dp)
            // 纵向 1dp：与 `DrawerRow` 同（它那 1dp 是给右侧 48dp 命中区留位，这里只为
            // 让两种行的高度算式逐项一致，行高仍是 50dp）
            .padding(horizontal = 12.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp),
        )
    }
}