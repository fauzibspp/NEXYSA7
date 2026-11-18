package com.nexysa7.crypto.domain.model

/** The physical (or simulated) link used to reach the FPGA engine. */
enum class TransportType(val displayName: String) {
    SIMULATED_USB("Simulated USB"),
    SIMULATED_BLE("Simulated BLE"),
    USB_SERIAL("USB Serial"),
    BLE("BLE"),
}
