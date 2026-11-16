package com.example.testingcast.network

import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.model.DiscoveryType
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket
import com.example.testingcast.network.socket.StreamClient
import com.example.testingcast.network.socket.StreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/**
 * Real-socket loopback tests. Server/client loops run forever, so they live in their own
 * IO scope (cancelled in [tearDown]) rather than in the test body's scope.
 */
class StreamSocketIntegrationTest {

    private companion object {
        const val TEST_TIMEOUT_MS = 10_000L
    }

    private lateinit var networkScope: CoroutineScope
    private lateinit var server: StreamServer
    private lateinit var client: StreamClient
    private lateinit var targetDevice: DiscoveredDevice

    @Before
    fun setUp() {
        networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server = StreamServer(networkScope)
        client = StreamClient(networkScope)

        val port = ServerSocket(0).use { it.localPort }
        targetDevice = DiscoveredDevice(
            id = "localhost",
            name = "TestReceiver",
            host = "127.0.0.1",
            port = port,
            type = DiscoveryType.NSD
        )
    }

    @After
    fun tearDown() {
        client.disconnect()
        server.stop()
        networkScope.cancel()
    }

    private fun runSocketTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) { block() }
    }

    private suspend fun startServerAndConnect() {
        server.start(targetDevice.port)
        server.connectionState.first { it is ConnectionState.Searching }
        assertTrue("Client should connect successfully", client.connect(targetDevice))
    }

    @Test
    fun testServerAndClientConnectionAndTransmission() = runSocketTest {
        startServerAndConnect()
        // Subscribe before sending: incomingPackets has no replay.
        val received = async(start = CoroutineStart.UNDISPATCHED) { server.incomingPackets.first() }

        val packet = StreamPacket(type = PacketType.VIDEO, sequenceNumber = 1L, payload = "SampleFrameData".toByteArray())
        assertTrue("Packet should be sent successfully", client.sendPacket(packet))

        val receivedPacket = received.await()
        assertEquals(PacketType.VIDEO, receivedPacket.type)
        assertEquals(1L, receivedPacket.sequenceNumber)
        assertEquals("SampleFrameData", String(receivedPacket.payload))
    }

    @Test
    fun testStartAndStopStreamingStateTransitions() = runSocketTest {
        startServerAndConnect()

        assertTrue(client.startStreaming())
        assertEquals(ConnectionState.Streaming(targetDevice), client.connectionState.value)
        server.connectionState.first { it is ConnectionState.Streaming }

        assertTrue(client.stopStreaming())
        assertEquals(ConnectionState.Connected(targetDevice), client.connectionState.value)
        server.connectionState.first { it is ConnectionState.Connected }
    }
}
