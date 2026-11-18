package com.nexysa7.crypto.domain.model

/** Snapshot of a chunked transfer to or from the FPGA. */
data class TransferProgress(
    val bytesProcessed: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) {
            1f
        } else {
            (bytesProcessed.toFloat() / totalBytes).coerceIn(0f, 1f)
        }
}
