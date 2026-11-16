package com.example.testingcast.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.testingcast.R
import com.example.testingcast.network.model.ConnectionState

@Composable
fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> stringResource(R.string.state_disconnected)
    ConnectionState.Searching -> stringResource(R.string.state_searching)
    is ConnectionState.Connecting -> stringResource(R.string.state_connecting, state.device.name)
    is ConnectionState.Connected -> stringResource(R.string.state_connected, state.device.name)
    is ConnectionState.Streaming -> stringResource(R.string.state_streaming, state.device.name)
    is ConnectionState.Error -> stringResource(R.string.state_error, state.message)
}
