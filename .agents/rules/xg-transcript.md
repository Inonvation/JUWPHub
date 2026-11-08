# 学工表单 · 盖章成绩单

作用域：改学工 WebView 表单（宿舍报修 / 请假）、报修进度只读链、成绩单导出与「最近导出」时读。规格见 DESIGN §3.15 / §4.25 / §4.26 / §4.49。

## 盖章成绩单走签章管理系统

- **导出盖章成绩单走的是签章管理系统，不是强智教务**（DESIGN §4.25）：
  CAS service = `http://jwxyxx.juwp.edu.cn/ptwork/cas`，落地拿 `sid`，再
  `POST /ptwork/DzqzController/ddqzcjList` 取 `pagePri`、`POST …/printStartCj` 回传它拿 PDF。
  四条不许按直觉改的口径：**`dysj`（pagePri）是权威条件、`xnxq` 被服务端忽略**；
  列表分页不影响出单（token 编码查询条件而非当页数据）；`limit` 无效、固定 15 行一页；
  **令牌存在不等于有数据**——无成绩学期照样给 token，解不开时服务端返回**空白却带章**的模板 PDF，
  所以出单前必须用 `total>0` 当闸门，响应必须过 `%PDF` 魔数。
  会话只从 WebView 的 `CookieManager` 现取（不存账号密码，与「不要写死密码」同一条纪律）；
  学期清单从接口取，**不要解析页面下拉**——「后台首页」那份模板的下拉最高只到 2022-2023-2
  且 37 个旧学期重复，是坏的。成绩单 PDF 的章是**注释 + 数字签名**（`/FT /Sig`，Rect 压在
  「学校盖章：」上），PDFium 系渲染器（含 pypdfium2）不画注释，用它截图自检会误判「没盖章」。
  签章系统只有 HTTP 明文，成绩单与 CAS 票据都明文回传，这句实话要留在导出页上。
  「最近导出」页（`SubpageScreen.TRANSCRIPTS`）的列表**就是 `filesDir/transcripts/` 目录本身**，
  不要给它加 Room 表或清单文件：文件名里已经带学期标签与导出时刻，两套真相迟早对不上。
  判定顺序是「先 `.part` 再 `.pdf`」（`x.pdf.part` 同时满足两个后缀），否则崩溃留下的半成品
  会被当成一条记录列出来；打开/分享前必须用 `TranscriptStore.existingFile` 复核（新导出会触发
  保留策略删旧的，列表那一屏可能已经过期）。
  PDF 含姓名/学号/证件照/全部成绩：`transcripts/` 已从 `backup_rules.xml` 与
  `data_extraction_rules.xml` 的 cloud / device-transfer **三段**排除，不上云、不随迁移；
  新增同类目录时同步这三处（漏一段 = 隐私文件进备份）。

## 学工表单是 WebView

- **学工表单是 WebView，不是原生表单**（2026-09-24，DESIGN §3.15 / §4.26）：
  `XgFormActivity` 承载全部学工表单（已登记：宿舍报修、请假），表单标识走 extra、
  清单在 `XgUrls.FORMS`，加一个新的只需在清单里补一条 + 在扩展服务页的图标映射里补一条。
  它是第三个「因为窗口里有统一认证表单而锁竖屏」的窗口（前两个是教务导入与成绩单导出），
  进页打开 `XgUrls.SSO_LOGIN`（`/sfrz/login343962`）→ 302 到 `eapp2` 的 CAS。
  学工与教务**共用同一套统一身份认证**，所以教务导入登录过一次这边就免登。学工自己的
  token **App 不存**（它在 localStorage 里，取不出来也用不上）；两者共用的那一层 CAS 凭证
  由 `data/session/CredentialVault` 管，口径见 DESIGN §4.27 与下面「登录态只有一条口径」。
  **别换成**超星的 `/passport/mlogin`（手机号 + 学习通密码，是另一套账号，学校没配它）；
  **也别去复刻** `/office/...` 的表单提交：请求里那批 `pageEnc` / `traceId` / `nodeUniqueId`
  由服务端每次下发，复刻出来的实现必然随官方改版失效。附件上传靠
  `WebChromeClient.onShowFileChooser`（漏了它 = 点上传没反应）；**结果解析不能交给
  `FileChooserParams.parseResult`**——Chromium 的实现只读 `Intent.data`、丢 `ClipData`，
  而表单的附件控件是 `<input type="file" multiple>`，选择器在多选模式下把选中项放进
  `ClipData`（只选一张也一样），交给它等于把每次选择都当成取消，附件位置恒为空
  （`XgFormScreen.parseFileChooserResult`：先读 `ClipData` 再回退 `data`）。状态条按域分档的判据
  与直达闸门唯一实现在 `data/xg/XgUrls.kt`。直达的顺序是硬约束：**先 `/sfrz/`，
  落到学工域后再进表单页**——表单页自己匿名可访问，先开它会把人引到超星 passport
  那套账号上；而闸门必须排除 `/sfrz/`（它也在学工域），否则 CAS 回跳的一刻被截断。
  直达地址**不带 `uuid`**：那是前端提交时现场生成的随机值，URL 上那个没有读取点。

