---
name: find-hugeicons
description: Use this skill when the user wants to find or search icons by keyword, or when you need to check which icon names are available before using them in Compose code. Icons live in `edu.jxslu.schedule.ui.icon.AppIcons` (iOS 移植 P2 起).
---

# 查图标名（`AppIcons`）

本仓库的图标是**源码里的 `ImageVector`**，不是运行时依赖：全部在
`shared/src/commonMain/kotlin/edu/jxslu/schedule/ui/icon/AppIcons.kt`。
**图标名不许猜，必须先在这个文件里查到。**

> 目录名与本 skill 的名字保留 `find-hugeicons` 是为了不破坏既有引用。图标数据确实源自
> HugeIcons（MIT），但**代码里已经不用那个 AAR 了**——原因见下面「为什么不直接用 HugeIcons」。

## 何时用

- 用户要求按关键词找图标（"找个搜索图标"、"设置用什么图标"）
- 你准备写 Compose 图标代码、要确认图标名是否真实存在
- 想知道某个概念下有哪些图标可选

## 三步

### 1. 查已有图标

```powershell
Select-String -Path shared\src\commonMain\kotlin\edu\jxslu\schedule\ui\icon\AppIcons.kt `
  -Pattern '^\s+val (\w+): ImageVector' | ForEach-Object { $_.Matches[0].Groups[1].Value } |
  Select-String 'water|drop|glass'        # ← 换成你的关键词（大小写不敏感）
```

想全量列出就把最后一行的 `Select-String` 去掉。

### 2. 用法

```kotlin
import edu.jxslu.schedule.ui.icon.AppIcons

Icon(AppIcons.Settings01, contentDescription = null)
```

`AppIcons.Xxx` 的类型是 `ImageVector`，与原来 `HugeIcons.Xxx` 完全一致，`Icon` / `IconButton` /
`painterResource` 之外的一切用法都不变。

### 3. 现有图标里没有怎么办

图标是**生成物**，要加新图标先让生成器认识它：

```powershell
# 1) 先在代码里写一处 AppIcons.NeoName 的调用点（生成器按调用点扫清单，不写死）
# 2) 确认上游 AAR 里真有这个图标名
$jar = Get-ChildItem "E:\DevCache\gradle\caches" -Recurse -Filter "hugeicons-compose-1.4.aar" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
# 3) 重新导出（会按字母序插入，diff 稳定）
.\gradlew.bat :app:testDebugUnitTest --tests "*IconExportTool*" -DexportIcons=true
```

生成器是 `app/src/test/java/edu/jxslu/schedule/tools/IconExportTool.kt`，它逐节点回读自检验证
路径没有丢失；不一致会直接失败并打出图标名。

## 约束

- 图标名是 PascalCase（如 `GlobalSearch`、`Settings03`、`GlassWater`）。
- 本仓库**禁止 emoji 当功能图标**（见 `AGENTS.md` 硬性禁止）；文案行内允许。
- **别手改 `AppIcons.kt`**：它是生成物，下次重跑就覆盖。要改就改生成器。
- 别把 `com.github.rikkahub:hugeicons-compose` 加回 `implementation`：它只发 Android 变体，
  加回去 iOS 侧就编不过；现在它是 `testImplementation`，只服务于生成器。
- 图标来源仍是 HugeIcons（MIT），`OpenSourceLicenses` 里那条署名**不要删**。

## 为什么不直接用 HugeIcons

`com.github.rikkahub:hugeicons-compose:1.4` 是 JitPack 上的 **Android-only AAR**（还刻意
`isTransitive = false` 以免搅乱 compose / core 版本边界）。iOS（Kotlin/Native）上没有对应产物，
`:shared` 的 `commonMain` 一旦引用就编不过。而图标本质只是矢量数据，
`androidx.compose.ui.graphics.vector.*` 在 CMP 的 `commonMain` 里是齐的——把数据搬进源码即可，
平台差异直接消失。2026-11-24 一次性导出 167 个图标 / 519 条 path。
