# 可移植性护栏（2026-11-24，iOS 移植 P-1；同日 P0 收尾后只剩一个根）
#
# 为什么要有这条：`:shared` 的 commonMain 最终要由 Kotlin/Native 编译，那里没有 JVM，
# `java.*` / `javax.*` 一律不存在。没有护栏的话，下一次随手写一个 `java.time.LocalDate`
# 就让前面的账白算。
#
# 扫描根：**只有 `shared/src/commonMain`**。2026-11-24 P0 收尾后 `app/.../domain` 已经搬空
# （最后一个文件也进了 commonMain），所以这里不再分「老域层 / 新共享层」两套口径，
# 全仓库只有一条线：commonMain 必须完全干净，没有允许清单。
#
# 注意**权威判据不是本脚本**，而是 `.\gradlew :shared:metadataCommonMainClasses`：
# 那是真编译器，看不见 JDK，任何 JVM 专有 API 都当场报 `Unresolved reference`。
# 本脚本的价值只是快（秒级、不用起 Gradle），适合本地随手跑；真判断以编译器为准。
#
# 判据：任何 java.* / javax.* / okhttp3.* / com.google.zxing.* / Math.* / System.* 都算违规。
#   - `Math.*`：`java.lang.Math` 在 Kotlin/JVM 里默认导入，commonMain 里是未解析符号。
#     换 `kotlin.math` 的 `cos` / `sqrt` / `roundToLong` / `PI` 与 `Int.floorDiv` / `Int.mod`
#     （`floorMod` 在标准库里叫 `mod`）。注意 `round` 是 `Math.rint`（0.5 取偶），
#     `roundToLong` 才是 `Math.round`，别搞反。
#   - `System.*`：`System.currentTimeMillis()` 在 Kotlin/Native 上不存在，统一用 `AppClock.nowMillis()`。
#     正则里的负向断言是为了放行 `Clock.System`（kotlin.time）。
#   - 注释行（`//` 与块注释的 `*`）跳过——KDoc 里提到类名是正常的。
#
# 退出码：0 干净、1 有违规、2 找不到源码目录（2 同样算失败，别当「没扫到所以没事」）。
#
# 本文件必须存成 UTF-8 **with BOM**：Windows PowerShell 5.1 读无 BOM 的 .ps1 会按系统
# ANSI 解码，中文注释变乱码后直接语法错误。

$ErrorActionPreference = 'Stop'

$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$roots = @(
    (Join-Path $repo 'shared\src\commonMain')
)

foreach ($r in $roots) {
    if (-not (Test-Path $r)) {
        Write-Host "找不到源码目录: $r"
        exit 2
    }
}

# 允许清单：前缀 -> 欠账原因。只对老域层（第二个根）生效。
# 2026-11-24 P0 第 2 批后**已全部清零**（java.time / zxing / javax.crypto / okhttp3 /
# 判据：任何 java.* / javax.* / okhttp3.* / com.google.zxing.* / Math.* / System.* 都算违规。
# 没有允许清单——搬完之后 commonMain 就是零欠账，再欠新账就当场红。
# 见文件头的逐项说明（尤其 `round` vs `roundToLong`、`floorMod` 叫 `mod`）。
$pattern = '(java\.[a-zA-Z]|javax\.[a-zA-Z]|okhttp3\.|com\.google\.zxing\.|(?<![A-Za-z.])Math\.|(?<![A-Za-z.])System\.)'
$violations = New-Object System.Collections.Generic.List[string]
$totalFiles = 0

foreach ($root in $roots) {
    $files = Get-ChildItem $root -Recurse -Include *.kt
    $totalFiles += $files.Count
    foreach ($file in $files) {
        $lines = [System.IO.File]::ReadAllLines($file.FullName)
        for ($i = 0; $i -lt $lines.Length; $i++) {
            $line = $lines[$i]
            $trimmed = $line.Trim()
            if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) { continue }
            if ($line -notmatch $pattern) { continue }
            $rel = $file.FullName.Substring($repo.Length).TrimStart('\', '/')
            $violations.Add(("{0}:{1}: {2}" -f $rel, ($i + 1), $trimmed))
        }
    }
}

Write-Host ("扫描 {0} 个 commonMain 文件" -f $totalFiles)

if ($violations.Count -gt 0) {
    Write-Host ''
    Write-Host '以下位置用了 JVM 专有 API（iOS 上编不过）：' -ForegroundColor Red
    foreach ($v in $violations) { Write-Host ("  " + $v) }
    Write-Host ''
    Write-Host '处理方式见 design/ios-port.md 的「JVM 默认导入陷阱」表。'
    exit 1
}

Write-Host 'commonMain 可移植性检查通过'
exit 0
