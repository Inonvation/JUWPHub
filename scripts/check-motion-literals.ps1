<#
.SYNOPSIS
    动效时长字面量检查：全仓只允许 `ui/theme/Motion.kt` 的四档作为时长来源。

.DESCRIPTION
    背景：2026-11-06 把全仓 142 处动效时长字面量收进 `ui/theme/Motion.kt`
    （tap 90 / state 180 / reveal 220 / page 280）。`.agents/rules/ui-common.md`
    里那条验收命令有两个坑，所以这里重写成脚本——**不是**为了多一个脚本，是因为
    那条命令会给两种错误结论：

      1. `Get-ChildItem -Path "...\**\*.kt"` 里 `**` 在 PowerShell 中**不递归**。
         它只是「这一层里名字任意」的字面通配，实际只扫到一层目录，绝大多数文件
         从未被检查——「0 命中」是假的。必须用 `-Recurse -Filter *.kt`。
      2. 直接 grep `tween(\s*\d` 会命中**注释里的示例**（`SheetDismissIme` 的 KDoc
         就写过 `tween(300ms, …)`）。于是「改了注释就红、真漏改反而绿」。
         本脚本跳过整行注释，只查代码行。

    退出码：0 = 干净；1 = 有命中；2 = 环境不对（找不到源码目录）。
    区分 2 与 0 是刻意的：路径推断错时**不能**静默报绿，那正是上面第 1 个坑的形态。

    新增例外必须**同时**改两处：本文件的 `$Excluded`，以及
    `.agents/rules/ui-common.md` 的「动效口径 → 验收命令」一节。
    只改一处就会出现「文档说绿、脚本说红」的分裂。

    **本文件必须存成 UTF-8 with BOM**。Windows PowerShell 5.1 读没有 BOM 的 .ps1 时
    按系统 ANSI 解码，下面的中文注释全变乱码，然后直接报语法错误
    （现象是 `意外的标记"}"` / `缺少右"}"`，位置都在中文行附近——别去数括号，
    是编码问题）。CI 的 pwsh 7 有没有 BOM 都行，但本地跑不起来等于这条护栏只在
    CI 上生效，所以 BOM 是必需的。
    坑：不少编辑器与补丁/改写工具保存时会**悄悄去掉 BOM**。改完本文件请回读确认
    前 3 个字节是 `EF BB BF`：
        [System.IO.File]::ReadAllBytes((Resolve-Path .\scripts\check-motion-literals.ps1))[0..2]
    （不要用 `Get-Content -Encoding Byte`：PS 7 里已改成 `-AsByteStream`，跨版本会断。）

    没有参数，**刻意不加**。需要只查某个子目录时，临时改 `$packageRoot` 跑完再改回来；
    参数化会诱使 CI 和本地用不同口径，那这条护栏就白设了。

.NOTES
    从任何工作目录调用都成立（靠 `$PSScriptRoot` 定位仓库根，不靠 CWD）。
#>

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# 路径
# ---------------------------------------------------------------------------
# 脚本永远在 scripts/ 下，所以仓库根 = 脚本目录的父目录。
# 不用 CWD：CI 与本地的工作目录不保证是仓库根，猜错时下面的 Test-Path 会兜住，
# 而不是「扫了 0 个文件 → 报 OK」。
$repoRoot = if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path }

# 包内相对路径的前缀：下面所有例外都用「相对这个前缀的路径」书写。
# 这样例外清单读起来和源码树一致，也不会因为仓库被 clone 到别的目录而失效。
$packageRoot = 'edu/jxslu/schedule'

