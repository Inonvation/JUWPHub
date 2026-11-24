# 重复与膨胀扫描（提交前自查用）
#
# 用途：改动前/后各跑一次，看有没有"又抄了一份"或"文件又变长了"。
# 不进 CI，只给人和 agent 看结论；退出码 0 恒定，不阻塞提交。
#
#   pwsh -File scripts\find_duplicate_kt.ps1
#
# 输出三段：
#   1) 逐字相同的函数体（归一化空白与行注释后比较，≥160 字符才比）
#   2) 同名私有函数 ≥3 份（同一件事被抄到多个文件）
#   3) 超过 1200 行的文件（只减不增预算线）
#
# 注意：本文件必须存成 UTF-8 with BOM，否则 Windows PowerShell 5.1 读中文会乱码报语法错。

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$mainDir = Join-Path $root 'app\src\main\java'
$testDir = Join-Path $root 'app\src\test\java'
# 2026-11-24（iOS 移植 P0）：domain 层已整体搬进 :shared，扫描范围必须跟上，
# 否则 136 个共享文件逃过重复检查——那正是最容易「又抄一份」的地方。
$sharedDir = Join-Path $root 'shared\src'

if (-not (Test-Path $mainDir)) { Write-Host "找不到 $mainDir"; exit 2 }

$files = Get-ChildItem $mainDir -Recurse -Filter *.kt
if (Test-Path $sharedDir) {
    $files = $files + (Get-ChildItem $sharedDir -Recurse -Filter *.kt)
} else {
    # shared 缺失说明 P0 被回退了，别静默少扫一半
    Write-Host "警告：找不到 $sharedDir，重复检查只覆盖 :app" -ForegroundColor Yellow
}
$testFiles = if (Test-Path $testDir) { Get-ChildItem $testDir -Recurse -Filter *.kt } else { @() }
$allText = (($files + $testFiles) | ForEach-Object { [System.IO.File]::ReadAllText($_.FullName) }) -join "`n"
$refCount = @{}
foreach ($m in [regex]::Matches($allText, '[A-Za-z_][A-Za-z0-9_]*')) {
    $k = $m.Value
    if ($refCount.ContainsKey($k)) { $refCount[$k]++ } else { $refCount[$k] = 1 }
}

$funcs = New-Object System.Collections.ArrayList
$privateNames = @{}
$bigFiles = New-Object System.Collections.ArrayList

# 生成物不计入「>1200 行只减不增」预算：文件大小由生成器与图标数量决定，
# 按行数警告只会训练人忽略这条输出。判定靠文件头里那句固定标记。
$generatedMarker = '这是生成物，不要手改'

foreach ($f in $files) {
    $lines = [System.IO.File]::ReadAllLines($f.FullName)
    $rel = $f.FullName.Replace("$root\", '')
    $isGenerated = ($lines.Count -gt 0) -and (($lines[0..([Math]::Min(40, $lines.Count - 1))] -join "`n").Contains($generatedMarker))
    if (-not $isGenerated -and $lines.Count -gt 1200) {
        [void]$bigFiles.Add(('{0,6}  {1}' -f $lines.Count, $rel))
    }

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*(?:private|internal|public)?\s*(?:suspend |inline |operator |infix |tailrec )*fun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][\w]*(?:<[^>]*>)?\??\.)?([A-Za-z_][\w]*)\s*\(') {
            $name = $Matches[1]
            $isPrivate = $line -match '^\s+private\s'
        } else {
            continue
        }
        if ($line -notmatch 'fun\s+([A-Za-z_][\w]*)\s*\(') { continue }

        # 表达式体（`fun x(...) = 某件(...)`）不是"又抄一遍逻辑"，而且没有 `{ }` 可数——
        # 不跳过的话会把后面整段代码吸进函数体，算出一个假的超长函数。
        if ($line -match '=\s*$' -and ($i + 1 -ge $lines.Count -or $lines[$i + 1].Trim() -notmatch '^\{')) { continue }

        $depth = 0; $opened = $false; $body = New-Object System.Collections.ArrayList
        for ($j = $i; $j -lt [Math]::Min($i + 120, $lines.Count); $j++) {
            [void]$body.Add($lines[$j])
            foreach ($ch in $lines[$j].ToCharArray()) {
                if ($ch -eq '{') { $depth++; $opened = $true } elseif ($ch -eq '}') { $depth-- }
            }
            if ($opened -and $depth -le 0) { break }
        }
        if (-not $opened) { continue }
        $norm = (($body -join ' ') -replace '//[^\n]*', '') -replace '\s+', ' '

        # 一行委托（private fun x(...) = 某公共件(...)）不算重复：它记的是本页参数。
        # 判据：够短且没有控制流。有 if / when / for / try 或超过 10 行的才可能是"又抄了一遍逻辑"。
        $hasControl = $norm -match 'if \(|when \(|for \(|while \(|try \{'
        if ($isPrivate -and ($hasControl -or $body.Count -gt 10)) {
            if (-not $privateNames.ContainsKey($name)) { $privateNames[$name] = New-Object System.Collections.ArrayList }
            [void]$privateNames[$name].Add($rel)
        }

        if ($norm.Length -lt 160) { continue }
        [void]$funcs.Add([pscustomobject]@{
            Name = $Matches[1]; File = $rel; Line = $i + 1; Lines = $body.Count; Norm = $norm
        })
    }
}

Write-Host "=== 1) 逐字相同的函数体 ==="
$dups = $funcs | Group-Object Norm | Where-Object { $_.Count -ge 2 } |
    Sort-Object { $_.Group[0].Lines * ($_.Count - 1) } -Descending
if (-not $dups) { Write-Host "（无）" }
foreach ($g in $dups) {
    $saved = $g.Group[0].Lines * ($g.Count - 1)
    Write-Host ("x{0}  约省 {1} 行  {2}" -f $g.Count, $saved, $g.Group[0].Name)
    foreach ($m in $g.Group) { Write-Host ("      {0}:{1}  ({2} 行)" -f $m.File, $m.Line, $m.Lines) }
}

Write-Host ""
Write-Host "=== 2) 同名私有函数 >= 3 份 ==="
$many = $privateNames.GetEnumerator() |
    Where-Object { ($_.Value | Sort-Object -Unique).Count -ge 3 } |
    Sort-Object { ($_.Value | Sort-Object -Unique).Count } -Descending
if (-not $many) { Write-Host "（无）" }
foreach ($e in $many) {
    $uniq = $e.Value | Sort-Object -Unique
    Write-Host ("{0,2}x  {1}" -f $uniq.Count, $e.Key)
    Write-Host ("      " + ($uniq -join ', '))
}

Write-Host ""
Write-Host "=== 3) 超过 1200 行的文件（只减不增） ==="
if ($bigFiles.Count -eq 0) { Write-Host "（无）" }
$bigFiles | Sort-Object -Descending | ForEach-Object { Write-Host $_ }

Write-Host ""
Write-Host ("扫描完成：{0} 个生产文件，{1} 个函数体参与比较。" -f $files.Count, $funcs.Count)
exit 0
