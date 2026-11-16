package com.example.testingcast.network.model

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 8888,
    val type: DiscoveryType = DiscoveryType.NSD,
    val rawDevice: Any? = null
)
