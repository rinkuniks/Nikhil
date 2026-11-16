package com.example.testingcast.network

import com.example.testingcast.network.protocol.AudioFormatInfo
import com.example.testingcast.network.protocol.ControlCommands
import com.example.testingcast.network.protocol.MediaFlags
import com.example.testingcast.network.protocol.MediaProtocol
import com.example.testingcast.network.protocol.PacketSerializer
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket
import com.example.testingcast.network.protocol.VideoFormatInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MediaProtocolTest {

    @Test
    fun `video format survives control packet round trip`() {
        val packet = MediaProtocol.videoFormatPacket(VideoFormatInfo(1280, 720))

        val (command, payload) = requireNotNull(StreamPacket.parseControlCommand(packet))

        assertEquals(ControlCommands.VIDEO_FORMAT, command)
        assertEquals(VideoFormatInfo(1280, 720), MediaProtocol.parseVideoFormat(payload))
    }

    @Test
    fun `audio format survives control packet round trip`() {
        val packet = MediaProtocol.audioFormatPacket(AudioFormatInfo(44_100, 2))

        val (command, payload) = requireNotNull(StreamPacket.parseControlCommand(packet))

        assertEquals(ControlCommands.AUDIO_FORMAT, command)
        assertEquals(AudioFormatInfo(44_100, 2), MediaProtocol.parseAudioFormat(payload))
    }

    @Test
    fun `rejects truncated or out of range format payloads`() {
        assertNull(MediaProtocol.parseVideoFormat(ByteArray(4)))
        assertNull(MediaProtocol.parseVideoFormat(java.nio.ByteBuffer.allocate(8).putInt(0).putInt(720).array()))
        assertNull(MediaProtocol.parseVideoFormat(java.nio.ByteBuffer.allocate(8).putInt(99_999).putInt(720).array()))
        assertNull(MediaProtocol.parseAudioFormat(java.nio.ByteBuffer.allocate(8).putInt(100).putInt(2).array()))
        assertNull(MediaProtocol.parseAudioFormat(java.nio.ByteBuffer.allocate(8).putInt(48_000).putInt(0).array()))
    }

    @Test
    fun `media flags encode config and key frame independently`() {
        val both = MediaFlags.of(isConfig = true, isKeyFrame = true)
        val keyOnly = MediaFlags.of(isConfig = false, isKeyFrame = true)
        val none = MediaFlags.of(isConfig = false, isKeyFrame = false)

        assertTrue(MediaFlags.isConfig(both) && MediaFlags.isKeyFrame(both))
        assertTrue(MediaFlags.isKeyFrame(keyOnly))
        assertFalse(MediaFlags.isConfig(keyOnly))
        assertFalse(MediaFlags.isConfig(none) || MediaFlags.isKeyFrame(none))
    }

    @Test
    fun `media packet keeps pts and flags through serializer`() {
        val data = byteArrayOf(0, 0, 0, 1, 0x65, 0x11)
        val packet = MediaProtocol.mediaPacket(PacketType.VIDEO, data, 33_333L, isConfig = false, isKeyFrame = true, sequenceNumber = 7)
        val out = ByteArrayOutputStream()

        PacketSerializer.writePacket(out, packet)
        val decoded = assertNotNullAndGet(PacketSerializer.readPacket(ByteArrayInputStream(out.toByteArray())))

        assertEquals(packet, decoded)
        assertEquals(33_333L, decoded.timestamp)
        assertTrue(MediaFlags.isKeyFrame(decoded.flags))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `media packet rejects control type`() {
        MediaProtocol.mediaPacket(PacketType.CONTROL, ByteArray(1), 0, isConfig = false, isKeyFrame = false, sequenceNumber = 0)
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
