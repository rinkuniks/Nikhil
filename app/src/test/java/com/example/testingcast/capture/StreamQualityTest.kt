package com.example.testingcast.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamQualityTest {

    @Test
    fun `scales portrait 1080x2400 down to 720p short side aligned to 16`() {
        val size = CaptureSizeCalculator.compute(1080, 2400, ResolutionOption.HD_720)

        assertEquals(720, size.width)
        assertEquals(1600, size.height)
    }

    @Test
    fun `scales landscape display and aligns odd results down`() {
        val size = CaptureSizeCalculator.compute(2340, 1080, ResolutionOption.SD_480)

        assertEquals(480, size.height)
        assertEquals(0, size.width % 16)
        assertTrue(size.width in 1024..1040)
    }

    @Test
    fun `rotating display to landscape swaps capture size so landscape fills the frame`() {
        val portrait = CaptureSizeCalculator.compute(1080, 2400, ResolutionOption.HD_720)
        val landscape = CaptureSizeCalculator.compute(2400, 1080, ResolutionOption.HD_720)

        assertEquals(CaptureSize(1600, 720), landscape)
        assertEquals(portrait.width, landscape.height)
        assertEquals(portrait.height, landscape.width)
    }

    @Test
    fun `never upscales a small display`() {
        val size = CaptureSizeCalculator.compute(800, 480, ResolutionOption.FHD_1080)

        assertEquals(800, size.width)
        assertEquals(480, size.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non positive display size`() {
        CaptureSizeCalculator.compute(0, 1080, ResolutionOption.HD_720)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects fps outside supported range`() {
        StreamQuality(fps = 120)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects bitrate outside supported range`() {
        StreamQuality(bitrateKbps = 100)
    }

    @Test
    fun `default quality is 720p 4mbps 30fps`() {
        val quality = StreamQuality()

        assertEquals(ResolutionOption.HD_720, quality.resolution)
        assertEquals(4_000, quality.bitrateKbps)
        assertEquals(30, quality.fps)
    }
}
