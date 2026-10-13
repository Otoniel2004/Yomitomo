package io.github.otoniel2004.mangareaderjp.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.otoniel2004.mangareaderjp.MainActivity
import io.github.otoniel2004.mangareaderjp.MangaReaderApp
import io.github.otoniel2004.mangareaderjp.R
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Background screen reader (a Foreground Service).
 *
 *   start(resultCode, data)            ← consent from the system "share your screen" dialog
 *     └─ persistent notification       (Android requires it for screen capture)
 *     └─ MediaProjection → VirtualDisplay → ImageReader   (a downscaled live copy of the screen)
 *     └─ every POLL_MILLIS on a worker thread:
 *          latest frame → signature → PageChangeDetector → status dot + notification
 *
 * Phase 2 only detects page changes. OCR and translation plug in here in the next phases.
 */
class ScreenReaderService : Service() {

    companion object {
        private const val ACTION_START = "io.github.otoniel2004.mangareaderjp.reader.START"
        private const val ACTION_STOP = "io.github.otoniel2004.mangareaderjp.reader.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "reader"
        private const val NOTIFICATION_ID = 1
        private const val CAPTURE_WIDTH = 360
        private const val POLL_MILLIS = 250L

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenReaderService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenReaderService::class.java).setAction(ACTION_STOP))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var overlay: StatusOverlay? = null

    private val detector = PageChangeDetector()
    private var lastSignature: IntArray? = null
    private var pages = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                if (data == null) {
                    stopSelf()
                } else if (projection == null) {
                    // Must be in the foreground BEFORE asking for the MediaProjection (Android 10+ rule).
                    createChannel()
                    ServiceCompat.startForeground(
                        this, NOTIFICATION_ID, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                    )
                    startCapture(code, data)
                }
            }
            else -> stopSelf() // ACTION_STOP, or a restart without consent data
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = manager.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            stopSelf()
            return
        }
        projection = mediaProjection

        val thread = HandlerThread("screen-reader").apply { start() }
        val handler = Handler(thread.looper)
        workerThread = thread
        worker = handler

        // Android 14+: the callback must be registered before creating the virtual display.
        // onStop fires when the user ends sharing from the status bar or the system revokes it.
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopSelf() }
            }
        }, handler)

        val (width, height, dpi) = captureSize()
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "manga-reader-capture", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler,
        )

        if (Settings.canDrawOverlays(this) && statusDotEnabled()) {
            overlay = StatusOverlay(this).also {
                it.show()
                it.setState(StatusOverlay.State.Waiting)
            }
        }
        ReaderStatus.update { ReaderStatus.State(running = true) }
        handler.post(poll)
    }

    /** Runs on the worker thread every POLL_MILLIS. */
    private val poll = object : Runnable {
        override fun run() {
            val reader = imageReader ?: return
            // acquireLatestImage drops older frames; null means the screen hasn't changed.
            reader.acquireLatestImage()?.use { image -> lastSignature = FrameSampler.signature(image) }
            lastSignature?.let { onEvent(detector.onFrame(it, SystemClock.uptimeMillis()), it) }
            worker?.postDelayed(this, POLL_MILLIS)
        }
    }

    private fun onEvent(event: PageChangeDetector.Event, signature: IntArray) {
        when (event) {
            PageChangeDetector.Event.MOVING -> overlay?.setState(StatusOverlay.State.Working)
            PageChangeDetector.Event.NEW_PAGE -> {
                if (PageChangeDetector.looksBlack(signature)) {
                    ReaderStatus.update { it.copy(captureBlocked = true) }
                    overlay?.setState(StatusOverlay.State.Blocked)
                } else {
                    pages++
                    ReaderStatus.update { it.copy(pagesDetected = pages, captureBlocked = false) }
                    overlay?.setState(StatusOverlay.State.NewPage(pages))
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
            }
            PageChangeDetector.Event.NONE -> Unit
        }
    }

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        val handler = worker
        val thread = workerThread
        if (handler != null && thread != null) {
            handler.removeCallbacks(poll)
            // Release capture objects on the worker thread so a running poll never sees them half-closed.
            handler.post {
                virtualDisplay?.release()
                imageReader?.close()
                projection?.stop()
                virtualDisplay = null
                imageReader = null
                projection = null
            }
            thread.quitSafely()
        }
        ReaderStatus.update { ReaderStatus.State() }
        super.onDestroy()
    }

    /** Downscaled capture size keeping the screen's aspect ratio (Phase 3 will raise it for OCR). */
    private fun captureSize(): Triple<Int, Int, Int> {
        val windowManager = getSystemService(WindowManager::class.java)
        val (realWidth, realHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
        val scale = (CAPTURE_WIDTH.toFloat() / realWidth).coerceAtMost(1f)
        val dpi = (resources.displayMetrics.densityDpi * scale).roundToInt().coerceAtLeast(1)
        return Triple((realWidth * scale).roundToInt(), (realHeight * scale).roundToInt(), dpi)
    }

    private fun statusDotEnabled(): Boolean = runBlocking {
        (application as MangaReaderApp).container.settings.readerSettings.first().showStatusDot
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Screen reader", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Manga Reader JP is reading your screen"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScreenReaderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            ReaderStatus.state.value.captureBlocked -> "The app on screen blocks capture"
            pages == 0 -> "Waiting for a page…"
            else -> "Pages detected: $pages"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reading screen")
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
