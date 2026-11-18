package com.nexysa7.crypto.domain.model

/**
 * The command byte carried in the [CryptoPacket] header.
 *
 * These values are part of the wire protocol and must stay in sync with the
 * FPGA firmware.
 */
enum class EncryptionMode(val commandByte: Byte) {
    ENCRYPT(0x01),
    DECRYPT(0x02),
    ;

    companion object {
        fun fromCommandByte(value: Byte): EncryptionMode? =
            entries.firstOrNull { it.commandByte == value }
    }
}
