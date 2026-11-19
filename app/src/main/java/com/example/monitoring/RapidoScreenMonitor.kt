package com.example.monitoring

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.SettingsDataStore
import com.example.engine.RecommendationManager
import com.example.ocr.OrderParser
import com.example.ocr.OrderTextRecognizer
import com.example.services.FloatingOverlayService
import com.example.services.ScreenCaptureForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MonitoringPhase {
    IDLE,
    ACTIVE,
    SCANNING,
    ERROR
}

data class MonitoringStatus(
    val phase: MonitoringPhase = MonitoringPhase.IDLE,
    val statusMessage: String = "Ready to monitor Rapido Captain orders",
    val lastScanEpochMs: Long? = null,
    val scansCompleted: Int = 0,
    val lastError: String? = null
)

class RapidoScreenMonitor(
    private val context: Context,
    private val screenCaptureManager: ScreenCaptureManager,
    private val textRecognizer: OrderTextRecognizer,
    private val orderParser: OrderParser,
    private val recommendationManager: RecommendationManager,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(MonitoringStatus())
    val status: StateFlow<MonitoringStatus> = _status.asStateFlow()

    private var scanLoopJob: Job? = null
    private var scanIntervalMs: Long = 2000L
    private var lastFrameFingerprint: Long? = null
    private var lastOcrSignature: String? = null

    init {
        scope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                scanIntervalMs = settings.ocrScanningIntervalMs.coerceIn(1000L, 10000L)
            }
        }
    }

    fun startMonitoringWithConsent(resultCode: Int, data: Intent) {
        _status.value = _status.value.copy(
            phase = MonitoringPhase.ACTIVE,
            statusMessage = "Starting screen capture service...",
            lastError = null
        )
        ScreenCaptureForegroundService.start(context, resultCode, data)
    }

    internal fun onForegroundServiceReady(resultCode: Int, data: Intent): Boolean {
        val started = screenCaptureManager.startCapture(
            resultCode = resultCode,
            resultData = data,
            onExternalStop = {
                stopLoopAndNotifyStopped("Screen capture stopped from system controls")
            }
        )
        if (!started) {
            _status.value = _status.value.copy(
                phase = MonitoringPhase.ERROR,
                statusMessage = "Screen capture failed to start",
                lastError = "Unable to initialize MediaProjection virtual display"
            )
            return false
        }

        lastFrameFingerprint = null
        lastOcrSignature = null
        _status.value = _status.value.copy(
            phase = MonitoringPhase.ACTIVE,
            statusMessage = "Live monitoring active — switch to Rapido Captain",
            lastError = null
        )
        launchScanLoop()
        return true
    }

    fun stopMonitoring() {
        ScreenCaptureForegroundService.stop(context)
        screenCaptureManager.stopCapture()
        FloatingOverlayService.hide(context)
        stopLoopAndNotifyStopped("Monitoring stopped")
    }

    fun triggerImmediateScan() {
        if (!screenCaptureManager.isCapturing) return
        scope.launch {
            performSingleScreenScan(force = true)
        }
    }

    suspend fun scanPickedScreenshot(uri: Uri) {
        _status.value = _status.value.copy(
            phase = MonitoringPhase.SCANNING,
            statusMessage = "Running local ML Kit OCR on screenshot...",
            lastError = null
        )
        try {
            val bitmap = decodeBitmapFromUri(uri)
            if (bitmap == null) {
                _status.value = _status.value.copy(
                    phase = if (screenCaptureManager.isCapturing) MonitoringPhase.ACTIVE else MonitoringPhase.IDLE,
                    statusMessage = "Could not decode selected screenshot",
                    lastError = "Unsupported image format"
                )
                return
            }
            processBitmapWithOcr(bitmap, recycleAfter = true, fromLiveCapture = false)
        } catch (t: Throwable) {
            Log.e(TAG, "Screenshot OCR failed", t)
            _status.value = _status.value.copy(
                phase = MonitoringPhase.ERROR,
                statusMessage = "Screenshot OCR failed",
                lastError = t.localizedMessage ?: "Unknown OCR error"
            )
        }
    }

    private fun launchScanLoop() {
        scanLoopJob?.cancel()
        scanLoopJob = scope.launch {
            // Brief initial delay so user can switch back to Rapido
            delay(800L)
            while (isActive && screenCaptureManager.isCapturing) {
                performSingleScreenScan(force = false)
                delay(scanIntervalMs)
            }
        }
    }

    private suspend fun performSingleScreenScan(force: Boolean) {
        val bitmap = screenCaptureManager.captureLatestBitmap() ?: return
        try {
            val fingerprint = screenCaptureManager.computeFrameFingerprint(bitmap)
            if (!force && lastFrameFingerprint == fingerprint) {
                return
            }
            lastFrameFingerprint = fingerprint
            processBitmapWithOcr(bitmap, recycleAfter = true, fromLiveCapture = true)
        } catch (t: Throwable) {
            Log.e(TAG, "Live scan error", t)
            if (!bitmap.isRecycled) bitmap.recycle()
            _status.value = _status.value.copy(
                phase = MonitoringPhase.ACTIVE,
                lastError = t.localizedMessage
            )
        }
    }

    private suspend fun processBitmapWithOcr(
        bitmap: Bitmap,
        recycleAfter: Boolean,
        fromLiveCapture: Boolean
    ) {
        val ocrResult = try {
            textRecognizer.recognizeBitmap(bitmap)
        } finally {
            if (recycleAfter && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        ocrResult.onSuccess { doc ->
            val parsed = orderParser.parse(doc)
            val now = System.currentTimeMillis()
            val scanCount = _status.value.scansCompleted + 1

            if (parsed.orders.isNotEmpty() || parsed.selectorStubs.isNotEmpty()) {
                val changed = parsed.rawTextSignature != lastOcrSignature || !fromLiveCapture
                if (changed) {
                    lastOcrSignature = parsed.rawTextSignature
                    recommendationManager.onScreenParsed(parsed)
                }
                _status.value = _status.value.copy(
                    phase = if (fromLiveCapture) MonitoringPhase.ACTIVE else MonitoringPhase.IDLE,
                    statusMessage = "Detected ${parsed.orders.size} order card(s)" +
                        (parsed.detectedOrderCount?.let { " • Rapido shows $it Orders" } ?: ""),
                    lastScanEpochMs = now,
                    scansCompleted = scanCount,
                    lastError = null
                )
            } else {
                _status.value = _status.value.copy(
                    phase = if (fromLiveCapture) MonitoringPhase.ACTIVE else MonitoringPhase.IDLE,
                    statusMessage = if (fromLiveCapture) {
                        "Monitoring active — waiting for Rapido orders on screen"
                    } else {
                        "No Rapido order cards found in selected image"
                    },
                    lastScanEpochMs = now,
                    scansCompleted = scanCount,
                    lastError = null
                )
            }
        }.onFailure { err ->
            Log.e(TAG, "ML Kit OCR failed", err)
            _status.value = _status.value.copy(
                phase = if (fromLiveCapture) MonitoringPhase.ACTIVE else MonitoringPhase.ERROR,
                statusMessage = "OCR scan error — retrying",
                lastError = err.localizedMessage
            )
        }
    }

    private fun stopLoopAndNotifyStopped(message: String) {
        scanLoopJob?.cancel()
        scanLoopJob = null
        lastFrameFingerprint = null
        lastOcrSignature = null
        _status.value = _status.value.copy(
            phase = MonitoringPhase.IDLE,
            statusMessage = message
        )
    }

    @Suppress("DEPRECATION")
    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    companion object {
        private const val TAG = "RapidoScreenMonitor"
    }
}
