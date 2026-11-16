package com.example.testingcast.network

import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.model.DiscoveryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConnectionStateTest {

    @Test
    fun testConnectionStateEquality() {
        val device1 = DiscoveredDevice("id1", "Device1", "192.168.1.10", 8888, DiscoveryType.NSD)
        val device2 = DiscoveredDevice("id1", "Device1", "192.168.1.10", 8888, DiscoveryType.NSD)
        val device3 = DiscoveredDevice("id2", "Device2", "192.168.1.11", 8888, DiscoveryType.WIFI_DIRECT)

        assertEquals(ConnectionState.Disconnected, ConnectionState.Disconnected)
        assertEquals(ConnectionState.Searching, ConnectionState.Searching)
        assertEquals(ConnectionState.Connected(device1), ConnectionState.Connected(device2))
        assertNotEquals(ConnectionState.Connected(device1), ConnectionState.Connected(device3))
    }
}
