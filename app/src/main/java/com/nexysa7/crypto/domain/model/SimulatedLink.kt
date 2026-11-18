package com.nexysa7.crypto.domain.model

/**
 * Selectable simulated link profiles.
 *
 * The UI connection selector exposes these as "Simulated USB" and "Simulated
 * BLE". Each profile carries realistic transfer characteristics so the two
 * modes visibly differ: USB uses 1 KiB chunks, while BLE uses the RN4870's
 * negotiated 247-byte MTU minus the 3-byte ATT header (244-byte chunks).
 */
enum class SimulatedLink(
    val displayName: String,
    val transportType: TransportType,
    val chunkSize: Int,
    val connectLatencyMs: Long,
    val handshakeLatencyMs: Long,
    val chunkLatencyMs: Long,
    val ackLatencyMs: Long,
    val disconnectLatencyMs: Long,
) {
    USB(
        displayName = "Simulated USB",
        transportType = TransportType.SIMULATED_USB,
        chunkSize = 1024,
        connectLatencyMs = 350,
        handshakeLatencyMs = 150,
        chunkLatencyMs = 12,
        ackLatencyMs = 30,
        disconnectLatencyMs = 100,
    ),
    BLE(
        displayName = "Simulated BLE",
        transportType = TransportType.SIMULATED_BLE,
        chunkSize = 244,
        connectLatencyMs = 700,
        handshakeLatencyMs = 300,
        chunkLatencyMs = 18,
        ackLatencyMs = 45,
        disconnectLatencyMs = 150,
    ),
}
