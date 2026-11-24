package edu.jxslu.schedule.domain

/**
 * 纯 Kotlin 的 AES-128 解密原语（ECB / CBC，NoPadding 语义）。
 *
 * **为什么自己实现而不用 `javax.crypto`**：iOS 侧的 Kotlin/Native 没有这套 API，
 * 走 expect/actual + CommonCrypto 等于给一段逻辑加两套平台代码。[QzxyClData] 用它解的
 * 只是一张用硬编码常量密钥签发的服务端凭据，风险等级很低，纯 Kotlin 实现让
 * Android / iOS 行为逐字节一致、能在 JVM 单测里直接验，也不必引入任何平台胶水。
 *
 * **只做解密**：主源码没有加密需求。正确性由 NIST 公开向量（FIPS-197 附录 C.1、
 * SP 800-38A 附录 F.2.1）在 `AesBlockTest` 里钉死，不靠「自己加密再解」自证。
 *
 * 填充按 NoPadding：尾部 PKCS#7 由 [QzxyClData] 手工剥。
 */
object AesBlock {

    private const val BLOCK_SIZE = 16

    /** AES-128 的密钥长度。AES-192/256 用不到，也不支持。 */
    private const val KEY_SIZE = 16

    private const val ROUNDS = 10

    /** 轮密钥展开后共 [BLOCK_SIZE] × (轮数 + 1) 字节。 */
    private const val EXPANDED_KEY_SIZE = BLOCK_SIZE * (ROUNDS + 1)

