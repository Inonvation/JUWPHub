package edu.jxslu.schedule.ui.web

/**
 * 非 http(s) 链接该不该交给宿主（2026-11-25，从 `:app/.../ui/common/WebViewSupport.kt` 收口到这里）。
 *
 * 排除 WebView 自己能处理 / 不能外发的路径（`about:` / `javascript:` / `data:` /
 * `blob:` / `file:`），其余（`tel:` / `mailto:` / `sms:` / `weixin://` /
 * `alipays://` / `intent://` / `market://` …）都算外链——留在 WebView 里只会显示
 * `ERR_UNKNOWN_URL_SCHEME` 错误页。
 *
 * ## 为什么这条判据在 `commonMain`
 *
 * 两端都要它：Android 的 `shouldOverrideUrlLoading` 与 iOS 的
 * `decidePolicyForNavigationAction:` 走的是同一个口径，抄两份迟早分叉。`:app` 那一侧
 * 保留同名函数，但只剩一行转发（学工 / 收银台 / 教务导入那几个窗口的调用点不动）。
 *
 * `:app` 的 `WebViewSupportTest` 仍在测这条判据，判宽 / 判窄两头的边界都钉在那里。
 */
fun shouldHandOffToSystem(url: String?): Boolean {
    val u = url.orEmpty().trim()
    if (u.isEmpty()) return false
    val lower = u.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) return false
    return !lower.startsWith("about:") &&
        !lower.startsWith("javascript:") &&
        !lower.startsWith("data:") &&
        !lower.startsWith("blob:") &&
        !lower.startsWith("file:")
}
