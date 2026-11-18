package com.nexysa7.crypto.data.protocol

/**
 * CRC-16/CCITT-FALSE: polynomial 0x1021, initial value 0xFFFF, no input/output
 * reflection and no final XOR. This is the checksum used by the Nexys frame.
 */
object Crc16 {
    private const val POLYNOMIAL = 0x1021
    private const val INITIAL_VALUE = 0xFFFF

    /**
     * Computes the checksum over `data[offset until offset + length]`.
     *
     * @throws IllegalArgumentException when the requested range is invalid.
     */
    fun ccittFalse(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset,
    ): Int {
        require(offset >= 0) { "offset must be >= 0" }
        require(length >= 0) { "length must be >= 0" }
        require(offset + length <= data.size) { "offset + length exceeds data size" }

        var crc = INITIAL_VALUE
        for (index in offset until offset + length) {
            crc = crc xor ((data[index].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor POLYNOMIAL) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }
}
