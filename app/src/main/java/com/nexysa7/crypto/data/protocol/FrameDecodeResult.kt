package com.nexysa7.crypto.data.protocol

import com.nexysa7.crypto.domain.model.CryptoPacket

/** Result of validating and decoding one Nexys frame. */
sealed interface FrameDecodeResult {
    data class Success(val packet: CryptoPacket) : FrameDecodeResult

    data class Failure(
        val error: FrameError,
        val detail: String = "",
    ) : FrameDecodeResult
}
