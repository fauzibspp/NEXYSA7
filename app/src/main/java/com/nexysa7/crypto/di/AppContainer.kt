package com.nexysa7.crypto.di

import android.app.Application
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexysa7.crypto.data.transport.SimulatedTransportHub
import com.nexysa7.crypto.domain.transport.FpgaTransport
import com.nexysa7.crypto.domain.transport.SimulatedTransportControl
import com.nexysa7.crypto.ui.file.FileCryptoViewModel
import com.nexysa7.crypto.ui.text.TextCryptoViewModel
import com.nexysa7.crypto.ui.viewmodel.ConnectionViewModel

/**
 * Manual dependency container (AGENTS.md allows either Hilt or a clean manual
 * container). It keeps construction in one place and gives ViewModels a
 * single factory each.
 */
class AppContainer(application: Application) {

    private val app: Application = application

    /**
     * The simulated hub owns both the USB and BLE profiles. Text/File
     * ViewModels use the active profile through [FpgaTransport], while the
     * connection UI uses [SimulatedTransportControl] to select a profile.
     *
     * To move to hardware later, bind a real FpgaTransport here and replace the
     * simulation selector with the hardware connection flow. Nothing else in
     * the ViewModels or Compose UI depends on the simulated implementation.
     */
    private val simulatedTransportHub = SimulatedTransportHub()

    val transport: FpgaTransport = simulatedTransportHub

    val simulatedTransportControl: SimulatedTransportControl = simulatedTransportHub

    val connectionViewModelFactory = viewModelFactory {
        initializer { ConnectionViewModel(transport, simulatedTransportControl) }
    }

    val textCryptoViewModelFactory = viewModelFactory {
        initializer { TextCryptoViewModel(transport) }
    }

    val fileCryptoViewModelFactory = viewModelFactory {
        initializer { FileCryptoViewModel(transport, app.contentResolver) }
    }
}
