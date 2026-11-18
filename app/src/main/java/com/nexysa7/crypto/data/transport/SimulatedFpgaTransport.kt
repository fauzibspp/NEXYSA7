package com.nexysa7.crypto.data.transport

import com.nexysa7.crypto.data.protocol.FrameDecodeResult
import com.nexysa7.crypto.data.protocol.NexysPacketFramer
import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.CryptoPacket
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.SimulatedLink
import com.nexysa7.crypto.domain.model.TransferProgress
import com.nexysa7.crypto.domain.model.TransportType
import com.nexysa7.crypto.domain.transport.FpgaTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default transport used until the physical Nexys A7 is available.
 *
 * It models the whole link: connection handshake, per-chunk transfer latency
 * and a deterministic reversible crypto core. Request and response bytes both
 * pass through [NexysPacketFramer], so the simulation exercises the same
 * framing and CRC path that real drivers will use.
 *
 * [link] selects the simulated USB or BLE profile; every latency and chunk size
 * is still overridable so unit tests can keep them small and run in virtual
 * time.
 */
class SimulatedFpgaTransport(
    private val link: SimulatedLink = SimulatedLink.USB,
    private val chunkSize: Int = link.chunkSize,
    private val connectLatencyMs: Long = link.connectLatencyMs,
    private val handshakeLatencyMs: Long = link.handshakeLatencyMs,
    private val chunkLatencyMs: Long = link.chunkLatencyMs,
    private val ackLatencyMs: Long = link.ackLatencyMs,
    private val disconnectLatencyMs: Long = link.disconnectLatencyMs,
) : FpgaTransport {

    init {
        require(chunkSize > 0) { "chunkSize must be > 0" }
        require(connectLatencyMs >= 0) { "connectLatencyMs must be >= 0" }
        require(handshakeLatencyMs >= 0) { "handshakeLatencyMs must be >= 0" }
        require(chunkLatencyMs >= 0) { "chunkLatencyMs must be >= 0" }
        require(ackLatencyMs >= 0) { "ackLatencyMs must be >= 0" }
        require(disconnectLatencyMs >= 0) { "disconnectLatencyMs must be >= 0" }
    }

    override val transportType: TransportType = link.transportType

    private val engine = SimulatedCryptoEngine()
    private val operationMutex = Mutex()
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect() {
        if (_connectionState.value is ConnectionState.Connected) return
        _connectionState.value = ConnectionState.Connecting(transportType)
        delay(connectLatencyMs)
        // A real board performs its hello/handshake exchange here.
        delay(handshakeLatencyMs)
        _connectionState.value = ConnectionState.Connected(transportType)
    }

    override suspend fun disconnect() {
        if (_connectionState.value is ConnectionState.Disconnected) return
        delay(disconnectLatencyMs)
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun execute(
        mode: EncryptionMode,
        payload: ByteArray,
        onProgress: (TransferProgress) -> Unit,
    ): CryptoResult = operationMutex.withLock {
        if (_connectionState.value !is ConnectionState.Connected) {
            return@withLock CryptoResult.Failure(mode, "FPGA transport is not connected")
        }
        if (payload.size > NexysPacketFramer.MAX_PAYLOAD_BYTES) {
            return@withLock CryptoResult.Failure(
                mode,
                "Payload of ${payload.size} bytes exceeds the ${NexysPacketFramer.MAX_PAYLOAD_BYTES}-byte limit",
            )
        }

        val startedAtNanos = System.nanoTime()
        try {
            // Encode once up front so the simulated link exercises the real
            // framing path (including the frame-size guard).
            NexysPacketFramer.encode(CryptoPacket(mode, payload))

            val total = payload.size.toLong()
            onProgress(TransferProgress(0L, total))

            // Process in chunks for realistic framing, but collapse the number
            // of delay/progress steps so multi-megabyte files stay usable in
            // the UI. Text and small files step once per chunk.
            val chunkCount = if (payload.isEmpty()) {
                0
            } else {
                (payload.size + chunkSize - 1) / chunkSize
            }
            val stepCount = minOf(chunkCount, MAX_PROGRESS_STEPS)
            val chunksPerStep = if (stepCount == 0) {
                0
            } else {
                (chunkCount + stepCount - 1) / stepCount
            }

            val output = ByteArray(payload.size)
            var offset = 0
            while (offset < payload.size) {
                var chunksInStep = 0
                while (offset < payload.size && chunksInStep < chunksPerStep) {
                    val end = minOf(offset + chunkSize, payload.size)
                    engine.process(
                        input = payload.copyOfRange(offset, end),
                        mode = mode,
                        offset = offset,
                    ).copyInto(output, offset)
                    offset = end
                    chunksInStep++
                }
                delay(chunkLatencyMs)
                onProgress(TransferProgress(offset.toLong(), total))
            }
            // Latency of the FPGA ack plus the response transfer.
            delay(ackLatencyMs)

            val responseFrame = NexysPacketFramer.encode(CryptoPacket(mode, output))
            when (val decoded = NexysPacketFramer.decode(responseFrame)) {
                is FrameDecodeResult.Success -> {
                    val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000
                    onProgress(TransferProgress(total, total))
                    CryptoResult.Success(
                        mode = mode,
                        transport = transportType,
                        inputSize = payload.size,
                        output = decoded.packet.payload,
                        elapsedMillis = elapsedMillis,
                    )
                }

                is FrameDecodeResult.Failure -> CryptoResult.Failure(
                    mode = mode,
                    reason = "Simulated response failed validation: ${decoded.error} (${decoded.detail})",
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            CryptoResult.Failure(
                mode = mode,
                reason = error.message ?: "Simulated transfer failed",
                cause = error,
            )
        }
    }

    companion object {
        const val MAX_PROGRESS_STEPS = 64
    }
}
