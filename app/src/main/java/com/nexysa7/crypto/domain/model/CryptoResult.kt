package com.nexysa7.crypto.domain.model

/** Outcome of one encrypt/decrypt operation on the FPGA engine. */
sealed interface CryptoResult {
    val mode: EncryptionMode

    data class Success(
        override val mode: EncryptionMode,
        val transport: TransportType,
        val inputSize: Int,
        val output: ByteArray,
        val elapsedMillis: Long,
    ) : CryptoResult

    data class Failure(
        override val mode: EncryptionMode,
        val reason: String,
        val cause: Throwable? = null,
    ) : CryptoResult
}