## 请假 → 销假要网页定位（2026-11-08）

- 销假页自己调 `navigator.geolocation`。Android WebView 的默认 `WebChromeClient`
  **不回填定位回调**，页面永远等不到坐标——用户看到的「内置浏览器拿不到手机定位」就是这条。
  实现在 `ui/common/WebViewSupport.kt` 的 `WebViewGeolocationBridge`（学工与门户共用）：
  `onGeolocationPermissionsShowPrompt` 把请求接到 App 的运行时权限
  （`AppPermissions.location`），已有权限直接 `invoke(true)`，没有就先弹系统授权，
  结果回来统一回填；`retain = false`，不把授权持久化到 WebView。
- `onGeolocationPermissionsHidePrompt` 必须清掉挂起的回调（页面撤销请求后按契约不能再回填）；
  多个请求同时挂起时要**全部**回填，漏一个那页就一直挂着。
- 定位权限已在 manifest 声明（与单车地图共用 `ACCESS_FINE/COARSE_LOCATION`），
  **不要**在进页时预申请：只在网页真的请求定位那一刻申请（挂起表非空时不重复 launch，
  系统弹框不支持并发）。

## 学工 WebView 的失败出口（2026-11-08 审查补）

- **文件上传、定位、外链**三件都走 `ui/common/WebViewSupport.kt`：别在页面里另写。
  `<input type=file>` 的结果解析必须读 `ClipData`（`parseWebViewFileChooserResult`），
  交给 `FileChooserParams.parseResult` 会把多选附件当取消。
- **主 frame 失败要有出口**：`onReceivedError` / `onReceivedHttpError` 记下失败 URL
  并显示一条「重试」，顶栏也有刷新。**不要在 `onPageStarted` 里清错误**——实测回调顺序
  可能是「错误 → onPageStarted → onPageFinished」，在 start 里清会把刚报出来的错洗掉；
  清理由 `onPageFinished` 按 URL 匹配决定（同 JwImportScreen 的口径）。
- **`onRenderProcessGone` 必须重写**：不处理时渲染进程崩溃会带走整个 App（SDK 文档）。
  四个 WebView 窗口统一走 `handleWebViewRendererGone`：返回 true + `Activity.recreate()`。

## 学工首页卡「正在加载中…」（2026-11-08）

- 症状：在请假表单里点右上角小房子回学工首页，页面停在首页 index.html 自带的
  「正在加载中…」；在设置里清浏览器缓存后恢复。
- 根因：`/` 的 index.html 是 SPA 壳，Vue `mount()` 会把 `#app` 里的
  `.loading-box` 整块替换掉。**占位还在 = JS 根本没跑起来**（缓存里的旧 HTML
  引用了已下线的 JS 哈希，或缓存条目坏了）；普通 `loadUrl` 继续命中同一份缓存，
  修不好，只有清缓存能恢复。页面本身与登录态无关：带任何 cookie / UA 请求 `/`
  拿到的 HTML 都逐字相同（2026-11-08 实测）。