$sourceRoot = Join-Path $repoRoot (Join-Path 'app/src/main/java' $packageRoot)
if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
    Write-Output "错误：找不到源码目录 $sourceRoot"
    Write-Output "（脚本位置推断错了？本脚本必须放在仓库的 scripts/ 目录下）"
    exit 2
}
$sourceRoot = (Resolve-Path -LiteralPath $sourceRoot).Path.TrimEnd('\', '/')

# ---------------------------------------------------------------------------
# 例外清单
# ---------------------------------------------------------------------------
# 按**包内相对路径**匹配，不按文件名裸匹配：`Skeleton.kt` 这种名字将来出现在
# 别的包里（比如 `ui/week/Skeleton.kt`）会被误伤，而路径是唯一的。
# 每一条都必须能回答「为什么它不是一次过渡的时长」——答不上来就不该进这张表。
$Excluded = @{
    # 四档的数值本身就写在这个文件里，它当然有字面量。
    'ui/theme/Motion.kt' = 'Motion 四档的唯一来源'

    # 引导页是一次性逐段编排：切屏 320、对勾描边 1100、呼吸 1500、扫光 2600……
    # 这些数是按「这一步要让人看懂什么」定的，压进四档会把描边压成看不清、
    # 把呼吸压成抽搐（理由写在 OnboardingScreen.kt 的时长口径注释里）。
    'ui/onboarding/OnboardingScreen.kt' = '引导页逐段编排（21 处，有意保留）'

    # 无限循环的**周期**不是「过渡时长」：骨架流光 1200、进行中圆点呼吸 900。
    # 周期决定「多久扫一遍」，跟一次状态变化该多快没有关系。
    'ui/common/Skeleton.kt' = '无限循环周期（骨架微光 / 呼吸圆点）'

    # 同上：加载水滴呼吸 700ms。
    'ui/common/LoadingHint.kt' = '无限循环周期（加载水滴呼吸）'

    # 洗涤进度按 1 秒一次的 tick 补间：时长要盖住两次 tick 之间的空档，且必须线性
    # （曲线一弯，每秒一格就读出「一顿一顿」）。调用点已写 `// 例外：`。
    # 这是全 App 唯一按「数据节奏」而非「用户动作」定长的动画。
    'ui/ujing/UjingScreen.kt' = '数据节奏补间（1 秒 tick 的进度条），非用户动作时长'
}

# ---------------------------------------------------------------------------
# 匹配
# ---------------------------------------------------------------------------
# 两个模式缺一不可：
#   - `tween(\s*\d`              抓 `tween(180)` / `tween(320, easing = …)`
#   - `durationMillis\s*=\s*\d`  抓 `tween(durationMillis = 700)`
# 第二种写法前面的模式**抓不到**（`tween(` 后面紧跟的是字母 d），而无限动画
# 全是这种写法——漏掉它等于漏掉整类。
# 用 `-cmatch`（区分大小写）：`tween` / `durationMillis` 是标识符，大小写变了
# 就不是那个 API，没必要为了"宽容"制造假阳性。
$pattern = 'tween\(\s*\d|durationMillis\s*=\s*\d'

$hits = [System.Collections.Generic.List[string]]::new()
$scanned = 0

foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter *.kt -File) {
    # 包内相对路径，统一成 `/`：Windows 上 FullName 是反斜杠，Linux 上是斜杠，
    # 而 CI（ubuntu）与本地（Windows）必须用同一份例外清单。
    $rel = $file.FullName.Substring($sourceRoot.Length).TrimStart('\', '/').Replace('\', '/')
    if ($Excluded.ContainsKey($rel)) { continue }

    $scanned++
    $lineNo = 0
    foreach ($text in Get-Content -LiteralPath $file.FullName -Encoding utf8) {
        $lineNo++
        $trimmed = $text.Trim()

        # 整行是注释就跳过（旧命令在这里误报，见文件头第 2 条）。
        # 只跳「整行」，行尾注释里出现字面量的代码行仍然会被抓到——那才是真漏改。
        if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) {
            continue
        }

        if ($text -cmatch $pattern) {
            # 打印仓库相对路径（不是包内相对路径）：CI 日志里能直接照着路径打开文件。
            $hits.Add(('app/src/main/java/{0}/{1}:{2}: {3}' -f $packageRoot, $rel, $lineNo, $trimmed))
        }
    }
}

# ---------------------------------------------------------------------------
# 结果
# ---------------------------------------------------------------------------
if ($hits.Count -gt 0) {
    $hits | ForEach-Object { Write-Output $_ }
    Write-Output ''
    Write-Output ('失败：{0} 处动效时长字面量（已扫 {1} 个文件）。' -f $hits.Count, $scanned)
    Write-Output '改法：按 ui/theme/Motion.kt 文件头的归并表换成 Motion.tap()/state()/reveal()/page()。'
    Write-Output '确需例外：在调用点写 // 例外：<原因>，并把该文件加进本脚本的 $Excluded'
    Write-Output '          与 .agents/rules/ui-common.md 的「验收命令」一节（两处必须一起改）。'
    exit 1
}

Write-Output ('OK: 0 处字面量（已扫 {0} 个文件，{1} 个文件按例外清单跳过）' -f $scanned, $Excluded.Count)
exit 0
