package com.example.testingcast.network.wifip2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.model.DiscoveryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class WifiP2pHelper(private val context: Context) {

    companion object {
        private const val TAG = "WifiP2pHelper"
        private const val SENDER_GROUP_OWNER_INTENT = 0
        private const val GROUP_FORMATION_TIMEOUT_MS = 30_000L
    }

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private var isDiscovering = false

    fun initialize() {
        val mgr = wifiP2pManager ?: return
        if (channel == null) {
            channel = mgr.initialize(context.applicationContext, Looper.getMainLooper(), null)
            registerReceiver()
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        _isWifiP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                        Log.d(TAG, "Wi-Fi P2P State Changed: enabled=${_isWifiP2pEnabled.value}")
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        Log.d(TAG, "Wi-Fi P2P Peers Changed")
                        requestPeers()
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }

                        if (networkInfo?.isConnected == true) {
                            Log.d(TAG, "Wi-Fi P2P Connected. Requesting connection info...")
                            requestConnectionInfo()
                        } else {
                            Log.d(TAG, "Wi-Fi P2P Disconnected.")
                            _connectionInfo.value = null
                        }
                    }

                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        }
                        Log.d(TAG, "This Wi-Fi P2P device: ${device?.deviceName}")
                    }
                }
            }
        }

        try {
            context.registerReceiver(receiver, intentFilter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register WifiP2p receiver", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        initialize()
        val mgr = wifiP2pManager
        val ch = channel
        if (mgr == null || ch == null) {
            Log.e(TAG, "WifiP2pManager not available")
            return
        }

        try {
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "WifiP2p discoverPeers success")
                    isDiscovering = true
                }

                override fun onFailure(reasonCode: Int) {
                    Log.e(TAG, "WifiP2p discoverPeers failure reasonCode=$reasonCode")
                    isDiscovering = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering WifiP2p peers", e)
        }
    }

    fun stopDiscovery() {
        val mgr = wifiP2pManager
        val ch = channel
        if (mgr != null && ch != null && isDiscovering) {
            try {
                mgr.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "WifiP2p stopPeerDiscovery success")
                        isDiscovering = false
                    }

                    override fun onFailure(reasonCode: Int) {
                        Log.e(TAG, "WifiP2p stopPeerDiscovery failure reasonCode=$reasonCode")
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping WifiP2p discovery", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun requestPeers() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return
        try {
            mgr.requestPeers(ch) { peersList: WifiP2pDeviceList? ->
                val devices = peersList?.deviceList?.map { device ->
                    DiscoveredDevice(
                        id = device.deviceAddress,
                        name = device.deviceName.ifBlank { device.deviceAddress },
                        host = device.deviceAddress,
                        port = 8888,
                        type = DiscoveryType.WIFI_DIRECT,
                        rawDevice = device
                    )
                } ?: emptyList()

                _discoveredDevices.value = devices
                Log.d(TAG, "Discovered ${devices.size} Wi-Fi Direct devices")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting WifiP2p peers", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice, onResult: (Boolean) -> Unit) {
        val mgr = wifiP2pManager ?: run {
            onResult(false)
            return
        }
        val ch = channel ?: run {
            onResult(false)
            return
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            // Sender prefers to be a client so the receiver (socket server) becomes group owner.
            groupOwnerIntent = SENDER_GROUP_OWNER_INTENT
        }

        try {
            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "WifiP2p connect request succeeded")
                    onResult(true)
                }

                override fun onFailure(reasonCode: Int) {
                    Log.e(TAG, "WifiP2p connect failed: reasonCode=$reasonCode")
                    onResult(false)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WifiP2p device", e)
            onResult(false)
        }
    }

    /**
     * Connects to [device] and suspends until a P2P group is formed.
     * Returns the group info, or null on failure/timeout.
     */
    suspend fun connectAndAwaitGroup(device: WifiP2pDevice): WifiP2pInfo? {
        initialize()
        val requested = suspendCancellableCoroutine { cont ->
            connectToDevice(device) { ok -> if (cont.isActive) cont.resume(ok) }
        }
        if (!requested) return null
        return withTimeoutOrNull(GROUP_FORMATION_TIMEOUT_MS) {
            connectionInfo.filterNotNull().first { it.groupFormed && it.groupOwnerAddress != null }
        }
    }

    fun requestConnectionInfo() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return
        try {
            mgr.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
                _connectionInfo.value = info
                Log.d(TAG, "WifiP2p connection info: isGroupOwner=${info?.isGroupOwner}, groupOwnerAddress=${info?.groupOwnerAddress}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting WifiP2p connection info", e)
        }
    }

    fun disconnect() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return
        try {
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "WifiP2p removeGroup success")
                    _connectionInfo.value = null
                }

                override fun onFailure(reasonCode: Int) {
                    Log.e(TAG, "WifiP2p removeGroup failure reasonCode=$reasonCode")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WifiP2p", e)
        }
    }

    fun tearDown() {
        stopDiscovery()
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            receiver = null
        }
        channel = null
    }
}
