package com.example.testingcast.capture

import android.media.MediaCodec
import com.example.testingcast.network.protocol.MediaProtocol
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket

/** Result of polling an encoder's output once. */
sealed interface EncoderOutput {
    data class Packet(val packet: StreamPacket, val isEndOfStream: Boolean) : EncoderOutput
    data object TryAgain : EncoderOutput
    data object EndOfStream : EncoderOutput
}

/**
 * Pulls one encoded buffer from [codec] and wraps it as a media [StreamPacket].
 * Format/buffer-change signals are reported as [EncoderOutput.TryAgain].
 */
class CodecOutputReader(private val type: PacketType) {
    private val bufferInfo = MediaCodec.BufferInfo()
    private var sequence = 0L

    fun poll(codec: MediaCodec, timeoutUs: Long): EncoderOutput {
        val index = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
        if (index < 0) return EncoderOutput.TryAgain

        try {
            val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            if (bufferInfo.size <= 0) {
                return if (isEos) EncoderOutput.EndOfStream else EncoderOutput.TryAgain
            }
            val buffer = codec.getOutputBuffer(index) ?: return EncoderOutput.TryAgain
            val data = ByteArray(bufferInfo.size)
            buffer.position(bufferInfo.offset)
            buffer.limit(bufferInfo.offset + bufferInfo.size)
            buffer.get(data)
            val packet = MediaProtocol.mediaPacket(
                type = type,
                data = data,
                presentationTimeUs = bufferInfo.presentationTimeUs,
                isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0,
                isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0,
                sequenceNumber = sequence++
            )
            return EncoderOutput.Packet(packet, isEos)
        } finally {
            codec.releaseOutputBuffer(index, false)
        }
    }
}
