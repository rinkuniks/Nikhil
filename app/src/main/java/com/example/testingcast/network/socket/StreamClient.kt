package com.example.testingcast.network.socket

import android.util.Log
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.protocol.ControlCommands
import com.example.testingcast.network.protocol.PacketSerializer
import com.example.testingcast.network.protocol.StreamPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class StreamClient(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "StreamClient"
        private const val CONNECT_TIMEOUT_MS = 5000
    }

    private var socket: Socket? = null
    private var readJob: Job? = null
    private var currentDevice: DiscoveredDevice? = null

    private val _incomingPackets = MutableSharedFlow<StreamPacket>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<StreamPacket> = _incomingPackets.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    suspend fun connect(device: DiscoveredDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        currentDevice = device
        _connectionState.value = ConnectionState.Connecting(device)

        try {
            val newSocket = Socket()
            newSocket.connect(InetSocketAddress(device.host, device.port), CONNECT_TIMEOUT_MS)
            socket = newSocket

            _connectionState.value = ConnectionState.Connected(device)
            Log.d(TAG, "Connected to ${device.name} (${device.host}:${device.port})")

            startReadLoop(newSocket, device)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ${device.host}:${device.port}", e)
            disconnect()
            _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}", e)
            false
        }
    }

    private fun startReadLoop(socket: Socket, device: DiscoveredDevice) {
        readJob = scope.launch(Dispatchers.IO) {
            try {
                val inputStream = BufferedInputStream(socket.getInputStream())
                while (isActive && !socket.isClosed) {
                    val packet = PacketSerializer.readPacket(inputStream)
                    if (packet == null) {
                        Log.d(TAG, "Server closed connection or sent invalid packet")
                        break
                    }

                    val controlInfo = StreamPacket.parseControlCommand(packet)
                    if (controlInfo != null) {
                        val (command, _) = controlInfo
                        Log.d(TAG, "Received control command from server: $command")
                    }

                    _incomingPackets.emit(packet)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in client read loop", e)
            } finally {
                Log.d(TAG, "Client read loop stopped")
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    suspend fun startStreaming(): Boolean {
        val dev = currentDevice ?: return false
        val startPacket = StreamPacket.createControl(ControlCommands.START)
        val success = sendPacket(startPacket)
        if (success) {
            _connectionState.value = ConnectionState.Streaming(dev)
        }
        return success
    }

    suspend fun stopStreaming(): Boolean {
        val dev = currentDevice ?: return false
        val stopPacket = StreamPacket.createControl(ControlCommands.STOP)
        val success = sendPacket(stopPacket)
        if (success) {
            _connectionState.value = ConnectionState.Connected(dev)
        }
        return success
    }

    suspend fun sendPacket(packet: StreamPacket): Boolean = withContext(Dispatchers.IO) {
        val activeSocket = socket
        if (activeSocket == null || activeSocket.isClosed || !activeSocket.isConnected) {
            Log.e(TAG, "Cannot send packet: socket is closed or null")
            return@withContext false
        }

        try {
            synchronized(activeSocket) {
                val outputStream = BufferedOutputStream(activeSocket.getOutputStream())
                PacketSerializer.writePacket(outputStream, packet)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send packet", e)
            disconnect()
            _connectionState.value = ConnectionState.Error("Failed to send data: ${e.message}", e)
            false
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null

        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        }
        socket = null
        currentDevice = null
        _connectionState.value = ConnectionState.Disconnected
        Log.d(TAG, "Disconnected")
    }
}
