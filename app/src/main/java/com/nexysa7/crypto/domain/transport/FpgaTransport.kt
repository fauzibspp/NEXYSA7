package com.nexysa7.crypto.domain.transport

import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.TransferProgress
import com.nexysa7.crypto.domain.model.TransportType
import kotlinx.coroutines.flow.StateFlow

/**
 * Hardware-agnostic link to the Nexys A7 cryptographic engine.
 *
 * The UI and ViewModel layers depend only on this interface. Moving from the
 * simulated engine to real hardware means binding a different implementation
 * in AppContainer; nothing above the data layer changes.
 */
interface FpgaTransport {
    val transportType: TransportType

    /** Current connection state. Safe to collect from any thread. */
    val connectionState: StateFlow<ConnectionState>

    /** Establishes the link (including any hardware handshake). */
    suspend fun connect()

    /** Releases the link and any hardware resources. */
    suspend fun disconnect()

    /**
     * Sends [payload] to the FPGA in [mode] and returns the processed bytes.
     *
     * [onProgress] is invoked as chunks are transferred. Implementations must
     * not throw for ordinary protocol or transport errors; they return a
     * [CryptoResult.Failure] instead. Coroutine cancellation is propagated.
     */
    suspend fun execute(
        mode: EncryptionMode,
        payload: ByteArray,
        onProgress: (TransferProgress) -> Unit = {},
    ): CryptoResult
}
