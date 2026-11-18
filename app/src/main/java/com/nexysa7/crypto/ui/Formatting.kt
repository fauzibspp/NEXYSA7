package com.nexysa7.crypto.ui

import java.util.Locale

/** Formats a byte count for compact UI readouts (B / KB / MB). */
fun formatByteCount(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Formats a simulated throughput readout, or an em dash when unavailable. */
fun formatThroughput(bytesPerSecond: Double?): String {
    if (bytesPerSecond == null || bytesPerSecond.isNaN() || bytesPerSecond <= 0.0) return "—"
    return when {
        bytesPerSecond < 1024.0 -> String.format(Locale.US, "%.0f B/s", bytesPerSecond)
        bytesPerSecond < 1024.0 * 1024.0 -> String.format(Locale.US, "%.1f KB/s", bytesPerSecond / 1024.0)
        else -> String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
    }
}
