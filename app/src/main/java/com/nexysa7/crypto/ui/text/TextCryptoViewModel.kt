package com.nexysa7.crypto.ui.text

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexysa7.crypto.data.format.HexCodec
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.TransportType
import com.nexysa7.crypto.domain.transport.FpgaTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** How the single text editor's content maps to bytes. */
enum class TextFormat(val label: String) {
    UTF8("UTF-8"),
    HEX("Hex"),
}

/**
 * Immutable UI state for the Text Encryption tab.
 *
 * There is deliberately a single [editor] field: plaintext and ciphertext share
 * the same box. After an operation the editor content is replaced with the
 * result and [format] is updated to match the representation on screen.
 */
data class TextCryptoUiState(
    val editor: String = "",
    val format: TextFormat = TextFormat.UTF8,
    val isBusy: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val lastOperation: EncryptionMode? = null,
    val lastTransport: TransportType? = null,
    val latencyMillis: Long? = null,
    val throughputBytesPerSecond: Double? = null,
    val previousEditor: String? = null,
    val previousFormat: TextFormat? = null,
) {
    val canRun: Boolean get() = !isBusy && editor.isNotEmpty()

    val canSwap: Boolean get() = !isBusy && previousEditor != null

    val hasStats: Boolean get() = latencyMillis != null
}

/**
 * Drives the single-editor text flow.
 *
 * - Encrypt reads the editor as UTF-8 text or hex bytes and writes the
 *   ciphertext back as hex.
 * - Decrypt reads the editor as UTF-8 text or hex bytes and writes readable
 *   UTF-8 when the result decodes cleanly, otherwise hex.
 * - Swap toggles between the current editor content and the previous content,
 *   which gives the single-box UI the same convenience as a Swap-to-Input
 *   control.
 */
class TextCryptoViewModel(
    private val transport: FpgaTransport,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextCryptoUiState())
    val uiState: StateFlow<TextCryptoUiState> = _uiState.asStateFlow()

    fun onEditorChange(value: String) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(editor = value, error = null) }
    }

    fun onFormatChange(format: TextFormat) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(format = format, error = null) }
    }

    fun encrypt() = startOperation(EncryptionMode.ENCRYPT)

    fun decrypt() = startOperation(EncryptionMode.DECRYPT)

    fun swapWithPrevious() {
        val snapshot = _uiState.value
        if (snapshot.isBusy) return
        val previousEditor = snapshot.previousEditor ?: return
        val previousFormat = snapshot.previousFormat ?: TextFormat.UTF8
        _uiState.update {
            it.copy(
                editor = previousEditor,
                format = previousFormat,
                previousEditor = snapshot.editor,
                previousFormat = snapshot.format,
                error = null,
            )
        }
    }

    fun clear() {
        if (_uiState.value.isBusy) return
        _uiState.value = TextCryptoUiState()
    }

    private fun startOperation(mode: EncryptionMode) {
        val snapshot = _uiState.value
        if (snapshot.isBusy || snapshot.editor.isEmpty()) return

        val inputBytes = when (snapshot.format) {
            TextFormat.UTF8 -> snapshot.editor.toByteArray(StandardCharsets.UTF_8)
            TextFormat.HEX -> HexCodec.decode(snapshot.editor) ?: run {
                _uiState.update {
                    it.copy(
                        error = "Enter valid hexadecimal bytes: an even number of 0-9/A-F characters.",
                    )
                }
                return
            }
        }

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
                    previousEditor = it.editor,
                    previousFormat = it.format,
                )
            }

            val result = transport.execute(mode, inputBytes) { progress ->
                _uiState.update { it.copy(progress = progress.fraction) }
            }

            when (result) {
                is CryptoResult.Success -> {
                    val rendered = when (mode) {
                        EncryptionMode.ENCRYPT -> HexCodec.encode(result.output) to TextFormat.HEX
                        EncryptionMode.DECRYPT -> {
                            val text = decodeUtf8OrNull(result.output)
                            if (text != null) {
                                text to TextFormat.UTF8
                            } else {
                                HexCodec.encode(result.output) to TextFormat.HEX
                            }
                        }
                    }
                    val throughput = if (result.elapsedMillis > 0) {
                        result.inputSize * 1000.0 / result.elapsedMillis
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            editor = rendered.first,
                            format = rendered.second,
                            isBusy = false,
                            progress = 1f,
                            error = null,
                            lastOperation = mode,
                            lastTransport = result.transport,
                            latencyMillis = result.elapsedMillis,
                            throughputBytesPerSecond = throughput,
                        )
                    }
                }

                is CryptoResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            progress = 0f,
                            error = result.reason,
                        )
                    }
                }
            }
        }
    }

    private fun decodeUtf8OrNull(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
