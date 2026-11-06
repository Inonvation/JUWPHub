package edu.jxslu.schedule.ui.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 收银台页面日志的脱敏（2026-11-06）。
 *
 * 页面控制台里的失败信息常常带着整条请求 URL，而收银台把会话放在 query 里
 * （`ticket=` / `token=`）。凭证不进日志是硬规则——这里是唯一把它们抹掉的地方，
 * 且这段逻辑必须纯函数可测（它只吃第三方页面的输出，不能指望内容规矩）。
 */
class CashierLogRedactionTest {

    @Test
    fun `控制台日志里的会话凭据会被抹掉`() {
        val raw = "GET https://yktpay.juwp.edu.cn/payment/prepayOrders/1" +
            "?ticket=abc123&payTicket=eyJhbGciOi&x=1 failed"

        val out = redactCashierSecrets(raw)

        assertFalse(out.contains("abc123"))
        assertFalse(out.contains("eyJhbGciOi"))
        assertEquals(
            "GET https://yktpay.juwp.edu.cn/payment/prepayOrders/1?ticket=***&payTicket=***&x=1 failed",
            out,
        )
    }

    @Test
    fun `大写 TOKEN 与下划线参数同样抹掉`() {
        val raw = "https://mobile.abchina.com/mpaynew/mpay/index?TOKEN=1793986&refresh_token=r3f"

        val out = redactCashierSecrets(raw)

        assertFalse(out.contains("1793986"))
        assertFalse(out.contains("r3f"))
        assertEquals(
            "https://mobile.abchina.com/mpaynew/mpay/index?TOKEN=***&refresh_token=***",
            out,
        )
    }

    @Test
    fun `没有凭据的日志原样返回`() {
        val raw = "Uncaught TypeError: window.AndroidFunc is undefined"

        assertEquals(raw, redactCashierSecrets(raw))
    }

    @Test
    fun `普通单词里含 token 不误伤`() {
        // header 里的 "token" 前缀（如 tokenizer）不该被当成参数名
        val raw = "tokenizer failed at tokenizer.js"

        assertEquals(raw, redactCashierSecrets(raw))
    }
}
