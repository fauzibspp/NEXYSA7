package com.nexysa7.crypto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.SimulatedLink
import com.nexysa7.crypto.domain.transport.FpgaTransport
import com.nexysa7.crypto.domain.transport.SimulatedTransportControl
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the connection lifecycle and the simulated-link selector.
 *
 * Selecting a simulated link disconnects the previous one and connects the new
 * profile. The Connect/Disconnect toggle reconnects the last selected profile
 * (defaulting to Simulated USB) or drops the link.
 */
class ConnectionViewModel(
    private val transport: FpgaTransport,
    private val simulation: SimulatedTransportControl,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = transport.connectionState

    val selectedLink: StateFlow<SimulatedLink?> = simulation.selectedLink

    fun selectLink(link: SimulatedLink?) {
        viewModelScope.launch {
            simulation.selectLink(link)
            if (link != null) {
                transport.connect()
            }
        }
    }

    fun toggleConnection() {
        viewModelScope.launch {
            when (transport.connectionState.value) {
                is ConnectionState.Connected -> transport.disconnect()
                is ConnectionState.Connecting -> Unit
                else -> {
                    if (simulation.selectedLink.value == null) {
                        simulation.selectLink(SimulatedLink.USB)
                    }
                    transport.connect()
                }
            }
        }
    }
}
