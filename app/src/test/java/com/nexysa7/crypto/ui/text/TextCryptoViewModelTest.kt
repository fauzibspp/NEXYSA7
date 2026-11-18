package com.nexysa7.crypto.ui.text

import com.nexysa7.crypto.data.format.HexCodec
import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.domain.model.CryptoResult
import com.nexysa7.crypto.domain.model.EncryptionMode
import com.nexysa7.crypto.domain.model.TransferProgress
import com.nexysa7.crypto.domain.model.TransportType
import com.nexysa7.crypto.domain.transport.FpgaTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextCryptoViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `encrypt writes hex ciphertext into the single editor`() = runTest {
        val viewModel = TextCryptoViewModel(FakeTransport())
        viewModel.onEditorChange("Hello")

        viewModel.encrypt()

        val state = viewModel.uiState.value
        assertEquals(TextFormat.HEX, state.format)
        assertEquals(HexCodec.encode(xorBytes("Hello".toByteArray())), state.editor)
        assertEquals(EncryptionMode.ENCRYPT, state.lastOperation)
        assertEquals(25L, state.latencyMillis ?: -1L)
        assertEquals(TransportType.SIMULATED_USB, state.lastTransport)
        assertTrue((state.throughputBytesPerSecond ?: 0.0) > 0.0)
    }

    @Test
    fun `decrypt restores utf8 and switches format back`() = runTest {
        val viewModel = TextCryptoViewModel(FakeTransport())
        viewModel.onFormatChange(TextFormat.HEX)
        viewModel.onEditorChange(HexCodec.encode(xorBytes("Hello".toByteArray())))

        viewModel.decrypt()

        val state = viewModel.uiState.value
        assertEquals(TextFormat.UTF8, state.format)
        assertEquals("Hello", state.editor)
        assertEquals(EncryptionMode.DECRYPT, state.lastOperation)
    }

    @Test
    fun `swap restores the previous content and format`() = runTest {
        val viewModel = TextCryptoViewModel(FakeTransport())
        viewModel.onEditorChange("Hello")
        viewModel.encrypt()

        viewModel.swapWithPrevious()

        val state = viewModel.uiState.value
        assertEquals(TextFormat.UTF8, state.format)
        assertEquals("Hello", state.editor)
    }

    @Test
    fun `invalid hex reports an error and keeps the editor`() = runTest {
        val viewModel = TextCryptoViewModel(FakeTransport())
        viewModel.onFormatChange(TextFormat.HEX)
        viewModel.onEditorChange("not hex")

        viewModel.encrypt()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertEquals("not hex", state.editor)
        assertEquals(false, state.isBusy)
    }

    private class FakeTransport : FpgaTransport {
        override val transportType: TransportType = TransportType.SIMULATED_USB
        private val _state = MutableStateFlow<ConnectionState>(
            ConnectionState.Connected(TransportType.SIMULATED_USB),
        )
        override val connectionState: StateFlow<ConnectionState> = _state

        override suspend fun connect() = Unit

        override suspend fun disconnect() = Unit

        override suspend fun execute(
            mode: EncryptionMode,
            payload: ByteArray,
            onProgress: (TransferProgress) -> Unit,
        ): CryptoResult {
            onProgress(TransferProgress(payload.size.toLong(), payload.size.toLong()))
            return CryptoResult.Success(
                mode = mode,
                transport = TransportType.SIMULATED_USB,
                inputSize = payload.size,
                output = xorBytes(payload),
                elapsedMillis = 25,
            )
        }
    }
}

private fun xorBytes(bytes: ByteArray): ByteArray =
    ByteArray(bytes.size) { index -> (bytes[index].toInt() xor 0x5A).toByte() }
