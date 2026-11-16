package com.example.testingcast.ui.sender

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.testingcast.R
import com.example.testingcast.capture.CaptureStatus
import com.example.testingcast.capture.ResolutionOption
import com.example.testingcast.capture.StreamQuality
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.model.DiscoveryType
import com.example.testingcast.ui.common.connectionLabel

private const val KBPS_PER_MBPS = 1000f

@Composable
private fun SectionCard(title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun StatusCard(state: SenderUiState, actions: SenderActions) {
    SectionCard(title = connectionLabel(state.connectionState)) {
        val capture = state.captureStatus
        if (capture is CaptureStatus.Active) {
            val audio = stringResource(if (capture.audioEnabled) R.string.audio_on else R.string.audio_off)
            Text(stringResource(R.string.sender_mirroring_info, capture.width, capture.height, audio))
            Spacer(Modifier.height(12.dp))
        }
        if (capture is CaptureStatus.Failed) {
            Text(capture.message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.isMirroring -> Button(onClick = actions.onStopMirroring) { Text(stringResource(R.string.sender_stop)) }
                else -> Button(onClick = actions.onStartMirroring, enabled = state.isConnected) {
                    Text(stringResource(R.string.sender_start))
                }
            }
            if (state.isConnected || state.connectionState is ConnectionState.Connecting) {
                OutlinedButton(onClick = actions.onDisconnect) { Text(stringResource(R.string.sender_disconnect)) }
            }
        }
    }
}

@Composable
fun DeviceListCard(state: SenderUiState, actions: SenderActions) {
    SectionCard(
        title = stringResource(R.string.sender_devices),
        trailing = {
            if (state.isDiscovering) {
                TextButton(onClick = actions.onStopScan) { Text(stringResource(R.string.sender_stop_scan)) }
            } else {
                TextButton(onClick = actions.onScan) { Text(stringResource(R.string.sender_scan)) }
            }
        }
    ) {
        if (state.isDiscovering) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sender_scanning), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.devices.isEmpty()) {
            Text(
                stringResource(R.string.sender_no_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val busy = state.isConnected || state.connectionState is ConnectionState.Connecting
        state.devices.forEachIndexed { index, device ->
            if (index > 0) HorizontalDivider()
            DeviceRow(device, enabled = !busy, onClick = { actions.onConnect(device) })
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, enabled: Boolean, onClick: () -> Unit) {
    val isP2p = device.type == DiscoveryType.WIFI_DIRECT
    ListItem(
        headlineContent = { Text(device.name) },
        supportingContent = {
            val type = stringResource(if (isP2p) R.string.sender_type_p2p else R.string.sender_type_wifi)
            Text(if (isP2p) type else "$type · ${device.host}:${device.port}")
        },
        leadingContent = { Icon(if (isP2p) Icons.Filled.WifiTethering else Icons.Filled.Tv, contentDescription = null) },
        trailingContent = {
            FilledTonalButton(onClick = onClick, enabled = enabled) { Text(stringResource(R.string.sender_connect)) }
        }
    )
}

@Composable
fun ManualConnectCard(state: SenderUiState, actions: SenderActions) {
    SectionCard(title = stringResource(R.string.sender_manual)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.manualHost,
                onValueChange = actions.onManualHostChange,
                label = { Text(stringResource(R.string.sender_manual_host)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.weight(2f)
            )
            OutlinedTextField(
                value = state.manualPort,
                onValueChange = actions.onManualPortChange,
                label = { Text(stringResource(R.string.sender_manual_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = actions.onConnectManual, enabled = !state.isConnected) {
            Text(stringResource(R.string.sender_connect))
        }
    }
}

@Composable
fun QualityCard(quality: StreamQuality, isMirroring: Boolean, onChange: (StreamQuality) -> Unit) {
    SectionCard(title = stringResource(R.string.sender_quality)) {
        if (isMirroring) {
            Text(
                stringResource(R.string.sender_quality_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        OptionRow(
            label = stringResource(R.string.sender_resolution),
            options = ResolutionOption.entries,
            selected = quality.resolution,
            optionLabel = { it.label },
            onSelect = { onChange(quality.copy(resolution = it)) }
        )
        OptionRow(
            label = stringResource(R.string.sender_bitrate),
            options = StreamQuality.BITRATE_OPTIONS_KBPS,
            selected = quality.bitrateKbps,
            optionLabel = { stringResource(R.string.sender_bitrate_value, it / KBPS_PER_MBPS) },
            onSelect = { onChange(quality.copy(bitrateKbps = it)) }
        )
        OptionRow(
            label = stringResource(R.string.sender_fps),
            options = StreamQuality.FPS_OPTIONS,
            selected = quality.fps,
            optionLabel = { stringResource(R.string.sender_fps_value, it) },
            onSelect = { onChange(quality.copy(fps = it)) }
        )
    }
}

@Composable
private fun <T> OptionRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(optionLabel(option)) }
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}
