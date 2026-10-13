package io.github.otoniel2004.mangareaderjp.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
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
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.otoniel2004.mangareaderjp.MainActivity
import io.github.otoniel2004.mangareaderjp.MangaReaderApp
import io.github.otoniel2004.mangareaderjp.R
import io.github.otoniel2004.mangareaderjp.data.ReaderSettings
import io.github.otoniel2004.mangareaderjp.reader.ocr.JapaneseOcr
import io.github.otoniel2004.mangareaderjp.reader.ocr.OcrDebug
import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.SavedItem
import io.github.otoniel2004.mangareaderjp.reader.ocr.Box
import io.github.otoniel2004.mangareaderjp.reader.overlay.PageOverlay
import io.github.otoniel2004.mangareaderjp.reader.overlay.PopupOverlay
import io.github.otoniel2004.mangareaderjp.reader.overlay.ReaderPopup
import io.github.otoniel2004.mangareaderjp.reader.overlay.TouchTargets
import io.github.otoniel2004.mangareaderjp.reader.page.AnalyzedBlock
import io.github.otoniel2004.mangareaderjp.reader.page.PageAnalyzer
import io.github.otoniel2004.mangareaderjp.reader.page.PageWord
import io.github.otoniel2004.mangareaderjp.srs.Srs
import io.github.otoniel2004.mangareaderjp.translation.BubbleTranslations
import io.github.otoniel2004.mangareaderjp.translation.TranslationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.github.otoniel2004.mangareaderjp.translation.TranslationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import android.widget.Toast
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import io.github.otoniel2004.mangareaderjp.reader.ocr.OcrFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Background screen reader (a Foreground Service).
 *
 *   start(resultCode, data)            ← consent from the system "share your screen" dialog
 *     └─ persistent notification       (Android requires it for screen capture)
 *     └─ MediaProjection → VirtualDisplay → ImageReader   (a live copy of the screen)
 *     └─ every POLL_MILLIS on the worker thread:
 *          latest frame → signature → PageChangeDetector
 *             MOVING    → clear our drawings (they'd be wrong on a moving page)
 *             NEW_PAGE  → OCR on the OCR thread → filter → boxes + status
 *             SAME_PAGE → restore the drawings of the current page
 *
 * Threads: the worker thread owns all capture/page state; OCR runs on its own thread
 * and hands results back to the worker. A "generation" number discards OCR results
 * for a page that is no longer on screen.
 */
class ScreenReaderService : Service() {

    companion object {
        private const val TAG = "ScreenReader"
        private const val ACTION_START = "io.github.otoniel2004.mangareaderjp.reader.START"
        private const val ACTION_STOP = "io.github.otoniel2004.mangareaderjp.reader.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        private const val CHANNEL_ID = "reader"
        private const val NOTIFICATION_ID = 1
        private const val RESUME_NOTIFICATION_ID = 2
        private const val MAX_CAPTURE_WIDTH = 1080 // OCR needs real resolution
        private const val POLL_MILLIS = 250L
        private const val MASK_MARGIN = 8
        private const val TOUCH_MARGIN = 8
        private const val CROP_MARGIN = 16
        private const val MAX_REMEMBERED = 300

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
    private val ocrExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var ocr: JapaneseOcr? = null

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var statusOverlay: StatusOverlay? = null
    private var pageOverlay: PageOverlay? = null
    private var touchTargets: TouchTargets? = null
    private var popup: PopupOverlay? = null
    private var settings = ReaderSettings()
    private val container get() = (application as MangaReaderApp).container
    private val pageAnalyzer by lazy { PageAnalyzer(container.analyzer, container.dictionary) }
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** English for each bubble text on recent pages, watched by the open popup. */
    private val translations = MutableStateFlow<Map<String, BubbleTranslations>>(emptyMap())
    /** On-device translation runs one text at a time, apart from the DeepL calls. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val offlineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    @Volatile private var previousPageText: String? = null

    @Volatile private var stopRequested = false // the user turned the reader off in our app
    @Volatile private var shuttingDown = false

    /** The analyzed page on screen; read by the touch handlers on the main thread. */
    @Volatile private var currentPage: PageState? = null

    private class PageState(val blocks: List<AnalyzedBlock>, val bitmap: Bitmap)

    // ── Worker-thread state ──────────────────────────────
    private val detector = PageChangeDetector()
    private var heldImage: Image? = null          // newest frame, kept open so OCR can use it
    private var lastSignature: IntArray? = null
    private var ignoredCells: BooleanArray? = null // where our drawings are, hidden from the detector
    private var paused = false                    // a popup is open: our own window is on screen
    private var generation = 0
    private var pages = 0
    private var captureWidth = 0
    private var captureHeight = 0
    private var captureToScreen = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested = true
                stopSelf()
            }
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
            else -> stopSelf() // a restart without consent data
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
        settings = readerSettings()
        ocr = JapaneseOcr()

        val thread = HandlerThread("screen-reader").apply { start() }
        val handler = Handler(thread.looper)
        workerThread = thread
        worker = handler

        // Android 14+: the callback must be registered before creating the virtual display.
        // onStop fires when the system ends sharing: the screen turned off (Android 15+),
        // the user tapped "stop sharing" in the status bar, or the permission was revoked.
        // Capture can't be restarted without new consent, so we offer a one-tap resume.
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (stopRequested || shuttingDown) return
                main.post {
                    showResumeNotification()
                    stopSelf()
                }
            }
        }, handler)

        val (width, height, dpi) = captureSize()
        captureWidth = width
        captureHeight = height
        // maxImages = 3: one held for OCR + room for acquireLatestImage to swap frames.
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        imageReader = reader
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "manga-reader-capture", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler,
        )

        if (Settings.canDrawOverlays(this)) {
            pageOverlay = PageOverlay(this).also { it.show() }
            touchTargets = TouchTargets(this, tapListener)
            popup = PopupOverlay(this) { worker?.post(::resumeAfterPopup) }
            if (settings.showStatusDot || settings.keepScreenOn) {
                statusOverlay = StatusOverlay(this, settings.showStatusDot, settings.keepScreenOn).also {
                    it.show()
                    it.setState(StatusOverlay.State.Waiting)
                }
            }
        }
        getSystemService(NotificationManager::class.java).cancel(RESUME_NOTIFICATION_ID)
        ReaderStatus.update { ReaderStatus.State(running = true) }
        handler.post(poll)
    }

    /** Runs on the worker thread every POLL_MILLIS. */
    private val poll = object : Runnable {
        override fun run() {
            val reader = imageReader ?: return
            // acquireLatestImage drops older frames; null means the screen hasn't changed.
            reader.acquireLatestImage()?.let { image ->
                heldImage?.close()
                heldImage = image
                lastSignature = FrameSampler.signature(image)
            }
            if (!paused) {
                lastSignature?.let { onEvent(detector.onFrame(it, SystemClock.uptimeMillis(), ignoredCells), it) }
            }
            worker?.postDelayed(this, POLL_MILLIS)
        }
    }

    private fun onEvent(event: PageChangeDetector.Event, signature: IntArray) {
        when (event) {
            PageChangeDetector.Event.MOVING -> {
                generation++
                clearDrawings()
                statusOverlay?.setState(StatusOverlay.State.Working)
            }
            PageChangeDetector.Event.NEW_PAGE -> {
                generation++
                currentPage = null
                if (PageChangeDetector.looksBlack(signature)) {
                    ReaderStatus.update { it.copy(captureBlocked = true) }
                    statusOverlay?.setState(StatusOverlay.State.Blocked)
                    notifyStatus()
                } else {
                    pages++
                    runOcr()
                }
            }
            PageChangeDetector.Event.SAME_PAGE -> {
                val page = currentPage
                if (page == null) runOcr() else {
                    showDrawings(page)
                    statusOverlay?.setState(StatusOverlay.State.Ready)
                }
            }
            PageChangeDetector.Event.NONE -> Unit
        }
    }

    private fun runOcr() {
        val image = heldImage ?: return
        val engine = ocr ?: return
        val bitmap = image.toBitmap()
        val requested = generation
        statusOverlay?.setState(StatusOverlay.State.Working)
        ocrExecutor.execute {
            val started = SystemClock.uptimeMillis()
            // OCR, then everything a tap needs (words, dictionary entries, furigana positions),
            // computed now so that taps answer instantly.
            val blocks = try {
                val raw = engine.recognize(bitmap)
                val cleaned = OcrFilter.clean(raw).also { OcrDebug.record(this, "screen", bitmap, raw, it) }
                pageAnalyzer.analyze(cleaned)
            } catch (e: Exception) {
                Log.w(TAG, "OCR failed", e)
                emptyList()
            }
            val elapsed = SystemClock.uptimeMillis() - started
            worker?.post {
                if (requested != generation) return@post // the page changed meanwhile
                val page = PageState(blocks, bitmap) // the bitmap stays for cropping saved cards
                currentPage = page
                showDrawings(page)
                ReaderStatus.update {
                    it.copy(
                        pagesDetected = pages, captureBlocked = false,
                        lastPageText = blocks.map { b -> b.block.text }, lastOcrMillis = elapsed,
                    )
                }
                statusOverlay?.setState(StatusOverlay.State.NewPage(pages, blocks.size))
                if (settings.autoTranslate) translatePage(blocks.map { it.block.text })
                notifyStatus()
            }
        }
    }

    /**
     * Furigana, debug boxes and tap targets for the page. The detector mask is set first,
     * so it never sees our own drawings appear and mistake them for a page change.
     */
    private fun showDrawings(page: PageState) {
        val rubies = if (settings.showFuriganaOnPage) page.blocks.flatMap { it.rubies } else emptyList()
        val blockBoxes = page.blocks.map { it.block.box }
        ignoredCells = FrameSampler.cellMask(blockBoxes + rubies.map { it.box }, captureWidth, captureHeight, MASK_MARGIN)
        pageOverlay?.setContent(
            rubies.map { it.copy(box = it.box.scaled(captureToScreen)) },
            if (settings.showOcrBoxes) blockBoxes.map { it.scaled(captureToScreen) } else emptyList(),
        )
        touchTargets?.setTargets(blockBoxes.map { it.expandedBy(TOUCH_MARGIN).scaled(captureToScreen) })
    }

    private fun clearDrawings() {
        pageOverlay?.clear()
        touchTargets?.clear()
        ignoredCells = null
    }

    // ── Taps (main thread) ───────────────────────────────

    private val tapListener = object : TouchTargets.Listener {
        override fun onTap(block: Int, screenX: Int, screenY: Int) {
            val analyzed = currentPage?.blocks?.getOrNull(block) ?: return
            val word = analyzed.wordAt((screenX / captureToScreen).toInt(), (screenY / captureToScreen).toInt()) ?: return
            showPopup { close ->
                ReaderPopup(
                    word, analyzed.segments, analyzed.block.text, translations, isLowerHalf(screenY),
                    onSaveWord = { saveWord(analyzed, it) },
                    onSaveSentence = { saveSentence(analyzed) },
                    onNeedTranslation = { ensureTranslation(analyzed.block.text) },
                    onClose = close,
                )
            }
        }

        override fun onDoubleTap(block: Int, screenX: Int, screenY: Int) {
            val analyzed = currentPage?.blocks?.getOrNull(block) ?: return
            showPopup { close ->
                ReaderPopup(
                    null, analyzed.segments, analyzed.block.text, translations, isLowerHalf(screenY),
                    onSaveWord = { saveWord(analyzed, it) },
                    onSaveSentence = { saveSentence(analyzed) },
                    onNeedTranslation = { ensureTranslation(analyzed.block.text) },
                    onClose = close,
                )
            }
        }
    }

    private fun isLowerHalf(screenY: Int) = screenY > resources.displayMetrics.heightPixels / 2

    /** Popups cover the screen: pause page detection until they close. */
    private fun showPopup(content: @Composable (close: () -> Unit) -> Unit) {
        val window = popup ?: return
        worker?.post { paused = true }
        window.show(content)
    }

    private fun resumeAfterPopup() {
        paused = false
        detector.forgetPrevious()
    }

    private fun saveWord(analyzed: AnalyzedBlock, word: PageWord) = save(
        SavedItem(
            type = word.type,
            text = word.text,
            segments = word.segments,
            meanings = word.entry?.meanings().orEmpty(),
            contextSegments = analyzed.segments,
            source = "Reader",
            createdAt = System.currentTimeMillis(),
            srs = Srs.newCard(System.currentTimeMillis()),
        ),
        analyzed,
    )

    private fun saveSentence(analyzed: AnalyzedBlock) = save(
        SavedItem(
            type = ItemType.SENTENCE,
            text = analyzed.block.text,
            segments = analyzed.segments,
            translation = translations.value[analyzed.block.text]?.best,
            source = "Reader",
            createdAt = System.currentTimeMillis(),
            srs = Srs.newCard(System.currentTimeMillis()),
        ),
        analyzed,
    )

    // ── Translation ──────────────────────────────────────

    /**
     * Translates every bubble of a new page in one DeepL request, with the previous page as
     * context (better pronouns and tone, not billed). Cached texts cost nothing.
     */
    private fun translatePage(texts: List<String>) {
        val context = previousPageText
        previousPageText = texts.joinToString("\n")
        requestTranslation(texts, context)
    }

    /**
     * Called when a popup shows the whole sentence. With automatic translation off, this is
     * the only time quota is spent: when you actually ask for the sentence. Failed attempts
     * (no connection…) are retried.
     */
    private fun ensureTranslation(text: String) {
        val current = translations.value[text]
        val offlineStarted = current?.offline.isStartedOrDone()
        val deepLStarted = !container.translations.isConfigured || current?.deepL.isStartedOrDone()
        if (offlineStarted && deepLStarted) return
        requestTranslation(listOf(text), previousPageText)
    }

    /**
     * Two translations per bubble, each filling its half as soon as it's ready:
     *   on-device (ML Kit) – always, one text at a time on its own thread
     *   DeepL              – only with a key; failures are kept quiet, the on-device one covers them
     */
    private fun requestTranslation(texts: List<String>, context: String?) {
        val offlinePending = texts.filter { !translations.value[it]?.offline.isStartedOrDone() }
        val deepLPending = if (!container.translations.isConfigured) emptyList()
        else texts.filter { !translations.value[it]?.deepL.isStartedOrDone() }

        if (offlinePending.isNotEmpty()) {
            updateBubbles(offlinePending) { it.copy(offline = TranslationState.Loading) }
            offlineScope.launch {
                for (text in offlinePending) {
                    val state = try {
                        TranslationState.Done(container.offlineTranslator.translate(text))
                    } catch (e: Exception) {
                        Log.w(TAG, "On-device translation failed", e)
                        TranslationState.Failed(e.message.orEmpty())
                    }
                    updateBubbles(listOf(text)) { it.copy(offline = state) }
                }
            }
        }
        if (deepLPending.isNotEmpty()) {
            updateBubbles(deepLPending) { it.copy(deepL = TranslationState.Loading) }
            saveScope.launch {
                val results: Map<String, TranslationState> = try {
                    val english = container.translations.translate(deepLPending, context)
                    deepLPending.associateWith { text -> english[text]?.let { TranslationState.Done(it) } ?: TranslationState.Failed("") }
                } catch (e: TranslationException) {
                    Log.w(TAG, "DeepL translation failed: ${e.message}")
                    deepLPending.associateWith { TranslationState.Failed(e.message.orEmpty()) }
                }
                results.forEach { (text, state) -> updateBubbles(listOf(text)) { it.copy(deepL = state) } }
            }
        }
    }

    private fun TranslationState?.isStartedOrDone() = this is TranslationState.Done || this == TranslationState.Loading

    /** Updates some bubbles' translations; keeps only the latest ones (all a popup can need). */
    private fun updateBubbles(texts: List<String>, change: (BubbleTranslations) -> BubbleTranslations) {
        translations.update { map ->
            val updated = map + texts.associateWith { change(map[it] ?: BubbleTranslations()) }
            if (updated.size <= MAX_REMEMBERED) updated
            else updated.entries.toList().takeLast(MAX_REMEMBERED).associate { it.key to it.value }
        }
    }

    /** Saves silently (the user stays in the manga) with a crop of the bubble. */
    private fun save(item: SavedItem, analyzed: AnalyzedBlock) {
        val bitmap = currentPage?.bitmap
        saveScope.launch {
            val imagePath = bitmap?.let { cropBlock(it, analyzed.block.box) }
            container.items.insert(item.copy(imagePath = imagePath))
            main.post { Toast.makeText(this@ScreenReaderService, "Saved ✓", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun cropBlock(bitmap: Bitmap, box: Box): String? = runCatching {
        val area = box.expandedBy(CROP_MARGIN)
        val left = area.left.coerceIn(0, bitmap.width - 1)
        val top = area.top.coerceIn(0, bitmap.height - 1)
        val crop = Bitmap.createBitmap(
            bitmap, left, top,
            area.right.coerceIn(left + 1, bitmap.width) - left,
            area.bottom.coerceIn(top + 1, bitmap.height) - top,
        )
        val file = File(container.items.imagesDir(), "reader-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.path
    }.getOrNull()

    override fun onDestroy() {
        shuttingDown = true
        statusOverlay?.hide()
        pageOverlay?.hide()
        touchTargets?.clear()
        popup?.close()
        statusOverlay = null
        pageOverlay = null
        touchTargets = null
        popup = null
        currentPage = null
        val handler = worker
        val thread = workerThread
        if (handler != null && thread != null) {
            handler.removeCallbacks(poll)
            // Release capture objects on the worker thread so a running poll never sees them half-closed.
            handler.post {
                heldImage?.close()
                heldImage = null
                virtualDisplay?.release()
                imageReader?.close()
                projection?.stop()
                virtualDisplay = null
                imageReader = null
                projection = null
            }
            thread.quitSafely()
        }
        ocrExecutor.execute { ocr?.close() }
        ocrExecutor.shutdown()
        ReaderStatus.update { ReaderStatus.State() }
        super.onDestroy()
    }

    /** Capture size: real screen size, capped at MAX_CAPTURE_WIDTH, same aspect ratio. */
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
        val scale = (MAX_CAPTURE_WIDTH.toFloat() / realWidth).coerceAtMost(1f)
        captureToScreen = 1f / scale
        val dpi = (resources.displayMetrics.densityDpi * scale).roundToInt().coerceAtLeast(1)
        return Triple((realWidth * scale).roundToInt(), (realHeight * scale).roundToInt(), dpi)
    }

    /** Copies a captured frame into a Bitmap. Rows may have padding at the end (rowStride > width × 4). */
    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer.apply { rewind() }
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val padded = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        if (rowPadding == 0) return padded
        return Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
    }

    private fun readerSettings(): ReaderSettings = runBlocking {
        (application as MangaReaderApp).container.settings.readerSettings.first()
    }

    private fun notifyStatus() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun showResumeNotification() {
        val resume = PendingIntent.getActivity(
            this, 2,
            Intent(this, ResumeReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reader paused")
            .setContentText("Screen sharing ended (e.g. the screen turned off). Tap to resume.")
            .setContentIntent(resume)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(RESUME_NOTIFICATION_ID, notification)
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
        val state = ReaderStatus.state.value
        val text = when {
            state.captureBlocked -> "The app on screen blocks capture"
            pages == 0 -> "Waiting for a page…"
            else -> "Page $pages · ${state.lastPageText.size} text blocks"
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
