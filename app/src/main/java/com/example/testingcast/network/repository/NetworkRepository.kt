package com.example.testingcast.network.repository

import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.protocol.StreamPacket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface NetworkRepository {
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>
    val connectionState: StateFlow<ConnectionState>
    val incomingPackets: Flow<StreamPacket>

    fun startAdvertising(deviceName: String = "TestingCast-Receiver", port: Int = 8888)
    fun stopAdvertising()

    fun startDiscovery()
    fun stopDiscovery()

    fun startWifiP2pDiscovery()
    fun stopWifiP2pDiscovery()

    suspend fun connectToDevice(device: DiscoveredDevice): Boolean
    fun disconnect()

    fun startServer(port: Int = 8888)
    fun stopServer()

    suspend fun startStreaming(): Boolean
    suspend fun stopStreaming(): Boolean
    suspend fun sendPacket(packet: StreamPacket): Boolean

    /** Stops discovery, advertising, sockets and Wi-Fi Direct groups. */
    fun release()
}
