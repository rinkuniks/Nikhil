package com.example.testingcast.ui.sender

import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testingcast.R
import com.example.testingcast.appContainer
import com.example.testingcast.capture.StreamQuality
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.ui.common.AppPermissions
import com.example.testingcast.ui.common.RequestPermissionsOnce
import com.example.testingcast.ui.common.isExpandedWidth

private val PANE_MAX_WIDTH = 640.dp

/** Callbacks exposed by the sender screen (keeps the stateless composable signature small). */
data class SenderActions(
    val onScan: () -> Unit,
    val onStopScan: () -> Unit,
    val onConnect: (DiscoveredDevice) -> Unit,
    val onManualHostChange: (String) -> Unit,
    val onManualPortChange: (String) -> Unit,
    val onConnectManual: () -> Unit,
    val onDisconnect: () -> Unit,
    val onQualityChange: (StreamQuality) -> Unit,
    val onStartMirroring: () -> Unit,
    val onStopMirroring: () -> Unit
)

@Composable
fun SenderRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel: SenderViewModel = viewModel {
        SenderViewModel(container.networkRepository, container.captureStatus, container.captureController)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wifiDirectPermission = AppPermissions.wifiDirect.first()

    RequestPermissionsOnce(AppPermissions.sender) { results ->
        viewModel.startDiscovery(includeWifiDirect = results[wifiDirectPermission] == true)
    }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onProjectionResult(it.resultCode, it.data)
    }
    val projectionManager = remember(context) { context.getSystemService(MediaProjectionManager::class.java) }

    val actions = remember(viewModel) {
        SenderActions(
            onScan = { viewModel.startDiscovery(includeWifiDirect = hasPermission(context, wifiDirectPermission)) },
            onStopScan = viewModel::stopDiscovery,
            onConnect = viewModel::connect,
            onManualHostChange = viewModel::onManualHostChange,
            onManualPortChange = viewModel::onManualPortChange,
            onConnectManual = viewModel::connectManual,
            onDisconnect = viewModel::disconnect,
            onQualityChange = viewModel::updateQuality,
            onStartMirroring = {
                if (viewModel.canStartMirroring()) projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            },
            onStopMirroring = viewModel::stopMirroring
        )
    }

    SenderScreen(state = state, actions = actions, onBack = onBack, onMessageShown = viewModel::consumeMessage)
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(state: SenderUiState, actions: SenderActions, onBack: () -> Unit, onMessageShown: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.toStringRes()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sender_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isExpandedWidth()) {
            TwoPaneLayout(state, actions, padding)
        } else {
            SinglePaneLayout(state, actions, padding)
        }
    }
}

@Composable
private fun TwoPaneLayout(state: SenderUiState, actions: SenderActions, padding: PaddingValues) {
    Row(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        PaneColumn(Modifier.weight(1f)) { DevicesPane(state, actions) }
        PaneColumn(Modifier.weight(1f)) { ControlsPane(state, actions) }
    }
}

@Composable
private fun SinglePaneLayout(state: SenderUiState, actions: SenderActions, padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PaneColumn(Modifier.widthIn(max = PANE_MAX_WIDTH).padding(horizontal = 16.dp)) {
            StatusCard(state, actions)
            DevicesPane(state, actions)
            QualityCard(state.quality, state.isMirroring, actions.onQualityChange)
        }
    }
}

@Composable
private fun PaneColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) { content() }
}

@Composable
private fun DevicesPane(state: SenderUiState, actions: SenderActions) {
    DeviceListCard(state, actions)
    ManualConnectCard(state, actions)
}

@Composable
private fun ControlsPane(state: SenderUiState, actions: SenderActions) {
    StatusCard(state, actions)
    QualityCard(state.quality, state.isMirroring, actions.onQualityChange)
}

private fun SenderMessage.toStringRes(): Int = when (this) {
    SenderMessage.INVALID_HOST -> R.string.msg_invalid_host
    SenderMessage.INVALID_PORT -> R.string.msg_invalid_port
    SenderMessage.CONNECTION_FAILED -> R.string.msg_connection_failed
    SenderMessage.NOT_CONNECTED -> R.string.msg_not_connected
    SenderMessage.CAPTURE_DENIED -> R.string.msg_capture_denied
}
