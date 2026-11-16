package com.example.testingcast.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.testingcast.R
import com.example.testingcast.ui.common.isMediumWidth
import com.example.testingcast.ui.common.isTelevision

private val CONTENT_MAX_WIDTH = 960.dp

@Composable
fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    val isTv = isTelevision()
    val wide = isMediumWidth()
    val receiveFocus = remember { FocusRequester() }
    val sendFocus = remember { FocusRequester() }

    // D-pad users need an initially focused element; TVs default to receiving.
    LaunchedEffect(isTv) { runCatching { (if (isTv) receiveFocus else sendFocus).requestFocus() } }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
                val send: @Composable (Modifier) -> Unit = { modifier ->
                    ModeCard(Icons.Filled.CastConnected, R.string.home_send_title, R.string.home_send_body, onSend,
                        modifier.focusRequester(sendFocus))
                }
                val receive: @Composable (Modifier) -> Unit = { modifier ->
                    ModeCard(Icons.Filled.Tv, R.string.home_receive_title, R.string.home_receive_body, onReceive,
                        modifier.focusRequester(receiveFocus))
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (isTv) receive(Modifier.weight(1f)) else send(Modifier.weight(1f))
                        if (isTv) send(Modifier.weight(1f)) else receive(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        send(Modifier.fillMaxWidth())
                        receive(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, titleRes: Int, bodyRes: Int, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(24.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
