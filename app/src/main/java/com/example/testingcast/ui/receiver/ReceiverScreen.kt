package com.example.testingcast.ui.receiver

import android.os.Build
import android.view.KeyEvent as AndroidKeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testingcast.R
import com.example.testingcast.appContainer
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.ui.common.AppPermissions
import com.example.testingcast.ui.common.RequestPermissionsOnce
import com.example.testingcast.ui.common.connectionLabel
import com.example.testingcast.util.NetworkAddresses
import kotlinx.coroutines.delay

private const val OVERLAY_AUTO_HIDE_MS = 4_000L
private val SELECT_KEYS = setOf(
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
    AndroidKeyEvent.KEYCODE_BUTTON_A
)

@Composable
fun ReceiverRoute() {
    val container = LocalContext.current.appContainer
    val viewModel: ReceiverViewModel = viewModel {
        ReceiverViewModel(
            repository = container.networkRepository,
            deviceName = Build.MODEL ?: "Android",
            addressProvider = NetworkAddresses::localIpv4Addresses
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RequestPermissionsOnce(AppPermissions.receiver) { results ->
        if (results.values.all { it }) viewModel.enableWifiDirect()
    }
    KeepScreenOnImmersive()

    ReceiverScreen(
        state = state,
        onSurfaceAvailable = viewModel::onSurfaceAvailable,
        onSurfaceDestroyed = viewModel::onSurfaceDestroyed
    )
}

@Composable
private fun KeepScreenOnImmersive() {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(view, window) {
        view.keepScreenOn = true
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun ReceiverScreen(
    state: ReceiverUiState,
    onSurfaceAvailable: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    var overlayVisible by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(overlayVisible, state.isPlaying) {
        if (overlayVisible && state.isPlaying) {
            delay(OVERLAY_AUTO_HIDE_MS)
            overlayVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                val isSelect = event.type == KeyEventType.KeyUp && event.key.nativeKeyCode in SELECT_KEYS
                if (isSelect) overlayVisible = !overlayVisible
                isSelect
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        val aspect = state.videoFormat?.let { it.width.toFloat() / it.height }
        VideoSurface(
            modifier = if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize(),
            onSurfaceAvailable = onSurfaceAvailable,
            onSurfaceDestroyed = onSurfaceDestroyed
        )
        when {
            !state.isPlaying -> WaitingPanel(state)
            overlayVisible -> StreamInfoOverlay(state, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun VideoSurface(
    modifier: Modifier,
    onSurfaceAvailable: (android.view.Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    val currentOnAvailable by rememberUpdatedState(onSurfaceAvailable)
    val currentOnDestroyed by rememberUpdatedState(onSurfaceDestroyed)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = currentOnAvailable(holder.surface)
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) = currentOnDestroyed()
                })
            }
        }
    )
}

@Composable
private fun WaitingPanel(state: ReceiverUiState) {
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .background(Color(0xCC1B1F3B), RoundedCornerShape(24.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
        val connecting = state.connectionState is ConnectionState.Connected ||
            state.connectionState is ConnectionState.Streaming
        val title = if (connecting) {
            stringResource(R.string.receiver_buffering, senderName(state.connectionState))
        } else {
            stringResource(R.string.receiver_waiting_title)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
        if (connecting) CircularProgressIndicator(color = Color.White)
        Text(stringResource(R.string.receiver_name, state.deviceName), color = Color.White)
        if (state.addresses.isEmpty()) {
            Text(stringResource(R.string.receiver_no_address), color = Color(0xFFFFB4AB), textAlign = TextAlign.Center)
        } else {
            state.addresses.forEach { Text(stringResource(R.string.receiver_address, it, state.port), color = Color.White) }
        }
        Spacer(Modifier.height(4.dp))
        Text(connectionLabel(state.connectionState), color = Color(0xFFB0B8E0))
        Text(stringResource(R.string.receiver_hint), color = Color(0xFFB0B8E0), textAlign = TextAlign.Center)
    }
}

@Composable
private fun StreamInfoOverlay(state: ReceiverUiState, modifier: Modifier) {
    val format = state.videoFormat ?: return
    val audio = stringResource(if (state.isAudioActive) R.string.audio_on else R.string.audio_off)
    Column(
        modifier = modifier
            .padding(24.dp)
            .background(Color(0x99000000), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(connectionLabel(state.connectionState), color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.receiver_stream_info, format.width, format.height, audio), color = Color.White)
        Text(stringResource(R.string.receiver_overlay_hint), color = Color(0xFFB0B8E0), style = MaterialTheme.typography.bodySmall)
    }
}

private fun senderName(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> state.device.name
    is ConnectionState.Streaming -> state.device.name
    else -> ""
}
