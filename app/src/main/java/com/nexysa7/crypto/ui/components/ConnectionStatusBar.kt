package com.nexysa7.crypto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.SimulatedLink

/**
 * Live top-bar badge for the target FPGA and the current link state.
 *
 * The chip part is static (it identifies the board); the dot and the second
 * line change with [state].
 */
@Composable
fun ChipStatusBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val presentation = state.toBadgePresentation()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = presentation.color.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(presentation.color, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = "XC7A100T-1CSG324C",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Text(
                    text = presentation.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = presentation.color,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Connection selector and Connect/Disconnect toggle shown under the top bar.
 */
@Composable
fun ConnectionControls(
    connectionState: ConnectionState,
    selectedLink: SimulatedLink?,
    onSelectLink: (SimulatedLink?) -> Unit,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionSelector(
            connectionState = connectionState,
            selectedLink = selectedLink,
            onSelectLink = onSelectLink,
            modifier = Modifier.weight(1f),
        )
        ConnectToggle(
            connectionState = connectionState,
            onToggleConnection = onToggleConnection,
        )
    }
}

@Composable
private fun ConnectionSelector(
    connectionState: ConnectionState,
    selectedLink: SimulatedLink?,
    onSelectLink: (SimulatedLink?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val linkIsUp = connectionState is ConnectionState.Connected ||
        connectionState is ConnectionState.Connecting
    val label = if (linkIsUp) {
        selectedLink?.displayName ?: "Disconnected"
    } else {
        "Disconnected"
    }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Text("▾")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Disconnected") },
                trailingIcon = { if (!linkIsUp || selectedLink == null) Text("✓") },
                onClick = {
                    expanded = false
                    onSelectLink(null)
                },
            )
            SimulatedLink.entries.forEach { link ->
                DropdownMenuItem(
                    text = { Text(link.displayName) },
                    trailingIcon = { if (linkIsUp && selectedLink == link) Text("✓") },
                    onClick = {
                        expanded = false
                        onSelectLink(link)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectToggle(
    connectionState: ConnectionState,
    onToggleConnection: () -> Unit,
) {
    val connecting = connectionState is ConnectionState.Connecting
    val connected = connectionState is ConnectionState.Connected
    Button(
        onClick = onToggleConnection,
        enabled = !connecting,
        modifier = Modifier.height(48.dp),
    ) {
        Text(
            when {
                connecting -> "Connecting…"
                connected -> "Disconnect"
                else -> "Connect"
            },
        )
    }
}

private data class BadgePresentation(val label: String, val color: Color)

private fun ConnectionState.toBadgePresentation(): BadgePresentation = when (this) {
    ConnectionState.Disconnected -> BadgePresentation("Offline", Color(0xFF9E9E9E))
    is ConnectionState.Connecting -> BadgePresentation("Connecting", Color(0xFFFFB300))
    is ConnectionState.Connected -> BadgePresentation("Online", Color(0xFF2E7D32))
    is ConnectionState.Failed -> BadgePresentation("Error", Color(0xFFC62828))
}
