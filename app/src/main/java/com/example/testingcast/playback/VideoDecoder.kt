package com.example.testingcast.playback

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.example.testingcast.network.protocol.VideoFormatInfo

/**
 * Synchronous H.264 decoder rendering straight to a [Surface].
 * Not thread-safe: call every method from the same single-threaded dispatcher.
 */
class VideoDecoder {
    companion object {
        private const val TAG = "VideoDecoder"
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val INPUT_TIMEOUT_US = 50_000L
    }

    private var codec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private var awaitingKeyFrame = true

    val isConfigured: Boolean get() = codec != null

    /** (Re)creates the codec. [codecConfig] is the SPS/PPS buffer emitted by the encoder. */
    fun configure(surface: Surface, format: VideoFormatInfo, codecConfig: ByteArray) {
        release()
        val mediaFormat = MediaFormat.createVideoFormat(MIME, format.width, format.height)
        val newCodec = MediaCodec.createDecoderByType(MIME)
        try {
            newCodec.configure(mediaFormat, surface, null, 0)
            newCodec.start()
        } catch (e: Exception) {
            newCodec.release()
            throw e
        }
        codec = newCodec
        awaitingKeyFrame = true
        queue(newCodec, codecConfig, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
        Log.d(TAG, "Decoder configured ${format.width}x${format.height}")
    }

    /**
     * Feeds one access unit. Returns false when the frame had to be dropped,
     * in which case the caller should request a new key frame.
     */
    fun decode(data: ByteArray, presentationTimeUs: Long, isKeyFrame: Boolean): Boolean {
        val active = codec ?: return false
        if (awaitingKeyFrame && !isKeyFrame) return true
        awaitingKeyFrame = false
        return try {
            val queued = queue(active, data, presentationTimeUs, if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            renderAvailable(active)
            if (!queued) awaitingKeyFrame = true
            queued
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Decode failed: ${e.message}")
            awaitingKeyFrame = true
            false
        }
    }

    private fun queue(active: MediaCodec, data: ByteArray, ptsUs: Long, flags: Int): Boolean {
        val index = active.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (index < 0) return false
        val input = checkNotNull(active.getInputBuffer(index))
        input.clear()
        if (data.size > input.capacity()) {
            active.queueInputBuffer(index, 0, 0, ptsUs, 0)
            return false
        }
        input.put(data)
        active.queueInputBuffer(index, 0, data.size, ptsUs, flags)
        return true
    }

    private fun renderAvailable(active: MediaCodec) {
        while (true) {
            val index = active.dequeueOutputBuffer(bufferInfo, 0)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (index >= 0) active.releaseOutputBuffer(index, true)
        }
    }

    fun release() {
        codec?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        codec = null
    }
}
