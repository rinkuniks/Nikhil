package com.example.testingcast.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

sealed interface CaptureStatus {
    data object Idle : CaptureStatus
    data object Starting : CaptureStatus
    data class Active(val width: Int, val height: Int, val audioEnabled: Boolean) : CaptureStatus
    data class Failed(val message: String) : CaptureStatus
}

/** Abstraction over starting/stopping the capture service so ViewModels stay testable. */
interface CaptureServiceController {
    fun start(resultCode: Int, data: Intent, quality: StreamQuality)
    fun stop()
}

class ScreenCaptureServiceController(private val context: Context) : CaptureServiceController {
    override fun start(resultCode: Int, data: Intent, quality: StreamQuality) {
        ContextCompat.startForegroundService(
            context,
            ScreenCaptureService.startIntent(context, resultCode, data, quality)
        )
    }

    override fun stop() {
        context.startService(ScreenCaptureService.stopIntent(context))
    }
}
