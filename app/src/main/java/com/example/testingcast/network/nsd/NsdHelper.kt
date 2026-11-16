package com.example.testingcast.network.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.model.DiscoveryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NsdHelper(context: Context) {

    companion object {
        private const val TAG = "NsdHelper"
        const val SERVICE_TYPE = "_testingcast._tcp"
        const val DEFAULT_SERVICE_NAME = "TestingCast-Receiver"
    }

    private val nsdManager: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isAdvertising = false
    private var isDiscovering = false

    fun startAdvertising(serviceName: String = DEFAULT_SERVICE_NAME, port: Int = 8888) {
        if (isAdvertising) stopAdvertising()

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("device_name", Build.MODEL ?: "Android Device")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service registered: ${serviceInfo.serviceName}")
                isAdvertising = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Service registration failed: errorCode=$errorCode")
                isAdvertising = false
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service unregistered: ${serviceInfo.serviceName}")
                isAdvertising = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Service unregistration failed: errorCode=$errorCode")
            }
        }

        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD advertising", e)
        }
    }

    fun stopAdvertising() {
        if (!isAdvertising && registrationListener == null) return
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping NSD advertising", e)
        } finally {
            registrationListener = null
            isAdvertising = false
        }
    }

    fun startDiscovery() {
        if (isDiscovering) stopDiscovery()

        _discoveredDevices.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD Discovery started: $regType")
                isDiscovering = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service found: ${serviceInfo.serviceName}, type: ${serviceInfo.serviceType}")
                if (serviceInfo.serviceType.contains("_testingcast._tcp")) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service lost: ${serviceInfo.serviceName}")
                _discoveredDevices.update { list ->
                    list.filterNot { it.name == serviceInfo.serviceName || it.id == serviceInfo.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD Discovery stopped: $serviceType")
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Discovery start failed: errorCode=$errorCode")
                isDiscovering = false
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (_: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "NSD Discovery stop failed: errorCode=$errorCode")
            }
        }

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD discovery", e)
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering && discoveryListener == null) return
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping NSD discovery", e)
        } finally {
            discoveryListener = null
            isDiscovering = false
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Resolve failed for ${serviceInfo.serviceName}: errorCode=$errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD Service resolved: ${resolvedInfo.serviceName} at ${resolvedInfo.host}:${resolvedInfo.port}")
                val hostAddress = resolvedInfo.host?.hostAddress ?: return
                val deviceNameAttr = try {
                    resolvedInfo.attributes?.get("device_name")?.let { String(it, Charsets.UTF_8) }
                } catch (_: Exception) { null }

                val device = DiscoveredDevice(
                    id = resolvedInfo.serviceName,
                    name = deviceNameAttr ?: resolvedInfo.serviceName,
                    host = hostAddress,
                    port = resolvedInfo.port,
                    type = DiscoveryType.NSD,
                    rawDevice = resolvedInfo
                )

                _discoveredDevices.update { current ->
                    val index = current.indexOfFirst { it.id == device.id }
                    if (index >= 0) {
                        current.toMutableList().apply { set(index, device) }
                    } else {
                        current + device
                    }
                }
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving NSD service", e)
        }
    }
}
