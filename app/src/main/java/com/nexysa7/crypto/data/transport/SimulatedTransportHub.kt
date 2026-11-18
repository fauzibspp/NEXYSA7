package com.nexysa7.crypto.data.transport

import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.SimulatedLink
import com.nexysa7.crypto.domain.model.TransferProgress
import com.nexysa7.crypto.domain.model.TransportType
import com.nexysa7.crypto.domain.transport.FpgaTransport
import com.nexysa7.crypto.domain.transport.SimulatedTransportControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Allows the UI to switch between the Simulated USB and Simulated BLE links.
 *
 * Each link is a separate [SimulatedFpgaTransport] with its own profile, so
 * switching paths disconnects the previous link and connects the new one.
 * Only one link is active at a time; operations and link switches are
 * serialized by a mutex so a transfer cannot be torn apart by a switch.
 */
class SimulatedTransportHub(
    links: Map<SimulatedLink, SimulatedFpgaTransport> = SimulatedLink.entries.associateWith { link ->
        SimulatedFpgaTransport(link = link)
    },
) : FpgaTransport, SimulatedTransportControl {

    private val transports: Map<SimulatedLink, SimulatedFpgaTransport> = links.toMap()
    private val mutex = Mutex()

    private val _selectedLink = MutableStateFlow<SimulatedLink?>(null)
    override val selectedLink: StateFlow<SimulatedLink?> = _selectedLink.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var active: SimulatedFpgaTransport? = null

    init {
        require(transports.keys.containsAll(SimulatedLink.entries)) {
            "SimulatedTransportHub needs a transport for every SimulatedLink"
        }
    }

    override val transportType: TransportType
        get() = active?.transportType
            ?: _selectedLink.value?.transportType
            ?: TransportType.SIMULATED_USB

    override suspend fun connect() = mutex.withLock {
        val link = _selectedLink.value ?: SimulatedLink.USB
        if (_selectedLink.value == null) {
            _selectedLink.value = link
        }
        val target = active ?: transports.getValue(link).also { active = it }
        if (target.connectionState.value is ConnectionState.Connected) {
            _connectionState.value = target.connectionState.value
            return@withLock
        }
        _connectionState.value = ConnectionState.Connecting(target.transportType)
        target.connect()
        _connectionState.value = target.connectionState.value
    }

    override suspend fun disconnect() = mutex.withLock {
        active?.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun selectLink(link: SimulatedLink?) = mutex.withLock {
        if (_selectedLink.value == link) return@withLock
        val previous = active
        active = null
        previous?.disconnect()
        _selectedLink.value = link
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun execute(
        mode: EncryptionMode,
        payload: ByteArray,
        onProgress: (TransferProgress) -> Unit,
    ): CryptoResult = mutex.withLock {
        val target = active
            ?: return@withLock CryptoResult.Failure(mode, "FPGA transport is not connected")
        target.execute(mode, payload, onProgress)
    }
}
