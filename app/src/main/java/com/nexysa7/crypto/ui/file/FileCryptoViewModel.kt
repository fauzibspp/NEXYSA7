package com.nexysa7.crypto.ui.file

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexysa7.crypto.data.format.HexCodec
import com.nexysa7.crypto.data.protocol.NexysPacketFramer
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.TransportType
import com.nexysa7.crypto.domain.transport.FpgaTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Immutable UI state for the File Encryption tab. */
data class FileCryptoUiState(
    val sourceName: String? = null,
    val sourceSize: Long = 0L,
    val previewHex: String = "",
    val outputName: String? = null,
    val outputSize: Long = 0L,
    val isBusy: Boolean = false,
    val isSaving: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val lastOperation: EncryptionMode? = null,
    val lastTransport: TransportType? = null,
    val latencyMillis: Long? = null,
    val throughputBytesPerSecond: Double? = null,
    val hasOutput: Boolean = false,
) {
    val canRun: Boolean get() = !isBusy && sourceName != null

    val hasStats: Boolean get() = latencyMillis != null

    val suggestedOutputName: String
        get() {
            val base = sourceName ?: "nexys"
            val suffix = if (lastOperation == EncryptionMode.DECRYPT) ".dec" else ".enc"
            return if (base.contains('.')) {
                base.substringBeforeLast('.') + suffix
            } else {
                base + suffix
            }
        }
}

/**
 * Drives the binary file flow. File I/O runs on Dispatchers.IO; the crypto
 * operation itself is delegated to [FpgaTransport].
 */
class FileCryptoViewModel(
    private val transport: FpgaTransport,
    private val contentResolver: ContentResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileCryptoUiState())
    val uiState: StateFlow<FileCryptoUiState> = _uiState.asStateFlow()

    private var sourceBytes: ByteArray? = null
    private var outputBytes: ByteArray? = null

    fun onFilePicked(uri: Uri) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            val loaded = withContext(Dispatchers.IO) { readFile(uri) }
            if (loaded == null) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        error = "Could not read the selected file (limit " +
                            "${NexysPacketFramer.MAX_PAYLOAD_BYTES / (1024 * 1024)} MiB).",
                    )
                }
                return@launch
            }
            sourceBytes = loaded.bytes
            outputBytes = null
            _uiState.update {
                it.copy(
                    sourceName = loaded.name,
                    sourceSize = loaded.bytes.size.toLong(),
                    previewHex = buildPreview(loaded.bytes),
                    outputName = null,
                    outputSize = 0L,
                    isBusy = false,
                    progress = 0f,
                    error = null,
                    hasOutput = false,
                    lastOperation = null,
                    lastTransport = null,
                    latencyMillis = null,
                    throughputBytesPerSecond = null,
                )
            }
        }
    }

    fun encrypt() = startOperation(EncryptionMode.ENCRYPT)

    fun decrypt() = startOperation(EncryptionMode.DECRYPT)

    fun saveResult(uri: Uri) {
        val bytes = outputBytes ?: return
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val saved = withContext(Dispatchers.IO) {
                try {
                    val stream = contentResolver.openOutputStream(uri)
                    if (stream == null) {
                        false
                    } else {
                        stream.use { it.write(bytes) }
                        true
                    }
                } catch (_: IOException) {
                    false
                }
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    error = if (saved) null else "Could not write the output file.",
                )
            }
        }
    }

    fun clear() {
        if (_uiState.value.isBusy) return
        sourceBytes = null
        outputBytes = null
        _uiState.value = FileCryptoUiState()
    }

    private fun startOperation(mode: EncryptionMode) {
        val bytes = sourceBytes ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0f,
                    error = null,
                    lastOperation = mode,
                    lastTransport = null,
                    latencyMillis = null,
                    throughputBytesPerSecond = null,
                    hasOutput = false,
                    outputName = null,
                    outputSize = 0L,
                )
            }

            val result = transport.execute(mode, bytes) { progress ->
                _uiState.update { it.copy(progress = progress.fraction) }
            }

            when (result) {
                is CryptoResult.Success -> {
                    outputBytes = result.output
                    val throughput = if (result.elapsedMillis > 0) {
                        result.inputSize * 1000.0 / result.elapsedMillis
                    } else {
                        null
                    }
                    _uiState.update { state ->
                        val withOperation = state.copy(
                            isBusy = false,
                            progress = 1f,
                            error = null,
                            hasOutput = true,
                            outputSize = result.output.size.toLong(),
                            lastOperation = mode,
                            lastTransport = result.transport,
                            latencyMillis = result.elapsedMillis,
                            throughputBytesPerSecond = throughput,
                        )
                        withOperation.copy(outputName = withOperation.suggestedOutputName)
                    }
                }

                is CryptoResult.Failure -> {
                    outputBytes = null
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            progress = 0f,
                            error = result.reason,
                            hasOutput = false,
                            outputSize = 0L,
                        )
                    }
                }
            }
        }
    }

    private fun readFile(uri: Uri): LoadedFile? {
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "selected-file"
        val limit = NexysPacketFramer.MAX_PAYLOAD_BYTES
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > limit) return null
                    output.write(buffer, 0, read)
                }
                LoadedFile(name = name, bytes = output.toByteArray())
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun buildPreview(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val head = bytes.copyOf(minOf(bytes.size, PREVIEW_BYTES))
        val hex = HexCodec.encode(head).chunked(2).joinToString(" ")
        return if (bytes.size > PREVIEW_BYTES) "$hex …" else hex
    }

    private data class LoadedFile(val name: String, val bytes: ByteArray)

    companion object {
        private const val READ_BUFFER_BYTES = 8 * 1024
        private const val PREVIEW_BYTES = 16
    }
}
