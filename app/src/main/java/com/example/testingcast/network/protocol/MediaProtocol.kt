package com.example.testingcast.network.protocol

import java.nio.ByteBuffer

data class VideoFormatInfo(val width: Int, val height: Int)

data class AudioFormatInfo(val sampleRate: Int, val channelCount: Int)

/** Bit flags carried in [StreamPacket.flags] for VIDEO / AUDIO packets. */
object MediaFlags {
    const val CONFIG: Short = 0x01
    const val KEY_FRAME: Short = 0x02

    fun of(isConfig: Boolean, isKeyFrame: Boolean): Short {
        var value = 0
        if (isConfig) value = value or CONFIG.toInt()
        if (isKeyFrame) value = value or KEY_FRAME.toInt()
        return value.toShort()
    }

    fun isConfig(flags: Short): Boolean = (flags.toInt() and CONFIG.toInt()) != 0

    fun isKeyFrame(flags: Short): Boolean = (flags.toInt() and KEY_FRAME.toInt()) != 0
}

/**
 * Encodes/decodes the media-specific packets exchanged on top of [StreamPacket].
 * Media packets reuse [StreamPacket.timestamp] as the presentation time in microseconds.
 */
object MediaProtocol {
    private const val FORMAT_PAYLOAD_SIZE = 8
    private const val MAX_VIDEO_DIMENSION = 8192
    private val SAMPLE_RATE_RANGE = 8_000..192_000
    private val CHANNEL_RANGE = 1..8

    fun videoFormatPacket(info: VideoFormatInfo): StreamPacket =
        StreamPacket.createControl(ControlCommands.VIDEO_FORMAT, intPair(info.width, info.height))

    fun audioFormatPacket(info: AudioFormatInfo): StreamPacket =
        StreamPacket.createControl(ControlCommands.AUDIO_FORMAT, intPair(info.sampleRate, info.channelCount))

    fun parseVideoFormat(payload: ByteArray): VideoFormatInfo? {
        val (width, height) = readIntPair(payload) ?: return null
        val valid = width in 1..MAX_VIDEO_DIMENSION && height in 1..MAX_VIDEO_DIMENSION
        return if (valid) VideoFormatInfo(width, height) else null
    }

    fun parseAudioFormat(payload: ByteArray): AudioFormatInfo? {
        val (sampleRate, channels) = readIntPair(payload) ?: return null
        val valid = sampleRate in SAMPLE_RATE_RANGE && channels in CHANNEL_RANGE
        return if (valid) AudioFormatInfo(sampleRate, channels) else null
    }

    fun mediaPacket(
        type: PacketType,
        data: ByteArray,
        presentationTimeUs: Long,
        isConfig: Boolean,
        isKeyFrame: Boolean,
        sequenceNumber: Long
    ): StreamPacket {
        require(type == PacketType.VIDEO || type == PacketType.AUDIO) { "Not a media type: $type" }
        return StreamPacket(
            type = type,
            timestamp = presentationTimeUs,
            sequenceNumber = sequenceNumber,
            flags = MediaFlags.of(isConfig, isKeyFrame),
            payload = data
        )
    }

    private fun intPair(first: Int, second: Int): ByteArray =
        ByteBuffer.allocate(FORMAT_PAYLOAD_SIZE).putInt(first).putInt(second).array()

    private fun readIntPair(payload: ByteArray): Pair<Int, Int>? {
        if (payload.size < FORMAT_PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(payload)
        return buffer.int to buffer.int
    }
}
