package com.nexysa7.crypto.domain.model

/** Lifecycle of the link between the app and the FPGA engine. */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data class Connecting(val transport: TransportType) : ConnectionState

    data class Connected(val transport: TransportType) : ConnectionState

    data class Failed(val transport: TransportType, val reason: String) : ConnectionState
}