    /** FIPS-197 Figure 7 的 S-box。 */
    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
    )

    /** 逆 S-box 由 S-box 反查得到：省一份手抄表，也就少一处抄错的机会。 */
    private val INV_SBOX = IntArray(256).also { inv ->
        SBOX.forEachIndexed { index, value -> inv[value] = index }
    }

    /** 逆 MixColumns 的四个 GF(2⁸) 乘积表，类加载时各算一次。 */
    private val MUL9 = IntArray(256) { gfMul(it, 9) }
    private val MUL11 = IntArray(256) { gfMul(it, 11) }
    private val MUL13 = IntArray(256) { gfMul(it, 13) }
    private val MUL14 = IntArray(256) { gfMul(it, 14) }

    /**
     * AES-128 ECB 解密（NoPadding）。
     *
     * @param key 16 字节密钥；长度不对抛 [IllegalArgumentException]。
     * @param cipherText 密文，只解完整的 16 字节块，尾部不足一块的字节直接丢掉
     *   （调用方 [QzxyClData] 已按 16 的整数倍筛过密文，这里兜的是「不抛崩」）。
     * @return 明文，长度恒为 16 的整数倍；输入不足一块时返回空数组。
     */
    fun decryptEcb(key: ByteArray, cipherText: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "AES-128 密钥必须是 16 字节，实际 ${key.size}" }
        val roundKeys = expandKey(key)
        val out = ByteArray(cipherText.size / BLOCK_SIZE * BLOCK_SIZE)
        for (offset in out.indices step BLOCK_SIZE) {
            decryptBlock(roundKeys, cipherText, offset, out, offset)
        }
        return out
    }

    /**
     * AES-128 CBC 解密（NoPadding），[iv] 只参与首块的异或链。
     *
     * @param key 16 字节密钥，[iv] 16 字节初始向量；长度不对抛 [IllegalArgumentException]。
     * @param cipherText 密文，只解完整的 16 字节块，规则同 [decryptEcb]。
     */
    fun decryptCbc(key: ByteArray, iv: ByteArray, cipherText: ByteArray): ByteArray {
        require(key.size == KEY_SIZE) { "AES-128 密钥必须是 16 字节，实际 ${key.size}" }
        require(iv.size == BLOCK_SIZE) { "CBC 的 IV 必须是 16 字节，实际 ${iv.size}" }
        val roundKeys = expandKey(key)
        val out = ByteArray(cipherText.size / BLOCK_SIZE * BLOCK_SIZE)
        val chain = IntArray(BLOCK_SIZE) { iv[it].toInt() and 0xFF }
        for (offset in out.indices step BLOCK_SIZE) {
            decryptBlock(roundKeys, cipherText, offset, out, offset)
            for (i in 0 until BLOCK_SIZE) {
                out[offset + i] = (out[offset + i].toInt() xor chain[i]).toByte()
                chain[i] = cipherText[offset + i].toInt() and 0xFF
            }
        }
        return out
    }

    /** 解一块：状态按列主序存放，`state[r + 4c]` 与 FIPS-197 的 `s[r][c]` 一一对应。 */
    private fun decryptBlock(
        roundKeys: IntArray,
        input: ByteArray,
        inOffset: Int,
        output: ByteArray,
        outOffset: Int,
    ) {
        val state = IntArray(BLOCK_SIZE) { input[inOffset + it].toInt() and 0xFF }
        addRoundKey(state, roundKeys, ROUNDS)
        for (round in ROUNDS - 1 downTo 1) {
            invShiftRows(state)
            invSubBytes(state)
            addRoundKey(state, roundKeys, round)
            invMixColumns(state)
        }
        invShiftRows(state)
        invSubBytes(state)
        addRoundKey(state, roundKeys, 0)
        for (i in 0 until BLOCK_SIZE) output[outOffset + i] = state[i].toByte()
    }

    private fun addRoundKey(state: IntArray, roundKeys: IntArray, round: Int) {
        val base = round * BLOCK_SIZE
        for (i in 0 until BLOCK_SIZE) state[i] = state[i] xor roundKeys[base + i]
    }

    /** 每行循环右移本行号位：`new[c] = old[(c - r) mod 4]`。 */
    private fun invShiftRows(state: IntArray) {
        for (row in 1..3) {
            val old = IntArray(4) { state[row + 4 * it] }
            for (col in 0 until 4) state[row + 4 * col] = old[(col - row + 4) % 4]
        }
    }

    private fun invSubBytes(state: IntArray) {
        for (i in 0 until BLOCK_SIZE) state[i] = INV_SBOX[state[i]]
    }

    private fun invMixColumns(state: IntArray) {
        for (col in 0 until 4) {
            val at = col * 4
            val s0 = state[at]
            val s1 = state[at + 1]
            val s2 = state[at + 2]
            val s3 = state[at + 3]
            state[at] = MUL14[s0] xor MUL11[s1] xor MUL13[s2] xor MUL9[s3]
            state[at + 1] = MUL9[s0] xor MUL14[s1] xor MUL11[s2] xor MUL13[s3]
            state[at + 2] = MUL13[s0] xor MUL9[s1] xor MUL14[s2] xor MUL11[s3]
            state[at + 3] = MUL11[s0] xor MUL13[s1] xor MUL9[s2] xor MUL14[s3]
        }
    }

    /**
     * AES-128 轮密钥展开，输出 [EXPANDED_KEY_SIZE] 个字节（每轮 16 字节）。
     * 解密直接用同一份展开结果，只是从最后一轮往前取。
     */
    private fun expandKey(key: ByteArray): IntArray {
        val words = IntArray(EXPANDED_KEY_SIZE / 4)
        for (i in 0 until 4) {
            val at = i * 4
            words[i] = ((key[at].toInt() and 0xFF) shl 24) or
                ((key[at + 1].toInt() and 0xFF) shl 16) or
                ((key[at + 2].toInt() and 0xFF) shl 8) or
                (key[at + 3].toInt() and 0xFF)
        }
        // 轮常量 rcon 依次是 2⁰..2⁹ 在 GF(2⁸) 里的值，现算省一张表。
        var rcon = 1
        for (i in 4 until words.size) {
            var temp = words[i - 1]
            if (i % 4 == 0) {
                temp = subWord(rotWord(temp)) xor (rcon shl 24)
                rcon = gfMul(rcon, 2)
            }
            words[i] = words[i - 4] xor temp
        }
        val bytes = IntArray(EXPANDED_KEY_SIZE)
        for (i in words.indices) {
            bytes[i * 4] = (words[i] ushr 24) and 0xFF
            bytes[i * 4 + 1] = (words[i] ushr 16) and 0xFF
            bytes[i * 4 + 2] = (words[i] ushr 8) and 0xFF
            bytes[i * 4 + 3] = words[i] and 0xFF
        }
        return bytes
    }

    private fun rotWord(word: Int): Int = (word shl 8) or (word ushr 24)

    private fun subWord(word: Int): Int =
        (SBOX[(word ushr 24) and 0xFF] shl 24) or
            (SBOX[(word ushr 16) and 0xFF] shl 16) or
            (SBOX[(word ushr 8) and 0xFF] shl 8) or
            SBOX[word and 0xFF]

    /** GF(2⁸) 乘法，模 AES 的不可约多项式 x⁸ + x⁴ + x³ + x + 1（0x11B）。 */
    private fun gfMul(a: Int, b: Int): Int {
        var x = a
        var y = b
        var product = 0
        repeat(8) {
            if (y and 1 != 0) product = product xor x
            x = ((x shl 1) xor (if (x and 0x80 != 0) 0x11B else 0)) and 0xFF
            y = y shr 1
        }
        return product
    }
}
