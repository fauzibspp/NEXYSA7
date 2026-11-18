package com.nexysa7.crypto.data.transport

import com.nexysa7.crypto.domain.model.EncryptionMode

/**
 * Deterministic, reversible stand-in for the FPGA crypto core.
 *
 * It XORs the payload with a repeating demo keystream. This is **not
 * cryptography** and must never be enabled as a security feature; it exists
 * only so Encrypt -> Decrypt round-trips behave like real hardware while the
 * Nexys A7 board is unavailable.
 *
 * [process] is offset-aware so chunk boundaries do not restart the keystream;
 * encryption and decryption are the same transform.
 */
internal class SimulatedCryptoEngine(
    private val key: ByteArray = DEMO_KEY,
) {
    init {
        require(key.isNotEmpty()) { "Simulation key must not be empty" }
    }

    /**
     * Applies the reversible transform to [input]. [offset] is the absolute
     * position of this chunk within the full payload.
     */
    @Suppress("UNUSED_PARAMETER")
    fun process(input: ByteArray, mode: EncryptionMode, offset: Int): ByteArray {
        val output = ByteArray(input.size)
        for (index in input.indices) {
            val keyByte = key[(offset + index) % key.size].toInt() and 0xFF
            output[index] = (input[index].toInt() xor keyByte).toByte()
        }
        return output
    }

    companion object {
        private val DEMO_KEY = "NEXYSA7-DEMO-KEY".toByteArray(Charsets.US_ASCII)
    }
}
