package com.nexysa7.crypto.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexysa7.crypto.di.AppContainer
import com.nexysa7.crypto.domain.model.ConnectionState
import com.nexysa7.crypto.ui.components.ChipStatusBadge
import com.nexysa7.crypto.ui.components.ConnectionControls
import com.nexysa7.crypto.ui.file.FileCryptoScreen
import com.nexysa7.crypto.ui.file.FileCryptoViewModel
import com.nexysa7.crypto.ui.text.TextCryptoScreen
import com.nexysa7.crypto.ui.text.TextCryptoViewModel
import com.nexysa7.crypto.ui.viewmodel.ConnectionViewModel

private val TABS = listOf("Text Encryption", "File Encryption")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexysA7App(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val connectionViewModel: ConnectionViewModel =
        viewModel(factory = container.connectionViewModelFactory)
    val textViewModel: TextCryptoViewModel =
        viewModel(factory = container.textCryptoViewModelFactory)
    val fileViewModel: FileCryptoViewModel =
        viewModel(factory = container.fileCryptoViewModelFactory)

    val connectionState by connectionViewModel.connectionState.collectAsStateWithLifecycle()
    val selectedLink by connectionViewModel.selectedLink.collectAsStateWithLifecycle()
    val textState by textViewModel.uiState.collectAsStateWithLifecycle()
    val fileState by fileViewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val isConnected = connectionState is ConnectionState.Connected

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Nexys A7 Crypto",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        ChipStatusBadge(
                            state = connectionState,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    },
                )
                ConnectionControls(
                    connectionState = connectionState,
                    selectedLink = selectedLink,
                    onSelectLink = connectionViewModel::selectLink,
                    onToggleConnection = connectionViewModel::toggleConnection,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            // Keeps each tab's rememberSaveable UI state (scroll position etc.)
            // while the tab is out of composition. ViewModel state survives
            // independently.
            saveableStateHolder.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    0 -> TextCryptoScreen(
                        state = textState,
                        isConnected = isConnected,
                        onEditorChange = textViewModel::onEditorChange,
                        onFormatChange = textViewModel::onFormatChange,
                        onEncrypt = textViewModel::encrypt,
                        onDecrypt = textViewModel::decrypt,
                        onSwap = textViewModel::swapWithPrevious,
                        onClear = textViewModel::clear,
                    )

                    else -> FileCryptoScreen(
                        state = fileState,
                        isConnected = isConnected,
                        activeLink = selectedLink,
                        onFilePicked = fileViewModel::onFilePicked,
                        onEncrypt = fileViewModel::encrypt,
                        onDecrypt = fileViewModel::decrypt,
                        onSaveResult = fileViewModel::saveResult,
                        onClear = fileViewModel::clear,
                    )
                }
            }
        }
    }
}
