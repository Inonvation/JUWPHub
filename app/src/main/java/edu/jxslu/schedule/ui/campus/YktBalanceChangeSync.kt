package edu.jxslu.schedule.ui.campus

import android.content.Context
import android.util.Log
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.prefs.setYktLastBalanceFen
import edu.jxslu.schedule.data.prefs.yktLastBalanceFen
import edu.jxslu.schedule.data.ykt.YktTurnoverSyncer
import edu.jxslu.schedule.domain.YktBalanceChange
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 余额变化 → 补拉一页最新流水（2026-11-06）。
 *
 * 问题：一卡通消费入收件箱的唯一挂点是 `YktTurnoverSyncer` 的新流水回调，而流水同步
 * 原先只由「进消费流水页 / 付款码页盯单 / 充值到账核对」触发。食堂 POS 直刷这类消费
 * 之后，今日页收件箱里那条「一卡通消费」要等用户自己打开账单页才出现——用户 2026-11-06
 * 反馈的原话是「余额有变化必须要到账单看一下，收件箱才会有更新」。
 *
 * 做法：**任何一条成功取到余额的链路**都把正式卡余额交给这里。和上次已知值不同就说明
 * 两次观察之间发生过交易，立刻按「进页增量」口径补拉一页流水（`force = true`，
 * 不吃 `YktSyncGate` 的 10 分钟闸门——余额变化本身就是「有新数据」的证据）；新见的
 * 支出走同一个 `paymentInboxCandidates` 守卫入箱（空库首同步不投、收入有专门键）。
 * 收件箱列表订阅 Room 流，投递后今日页/收件箱页自动出现，不需要用户进账单页。
 *
 * 基线落盘（`DisplayPrefsStore.yktLastBalanceFen`）：进程被杀后第一次取余额也要能判出
 * 「这段时间扣过钱」。首次（从未记录）只建基线不补拉，判定见 [YktBalanceChange]。
 *
 * 旁路纪律（同收件箱那套）：整段挂在 [Graph.appScope]、全部吞错——余额链路本身已经
 * 成功，补拉流水失败不影响任何展示，下一次余额观察还会再来。
 *
 * 挂点（都是「余额已到手」的结果点，别在别处再发一份）：
 * - `CampusCardViewModel.applySnapshot`：侧栏 / 校园卡设置页 / 到账核对 / 付款后刷新；
 * - `PayCodeViewModel.loadBalanceSnapshot`：付款码页（用户看完余额立刻退出时 payWatch
 *   未必跑完第一轮，这里兜一份；同键幂等）；
 * - `BalanceAlertReminder.checkYkt`：余额提醒的周期核对（后台也能让收件箱跟上）；
 * - `LifeWidgetSync.maybeFetchCampusBalance`：桌面生活小组件的后台取数。
 */
object YktBalanceChangeSync {

    private const val TAG = "YktBalanceChangeSync"

    /** 同一次变化可能被多条链路同时观察到（侧栏与到账轮询各有一份 VM）：串行化后只补拉一次。 */
    private val mutex = Mutex()

    /**
     * 进程内最近一次报告值：到账轮询每 5 秒就会把同一个余额报过来一次，没变就连
     * DataStore 都不读。null = 本进程还没报过（首次仍走落盘基线比对）。
     */
    @Volatile
    private var lastReportedFen: Long? = null

    /**
     * 报告一次「刚取到的正式卡余额」（分）。
     *
     * 非 suspend、立即返回：调用方都是展示链路，补拉流水在 [Graph.appScope] 上跑，
     * 绝不因为这条旁路拖慢/挡住余额展示或付款码流程。
     */
    fun onBalanceObserved(context: Context, cardFen: Long) {
        if (lastReportedFen == cardFen) return
        lastReportedFen = cardFen
        val appContext = context.applicationContext
        Graph.appScope.launch {
            runCatching {
                mutex.withLock {
                    val prefs = Graph.displayPrefs(appContext)
                    val previous = prefs.yktLastBalanceFen()
                    val pull = YktBalanceChange.shouldPullStatement(previous, cardFen)
                    // 先记基线再补拉：并发观察同一笔变化时只有第一条真正发请求。
                    // 没变化就不写盘（每轮余额核对都写一次 DataStore 没必要）。
                    if (previous != cardFen) prefs.setYktLastBalanceFen(cardFen)
                    if (!pull) return@withLock
                    val credentials = Graph.yktCredentialStore(appContext).read() ?: return@withLock
                    YktTurnoverSyncer(Graph.yktRepository(appContext), JuwDatabase.get(appContext)) {
                        postYktPaymentInbox(appContext, it)
                    }.sync(
                        username = credentials.username,
                        password = credentials.password,
                        maxPages = 1,
                        force = true,
                    )
                }
            }.onFailure {
                // 只留日志：补拉失败不影响余额展示；基线已前进，要等下一次余额变化再补
                //（进账单页那条老路照常兜底）。
                Log.w(TAG, "余额变化后补拉流水失败（旁路，进账单页仍可补上）", it)
            }
        }
    }
}
