package com.example.testingcast.util

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddresses {
    private const val TAG = "NetworkAddresses"

    /** Site-local IPv4 addresses (Wi-Fi, hotspot, Wi-Fi Direct) a sender can type in manually. */
    fun localIpv4Addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
    } catch (e: Exception) {
        Log.w(TAG, "Unable to enumerate network interfaces", e)
        emptyList()
    }
}
