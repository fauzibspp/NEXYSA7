package com.nexysa7.crypto.data.transport

import com.nexysa7.crypto.data.format.HexCodec
import com.nexysa7.crypto.data.protocol.NexysPacketFramer
import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.SimulatedLink
import com.nexysa7.crypto.domain.model.TransferProgress
import com.nexysa7.crypto.domain.model.TransportType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedFpgaTransportTest {

    private fun newTransport(chunkSize: Int = 1024) = SimulatedFpgaTransport(
        chunkSize = chunkSize,
        connectLatencyMs = 40,
        handshakeLatencyMs = 10,
        chunkLatencyMs = 1,
        ackLatencyMs = 5,
        disconnectLatencyMs = 5,
    )

    @Test
    fun `starts disconnected`() = runTest {
        val transport = newTransport()
        assertEquals(ConnectionState.Disconnected, transport.connectionState.value)
        assertEquals(TransportType.SIMULATED_USB, transport.transportType)
    }

    @Test
    fun `connect reports connecting then connected after the handshake delay`() = runTest {
        val transport = newTransport()
        val states = mutableListOf<ConnectionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            transport.connectionState.toList(states)
        }

        transport.connect()

        assertEquals(ConnectionState.Connecting(TransportType.SIMULATED_USB), states[1])
        assertEquals(ConnectionState.Connected(TransportType.SIMULATED_USB), states.last())
        assertEquals(50L, testScheduler.currentTime)
    }

    @Test
    fun `connect is idempotent while connected`() = runTest {
        val transport = newTransport()
        transport.connect()
        val afterFirstConnect = testScheduler.currentTime

        transport.connect()

        assertEquals(afterFirstConnect, testScheduler.currentTime)
        assertEquals(ConnectionState.Connected(TransportType.SIMULATED_USB), transport.connectionState.value)
    }

    @Test
    fun `execute before connect fails cleanly`() = runTest {
        val transport = newTransport()
        val result = transport.execute(EncryptionMode.ENCRYPT, "hello".toByteArray())
        assertTrue(result is CryptoResult.Failure)
    }

    @Test
    fun `disconnect returns to disconnected`() = runTest {
        val transport = newTransport()
        transport.connect()
        transport.disconnect()
        assertEquals(ConnectionState.Disconnected, transport.connectionState.value)
    }

    @Test
    fun `text round-trips through base64 and the simulated engine`() = runTest {
        val transport = newTransport()
        transport.connect()
        val plaintext = "Nexys A7 — encrypt me ✓"

        val encrypted = transport.execute(
            EncryptionMode.ENCRYPT,
            plaintext.toByteArray(Charsets.UTF_8),
        )
        assertTrue(encrypted is CryptoResult.Success)
        val cipherBytes = (encrypted as CryptoResult.Success).output
        assertNotEquals(plaintext, cipherBytes.toString(Charsets.UTF_8))

        val wireText = HexCodec.encode(cipherBytes)
        val decodedCipher = HexCodec.decode(wireText)!!
        val decrypted = transport.execute(EncryptionMode.DECRYPT, decodedCipher)
        assertTrue(decrypted is CryptoResult.Success)

        assertEquals(
            plaintext,
            (decrypted as CryptoResult.Success).output.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun `binary file round-trips through disk`() = runTest {
        val transport = newTransport(chunkSize = 256)
        transport.connect()
        val original = ByteArray(8 * 1024) { index -> ((index * 31) + 7).toByte() }
        val directory = File(
            System.getProperty("java.io.tmpdir"),
            "nexys-a7-test-${System.nanoTime()}",
        )
        assertTrue(directory.mkdirs())
        try {
            val source = File(directory, "input.bin").apply { writeBytes(original) }
            val encrypted = transport.execute(EncryptionMode.ENCRYPT, source.readBytes())
            assertTrue(encrypted is CryptoResult.Success)
            val cipherFile = File(directory, "input.bin.enc").apply {
                writeBytes((encrypted as CryptoResult.Success).output)
            }

            val decrypted = transport.execute(EncryptionMode.DECRYPT, cipherFile.readBytes())
            assertTrue(decrypted is CryptoResult.Success)
            assertArrayEquals(original, (decrypted as CryptoResult.Success).output)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `chunked progress is monotonic and reaches one`() = runTest {
        val transport = newTransport(chunkSize = 256)
        transport.connect()
        val payload = ByteArray(4 * 1024) { it.toByte() }
        val progress = mutableListOf<TransferProgress>()

        val result = transport.execute(EncryptionMode.ENCRYPT, payload) { progress += it }

        assertTrue(result is CryptoResult.Success)
        assertTrue("Expected multiple progress updates, got ${progress.size}", progress.size > 2)
        assertEquals(0f, progress.first().fraction, 0.0001f)
        assertEquals(1f, progress.last().fraction, 0.0001f)
        progress.zipWithNext { previous, next ->
            assertTrue(
                "Progress must not decrease: ${previous.bytesProcessed} -> ${next.bytesProcessed}",
                next.bytesProcessed >= previous.bytesProcessed,
            )
        }
    }

    @Test
    fun `empty payload round-trips and reports full progress`() = runTest {
        val transport = newTransport()
        transport.connect()
        val progress = mutableListOf<TransferProgress>()

        val result = transport.execute(EncryptionMode.ENCRYPT, ByteArray(0)) { progress += it }

        assertTrue(result is CryptoResult.Success)
        assertArrayEquals(ByteArray(0), (result as CryptoResult.Success).output)
        assertEquals(1f, progress.last().fraction, 0.0001f)
    }

    @Test
    fun `payload above the frame limit fails without throwing`() = runTest {
        val transport = newTransport()
        transport.connect()
        val oversized = ByteArray(NexysPacketFramer.MAX_PAYLOAD_BYTES + 1)

        val result = transport.execute(EncryptionMode.ENCRYPT, oversized)

        assertTrue(result is CryptoResult.Failure)
    }

    @Test
    fun `ble profile reports its transport type and uses ble chunks`() = runTest {
        val transport = SimulatedFpgaTransport(
            link = SimulatedLink.BLE,
            connectLatencyMs = 40,
            handshakeLatencyMs = 10,
            chunkLatencyMs = 1,
            ackLatencyMs = 5,
            disconnectLatencyMs = 5,
        )
        transport.connect()

        assertEquals(
            ConnectionState.Connected(TransportType.SIMULATED_BLE),
            transport.connectionState.value,
        )
        assertEquals(TransportType.SIMULATED_BLE, transport.transportType)

        val progress = mutableListOf<TransferProgress>()
        val result = transport.execute(EncryptionMode.ENCRYPT, ByteArray(1000)) { progress += it }

        assertTrue(result is CryptoResult.Success)
        assertEquals(TransportType.SIMULATED_BLE, (result as CryptoResult.Success).transport)
        // 1000 bytes at the 244-byte BLE chunk size gives five chunk steps.
        assertTrue("Expected several progress updates, got ${progress.size}", progress.size >= 3)
    }
}
