package com.example.testingcast.ui.sender

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testingcast.capture.CaptureServiceController
import com.example.testingcast.capture.CaptureStatus
import com.example.testingcast.capture.StreamQuality
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.model.DiscoveryType
import com.example.testingcast.network.repository.NetworkRepository
import com.example.testingcast.network.socket.StreamServer
import com.example.testingcast.util.HostValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SenderMessage { INVALID_HOST, INVALID_PORT, CONNECTION_FAILED, NOT_CONNECTED, CAPTURE_DENIED }

data class SenderUiState(
    val devices: List<DiscoveredDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val captureStatus: CaptureStatus = CaptureStatus.Idle,
    val quality: StreamQuality = StreamQuality(),
    val isDiscovering: Boolean = false,
    val manualHost: String = "",
    val manualPort: String = StreamServer.DEFAULT_PORT.toString(),
    val message: SenderMessage? = null
) {
    val isConnected: Boolean
        get() = connectionState is ConnectionState.Connected || connectionState is ConnectionState.Streaming

    val isMirroring: Boolean
        get() = captureStatus is CaptureStatus.Active || captureStatus is CaptureStatus.Starting
}

private data class SenderLocalState(
    val quality: StreamQuality = StreamQuality(),
    val isDiscovering: Boolean = false,
    val manualHost: String = "",
    val manualPort: String = StreamServer.DEFAULT_PORT.toString(),
    val message: SenderMessage? = null
)

class SenderViewModel(
    private val repository: NetworkRepository,
    captureStatus: StateFlow<CaptureStatus>,
    private val captureController: CaptureServiceController
) : ViewModel() {

    private val local = MutableStateFlow(SenderLocalState())

    val uiState: StateFlow<SenderUiState> = combine(
        repository.discoveredDevices,
        repository.connectionState,
        captureStatus,
        local
    ) { devices, connection, capture, localState ->
        SenderUiState(
            devices = devices,
            connectionState = connection,
            captureStatus = capture,
            quality = localState.quality,
            isDiscovering = localState.isDiscovering,
            manualHost = localState.manualHost,
            manualPort = localState.manualPort,
            message = localState.message
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SenderUiState())

    fun startDiscovery(includeWifiDirect: Boolean) {
        repository.startDiscovery()
        if (includeWifiDirect) repository.startWifiP2pDiscovery()
        local.update { it.copy(isDiscovering = true) }
    }

    fun stopDiscovery() {
        repository.stopDiscovery()
        repository.stopWifiP2pDiscovery()
        local.update { it.copy(isDiscovering = false) }
    }

    fun connect(device: DiscoveredDevice) {
        viewModelScope.launch {
            val connected = repository.connectToDevice(device)
            if (!connected) showMessage(SenderMessage.CONNECTION_FAILED)
        }
    }

    fun onManualHostChange(value: String) = local.update { it.copy(manualHost = value) }

    fun onManualPortChange(value: String) = local.update { it.copy(manualPort = value) }

    fun connectManual() {
        val state = local.value
        val host = state.manualHost.trim()
        if (!HostValidator.isValidHost(host)) return showMessage(SenderMessage.INVALID_HOST)
        val port = HostValidator.parsePort(state.manualPort) ?: return showMessage(SenderMessage.INVALID_PORT)
        connect(DiscoveredDevice(id = "manual:$host:$port", name = host, host = host, port = port, type = DiscoveryType.NSD))
    }

    fun disconnect() {
        if (uiState.value.isMirroring) captureController.stop()
        repository.disconnect()
    }

    fun updateQuality(quality: StreamQuality) = local.update { it.copy(quality = quality) }

    /** Returns true when the UI may launch the screen-capture consent dialog. */
    fun canStartMirroring(): Boolean {
        val connected = uiState.value.isConnected
        if (!connected) showMessage(SenderMessage.NOT_CONNECTED)
        return connected
    }

    fun onProjectionResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) return showMessage(SenderMessage.CAPTURE_DENIED)
        captureController.start(resultCode, data, local.value.quality)
    }

    fun stopMirroring() = captureController.stop()

    fun consumeMessage() = local.update { it.copy(message = null) }

    private fun showMessage(message: SenderMessage) = local.update { it.copy(message = message) }

    override fun onCleared() {
        repository.stopDiscovery()
        repository.stopWifiP2pDiscovery()
    }
}
