package com.nexysa7.crypto.data.transport

import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.SimulatedLink
import com.nexysa7.crypto.domain.model.TransportType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedTransportHubTest {

    private fun newHub(): SimulatedTransportHub = SimulatedTransportHub(
        links = SimulatedLink.entries.associateWith { link ->
            SimulatedFpgaTransport(
                link = link,
                connectLatencyMs = 5,
                handshakeLatencyMs = 5,
                chunkLatencyMs = 1,
                ackLatencyMs = 1,
                disconnectLatencyMs = 1,
            )
        },
    )

    @Test
    fun `starts disconnected with no link selected`() = runTest {
        val hub = newHub()

        assertEquals(ConnectionState.Disconnected, hub.connectionState.value)
        assertNull(hub.selectedLink.value)
        assertTrue(hub.execute(EncryptionMode.ENCRYPT, "x".toByteArray()) is CryptoResult.Failure)
    }

    @Test
    fun `connect defaults to simulated usb`() = runTest {
        val hub = newHub()

        hub.connect()

        assertEquals(SimulatedLink.USB, hub.selectedLink.value)
        assertEquals(
            ConnectionState.Connected(TransportType.SIMULATED_USB),
            hub.connectionState.value,
        )
    }

    @Test
    fun `selecting ble switches the active profile`() = runTest {
        val hub = newHub()
        hub.connect()

        hub.selectLink(SimulatedLink.BLE)

        assertEquals(SimulatedLink.BLE, hub.selectedLink.value)
        assertEquals(ConnectionState.Disconnected, hub.connectionState.value)

        hub.connect()
        assertEquals(
            ConnectionState.Connected(TransportType.SIMULATED_BLE),
            hub.connectionState.value,
        )
        val result = hub.execute(EncryptionMode.ENCRYPT, "hello".toByteArray())
        assertTrue(result is CryptoResult.Success)
        assertEquals(TransportType.SIMULATED_BLE, (result as CryptoResult.Success).transport)
    }

    @Test
    fun `selecting disconnected clears the link`() = runTest {
        val hub = newHub()
        hub.connect()

        hub.selectLink(null)

        assertNull(hub.selectedLink.value)
        assertEquals(ConnectionState.Disconnected, hub.connectionState.value)
    }

    @Test
    fun `disconnect keeps the selected link for reconnect`() = runTest {
        val hub = newHub()
        hub.selectLink(SimulatedLink.BLE)
        hub.connect()
        hub.disconnect()

        assertEquals(SimulatedLink.BLE, hub.selectedLink.value)
        assertEquals(ConnectionState.Disconnected, hub.connectionState.value)

        hub.connect()
        assertEquals(
            ConnectionState.Connected(TransportType.SIMULATED_BLE),
            hub.connectionState.value,
        )
    }
}
