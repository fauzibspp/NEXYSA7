package com.nexysa7.crypto.domain.transport

import com.nexysa7.crypto.domain.model.SimulatedLink
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls which simulated link is currently selected.
 *
 * Kept separate from [FpgaTransport] so the transport abstraction stays
 * hardware-agnostic; only the simulation UI needs link switching.
 */
interface SimulatedTransportControl {
    /** Currently selected simulated link, or `null` for the Disconnected choice. */
    val selectedLink: StateFlow<SimulatedLink?>

    /**
     * Selects a simulated link. Implementations must disconnect the previous
     * link first. Selecting `null` disconnects and leaves no active link.
     */
    suspend fun selectLink(link: SimulatedLink?)
}
