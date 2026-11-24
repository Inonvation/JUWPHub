package edu.jxslu.schedule.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [AesBlock] 对公开标准向量的解密结果。
 *
 * 明文/密文配对一律取自标准文本，**不在测试里用本实现造密文**——那样只能证明自洽：
 * ECB 取 FIPS-197 附录 C.1，CBC 取 NIST SP 800-38A 附录 F.2.1。
 */
class AesBlockTest {

    private fun hex(text: String): ByteArray =
        requireNotNull(QzxyFrame.hexToBytes(text)) { "测试向量不是合法十六进制：$text" }

    @Test
    fun `AES-128 ECB 解出 FIPS-197 附录 C1 的明文`() {
        val key = hex("000102030405060708090a0b0c0d0e0f")
        val cipherText = hex("69c4e0d86a7b0430d8cdb78070b4c55a")
        assertArrayEquals(
            hex("00112233445566778899aabbccddeeff"),
            AesBlock.decryptEcb(key, cipherText),
        )
    }

    @Test
    fun `AES-128 CBC 解出 SP 800-38A F21 的首块`() {
        val key = hex("2b7e151628aed2a6abf7158809cf4f3c")
        val iv = hex("000102030405060708090a0b0c0d0e0f")
        val cipherText = hex("7649abac8119b246cee98e9b12e9197d")
        assertArrayEquals(
            hex("6bc1bee22e409f96e93d7e117393172a"),
            AesBlock.decryptCbc(key, iv, cipherText),
        )
    }

    @Test
    fun `CBC 多块按异或链解开四块`() {
        // 同一个 F.2.1 向量，一次给满四块：只对首块正确还可能是 IV 用对了、
        // 链没接上，这块专门钉「上一块密文当作下一块的异或输入」。
        val key = hex("2b7e151628aed2a6abf7158809cf4f3c")
        val iv = hex("000102030405060708090a0b0c0d0e0f")
        val cipherText = hex(
            "7649abac8119b246cee98e9b12e9197d" +
                "5086cb9b507219ee95db113a917678b2" +
                "73bed6b8e3c1743b7116e69e22229516" +
                "3ff1caa1681fac09120eca307586e1a7",
        )
        assertArrayEquals(
            hex(
                "6bc1bee22e409f96e93d7e117393172a" +
                    "ae2d8a571e03ac9c9eb76fac45af8e51" +
                    "30c81c46a35ce411e5fbc1191a0a52ef" +
                    "f69f2445df4f9b17ad2b417be66c3710",
            ),
            AesBlock.decryptCbc(key, iv, cipherText),
        )
    }

    @Test
    fun `尾部不足一块的字节被丢掉而不是抛异常`() {
        val key = hex("000102030405060708090a0b0c0d0e0f")
        val cipherText = hex("69c4e0d86a7b0430d8cdb78070b4c55aff")
        assertEquals(16, AesBlock.decryptEcb(key, cipherText).size)
        assertEquals(0, AesBlock.decryptEcb(key, hex("aabbcc")).size)
    }

    @Test
    fun `密钥或 IV 长度不对直接拒绝`() {
        val cipherText = hex("69c4e0d86a7b0430d8cdb78070b4c55a")
        assertThrows(IllegalArgumentException::class.java) {
            AesBlock.decryptEcb(ByteArray(15), cipherText)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AesBlock.decryptCbc(ByteArray(16), ByteArray(8), cipherText)
        }
    }
}
