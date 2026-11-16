package com.example.testingcast.ui.receiver

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.protocol.ControlCommands
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket
import com.example.testingcast.network.protocol.VideoFormatInfo
import com.example.testingcast.network.repository.NetworkRepository
import com.example.testingcast.network.socket.StreamServer
import com.example.testingcast.playback.AudioPlaybackController
import com.example.testingcast.playback.MediaCodecAudioSink
import com.example.testingcast.playback.MediaCodecVideoSink
import com.example.testingcast.playback.VideoPlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class ReceiverUiState(
    val deviceName: String,
    val addresses: List<String>,
    val port: Int = StreamServer.DEFAULT_PORT,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val videoFormat: VideoFormatInfo? = null,
    val isAudioActive: Boolean = false
) {
    val isPlaying: Boolean get() = videoFormat != null && connectionState is ConnectionState.Streaming
}

/**
 * Runs the receiver: socket server + NSD advertising, and routes packets to
 * single-threaded video/audio lanes that own the MediaCodec decoders.
 */
class ReceiverViewModel(
    private val repository: NetworkRepository,
    private val deviceName: String,
    addressProvider: () -> List<String>
) : ViewModel() {

    private companion object {
        const val VIDEO_QUEUE_CAPACITY = 120
        const val AUDIO_QUEUE_CAPACITY = 64
        const val KEY_FRAME_REQUEST_INTERVAL_MS = 500L
        const val SERVICE_NAME_PREFIX = "TestingCast-"
    }

    private val videoLane = Dispatchers.IO.limitedParallelism(1)
    private val audioLane = Dispatchers.IO.limitedParallelism(1)
    private val videoQueue = Channel<StreamPacket>(VIDEO_QUEUE_CAPACITY)
    private val audioQueue = Channel<StreamPacket>(AUDIO_QUEUE_CAPACITY, BufferOverflow.DROP_OLDEST)
    private val lastKeyFrameRequest = AtomicLong(0L)

    private val videoFormat = MutableStateFlow<VideoFormatInfo?>(null)
    private val audioActive = MutableStateFlow(false)
    private val addresses = MutableStateFlow(addressProvider())

    private val video = VideoPlaybackController(MediaCodecVideoSink(), ::requestKeyFrame) { videoFormat.value = it }
    private val audio = AudioPlaybackController(MediaCodecAudioSink())

    val uiState: StateFlow<ReceiverUiState> = combine(
        repository.connectionState, videoFormat, audioActive, addresses
    ) { connection, format, audioOn, ips ->
        ReceiverUiState(deviceName, ips, StreamServer.DEFAULT_PORT, connection, format, audioOn)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReceiverUiState(deviceName, addresses.value))

    init {
        repository.startServer(StreamServer.DEFAULT_PORT)
        repository.startAdvertising(SERVICE_NAME_PREFIX + deviceName, StreamServer.DEFAULT_PORT)
        viewModelScope.launch { repository.incomingPackets.collect { route(it) } }
        viewModelScope.launch(videoLane) { for (packet in videoQueue) video.onPacket(packet) }
        viewModelScope.launch(audioLane) {
            for (packet in audioQueue) {
                audio.onPacket(packet)
                audioActive.value = audio.isActive
            }
        }
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                if (state is ConnectionState.Disconnected || state is ConnectionState.Error) resetPlayback()
            }
        }
        viewModelScope.launch {
            repository.connectionState.collect { addresses.value = addressProvider() }
        }
    }

    /** Makes this receiver visible to Wi-Fi Direct senders (requires nearby-devices permission). */
    fun enableWifiDirect() = repository.startWifiP2pDiscovery()

    fun onSurfaceAvailable(surface: Surface) {
        viewModelScope.launch(videoLane) { video.setSurface(surface) }
    }

    /** Blocks briefly so the decoder stops rendering before the surface is destroyed. */
    fun onSurfaceDestroyed() {
        runBlocking(videoLane) { video.setSurface(null) }
    }

    private fun route(packet: StreamPacket) {
        when (packet.type) {
            PacketType.VIDEO -> if (videoQueue.trySend(packet).isFailure) requestKeyFrame()
            PacketType.AUDIO -> audioQueue.trySend(packet)
            PacketType.CONTROL -> routeControl(packet)
        }
    }

    private fun routeControl(packet: StreamPacket) {
        when (StreamPacket.parseControlCommand(packet)?.first) {
            ControlCommands.VIDEO_FORMAT -> videoQueue.trySend(packet)
            ControlCommands.AUDIO_FORMAT -> audioQueue.trySend(packet)
        }
    }

    private fun requestKeyFrame() {
        val now = System.currentTimeMillis()
        val last = lastKeyFrameRequest.get()
        if (now - last < KEY_FRAME_REQUEST_INTERVAL_MS || !lastKeyFrameRequest.compareAndSet(last, now)) return
        viewModelScope.launch {
            repository.sendPacket(StreamPacket.createControl(ControlCommands.REQUEST_KEYFRAME))
        }
    }

    private fun resetPlayback() {
        viewModelScope.launch(videoLane) { video.reset() }
        viewModelScope.launch(audioLane) {
            audio.reset()
            audioActive.value = false
        }
    }

    override fun onCleared() {
        repository.stopAdvertising()
        repository.stopServer()
        repository.stopWifiP2pDiscovery()
        videoQueue.close()
        audioQueue.close()
        runBlocking {
            withContext(videoLane) {
                video.setSurface(null)
                video.reset()
            }
            withContext(audioLane) { audio.reset() }
        }
    }
}
