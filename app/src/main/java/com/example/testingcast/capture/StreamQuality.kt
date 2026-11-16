package com.example.testingcast.capture

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ResolutionOption(val label: String, val shortSide: Int) {
    SD_480("480p", 480),
    HD_720("720p", 720),
    FHD_1080("1080p", 1080)
}

data class StreamQuality(
    val resolution: ResolutionOption = ResolutionOption.HD_720,
    val bitrateKbps: Int = DEFAULT_BITRATE_KBPS,
    val fps: Int = DEFAULT_FPS
) {
    init {
        require(bitrateKbps in MIN_BITRATE_KBPS..MAX_BITRATE_KBPS) { "bitrate out of range: $bitrateKbps" }
        require(fps in MIN_FPS..MAX_FPS) { "fps out of range: $fps" }
    }

    companion object {
        const val DEFAULT_BITRATE_KBPS = 4_000
        const val DEFAULT_FPS = 30
        const val MIN_BITRATE_KBPS = 500
        const val MAX_BITRATE_KBPS = 20_000
        const val MIN_FPS = 10
        const val MAX_FPS = 60
        val BITRATE_OPTIONS_KBPS = listOf(1_000, 2_000, 4_000, 8_000, 12_000)
        val FPS_OPTIONS = listOf(15, 30, 60)
    }
}

data class CaptureSize(val width: Int, val height: Int)

object CaptureSizeCalculator {
    /** H.264 hardware encoders are most reliable with 16-pixel aligned dimensions. */
    private const val ALIGNMENT = 16

    /**
     * Scales the display so its short side matches [target] (never upscaling),
     * keeping aspect ratio and aligning both sides down to [ALIGNMENT].
     */
    fun compute(displayWidth: Int, displayHeight: Int, target: ResolutionOption): CaptureSize {
        require(displayWidth > 0 && displayHeight > 0) { "Invalid display size ${displayWidth}x$displayHeight" }
        val shortSide = min(displayWidth, displayHeight)
        val scale = min(1.0, target.shortSide.toDouble() / shortSide)
        return CaptureSize(
            width = align((displayWidth * scale).roundToInt()),
            height = align((displayHeight * scale).roundToInt())
        )
    }

    private fun align(value: Int): Int = max(ALIGNMENT, value - value % ALIGNMENT)
}
