package com.nexysa7.crypto.data.protocol

import com.nexysa7.crypto.domain.model.CryptoPacket
import com.nexysa7.crypto.domain.model.EncryptionMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexysPacketFramerTest {

    private val payload = "ABC".toByteArray(Charsets.US_ASCII)
    private val packet = CryptoPacket(EncryptionMode.ENCRYPT, payload)

    @Test
    fun `encodes the documented frame layout`() {
        val frame = NexysPacketFramer.encode(packet)

        assertEquals(8 + payload.size, frame.size)
        assertEquals(NexysPacketFramer.START_BYTE, frame[0])
        assertEquals(EncryptionMode.ENCRYPT.commandByte, frame[1])
        assertEquals(0, frame[2].toInt())
        assertEquals(0, frame[3].toInt())
        assertEquals(0, frame[4].toInt())
        assertEquals(payload.size, frame[5].toInt())
        assertArrayEquals(payload, frame.copyOfRange(6, 6 + payload.size))

        val crc = ((frame[6 + payload.size].toInt() and 0xFF) shl 8) or
            (frame[7 + payload.size].toInt() and 0xFF)
        val expected = Crc16.ccittFalse(frame, offset = 1, length = 5 + payload.size)
        assertEquals(expected, crc)
    }

    @Test
    fun `round-trips both encryption modes`() {
        for (mode in EncryptionMode.entries) {
            val original = CryptoPacket(mode, payload)
            val decoded = NexysPacketFramer.decode(NexysPacketFramer.encode(original))
            assertTrue("Expected success for $mode, got $decoded", decoded is FrameDecodeResult.Success)
            assertEquals(original, (decoded as FrameDecodeResult.Success).packet)
        }
    }

    @Test
    fun `round-trips an empty payload`() {
        val original = CryptoPacket(EncryptionMode.DECRYPT, ByteArray(0))
        val decoded = NexysPacketFramer.decode(NexysPacketFramer.encode(original))
        assertEquals(original, (decoded as FrameDecodeResult.Success).packet)
    }

    @Test
    fun `rejects a frame that is too short`() {
        val result = NexysPacketFramer.decode(byteArrayOf(0xAA.toByte(), 0x01))
        assertFailure(result, FrameError.TOO_SHORT)
    }

    @Test
    fun `rejects an invalid start byte`() {
        val frame = NexysPacketFramer.encode(packet).also { it[0] = 0x00.toByte() }
        assertFailure(NexysPacketFramer.decode(frame), FrameError.INVALID_START_BYTE)
    }

    @Test
    fun `rejects an unknown command even with a valid crc`() {
        val frame = NexysPacketFramer.encode(packet)
        frame[1] = 0x7F.toByte()
        val crc = Crc16.ccittFalse(frame, offset = 1, length = 5 + payload.size)
        frame[6 + payload.size] = (crc ushr 8).toByte()
        frame[7 + payload.size] = crc.toByte()
        assertFailure(NexysPacketFramer.decode(frame), FrameError.UNKNOWN_COMMAND)
    }

    @Test
    fun `rejects a declared length above the limit`() {
        val frame = NexysPacketFramer.encode(packet)
        frame[2] = 0x00
        frame[3] = 0x80.toByte()
        frame[4] = 0x00
        frame[5] = 0x01
        assertFailure(NexysPacketFramer.decode(frame), FrameError.PAYLOAD_TOO_LARGE)
    }

    @Test
    fun `rejects length mismatches`() {
        val frame = NexysPacketFramer.encode(packet)
        assertFailure(
            NexysPacketFramer.decode(frame.copyOf(frame.size - 1)),
            FrameError.LENGTH_MISMATCH,
        )
        assertFailure(
            NexysPacketFramer.decode(frame + 0x00.toByte()),
            FrameError.LENGTH_MISMATCH,
        )
    }

    @Test
    fun `rejects a corrupted crc`() {
        val frame = NexysPacketFramer.encode(packet)
        frame[frame.lastIndex] = (frame[frame.lastIndex].toInt() xor 0xFF).toByte()
        assertFailure(NexysPacketFramer.decode(frame), FrameError.CRC_MISMATCH)
    }

    @Test
    fun `rejects a corrupted payload byte`() {
        val frame = NexysPacketFramer.encode(packet)
        frame[6] = (frame[6].toInt() xor 0x01).toByte()
        assertFailure(NexysPacketFramer.decode(frame), FrameError.CRC_MISMATCH)
    }

    @Test
    fun `crypto packet equality compares payload content`() {
        assertEquals(
            CryptoPacket(EncryptionMode.ENCRYPT, byteArrayOf(1, 2, 3)),
            CryptoPacket(EncryptionMode.ENCRYPT, byteArrayOf(1, 2, 3)),
        )
        assertNotEquals(
            CryptoPacket(EncryptionMode.ENCRYPT, byteArrayOf(1, 2, 3)),
            CryptoPacket(EncryptionMode.ENCRYPT, byteArrayOf(1, 2, 4)),
        )
    }

    private fun assertFailure(result: FrameDecodeResult, expected: FrameError) {
        assertTrue("Expected failure $expected but got $result", result is FrameDecodeResult.Failure)
        assertEquals(expected, (result as FrameDecodeResult.Failure).error)
    }
}
