package com.nexysa7.crypto.data.format

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexCodecTest {

    @Test
    fun `round-trips arbitrary bytes`() {
        val bytes = ByteArray(256) { it.toByte() }
        assertArrayEquals(bytes, HexCodec.decode(HexCodec.encode(bytes)))
    }

    @Test
    fun `encodes uppercase two-digit groups`() {
        val bytes = byteArrayOf(0x00, 0x0A, 0xFF.toByte())
        assertEquals("00 0A FF", HexCodec.encode(bytes).chunked(2).joinToString(" "))
    }

    @Test
    fun `ignores whitespace when decoding`() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x23, 0xAB.toByte()),
            HexCodec.decode("01 23\nAB"),
        )
    }

    @Test
    fun `rejects odd length and invalid characters`() {
        assertNull(HexCodec.decode("ABC"))
        assertNull(HexCodec.decode("GG"))
    }

    @Test
    fun `blank input decodes to empty bytes`() {
        assertEquals(0, HexCodec.decode("   ")!!.size)
    }
}
