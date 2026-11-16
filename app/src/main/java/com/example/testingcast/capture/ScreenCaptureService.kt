package com.example.testingcast.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.testingcast.R
import com.example.testingcast.appContainer
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.protocol.ControlCommands
import com.example.testingcast.network.protocol.MediaProtocol
import com.example.testingcast.network.protocol.StreamPacket
import com.example.testingcast.network.protocol.VideoFormatInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service owning the MediaProjection session: VirtualDisplay -> H.264 encoder
 * (+ optional AAC playback capture) -> socket via [com.example.testingcast.network.repository.NetworkRepository].
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val ACTION_START = "com.example.testingcast.action.START_CAPTURE"
        private const val ACTION_STOP = "com.example.testingcast.action.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val EXTRA_RESOLUTION = "extra_resolution"
        private const val EXTRA_BITRATE = "extra_bitrate"
        private const val EXTRA_FPS = "extra_fps"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 1001
        private const val VIRTUAL_DISPLAY_NAME = "TestingCast"

        fun startIntent(context: Context, resultCode: Int, data: Intent, quality: StreamQuality): Intent =
            Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
                .putExtra(EXTRA_RESOLUTION, quality.resolution.name)
                .putExtra(EXTRA_BITRATE, quality.bitrateKbps)
                .putExtra(EXTRA_FPS, quality.fps)

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = appContainer.networkRepository
    private val captureStatus get() = appContainer.captureStatus

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    private var sessionJob: Job? = null
    private var isStopping = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system/user")
            stopCapture()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> stopCapture()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        // Must be foreground (mediaProjection type) before obtaining the projection on API 29+.
        startInForeground()
        if (projection != null) return

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        val quality = qualityFrom(intent)
        if (resultCode != Activity.RESULT_OK || data == null || quality == null) {
            stopCapture(CaptureStatus.Failed(getString(R.string.error_capture_permission)))
            return
        }

        captureStatus.value = CaptureStatus.Starting
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        if (mediaProjection == null) {
            stopCapture(CaptureStatus.Failed(getString(R.string.error_capture_permission)))
            return
        }
        // API 34+: callback must be registered before creating the VirtualDisplay.
        mediaProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = mediaProjection
        sessionJob = scope.launch { runSession(mediaProjection, quality) }
    }

    private suspend fun runSession(mediaProjection: MediaProjection, quality: StreamQuality) {
        try {
            if (!repository.startStreaming()) {
                stopCapture(CaptureStatus.Failed(getString(R.string.error_not_connected)))
                return
            }
            val (displayWidth, displayHeight) = displaySize()
            val size = CaptureSizeCalculator.compute(displayWidth, displayHeight, quality.resolution)
            val encoder = VideoEncoder(size, quality) { packet -> repository.sendPacket(packet) }
            videoEncoder = encoder
            val surface = withContext(Dispatchers.IO) { encoder.prepare() }
            virtualDisplay = mediaProjection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME, size.width, size.height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null
            )
            repository.sendPacket(MediaProtocol.videoFormatPacket(VideoFormatInfo(size.width, size.height)))
            encoder.startDraining(scope)
            val audioEnabled = startAudio(mediaProjection)
            captureStatus.value = CaptureStatus.Active(size.width, size.height, audioEnabled)
            scope.launch { listenForKeyFrameRequests() }

            repository.connectionState.first { it is ConnectionState.Disconnected || it is ConnectionState.Error }
            Log.d(TAG, "Receiver connection lost; stopping capture")
            stopCapture(CaptureStatus.Failed(getString(R.string.error_connection_lost)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Capture session failed", e)
            stopCapture(CaptureStatus.Failed(getString(R.string.error_capture_failed, e.message ?: "")))
        }
    }

    @SuppressLint("MissingPermission") // checked explicitly below
    private suspend fun startAudio(mediaProjection: MediaProjection): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        repository.sendPacket(MediaProtocol.audioFormatPacket(AudioEncoder.FORMAT))
        val encoder = AudioEncoder(mediaProjection) { packet -> repository.sendPacket(packet) }
        val started = encoder.start(scope)
        audioEncoder = encoder.takeIf { started }
        return started
    }

    private suspend fun listenForKeyFrameRequests() {
        repository.incomingPackets.collect { packet ->
            val command = StreamPacket.parseControlCommand(packet)?.first
            if (command == ControlCommands.REQUEST_KEYFRAME) videoEncoder?.requestKeyFrame()
        }
    }

    private fun stopCapture(finalStatus: CaptureStatus = CaptureStatus.Idle) {
        if (isStopping) return
        isStopping = true
        scope.launch {
            sessionJob?.cancel()
            withContext(NonCancellable) { releaseCapture() }
            captureStatus.value = finalStatus
            ServiceCompat.stopForeground(this@ScreenCaptureService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun releaseCapture() {
        if (repository.connectionState.value is ConnectionState.Streaming) {
            runCatching { repository.stopStreaming() }
        }
        videoEncoder?.release()
        videoEncoder = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) audioEncoder?.release()
        audioEncoder = null
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.let { active ->
            active.unregisterCallback(projectionCallback)
            active.stop()
        }
        projection = null
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        projection?.stop()
        if (!isStopping) captureStatus.value = CaptureStatus.Idle
        scope.cancel()
        super.onDestroy()
    }

    private fun qualityFrom(intent: Intent): StreamQuality? = runCatching {
        StreamQuality(
            resolution = ResolutionOption.valueOf(intent.getStringExtra(EXTRA_RESOLUTION).orEmpty()),
            bitrateKbps = intent.getIntExtra(EXTRA_BITRATE, StreamQuality.DEFAULT_BITRATE_KBPS),
            fps = intent.getIntExtra(EXTRA_FPS, StreamQuality.DEFAULT_FPS)
        )
    }.getOrNull()

    private fun displaySize(): Pair<Int, Int> {
        val windowManager = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_capture), NotificationManager.IMPORTANCE_LOW)
        )
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cast_notification)
            .setContentTitle(getString(R.string.notification_capture_title))
            .setContentText(getString(R.string.notification_capture_text))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.action_stop_mirroring), stopPending)
            .build()
    }
}
