package com.example.testingcast.network.repository

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.util.Log
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.model.DiscoveryType
import com.example.testingcast.network.nsd.NsdHelper
import com.example.testingcast.network.protocol.StreamPacket
import com.example.testingcast.network.socket.StreamClient
import com.example.testingcast.network.socket.StreamServer
import com.example.testingcast.network.wifip2p.WifiP2pHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

private const val TAG = "NetworkRepository"

class NetworkRepositoryImpl(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : NetworkRepository {

    private val nsdHelper = NsdHelper(context)
    private val wifiP2pHelper = WifiP2pHelper(context)
    private val streamServer = StreamServer(scope)
    private val streamClient = StreamClient(scope)

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override val incomingPackets: Flow<StreamPacket> = merge(
        streamServer.incomingPackets,
        streamClient.incomingPackets
    )

    init {
        scope.launch {
            combine(
                nsdHelper.discoveredDevices,
                wifiP2pHelper.discoveredDevices
            ) { nsdList, p2pList ->
                (nsdList + p2pList).distinctBy { it.id }
            }.collect { combined ->
                _discoveredDevices.value = combined
            }
        }

        scope.launch {
            combine(
                streamServer.connectionState,
                streamClient.connectionState
            ) { serverState, clientState ->
                when {
                    clientState !is ConnectionState.Disconnected -> clientState
                    serverState !is ConnectionState.Disconnected -> serverState
                    else -> ConnectionState.Disconnected
                }
            }.collect { state ->
                _connectionState.value = state
            }
        }
    }

    override fun startAdvertising(deviceName: String, port: Int) {
        nsdHelper.startAdvertising(deviceName, port)
    }

    override fun stopAdvertising() {
        nsdHelper.stopAdvertising()
    }

    override fun startDiscovery() {
        nsdHelper.startDiscovery()
    }

    override fun stopDiscovery() {
        nsdHelper.stopDiscovery()
    }

    override fun startWifiP2pDiscovery() {
        wifiP2pHelper.startDiscovery()
    }

    override fun stopWifiP2pDiscovery() {
        wifiP2pHelper.stopDiscovery()
    }

    override suspend fun connectToDevice(device: DiscoveredDevice): Boolean {
        val target = when (device.type) {
            DiscoveryType.NSD -> device
            DiscoveryType.WIFI_DIRECT -> resolveWifiDirectTarget(device) ?: return false
        }
        return streamClient.connect(target)
    }

    /**
     * Forms a Wi-Fi Direct group with the receiver and returns a socket-reachable
     * target (the group owner address). Peer MAC addresses are not routable.
     */
    private suspend fun resolveWifiDirectTarget(device: DiscoveredDevice): DiscoveredDevice? {
        val p2pDevice = device.rawDevice as? WifiP2pDevice ?: run {
            Log.e(TAG, "Wi-Fi Direct device missing raw WifiP2pDevice: ${device.id}")
            return null
        }
        val info = wifiP2pHelper.connectAndAwaitGroup(p2pDevice) ?: run {
            Log.e(TAG, "Wi-Fi Direct group formation failed for ${device.name}")
            return null
        }
        if (info.isGroupOwner) {
            Log.e(TAG, "This sender became group owner; receiver address unknown")
            wifiP2pHelper.disconnect()
            return null
        }
        val host = info.groupOwnerAddress?.hostAddress ?: return null
        return device.copy(host = host, port = StreamServer.DEFAULT_PORT)
    }

    override fun disconnect() {
        streamClient.disconnect()
        streamServer.stop()
        wifiP2pHelper.disconnect()
    }

    override fun startServer(port: Int) {
        streamServer.start(port)
    }

    override fun stopServer() {
        streamServer.stop()
    }

    override suspend fun startStreaming(): Boolean {
        return streamClient.startStreaming()
    }

    override suspend fun stopStreaming(): Boolean {
        return streamClient.stopStreaming()
    }

    override fun release() {
        nsdHelper.stopDiscovery()
        nsdHelper.stopAdvertising()
        streamClient.disconnect()
        streamServer.stop()
        wifiP2pHelper.tearDown()
    }

    override suspend fun sendPacket(packet: StreamPacket): Boolean {
        return if (streamClient.connectionState.value !is ConnectionState.Disconnected) {
            streamClient.sendPacket(packet)
        } else {
            streamServer.sendPacket(packet)
        }
    }
}
