package com.example.testingcast.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Hardware H.264 encoder fed by a [Surface] (backed by a VirtualDisplay).
 * Encoded access units are delivered to [onPacket] from a background coroutine.
 */
class VideoEncoder(
    private val size: CaptureSize,
    private val quality: StreamQuality,
    private val onPacket: suspend (StreamPacket) -> Unit
) {
    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val I_FRAME_INTERVAL_SEC = 1
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val KBPS = 1_000
    }

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var drainJob: Job? = null
    private val reader = CodecOutputReader(PacketType.VIDEO)

    /** Configures and starts the codec; returns the surface to render the screen into. */
    fun prepare(): Surface {
        val format = MediaFormat.createVideoFormat(MIME, size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrateKbps * KBPS)
            setInteger(MediaFormat.KEY_FRAME_RATE, quality.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
            // Keep producing frames on a static screen so a late decoder still gets output.
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, MICROS_PER_SECOND / quality.fps)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        val newCodec = MediaCodec.createEncoderByType(MIME)
        try {
            newCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = newCodec.createInputSurface()
            newCodec.start()
            codec = newCodec
            inputSurface = surface
            Log.d(TAG, "Encoder started ${size.width}x${size.height} ${quality.bitrateKbps}kbps ${quality.fps}fps")
            return surface
        } catch (e: Exception) {
            newCodec.release()
            throw e
        }
    }

    fun startDraining(scope: CoroutineScope) {
        val activeCodec = checkNotNull(codec) { "prepare() must be called first" }
        drainJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    when (val output = reader.poll(activeCodec, DEQUEUE_TIMEOUT_US)) {
                        is EncoderOutput.Packet -> {
                            onPacket(output.packet)
                            if (output.isEndOfStream) break
                        }
                        EncoderOutput.EndOfStream -> break
                        EncoderOutput.TryAgain -> Unit
                    }
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Encoder drain stopped: ${e.message}")
            }
        }
    }

    fun requestKeyFrame() {
        try {
            codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Key frame request ignored: ${e.message}")
        }
    }

    suspend fun release() {
        drainJob?.cancelAndJoin()
        drainJob = null
        codec?.let { active ->
            runCatching { active.stop() }.onFailure { Log.w(TAG, "stop failed", it) }
            active.release()
        }
        codec = null
        inputSurface?.release()
        inputSurface = null
    }
}
