package edu.jxslu.schedule.domain

import okio.ByteString.Companion.decodeBase64

/**
 * 上传消费数据后服务端返回的 `clData`（DESIGN §4.30）。
 *
 * 客户端拿到它之后解密一次，明文里带一个 `-` 分隔符。
 * 设备那句「每次需验证通过后才能清除数据」很可能就指它：蓝牙设备服务端够不着，
 * 只能由服务端签发一张凭据、App 转交给设备验，验过了才让清。
 *
 * 解密所需的密钥与 IV 是两个固定常量（2026-09-27 经协议分析确定）：
 *
 * ```
 * 密钥  "20210118klcx@002"   16 字节
 * IV    "1234567876543210"   ECB 模式下不用
 * ```
 *
 * 同一加密库里另有几把旧协议的密钥（`20181201klcx@001`、`klcx002@20181224` 等），
 * 与这里无关，别混用。
 *
 * 模式是 ECB，按无填充处理，所以这里也走 `NoPadding`，再手工剥 PKCS#7——
 * 有填充就剥，没有就原样返回。
 *
 * 解密本身走 [AesBlock] 的纯 Kotlin 实现，不碰 `javax.crypto`：iOS 侧的
 * Kotlin/Native 没有这套 API，而这里解的只是一张常量密钥签发的凭据，
 * 自己实现比给两端各挂一套平台加密库更省事，两端结果也逐字节一致。
 */
object QzxyClData {

    /** AES-128 密钥（协议固定常量）。 */
    const val KEY: String = "20210118klcx@002"

    /** ECB 模式下用不到，CBC 回退时才用。 */
    const val IV: String = "1234567876543210"

    private const val BLOCK_SIZE = 16

    /**
     * 解密 `clData`。密文按十六进制或 Base64 解，哪种能解出 16 的整数倍就用哪种。
     * 长度不对、密钥不可用、解密失败都返回 null。
     *
     * 模式按协议分析是 ECB，但入参里确实带着 IV，
     * 所以 ECB 解出来不是可打印文本时再试一次 CBC。正常路径不会走到 CBC。
     */
    fun decrypt(encoded: String?): ByteArray? {
        val text = encoded?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cipherText = decodeCipherText(text) ?: return null
        // `Charsets` 是 JVM 专有的（kotlin.text.Charsets），commonMain 里用 encodeToByteArray() /
        // decodeToString()。这里的密钥、IV 与「可打印文本」判定本来就是 ASCII，UTF-8 逐字节等价。
        val key = KEY.encodeToByteArray()
        val ecb = runCatching { stripPadding(AesBlock.decryptEcb(key, cipherText)) }.getOrNull()
        if (ecb != null && isPrintable(ecb)) return ecb
        val cbc = runCatching {
            stripPadding(AesBlock.decryptCbc(key, IV.encodeToByteArray(), cipherText))
        }.getOrNull()
        if (cbc != null && isPrintable(cbc)) return cbc
        return ecb
    }

    private fun isPrintable(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.all { (it.toInt() and 0xFF) in 0x20..0x7E }

    private fun decodeCipherText(text: String): ByteArray? {
        QzxyFrame.hexToBytes(text)
            ?.takeIf { it.size % BLOCK_SIZE == 0 }
            ?.let { return it }
        return text.decodeBase64()
            ?.toByteArray()
            ?.takeIf { it.size % BLOCK_SIZE == 0 }
    }

    /** 剥 PKCS#7 填充。填充不合法就原样返回，宁可把多出来的字节留给调用方看。 */
    private fun stripPadding(plain: ByteArray): ByteArray {
        if (plain.isEmpty()) return plain
        val pad = plain.last().toInt() and 0xFF
        if (pad !in 1..BLOCK_SIZE || pad > plain.size) return plain
        if (plain.takeLast(pad).any { (it.toInt() and 0xFF) != pad }) return plain
        return plain.copyOfRange(0, plain.size - pad)
    }

    /**
     * 给人看的明文形式：全是可打印 ASCII 就按文本给，否则给十六进制。
     * 调试日志与诊断文本共用，省得两边各写一份。
     */
    fun describe(plain: ByteArray?): String {
        if (plain == null) return "-"
        if (plain.isEmpty()) return "（空）"
        val printable = plain.all { (it.toInt() and 0xFF) in 0x20..0x7E }
        return if (printable) {
            "${plain.decodeToString()}（${plain.size} 字节）"
        } else {
            "${QzxyFrame.bytesToHex(plain)}（${plain.size} 字节）"
        }
    }
}
