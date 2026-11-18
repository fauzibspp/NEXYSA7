package com.nexysa7.crypto.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Crc16Test {

    @Test
    fun `matches the CCITT-FALSE check value for 123456789`() {
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0x29B1, Crc16.ccittFalse(data))
    }

    @Test
    fun `empty input returns the initial value`() {
        assertEquals(0xFFFF, Crc16.ccittFalse(ByteArray(0)))
    }

    @Test
    fun `single zero byte matches the published vector`() {
        assertEquals(0xE1F0, Crc16.ccittFalse(byteArrayOf(0x00)))
    }

    @Test
    fun `honors offset and length slices`() {
        val padding = byteArrayOf(0x01, 0x02, 0x03)
        val payload = "123456789".toByteArray(Charsets.US_ASCII)
        val data = padding + payload
        assertEquals(
            0x29B1,
            Crc16.ccittFalse(data = data, offset = padding.size, length = payload.size),
        )
    }

    @Test
    fun `rejects out-of-bounds ranges`() {
        assertThrows(IllegalArgumentException::class.java) {
            Crc16.ccittFalse(ByteArray(4), offset = 2, length = 4)
        }
    }
}
