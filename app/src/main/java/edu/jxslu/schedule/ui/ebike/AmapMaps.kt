package edu.jxslu.schedule.ui.ebike

import android.content.Context
import android.util.Log
import edu.jxslu.schedule.BuildConfig
import com.amap.api.maps.MapsInitializer

/**
 * 高德地图 SDK 的进程内初始化（DESIGN §3.9 / §4.23，2026-11-17）。
 *
 * 顺序是硬要求，不能换：
 * 1. `updatePrivacyShow` / `updatePrivacyAgree` —— 8.1.0 起不先声明隐私合规，SDK 直接不工作。
 *    这两个接口传 true 的含义是「本应用已向用户展示隐私政策并取得同意」，所以**必须等用户
 *    在地图页的确认弹层里点过同意**才调；没同意时不初始化 SDK，地图层走 osmdroid 栅格。
 * 2. `initialize` —— 注意它**返回 void**（拆包确认过 `MapsInitializer` 的签名），
 *    所以「key 好不好用」在这里问不出来：key 校验是高德服务端在首次请求底图时做的。
 *    可用性的唯一可靠信号是 `AMap.OnMapLoadedListener` 会不会来，见 [AmapMapView] 的超时。
 *
 * 只做一次：SDK 的初始化是进程级的，重复调用没有意义；失败后要重试先 [reset]
 * （地图页「重试高德地图」走这条）。
 */
internal object AmapMaps {

    private const val TAG = "AmapMaps"

    /** 用户是否在地图页确认过「地图服务由高德提供」。 */
    private var privacyAgreed = false

    /** `initialize` 已经调过（不代表 key 可用，见类注释）。 */
    private var initialized = false

    /** 用户点了「同意」：之后才允许碰 SDK。 */
    fun agreePrivacy(context: Context) {
        privacyAgreed = true
        runCatching {
            MapsInitializer.updatePrivacyShow(context.applicationContext, true, true)
            MapsInitializer.updatePrivacyAgree(context.applicationContext, true)
        }.onFailure { Log.w(TAG, "隐私合规接口调用失败", it) }
    }

    /**
     * 初始化 SDK。
     *
     * @return true = 已经具备创建地图的条件（用户同意过、initialize 已调）；
     *   false = 用户没同意过，调用方必须走栅格。**返回 true 不代表 key 有效**——
     *   那要等 `onMapLoaded`。
     */
    fun prepare(context: Context): Boolean {
        if (!privacyAgreed) return false
        // 构建没配 key（clone 仓库、或 keystore.properties 缺字段）就别去试了：
        // 否则要白等 10 秒超时才回退栅格，进页面就是一块空地图
        if (BuildConfig.AMAP_KEY.isBlank()) return false
        if (!initialized) {
            // 地形 / 等高线是**进程级**开关，且必须在 initialize 之前给（2026-11-18 拖动卡顿）：
            // 这两层是附加几何，我们只画校园尺度的车与还车点，留着只是白算。
            runCatching {
                MapsInitializer.setTerrainEnable(false)
                // 注意方法名里那个大写 I：SDK 自己的拼写，照着抄，别"修"
                MapsInitializer.setContourLIneEnable(false)
            }.onFailure { Log.w(TAG, "地形 / 等高线开关设置失败", it) }
            runCatching { MapsInitializer.initialize(context.applicationContext) }
                .onFailure { Log.w(TAG, "initialize 抛异常，本次回退 osmdroid 栅格", it) }
            initialized = true
        }
        return true
    }

    /** 手动重试前清掉上一次的结论。隐私同意保持（用户已经点过一次，不再重复打扰）。 */
    fun reset() {
        initialized = false
    }
}
