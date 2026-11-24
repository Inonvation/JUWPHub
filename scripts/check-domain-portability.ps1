# 可移植性护栏（2026-11-24，iOS 移植 P-1；2026-11-24 P0 起扫两个根）
#
# 为什么要有这条：`:shared` 的 commonMain 最终要由 Kotlin/Native 编译，那里没有 JVM，
# `java.*` / `javax.*` 一律不存在。清理是一轮一轮做的，没有护栏的话，
# 下一次随手写一个 `java.time.LocalDate` 就让前面的账白算。
#
# 两个扫描根：
#   1. shared/src/commonMain —— 已经是 KMP 共享代码，**要求完全干净**（允许清单对该根不生效）。
#      注意权威判据不是本脚本，而是 `.\gradlew :shared:metadataCommonMainClasses`：
#      那是真编译器，看不见 JDK，任何 JVM 专有 API 都当场报 `Unresolved reference 'java'`。
#      本脚本的价值只是快（秒级、不用起 Gradle），适合本地随手跑。
#   2. app/src/main/java/edu/jxslu/schedule/domain —— 还没搬完的老域层，
#      允许清单里的前缀放行（同时打印剩余量，方便看还欠多少）。
#
# 判据：允许清单之外，任何 java.* / javax.* / okhttp3.* / com.google.zxing.* / System.* 都算违规
#   （`System.*` 也算：`System.currentTimeMillis()` 在 Kotlin/Native 上不存在，
#   统一用 `AppClock.nowMillis()`；这条 2026-11-24 第 11 批加进来时域内已清零）。
#   注释行（`//` 与块注释的 `*`）跳过——KDoc 里提到类名是正常的。
#
# 退出码：0 干净、1 有违规、2 找不到源码目录（2 同样算失败，别当「没扫到所以没事」）。
#
# 清完一条欠账就把对应项从 $allowed 删掉，护栏会自动收紧。
# 本文件必须存成 UTF-8 **with BOM**：Windows PowerShell 5.1 读无 BOM 的 .ps1 会按系统
# ANSI 解码，中文注释变乱码后直接语法错误。

$ErrorActionPreference = 'Stop'

$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$roots = @(
    (Join-Path $repo 'shared\src\commonMain'),
    (Join-Path $repo 'app\src\main\java\edu\jxslu\schedule\domain')
)

foreach ($r in $roots) {
    if (-not (Test-Path $r)) {
        Write-Host "找不到源码目录: $r"
        exit 2
    }
}

# 允许清单：前缀 -> 欠账原因。只对老域层（第二个根）生效。
# 2026-11-24 P0 第 2 批后**已全部清零**（java.time / zxing / javax.crypto / okhttp3 /
# SimpleDateFormat / Date / Locale 都不欠了），所以这里是空表。再欠新账就往里加，并写清原因。
$allowed = [ordered]@{
}

# `Math.` 也算违规：`java.lang.Math` 在 Kotlin/JVM 里是默认导入，`Math.cos` 这类能编过，
# 但 commonMain 里 `Math` 是未解析符号。迁到 `kotlin.math` 的 `cos` / `sqrt` / `roundToLong` /
# `PI` 与 `Int.floorDiv` / `Int.mod`（`floorMod` 在标准库里叫 `mod`）。
# 注意 `round` 是 `Math.rint`（0.5 取偶），`roundToLong` 才是 `Math.round`，别搞反。
$pattern = '(java\.[a-zA-Z]|javax\.[a-zA-Z]|okhttp3\.|com\.google\.zxing\.|(?<![A-Za-z.])Math\.|(?<![A-Za-z.])System\.)'
$violations = New-Object System.Collections.Generic.List[string]
$pending = @{}
foreach ($key in $allowed.Keys) { $pending[$key] = 0 }
$totalFiles = 0

for ($ri = 0; $ri -lt $roots.Count; $ri++) {
    $root = $roots[$ri]
    # 只有老域层吃允许清单；commonMain 必须全干净。
    $strict = ($ri -eq 0)

    $files = Get-ChildItem $root -Recurse -Include *.kt
    $totalFiles += $files.Count
    foreach ($file in $files) {
        $lines = [System.IO.File]::ReadAllLines($file.FullName)
        for ($i = 0; $i -lt $lines.Length; $i++) {
            $line = $lines[$i]
            $trimmed = $line.Trim()
            if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) { continue }
            if ($line -notmatch $pattern) { continue }

            $remainder = $line
            if (-not $strict) {
                foreach ($key in $allowed.Keys) {
                    $pending[$key] += ([regex]::Matches($line, [regex]::Escape($key))).Count
                    $remainder = $remainder.Replace($key, '')
                }
            }
            if ($remainder -match $pattern) {
                $rel = $file.FullName.Substring($repo.Length).TrimStart('\', '/')
                $violations.Add(("{0}:{1}: {2}" -f $rel, ($i + 1), $trimmed))
            }
        }
    }
}

Write-Host ("扫描 {0} 个域层文件（含 shared/src/commonMain）" -f $totalFiles)
foreach ($key in $allowed.Keys) {
    if ($pending[$key] -gt 0) {
        Write-Host ("  欠账 {0,-20} {1,3} 处  {2}" -f $key, $pending[$key], $allowed[$key])
    }
}

if ($violations.Count -gt 0) {
    Write-Host ''
    Write-Host '以下位置用了允许清单之外的 JVM 专有 API（iOS 上编不过）：' -ForegroundColor Red
    foreach ($v in $violations) { Write-Host ("  " + $v) }
    Write-Host ''
    Write-Host '处理方式：换成跨平台实现；确实要欠着，就把前缀加进本脚本的 $allowed 并写清原因。'
    exit 1
}

Write-Host '域层可移植性检查通过'
exit 0
