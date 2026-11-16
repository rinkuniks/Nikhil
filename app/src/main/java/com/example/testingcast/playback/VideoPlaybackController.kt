package com.example.testingcast.playback

import android.util.Log
import com.example.testingcast.network.protocol.ControlCommands
import com.example.testingcast.network.protocol.MediaFlags
import com.example.testingcast.network.protocol.MediaProtocol
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket
import com.example.testingcast.network.protocol.VideoFormatInfo

/**
 * Drives a [VideoSink] from incoming packets and surface lifecycle.
 * The decoder is only configured once surface, format and SPS/PPS are all known.
 * Not thread-safe: confine to one single-threaded dispatcher.
 */
class VideoPlaybackController<S : Any>(
    private val sink: VideoSink<S>,
    private val requestKeyFrame: () -> Unit,
    private val onFormatChanged: (VideoFormatInfo?) -> Unit = {}
) {
    private var surface: S? = null
    private var format: VideoFormatInfo? = null
    private var codecConfig: ByteArray? = null

    fun onPacket(packet: StreamPacket) {
        when (packet.type) {
            PacketType.CONTROL -> onControl(packet)
            PacketType.VIDEO -> onVideo(packet)
            PacketType.AUDIO -> Unit
        }
    }

    private fun onControl(packet: StreamPacket) {
        val (command, payload) = StreamPacket.parseControlCommand(packet) ?: return
        if (command != ControlCommands.VIDEO_FORMAT) return
        val newFormat = MediaProtocol.parseVideoFormat(payload) ?: return
        if (newFormat == format) return
        sink.release()
        format = newFormat
        codecConfig = null
        onFormatChanged(newFormat)
    }

    private fun onVideo(packet: StreamPacket) {
        if (MediaFlags.isConfig(packet.flags)) {
            codecConfig = packet.payload
            sink.release()
            configureIfReady()
            return
        }
        if (!sink.isConfigured && !configureIfReady()) return
        val decoded = sink.decode(packet.payload, packet.timestamp, MediaFlags.isKeyFrame(packet.flags))
        if (!decoded) requestKeyFrame()
    }

    fun setSurface(newSurface: S?) {
        sink.release()
        surface = newSurface
        if (newSurface != null && configureIfReady()) requestKeyFrame()
    }

    /** Clears per-session state (e.g. after the sender disconnects). */
    fun reset() {
        sink.release()
        format = null
        codecConfig = null
        onFormatChanged(null)
    }

    private fun configureIfReady(): Boolean {
        if (sink.isConfigured) return true
        val target = surface ?: return false
        val currentFormat = format ?: return false
        val config = codecConfig ?: return false
        return try {
            sink.configure(target, currentFormat, config)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Video decoder configure failed", e)
            sink.release()
            false
        }
    }

    private companion object {
        const val TAG = "VideoPlayback"
    }
}
