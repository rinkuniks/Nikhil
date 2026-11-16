package com.example.testingcast.ui.common

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

object AppPermissions {
    /** Wi-Fi Direct peer discovery: NEARBY_WIFI_DEVICES on 33+, fine location before. */
    val wifiDirect: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val sender: List<String> = buildList {
        addAll(wifiDirect)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val receiver: List<String> = wifiDirect
}

/** Requests [permissions] once per screen instance (survives rotation) and reports the result. */
@Composable
fun RequestPermissionsOnce(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
    var requested by rememberSaveable { mutableStateOf(false) }
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentOnResult(it)
    }
    LaunchedEffect(Unit) {
        if (!requested) {
            requested = true
            launcher.launch(permissions.toTypedArray())
        }
    }
}
