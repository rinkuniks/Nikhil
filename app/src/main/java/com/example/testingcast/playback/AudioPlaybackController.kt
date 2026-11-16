package com.example.testingcast.playback

import android.util.Log
import com.example.testingcast.network.protocol.AudioFormatInfo
import com.example.testingcast.network.protocol.ControlCommands
import com.example.testingcast.network.protocol.MediaFlags
import com.example.testingcast.network.protocol.MediaProtocol
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket

/** Drives an [AudioSink]; not thread-safe, confine to one single-threaded dispatcher. */
class AudioPlaybackController(private val sink: AudioSink) {
    private var format: AudioFormatInfo? = null

    val isActive: Boolean get() = sink.isConfigured

    fun onPacket(packet: StreamPacket) {
        when (packet.type) {
            PacketType.CONTROL -> onControl(packet)
            PacketType.AUDIO -> onAudio(packet)
            PacketType.VIDEO -> Unit
        }
    }

    private fun onControl(packet: StreamPacket) {
        val (command, payload) = StreamPacket.parseControlCommand(packet) ?: return
        if (command != ControlCommands.AUDIO_FORMAT) return
        val newFormat = MediaProtocol.parseAudioFormat(payload) ?: return
        if (newFormat == format) return
        sink.release()
        format = newFormat
    }

    private fun onAudio(packet: StreamPacket) {
        if (MediaFlags.isConfig(packet.flags)) {
            val currentFormat = format ?: return
            try {
                sink.configure(currentFormat, packet.payload)
            } catch (e: Exception) {
                Log.e(TAG, "Audio decoder configure failed", e)
                sink.release()
            }
            return
        }
        if (sink.isConfigured) sink.decode(packet.payload, packet.timestamp)
    }

    fun reset() {
        sink.release()
        format = null
    }

    private companion object {
        const val TAG = "AudioPlayback"
    }
}
