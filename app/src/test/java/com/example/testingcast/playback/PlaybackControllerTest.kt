package com.example.testingcast.playback

import com.example.testingcast.network.protocol.AudioFormatInfo
import com.example.testingcast.network.protocol.MediaProtocol
import com.example.testingcast.network.protocol.PacketType
import com.example.testingcast.network.protocol.StreamPacket
import com.example.testingcast.network.protocol.VideoFormatInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeVideoSink(var decodeResult: Boolean = true) : VideoSink<String> {
    override var isConfigured = false
    val configureCalls = mutableListOf<Triple<String, VideoFormatInfo, ByteArray>>()
    val decoded = mutableListOf<Pair<Long, Boolean>>()
    var releaseCount = 0

    override fun configure(surface: String, format: VideoFormatInfo, codecConfig: ByteArray) {
        configureCalls += Triple(surface, format, codecConfig)
        isConfigured = true
    }

    override fun decode(data: ByteArray, presentationTimeUs: Long, isKeyFrame: Boolean): Boolean {
        decoded += presentationTimeUs to isKeyFrame
        return decodeResult
    }

    override fun release() {
        releaseCount++
        isConfigured = false
    }
}

private class FakeAudioSink : AudioSink {
    override var isConfigured = false
    var configuredWith: Pair<AudioFormatInfo, ByteArray>? = null
    val decoded = mutableListOf<Long>()

    override fun configure(format: AudioFormatInfo, codecConfig: ByteArray) {
        configuredWith = format to codecConfig
        isConfigured = true
    }

    override fun decode(data: ByteArray, presentationTimeUs: Long) {
        decoded += presentationTimeUs
    }

    override fun release() {
        isConfigured = false
    }
}

private val SPS_PPS = byteArrayOf(0, 0, 0, 1, 0x67, 0, 0, 0, 1, 0x68)
private val FORMAT_720 = VideoFormatInfo(1280, 720)

private fun video(pts: Long, config: Boolean = false, key: Boolean = false, data: ByteArray = byteArrayOf(1)) =
    MediaProtocol.mediaPacket(PacketType.VIDEO, data, pts, config, key, pts)

private fun audio(pts: Long, config: Boolean = false, data: ByteArray = byteArrayOf(2)) =
    MediaProtocol.mediaPacket(PacketType.AUDIO, data, pts, config, false, pts)

class VideoPlaybackControllerTest {

    private val sink = FakeVideoSink()
    private var keyFrameRequests = 0
    private var lastFormat: VideoFormatInfo? = null
    private val controller = VideoPlaybackController(sink, { keyFrameRequests++ }, { lastFormat = it })

    @Test
    fun `configures decoder only once surface format and config are known`() {
        controller.onPacket(MediaProtocol.videoFormatPacket(FORMAT_720))
        controller.onPacket(video(0, config = true, data = SPS_PPS))
        assertTrue(sink.configureCalls.isEmpty())

        controller.setSurface("surface-1")

        assertEquals(1, sink.configureCalls.size)
        val (surface, format, config) = sink.configureCalls.single()
        assertEquals("surface-1", surface)
        assertEquals(FORMAT_720, format)
        assertArrayEquals(SPS_PPS, config)
        assertEquals(1, keyFrameRequests)
        assertEquals(FORMAT_720, lastFormat)
    }

    @Test
    fun `drops frames before configuration and decodes after`() {
        controller.onPacket(video(10, key = true))
        assertTrue(sink.decoded.isEmpty())

        controller.setSurface("s")
        controller.onPacket(MediaProtocol.videoFormatPacket(FORMAT_720))
        controller.onPacket(video(0, config = true, data = SPS_PPS))
        controller.onPacket(video(20, key = true))
        controller.onPacket(video(30))

        assertEquals(listOf(20L to true, 30L to false), sink.decoded)
    }

    @Test
    fun `requests key frame when decoder drops a frame`() {
        sink.decodeResult = false
        controller.setSurface("s")
        controller.onPacket(MediaProtocol.videoFormatPacket(FORMAT_720))
        controller.onPacket(video(0, config = true, data = SPS_PPS))

        controller.onPacket(video(1, key = true))

        assertEquals(1, keyFrameRequests)
    }

    @Test
    fun `surface loss releases decoder and new surface reconfigures`() {
        controller.setSurface("s1")
        controller.onPacket(MediaProtocol.videoFormatPacket(FORMAT_720))
        controller.onPacket(video(0, config = true, data = SPS_PPS))

        controller.setSurface(null)
        assertFalse(sink.isConfigured)
        controller.setSurface("s2")

        assertEquals(listOf("s1", "s2"), sink.configureCalls.map { it.first })
    }

    @Test
    fun `format change waits for new codec config`() {
        controller.setSurface("s")
        controller.onPacket(MediaProtocol.videoFormatPacket(FORMAT_720))
        controller.onPacket(video(0, config = true, data = SPS_PPS))

        controller.onPacket(MediaProtocol.videoFormatPacket(VideoFormatInfo(1920, 1080)))
        controller.onPacket(video(5, key = true))

        assertFalse(sink.isConfigured)
        assertEquals(1, sink.configureCalls.size)
    }

    @Test
    fun `reset clears format and notifies ui`() {
        controller.onPacket(MediaProtocol.videoFormatPacket(FORMAT_720))

        controller.reset()

        assertNull(lastFormat)
        assertFalse(sink.isConfigured)
    }

    @Test
    fun `ignores unrelated control packets`() {
        controller.onPacket(StreamPacket.createControl("PING"))

        assertNull(lastFormat)
    }
}

class AudioPlaybackControllerTest {

    private val sink = FakeAudioSink()
    private val controller = AudioPlaybackController(sink)
    private val format = AudioFormatInfo(44_100, 2)

    @Test
    fun `configures on codec config after format and decodes frames`() {
        controller.onPacket(MediaProtocol.audioFormatPacket(format))
        controller.onPacket(audio(0, config = true, data = byteArrayOf(0x12, 0x10)))
        controller.onPacket(audio(23_219))

        assertEquals(format, sink.configuredWith?.first)
        assertArrayEquals(byteArrayOf(0x12, 0x10), sink.configuredWith?.second)
        assertEquals(listOf(23_219L), sink.decoded)
        assertTrue(controller.isActive)
    }

    @Test
    fun `ignores config without format and frames before configuration`() {
        controller.onPacket(audio(0, config = true))
        controller.onPacket(audio(1))

        assertNull(sink.configuredWith)
        assertTrue(sink.decoded.isEmpty())
    }

    @Test
    fun `reset releases sink`() {
        controller.onPacket(MediaProtocol.audioFormatPacket(format))
        controller.onPacket(audio(0, config = true))

        controller.reset()

        assertFalse(controller.isActive)
    }
}
