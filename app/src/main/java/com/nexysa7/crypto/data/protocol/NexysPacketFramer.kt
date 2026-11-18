package com.nexysa7.crypto.data.protocol

import com.nexysa7.crypto.domain.model.CryptoPacket
import com.nexysa7.crypto.domain.model.EncryptionMode

/**
 * Encodes and decodes the Nexys A7 framed protocol:
 *
 * ```
 * [START_BYTE: 0xAA][CMD: 1][PAYLOAD_LEN: 4, big-endian][PAYLOAD: N][CRC16: 2, big-endian]
 * ```
 *
 * The CRC is CRC-16/CCITT-FALSE over the command byte, the length field and
 * the payload (frame bytes 1 up to the CRC). The start byte is not covered.
 * All multi-byte fields are big-endian.
 */
object NexysPacketFramer {
    const val START_BYTE: Byte = 0xAA.toByte()
    const val MAX_PAYLOAD_BYTES: Int = 8 * 1024 * 1024

    const val START_BYTE_SIZE = 1
    const val COMMAND_SIZE = 1
    const val LENGTH_SIZE = 4
    const val CRC_SIZE = 2
    const val HEADER_SIZE = START_BYTE_SIZE + COMMAND_SIZE + LENGTH_SIZE
    const val OVERHEAD_BYTES = HEADER_SIZE + CRC_SIZE

    /** Total number of bytes a frame with [payloadSize] payload bytes occupies. */
    fun frameSizeFor(payloadSize: Int): Int = OVERHEAD_BYTES + payloadSize

    /**
     * Serializes [packet] into a complete frame.
     *
     * @throws IllegalArgumentException when the payload exceeds [MAX_PAYLOAD_BYTES].
     */
    fun encode(packet: CryptoPacket): ByteArray {
        require(packet.payload.size <= MAX_PAYLOAD_BYTES) {
            "Payload of ${packet.payload.size} bytes exceeds the $MAX_PAYLOAD_BYTES-byte frame limit"
        }
        val payload = packet.payload
        val frame = ByteArray(frameSizeFor(payload.size))
        frame[0] = START_BYTE
        frame[1] = packet.mode.commandByte
        writeIntBigEndian(frame, 2, payload.size)
        payload.copyInto(frame, HEADER_SIZE)
        val crc = Crc16.ccittFalse(
            data = frame,
            offset = COMMAND_SIZE,
            length = COMMAND_SIZE + LENGTH_SIZE + payload.size,
        )
        writeShortBigEndian(frame, HEADER_SIZE + payload.size, crc)
        return frame
    }

    /**
     * Validates and decodes a complete frame. Never throws for malformed input;
     * the failure reason is reported through [FrameDecodeResult.Failure].
     */
    fun decode(frame: ByteArray): FrameDecodeResult {
        if (frame.size < OVERHEAD_BYTES) {
            return FrameDecodeResult.Failure(
                error = FrameError.TOO_SHORT,
                detail = "Frame has ${frame.size} bytes; at least $OVERHEAD_BYTES are required",
            )
        }
        if (frame[0] != START_BYTE) {
            return FrameDecodeResult.Failure(
                error = FrameError.INVALID_START_BYTE,
                detail = "Expected start byte 0xAA but found 0x%02X".format(frame[0].toInt() and 0xFF),
            )
        }
        val declaredLength = readIntBigEndian(frame, START_BYTE_SIZE + COMMAND_SIZE)
        if (declaredLength < 0 || declaredLength > MAX_PAYLOAD_BYTES) {
            return FrameDecodeResult.Failure(
                error = FrameError.PAYLOAD_TOO_LARGE,
                detail = "Declared payload length $declaredLength is outside 0..$MAX_PAYLOAD_BYTES",
            )
        }
        val expectedSize = frameSizeFor(declaredLength)
        if (frame.size != expectedSize) {
            return FrameDecodeResult.Failure(
                error = FrameError.LENGTH_MISMATCH,
                detail = "Frame has ${frame.size} bytes but the length field implies $expectedSize",
            )
        }
        val mode = EncryptionMode.fromCommandByte(frame[COMMAND_SIZE])
        if (mode == null) {
            return FrameDecodeResult.Failure(
                error = FrameError.UNKNOWN_COMMAND,
                detail = "Unknown command byte 0x%02X".format(frame[COMMAND_SIZE].toInt() and 0xFF),
            )
        }
        val expectedCrc = readShortBigEndian(frame, HEADER_SIZE + declaredLength)
        val actualCrc = Crc16.ccittFalse(
            data = frame,
            offset = COMMAND_SIZE,
            length = COMMAND_SIZE + LENGTH_SIZE + declaredLength,
        )
        if (actualCrc != expectedCrc) {
            return FrameDecodeResult.Failure(
                error = FrameError.CRC_MISMATCH,
                detail = "Frame carries CRC 0x%04X but the bytes compute to 0x%04X"
                    .format(expectedCrc, actualCrc),
            )
        }
        val payload = frame.copyOfRange(HEADER_SIZE, HEADER_SIZE + declaredLength)
        return FrameDecodeResult.Success(CryptoPacket(mode = mode, payload = payload))
    }

    private fun writeIntBigEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readIntBigEndian(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)

    private fun writeShortBigEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 8).toByte()
        target[offset + 1] = value.toByte()
    }

    private fun readShortBigEndian(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 8) or (source[offset + 1].toInt() and 0xFF)
}
