package com.example.testingcast.network.socket

import android.util.Log
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.model.DiscoveryType
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
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class StreamServer(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "StreamServer"
        const val DEFAULT_PORT = 8888
    }

    private var serverSocket: ServerSocket? = null
    private var activeClientSocket: Socket? = null
    private var acceptJob: Job? = null
    private var readJob: Job? = null

    private val _incomingPackets = MutableSharedFlow<StreamPacket>(extraBufferCapacity = 128)
    val incomingPackets: SharedFlow<StreamPacket> = _incomingPackets.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentPort: Int = DEFAULT_PORT

    fun start(port: Int = DEFAULT_PORT) {
        if (serverSocket != null && !serverSocket!!.isClosed) {
            if (currentPort == port) return
            stop()
        }

        currentPort = port
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                Log.d(TAG, "StreamServer started on port $port")
                _connectionState.value = ConnectionState.Searching

                while (isActive && !ss.isClosed) {
                    val clientSocket = try {
                        ss.accept()
                    } catch (e: SocketException) {
                        Log.d(TAG, "ServerSocket closed or accept interrupted")
                        break
                    } catch (e: IOException) {
                        Log.e(TAG, "Error accepting client connection", e)
                        break
                    }

                    Log.d(TAG, "Client connected from ${clientSocket.inetAddress.hostAddress}:${clientSocket.port}")
                    handleClientConnection(clientSocket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "StreamServer error", e)
                _connectionState.value = ConnectionState.Error("Server error: ${e.message}", e)
            }
        }
    }

    private fun handleClientConnection(clientSocket: Socket) {
        closeActiveClient()
        activeClientSocket = clientSocket

        val clientDevice = DiscoveredDevice(
            id = clientSocket.inetAddress.hostAddress ?: "Client",
            name = clientSocket.inetAddress.hostName ?: "Sender",
            host = clientSocket.inetAddress.hostAddress ?: "",
            port = clientSocket.port,
            type = DiscoveryType.NSD
        )
        _connectionState.value = ConnectionState.Connected(clientDevice)

        readJob = scope.launch(Dispatchers.IO) {
            try {
                clientSocket.soTimeout = 0
                val inputStream = BufferedInputStream(clientSocket.getInputStream())

                while (isActive && !clientSocket.isClosed) {
                    val packet = PacketSerializer.readPacket(inputStream)
                    if (packet == null) {
                        Log.d(TAG, "End of stream or corrupted packet received from client")
                        break
                    }

                    val controlInfo = StreamPacket.parseControlCommand(packet)
                    if (controlInfo != null) {
                        val (command, _) = controlInfo
                        Log.d(TAG, "Received control command: $command")
                        when (command) {
                            ControlCommands.PING -> {
                                sendPacket(StreamPacket.createControl(ControlCommands.PONG))
                            }
                            ControlCommands.START -> {
                                _connectionState.value = ConnectionState.Streaming(clientDevice)
                            }
                            ControlCommands.STOP -> {
                                _connectionState.value = ConnectionState.Connected(clientDevice)
                            }
                        }
                    }

                    _incomingPackets.emit(packet)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from client socket", e)
            } finally {
                Log.d(TAG, "Client disconnected")
                closeActiveClient()
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    suspend fun sendPacket(packet: StreamPacket): Boolean = withContext(Dispatchers.IO) {
        val socket = activeClientSocket
        if (socket == null || socket.isClosed || !socket.isConnected) {
            Log.e(TAG, "Cannot send packet: client socket not connected")
            return@withContext false
        }

        try {
            synchronized(socket) {
                val outputStream = BufferedOutputStream(socket.getOutputStream())
                PacketSerializer.writePacket(outputStream, packet)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send packet to client", e)
            closeActiveClient()
            _connectionState.value = ConnectionState.Error("Failed to send packet", e)
            false
        }
    }

    private fun closeActiveClient() {
        readJob?.cancel()
        readJob = null
        try {
            activeClientSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing client socket", e)
        }
        activeClientSocket = null
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        closeActiveClient()

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        _connectionState.value = ConnectionState.Disconnected
        Log.d(TAG, "StreamServer stopped")
    }
}
