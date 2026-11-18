package com.nexysa7.crypto.domain.model

/**
 * A logical request/response for the FPGA engine.
 *
 * [payload] is opaque binary data, so text and file flows share this model.
 */
data class CryptoPacket(
    val mode: EncryptionMode,
    val payload: ByteArray,
) {
    val payloadSize: Int get() = payload.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CryptoPacket) return false
        return mode == other.mode && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * mode.hashCode() + payload.contentHashCode()

    override fun toString(): String = "CryptoPacket(mode=$mode, payloadSize=${payload.size})"
}
