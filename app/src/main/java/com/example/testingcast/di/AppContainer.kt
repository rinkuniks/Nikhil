package com.example.testingcast.di

import android.content.Context
import com.example.testingcast.capture.CaptureServiceController
import com.example.testingcast.capture.CaptureStatus
import com.example.testingcast.capture.ScreenCaptureServiceController
import com.example.testingcast.network.repository.NetworkRepository
import com.example.testingcast.network.repository.NetworkRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual DI: process-wide singletons shared by the UI and the capture service. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val networkRepository: NetworkRepository by lazy { NetworkRepositoryImpl(appContext) }

    /** Written by the capture service, observed by the sender UI. */
    val captureStatus = MutableStateFlow<CaptureStatus>(CaptureStatus.Idle)

    val captureController: CaptureServiceController by lazy {
        ScreenCaptureServiceController(appContext)
    }
}
