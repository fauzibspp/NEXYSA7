package com.nexysa7.crypto.data.format

/**
 * Hexadecimal presentation codec used by the text tab and the file preview.
 *
 * Decoding ignores whitespace but rejects odd-length input and any character
 * outside `0-9`, `A-F` or `a-f`.
 */
object HexCodec {
    private val HEX_DIGITS = "0123456789ABCDEF"

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val output = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            output.append(HEX_DIGITS[value ushr 4])
            output.append(HEX_DIGITS[value and 0x0F])
        }
        return output.toString()
    }

    fun decode(text: String): ByteArray? {
        val compact = buildString(text.length) {
            for (character in text) {
                if (!character.isWhitespace()) append(character)
            }
        }
        if (compact.isEmpty()) return ByteArray(0)
        if (compact.length % 2 != 0) return null

        val output = ByteArray(compact.length / 2)
        for (index in output.indices) {
            val high = hexValue(compact[index * 2]) ?: return null
            val low = hexValue(compact[index * 2 + 1]) ?: return null
            output[index] = ((high shl 4) or low).toByte()
        }
        return output
    }

    private fun hexValue(character: Char): Int? = when (character) {
        in '0'..'9' -> character - '0'
        in 'a'..'f' -> character - 'a' + 10
        in 'A'..'F' -> character - 'A' + 10
        else -> null
    }
}
