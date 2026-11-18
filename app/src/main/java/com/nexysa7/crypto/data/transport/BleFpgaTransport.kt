package com.nexysa7.crypto.data.transport

import android.bluetooth.BluetoothManager
import android.content.Context
import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.TransferProgress
import com.nexysa7.crypto.domain.model.TransportType
import com.nexysa7.crypto.domain.transport.FpgaTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE transport for the Digilent Pmod BLE (Microchip RN4870) using the
 * Transparent UART service.
 *
 * Placeholder only: the service/characteristic UUIDs and the intended data flow
 * are documented below, while every physical Bluetooth hook is marked
 * `TODO(HARDWARE)`.
 */
class BleFpgaTransport(context: Context) : FpgaTransport {

    private val appContext: Context = context.applicationContext

    override val transportType: TransportType = TransportType.BLE

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect() {
        _connectionState.value = ConnectionState.Connecting(transportType)

        // TODO(HARDWARE): Check BLUETOOTH_SCAN / BLUETOOTH_CONNECT runtime
        //  permissions, scan for the RN4870, then connectGatt(context, false, callback).
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (manager?.adapter == null) {
            _connectionState.value = ConnectionState.Failed(
                transportType,
                "Bluetooth adapter is unavailable.",
            )
            return
        }

        // TODO(HARDWARE): In BluetoothGattCallback.onServicesDiscovered:
        //  - request a larger MTU (e.g. 247 bytes),
        //  - locate TRANSPARENT_UART_SERVICE and its TX/RX characteristics,
        //  - enable notifications on TRANSPARENT_UART_TX by writing the CCCD
        //    (0x2902) and setting setCharacteristicNotification(gatt, true).
        // TODO(HARDWARE): Only report ConnectionState.Connected after the CCCD
        //  write succeeds and the FPGA handshake completes.
        // TODO(HARDWARE): Send outgoing frames by writing them to
        //  TRANSPARENT_UART_RX, chunked to (MTU - 3) bytes with
        //  WRITE_TYPE_DEFAULT.
        // TODO(HARDWARE): Feed incoming TRANSPARENT_UART_TX notifications into
        //  a rolling buffer and decode complete frames with NexysPacketFramer.
        // TODO(HARDWARE): In disconnect(), disable notifications, close the
        //  BluetoothGatt and release the callback.
        _connectionState.value = ConnectionState.Failed(
            transportType,
            "BLE RN4870 transport is not implemented yet (TODO(HARDWARE)).",
        )
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun execute(
        mode: EncryptionMode,
        payload: ByteArray,
        onProgress: (TransferProgress) -> Unit,
    ): CryptoResult = CryptoResult.Failure(
        mode = mode,
        reason = "BLE RN4870 transport is not implemented yet (TODO(HARDWARE)).",
    )

    companion object {
        /** Microchip RN4870 Transparent UART service. */
        val TRANSPARENT_UART_SERVICE: UUID =
            UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455")

        /** Module -> phone data (notifications). */
        val TRANSPARENT_UART_TX: UUID =
            UUID.fromString("49535343-1E4D-4BD9-BA61-23C647249616")

        /** Phone -> module data (writes). */
        val TRANSPARENT_UART_RX: UUID =
            UUID.fromString("49535343-8841-43F4-A8D4-ECBE34729BB3")

        /** Optional transparent UART control point. */
        val TRANSPARENT_UART_CONTROL_POINT: UUID =
            UUID.fromString("49535343-4C8A-39B3-2F49-511CFF073B7E")

        /** Default advertising name of the Pmod BLE module. */
        const val TARGET_DEVICE_NAME = "RN4870"
    }
}
