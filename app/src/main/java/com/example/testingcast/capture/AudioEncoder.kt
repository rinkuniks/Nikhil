package com.example.testingcast.capture

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.testingcast.network.protocol.AudioFormatInfo
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures device playback audio (API 29+) via [AudioPlaybackCaptureConfiguration]
 * and encodes it to AAC-LC.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AudioEncoder(
    private val projection: MediaProjection,
    private val onPacket: suspend (StreamPacket) -> Unit
) {
    companion object {
        private const val TAG = "AudioEncoder"
        private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_COUNT = 2
        private const val BIT_RATE = 128_000
        private const val BYTES_PER_FRAME = 2 * CHANNEL_COUNT
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val INPUT_TIMEOUT_US = 10_000L
        private const val BUFFER_MULTIPLIER = 2
        val FORMAT = AudioFormatInfo(SAMPLE_RATE, CHANNEL_COUNT)
    }

    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var job: Job? = null
    private val reader = CodecOutputReader(PacketType.AUDIO)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(scope: CoroutineScope): Boolean {
        return try {
            val newRecord = createAudioRecord()
            val newCodec = createCodec()
            newRecord.startRecording()
            record = newRecord
            codec = newCodec
            job = scope.launch(Dispatchers.IO) { pump(newRecord, newCodec) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture unavailable", e)
            releaseResources()
            false
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createAudioRecord(): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuffer * BUFFER_MULTIPLIER)
            .setAudioPlaybackCaptureConfig(config)
            .build()
            .also { check(it.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialized" } }
    }

    private fun createCodec(): MediaCodec {
        val format = MediaFormat.createAudioFormat(MIME, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        }
        val newCodec = MediaCodec.createEncoderByType(MIME)
        try {
            newCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            newCodec.start()
        } catch (e: Exception) {
            newCodec.release()
            throw e
        }
        return newCodec
    }

    private suspend fun CoroutineScope.pump(audioRecord: AudioRecord, encoder: MediaCodec) {
        var framesQueued = 0L
        try {
            while (isActive) {
                val inputIndex = encoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val input = checkNotNull(encoder.getInputBuffer(inputIndex))
                    val read = audioRecord.read(input, input.capacity())
                    val size = read.coerceAtLeast(0)
                    val ptsUs = framesQueued * MICROS_PER_SECOND / SAMPLE_RATE
                    encoder.queueInputBuffer(inputIndex, 0, size, ptsUs, 0)
                    framesQueued += size / BYTES_PER_FRAME
                }
                drainAvailable(encoder)
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Audio pump stopped: ${e.message}")
        }
    }

    private suspend fun drainAvailable(encoder: MediaCodec) {
        while (true) {
            when (val output = reader.poll(encoder, 0)) {
                is EncoderOutput.Packet -> onPacket(output.packet)
                EncoderOutput.TryAgain, EncoderOutput.EndOfStream -> return
            }
        }
    }

    suspend fun release() {
        job?.cancelAndJoin()
        job = null
        releaseResources()
    }

    private fun releaseResources() {
        record?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        record = null
        codec?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        codec = null
    }
}
