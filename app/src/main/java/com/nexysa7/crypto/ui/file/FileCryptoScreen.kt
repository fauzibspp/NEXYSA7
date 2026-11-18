package com.nexysa7.crypto.ui.file

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexysa7.crypto.data.protocol.NexysPacketFramer
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.SimulatedLink
import com.nexysa7.crypto.ui.formatByteCount
import com.nexysa7.crypto.ui.formatThroughput

/**
 * Stateless File Encryption tab.
 *
 * Shows the selected file, its size and the active link's chunk plan, then a
 * linear progress indicator while the simulated transfer runs. The result can
 * be saved with the Storage Access Framework.
 */
@Composable
fun FileCryptoScreen(
    state: FileCryptoUiState,
    isConnected: Boolean,
    activeLink: SimulatedLink?,
    onFilePicked: (android.net.Uri) -> Unit,
    onEncrypt: () -> Unit,
    onDecrypt: () -> Unit,
    onSaveResult: (android.net.Uri) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) onFilePicked(uri) },
    )
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri -> if (uri != null) onSaveResult(uri) },
    )

    val transferPlan = activeLink?.let { link ->
        val chunkCount = if (state.sourceSize > 0) {
            (state.sourceSize + link.chunkSize - 1) / link.chunkSize
        } else {
            0L
        }
        TransferPlan(link = link, chunkCount = chunkCount, chunkSize = link.chunkSize)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Pick a file, run it through the Nexys A7 engine, then save the result. " +
                "Maximum size: ${NexysPacketFramer.MAX_PAYLOAD_BYTES / (1024 * 1024)} MiB.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { openFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f),
                enabled = !state.isBusy,
            ) { Text("Choose file") }

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                enabled = !state.isBusy && state.sourceName != null,
            ) { Text("Clear") }
        }

        state.sourceName?.let { name ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatByteCount(state.sourceSize)} · ${state.sourceSize} bytes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (transferPlan != null) {
                        Text(
                            text = "Transfer plan: ${transferPlan.chunkCount} chunks × " +
                                "${formatByteCount(transferPlan.chunkSize.toLong())} " +
                                "(${transferPlan.link.displayName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Select a simulated link to preview the chunk plan.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.previewHex.isNotEmpty()) {
                        Text(
                            text = "First bytes: ${state.previewHex}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

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
                text = "Transferring over ${activeLink?.displayName ?: "simulated link"}… " +
                    "${(state.progress * 100).toInt()}%",
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
            Text(
                text = "Latency: ${state.latencyMillis ?: 0} ms · " +
                    "Throughput: ${formatThroughput(state.throughputBytesPerSecond)} · " +
                    "Link: ${state.lastTransport?.displayName ?: "—"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.hasOutput) {
            Text(
                text = "Output: ${state.outputName} (${formatByteCount(state.outputSize)})",
                style = MaterialTheme.typography.titleSmall,
            )
            Button(
                onClick = { saveFileLauncher.launch(state.suggestedOutputName) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy && !state.isSaving,
            ) { Text(if (state.isSaving) "Saving…" else "Save Output") }

            state.lastOperation?.let { mode ->
                Text(
                    text = if (mode == EncryptionMode.ENCRYPT) {
                        "Encrypted output is raw binary ciphertext."
                    } else {
                        "Decrypted output restores the original file bytes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class TransferPlan(
    val link: SimulatedLink,
    val chunkCount: Long,
    val chunkSize: Int,
)
