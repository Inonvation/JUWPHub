package edu.jxslu.schedule.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置 WebView 的外链分流判据（2026-11-08，审查 #3）。
 *
 * 判宽的代价：`about:` / `blob:` 这类 WebView 自己要处理（或根本不能外发）的地址被
 * 当成外部链接，页面功能直接坏掉；判窄的代价：`tel:` / `weixin://` 留在 WebView 里
 * 变成 `ERR_UNKNOWN_URL_SCHEME` 错误页。两头都钉住。
 */
class WebViewSupportTest {

    @Test
    fun httpAndWebViewInternalSchemesStayInWebView() {
        assertFalse(shouldHandOffToSystem("https://xgxt.juwp.edu.cn/"))
        assertFalse(shouldHandOffToSystem("http://portal.juwp.edu.cn/cas/login_portal"))
        assertFalse(shouldHandOffToSystem("about:blank"))
        assertFalse(shouldHandOffToSystem("javascript:void(0)"))
        assertFalse(shouldHandOffToSystem("data:text/html,hi"))
        assertFalse(shouldHandOffToSystem("blob:https://xgxt.juwp.edu.cn/abc"))
        assertFalse(shouldHandOffToSystem("file:///sdcard/a.txt"))
        assertFalse(shouldHandOffToSystem(null))
        assertFalse(shouldHandOffToSystem(""))
    }

    @Test
    fun systemSchemesAreHandedOff() {
        assertTrue(shouldHandOffToSystem("tel:12345678"))
        assertTrue(shouldHandOffToSystem("mailto:student@example.com"))
        assertTrue(shouldHandOffToSystem("weixin://dl/business"))
        assertTrue(shouldHandOffToSystem("alipays://platformapi/startapp"))
        assertTrue(shouldHandOffToSystem("intent://scan/#Intent;scheme=zxing;end"))
        assertTrue(shouldHandOffToSystem("market://details?id=x"))
    }
}
