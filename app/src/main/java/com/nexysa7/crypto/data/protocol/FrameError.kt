package com.nexysa7.crypto.data.protocol

/** Reasons a byte array can fail to decode as a Nexys frame. */
enum class FrameError {
    TOO_SHORT,
    INVALID_START_BYTE,
    UNKNOWN_COMMAND,
    PAYLOAD_TOO_LARGE,
    LENGTH_MISMATCH,
    CRC_MISMATCH,
}
