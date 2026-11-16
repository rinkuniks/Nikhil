package com.example.testingcast.network.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Searching : ConnectionState()
    data class Connecting(val device: DiscoveredDevice) : ConnectionState()
    data class Connected(val device: DiscoveredDevice) : ConnectionState()
    data class Streaming(val device: DiscoveredDevice) : ConnectionState()
    data class Error(val message: String, val cause: Throwable? = null) : ConnectionState()
}
