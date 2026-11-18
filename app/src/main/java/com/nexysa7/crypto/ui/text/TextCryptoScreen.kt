package com.nexysa7.crypto.ui.text

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.ui.formatThroughput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stateless Text Encryption tab.
 *
 * The editor is both the input and the output. Encrypt replaces its content
 * with hex ciphertext; Decrypt restores UTF-8 text when possible. Copy copies
 * the current editor, and Swap restores the previous content and format.
 */
@Composable
fun TextCryptoScreen(
    state: TextCryptoUiState,
    isConnected: Boolean,
    onEditorChange: (String) -> Unit,
    onFormatChange: (TextFormat) -> Unit,
    onEncrypt: () -> Unit,
    onDecrypt: () -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    fun copyEditor() {
        if (state.editor.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Nexys A7 text", state.editor))
        copied = true
        scope.launch {
            delay(1500)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "One editor holds both forms: Encrypt replaces it with hex ciphertext; " +
                "Decrypt restores readable UTF-8 when possible.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FormatSelector(
            selected = state.format,
            enabled = !state.isBusy,
            onSelect = onFormatChange,
        )

        OutlinedTextField(
            value = state.editor,
            onValueChange = onEditorChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    when (state.format) {
                        TextFormat.UTF8 -> "Plaintext (UTF-8)"
                        TextFormat.HEX -> "Ciphertext (Hex)"
                    },
                )
            },
            supportingText = {
                Text(
                    when (state.format) {
                        TextFormat.UTF8 -> "The editor is read and written as UTF-8 text."
                        TextFormat.HEX -> "The editor is read and written as hexadecimal bytes."
                    },
                )
            },
            minLines = 8,
            readOnly = state.isBusy,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onEncrypt,
                modifier = Modifier.weight(1f),
                enabled = isConnected && state.canRun,
            ) { Text("Encrypt") }

            Button(
                onClick = onDecrypt,
                modifier = Modifier.weight(1f),
                enabled = isConnected && state.canRun,
            ) { Text("Decrypt") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = ::copyEditor,
                modifier = Modifier.weight(1f),
                enabled = state.editor.isNotEmpty() && !state.isBusy,
            ) { Text(if (copied) "Copied" else "Copy") }

            OutlinedButton(
                onClick = onSwap,
                modifier = Modifier.weight(1f),
                enabled = state.canSwap,
            ) { Text("Swap") }

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                enabled = !state.isBusy && (state.editor.isNotEmpty() || state.hasStats),
            ) { Text("Clear") }
        }

        if (!isConnected) {
            Text(
                text = "Connect to a simulated link to run an operation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.isBusy) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Transferring… ${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
            )
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.hasStats) {
            StatsStrip(state)
        }

        if (state.previousEditor != null) {
            Text(
                text = "Swap restores the previous editor content and format.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FormatSelector(
    selected: TextFormat,
    enabled: Boolean,
    onSelect: (TextFormat) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Format",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextFormat.entries.forEach { format ->
            FilterChip(
                selected = selected == format,
                onClick = { onSelect(format) },
                enabled = enabled,
                label = { Text(format.label) },
            )
        }
    }
}

@Composable
private fun StatsStrip(state: TextCryptoUiState) {
    val operationLabel = when (state.lastOperation) {
        EncryptionMode.ENCRYPT -> "Encrypt"
        EncryptionMode.DECRYPT -> "Decrypt"
        null -> "—"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell(
                    label = "Latency",
                    value = "${state.latencyMillis ?: 0} ms",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "Throughput",
                    value = formatThroughput(state.throughputBytesPerSecond),
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "Link",
                    value = state.lastTransport?.displayName ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Last operation: $operationLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
