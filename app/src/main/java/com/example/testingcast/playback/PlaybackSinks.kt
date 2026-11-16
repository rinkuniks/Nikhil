package com.example.testingcast.playback

import android.view.Surface
import com.example.testingcast.network.protocol.AudioFormatInfo
import com.example.testingcast.network.protocol.VideoFormatInfo

/** Decoder abstraction so playback logic can be unit-tested without MediaCodec. */
interface VideoSink<S> {
    val isConfigured: Boolean
    fun configure(surface: S, format: VideoFormatInfo, codecConfig: ByteArray)
    fun decode(data: ByteArray, presentationTimeUs: Long, isKeyFrame: Boolean): Boolean
    fun release()
}

interface AudioSink {
    val isConfigured: Boolean
    fun configure(format: AudioFormatInfo, codecConfig: ByteArray)
    fun decode(data: ByteArray, presentationTimeUs: Long)
    fun release()
}

class MediaCodecVideoSink(private val decoder: VideoDecoder = VideoDecoder()) : VideoSink<Surface> {
    override val isConfigured: Boolean get() = decoder.isConfigured
    override fun configure(surface: Surface, format: VideoFormatInfo, codecConfig: ByteArray) =
        decoder.configure(surface, format, codecConfig)
    override fun decode(data: ByteArray, presentationTimeUs: Long, isKeyFrame: Boolean): Boolean =
        decoder.decode(data, presentationTimeUs, isKeyFrame)
    override fun release() = decoder.release()
}

class MediaCodecAudioSink(private val decoder: AudioDecoder = AudioDecoder()) : AudioSink {
    override val isConfigured: Boolean get() = decoder.isConfigured
    override fun configure(format: AudioFormatInfo, codecConfig: ByteArray) = decoder.configure(format, codecConfig)
    override fun decode(data: ByteArray, presentationTimeUs: Long) = decoder.decode(data, presentationTimeUs)
    override fun release() = decoder.release()
}