- 修法（`XgFormScreen`，两段）：
  1. Home 点击时用一次性 `LOAD_NO_CACHE` 重取首页（`onPageFinished` 还原
     `LOAD_DEFAULT`）。只绕缓存，**不动 cookie / localStorage**，用户不用重新登录。
  2. `onPageFinished` 后 2.5s 探一次 `#app .loading-box` 是否还在；还在就把状态条换成
     「卡住了？清理网页缓存重新加载试试」+ 右侧「清理并刷新」按钮（`checkHomeShellStuck`），
     点了才 `clearCache(true)` + no-cache 重载首页。**不在探测到卡住时自动重载**：页面
     会在用户眼皮底下自己刷新，容易被当成操作丢了（2026-11-08 用户口径）。
- 状态条常驻 48dp 高（`heightIn(min = 48.dp)`）：提示与按钮出现/消失**不能改变 WebView
  高度**（MIUI 上 resize 可能整面刷白，与进度条 overlay 同一条）。
- 探测带**导航代次**（`navToken`，`onPageStarted` +1）：2.5s 内用户 reload 过首页时旧探测作废，
  不能拿新页面的 DOM 下结论（否则新页还在挂载的一两秒会被误报）。Vue 的 `mount()` 会
  `innerHTML=""` 清掉占位，所以健康加载到这里必然是 0；健康页面通常还会被路由改写成
  `/home`，`isHomeShell` 直接把探测挡掉。
- 别把判据放宽到整个学工域：表单页（`/office/...`）也有 `#app`，清缓存重载会把
  用户从表单里拽走。首页壳判定唯一实现在 `XgUrls.isHomeShell`（有单测）。
- 清的是 **HTTP 资源缓存（整 App 共享）**，不清 cookie / localStorage：教务/门户窗口
  下次只是重新下载资源；别在退出登录路径上复用这段（那边的口径是
  `WebViewCookieBridge.clearAll`）。

## 报修进度是只读直取，不碰表单写接口（DESIGN §4.49）

- **唯一接口**：`POST /office/data/approve/apps/forms/fore/user/list`（`XgUrls.OFFICE_RECORD_LIST`，
  参数 `XgForm.recordListForm()`）。`handle/*`（审批/退回/转交）、`user/save`、`submit/validate`
  **一律不碰**——这条链路只负责读「个人提交记录」。
- **取数顺序按成本从低到高**（`DormRepairSync.fetchBody`）：
  1. `DormRepairWebClient` 读 `CookieManager.getCookie("https://xgxt.juwp.edu.cn/office/")`
     的现成会话直接 POST——不登录、最便宜，刚用过报修页时一定新鲜；
  2. `CasSession`（`ensureValid` → `GET /sfrz/login343962` → `GET /office/` → POST）。
     **自动检查的 CAS 登录最多 12 小时一次**（`AUTO_LOGIN_INTERVAL_MS`），
     回到前台 30 分钟内不重复请求；手动刷新不受限。冷启动别为一条低频记录完整登录。
- **解析按 label 不按字段 id**（`data/xg/DormRepairParser`）：`formsConfig` 建
  `id → label`，从 `formData` 取「当前状态 / 宿舍信息 / 宿舍 / 故障类型 / 详情描述 /
  最终完成时间」；`flowUser` 只取 `cardType == 1` 的审批节点，当前节点靠
  `currentApproveUserList[].nodeUniqueId` 匹配。
- **个人信息解析阶段丢弃**（姓名/学号/院系/专业/班级/手机号，`PERSONAL_LABELS`），
  不进快照、不上屏、不进日志；`dorm_repair.json` 已进两份备份排除 XML。
- 完成判定 = 表单「已完成」或审批到终点（`2 / -1 / -2`）。审批通过后表单还是「已派单」
  时卡片按「已完成」，详情页并列显示表单口径，不藏差异。
- 失败宁旧勿空；自动检查没有可用会话就静默跳过，不在今日页弹错误。
- **退出学校统一认证时清空 `dorm_repair.json`**（`JwAccountScreen` 里 `DormRepairStore.clear()`）：
  换账号后不能把上一位用户的宿舍/故障/描述留在今日页。新增任何「本机镜像学校数据」的
  store 时都按这条检查一遍退出路径。
