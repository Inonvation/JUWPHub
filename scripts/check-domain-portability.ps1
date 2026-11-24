# domain 层「可移植性」护栏（2026-11-24，iOS 移植 P-1）
#
# 为什么要有这条：domain/ 最终要整体搬进 Kotlin Multiplatform 的 commonMain，
# 那里没有 JVM，`java.*` / `javax.*` 一律不存在。清理是一轮一轮做的，
# 没有护栏的话，下一次随手写一个 `java.time.LocalDate` 就让前面的账白算。
#
# 判据：扫 app/src/main/java/edu/jxslu/schedule/domain 下所有 .kt，
#   允许清单里的前缀放行（同时打印剩余量，方便看还欠多少），
#   其余任何 java.* / javax.* / okhttp3.* / com.google.zxing.* / System.* 都算违规
#   （`System.*` 也算：`System.currentTimeMillis()` 在 Kotlin/Native 上不存在，
#   统一用 `kotlin.time.Clock`；这条 2026-11-24 第 11 批加进来时域内已清零）。
#   注释行（`//` 与块注释的 `*`）跳过——KDoc 里提到类名是正常的。
#
# 退出码：0 干净、1 有违规、2 找不到源码目录（2 同样算失败，别当「没扫到所以没事」）。
#
# 清完一条欠账就把对应项从 $allowed 删掉，护栏会自动收紧。

$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot '..\app\src\main\java\edu\jxslu\schedule\domain'
$root = [System.IO.Path]::GetFullPath($root)
if (-not (Test-Path $root)) {
    Write-Host "找不到 domain 目录: $root"
    exit 2
}

# 允许清单：前缀 -> 欠账原因
$allowed = [ordered]@{
    'java.time.'        = '待 kotlinx-datetime（P1）'
    'java.text.SimpleDateFormat' = 'QzxyWatering 的开阀时刻文案，随 java.time 一起换'
    'java.util.Date'    = '同上，与 SimpleDateFormat 成对出现'
    'java.util.Locale'  = '随 java.time 与 TextFmt.decimals 一起清'
    'javax.crypto.'     = 'QzxyClData 的 AES，待 CommonCrypto expect/actual'
    'okhttp3.'          = 'HttpUrl 相对路径解析，待 Ktor URL'
    'com.google.zxing.' = '条码生成/识别，待 KMP 条码库'
}

$pattern = '(java\.[a-zA-Z]|javax\.[a-zA-Z]|okhttp3\.|com\.google\.zxing\.|(?<![A-Za-z.])System\.)'
$violations = New-Object System.Collections.Generic.List[string]
$pending = @{}
foreach ($key in $allowed.Keys) { $pending[$key] = 0 }

$files = Get-ChildItem $root -Recurse -Include *.kt
foreach ($file in $files) {
    $lines = [System.IO.File]::ReadAllLines($file.FullName)
    for ($i = 0; $i -lt $lines.Length; $i++) {
        $line = $lines[$i]
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) { continue }
        if ($line -notmatch $pattern) { continue }

        $remainder = $line
        foreach ($key in $allowed.Keys) {
            $pending[$key] += ([regex]::Matches($line, [regex]::Escape($key))).Count
            $remainder = $remainder.Replace($key, '')
        }
        if ($remainder -match $pattern) {
            $rel = $file.FullName.Substring($root.Length).TrimStart('\', '/')
            $violations.Add(("{0}:{1}: {2}" -f $rel, ($i + 1), $trimmed))
        }
    }
}

Write-Host ("扫描 {0} 个 domain 文件" -f $files.Count)
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

Write-Host 'domain 层可移植性检查通过'
exit 0
