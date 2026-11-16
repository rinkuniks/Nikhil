package com.example.testingcast.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.example.testingcast.network.protocol.AudioFormatInfo
import java.nio.ByteBuffer

/**
 * AAC decoder writing PCM into an [AudioTrack].
 * Not thread-safe: call every method from the same single-threaded dispatcher.
 */
class AudioDecoder {
    companion object {
        private const val TAG = "AudioDecoder"
        private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val INPUT_TIMEOUT_US = 20_000L
        private const val TRACK_BUFFER_MULTIPLIER = 4
        private const val MONO = 1
    }

    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    val isConfigured: Boolean get() = codec != null

    /** [codecConfig] is the AudioSpecificConfig (csd-0) emitted by the AAC encoder. */
    fun configure(format: AudioFormatInfo, codecConfig: ByteArray) {
        release()
        val mediaFormat = MediaFormat.createAudioFormat(MIME, format.sampleRate, format.channelCount).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(codecConfig))
        }
        val newCodec = MediaCodec.createDecoderByType(MIME)
        try {
            newCodec.configure(mediaFormat, null, null, 0)
            newCodec.start()
            track = createTrack(format).also { it.play() }
            codec = newCodec
            Log.d(TAG, "Audio decoder configured ${format.sampleRate}Hz x${format.channelCount}")
        } catch (e: Exception) {
            newCodec.release()
            track?.release()
            track = null
            throw e
        }
    }

    private fun createTrack(format: AudioFormatInfo): AudioTrack {
        val channelMask = if (format.channelCount == MONO) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(format.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * TRACK_BUFFER_MULTIPLIER)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun decode(data: ByteArray, presentationTimeUs: Long) {
        val active = codec ?: return
        val output = track ?: return
        try {
            val index = active.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (index >= 0) {
                val input = checkNotNull(active.getInputBuffer(index))
                input.clear()
                if (data.size <= input.capacity()) {
                    input.put(data)
                    active.queueInputBuffer(index, 0, data.size, presentationTimeUs, 0)
                } else {
                    active.queueInputBuffer(index, 0, 0, presentationTimeUs, 0)
                }
            }
            playAvailable(active, output)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Audio decode failed: ${e.message}")
        }
    }

    private fun playAvailable(active: MediaCodec, output: AudioTrack) {
        while (true) {
            val index = active.dequeueOutputBuffer(bufferInfo, 0)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (index < 0) continue
            val pcm = active.getOutputBuffer(index)
            if (pcm != null && bufferInfo.size > 0) {
                pcm.position(bufferInfo.offset)
                pcm.limit(bufferInfo.offset + bufferInfo.size)
                output.write(pcm, bufferInfo.size, AudioTrack.WRITE_BLOCKING)
            }
            active.releaseOutputBuffer(index, false)
        }
    }

    fun release() {
        codec?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        codec = null
        track?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        track = null
    }
}
