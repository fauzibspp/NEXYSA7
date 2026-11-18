package com.nexysa7.crypto.data.transport

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.TransferProgress
import com.nexysa7.crypto.domain.model.TransportType
import com.nexysa7.crypto.domain.transport.FpgaTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * USB-UART transport for the Digilent Nexys A7's onboard FTDI FT2232HQ bridge
 * (115200 baud, 8N1).
 *
 * This is a compile-ready placeholder: it discovers the FTDI interface through
 * usb-serial-for-android but deliberately does not open or use it yet. Every
 * physical I/O point is marked `TODO(HARDWARE)`.
 */
class UsbSerialFpgaTransport(
    context: Context,
    private val baudRate: Int = DEFAULT_BAUD_RATE,
) : FpgaTransport {

    private val appContext: Context = context.applicationContext

    override val transportType: TransportType = TransportType.USB_SERIAL

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect() {
        _connectionState.value = ConnectionState.Connecting(transportType)

        // TODO(HARDWARE): Request UsbManager permission for the FTDI device and
        //  register a BroadcastReceiver for ACTION_USB_PERMISSION before
        //  opening the port.
        val driver = findFtdiDriver()
        if (driver == null) {
            _connectionState.value = ConnectionState.Failed(
                transportType,
                "No FTDI FT2232HQ detected. Connect the Nexys A7 over USB.",
            )
            return
        }

        // TODO(HARDWARE): Open the first interface of the FT2232HQ and
        //  configure it:
        //    val connection = usbManager.openDevice(driver.device)
        //    val port = driver.ports.first()
        //    port.open(connection)
        //    port.setParameters(
        //        baudRate,
        //        DATA_BITS,
        //        UsbSerialPort.STOPBITS_1,
        //        UsbSerialPort.PARITY_NONE,
        //    )
        //    port.dtr = true
        //    port.rts = true
        // TODO(HARDWARE): Start a reader thread that feeds bytes into
        //  NexysPacketFramer.decode(...) plus a rolling buffer for partial
        //  frames.
        // TODO(HARDWARE): Exchange the FPGA hello frame and only then publish
        //  ConnectionState.Connected.
        _connectionState.value = ConnectionState.Failed(
            transportType,
            "USB serial driver found, but hardware I/O is not implemented yet (TODO(HARDWARE)).",
        )
    }

    override suspend fun disconnect() {
        // TODO(HARDWARE): Stop the reader thread, close the UsbSerialPort and
        //  release the UsbDeviceConnection.
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun execute(
        mode: EncryptionMode,
        payload: ByteArray,
        onProgress: (TransferProgress) -> Unit,
    ): CryptoResult = CryptoResult.Failure(
        mode = mode,
        reason = "USB serial transport is not implemented yet (TODO(HARDWARE)).",
    )

    /**
     * Enumerates USB devices and returns the first FTDI FT2232HQ driver, if one
     * is attached. This is the only physical hook currently implemented; it
     * does not open the device.
     */
    private fun findFtdiDriver(): UsbSerialDriver? {
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return null
        return UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .firstOrNull {
                it.device.vendorId == FTDI_VENDOR_ID &&
                    it.device.productId == FT2232_PRODUCT_ID
            }
    }

    companion object {
        const val DEFAULT_BAUD_RATE = 115200
        const val FTDI_VENDOR_ID = 0x0403
        const val FT2232_PRODUCT_ID = 0x6010
        const val DATA_BITS = 8
        const val STOP_BITS = 1
    }
}
