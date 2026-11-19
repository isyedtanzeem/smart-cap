package com.example.monitoring

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var captureWidth: Int = 720
    private var captureHeight: Int = 1600
    private var densityDpi: Int = DisplayMetrics.DENSITY_HIGH

    private val mainHandler = Handler(Looper.getMainLooper())

    val isCapturing: Boolean
        get() = mediaProjection != null && virtualDisplay != null

    fun startCapture(
        resultCode: Int,
        resultData: Intent,
        onExternalStop: () -> Unit
    ): Boolean {
        stopCapture()

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            ?: return false

        return try {
            val projection = projectionManager.getMediaProjection(resultCode, resultData) ?: return false
            resolveDisplayMetrics()

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system or user")
                    releaseVirtualDisplayOnly()
                    mediaProjection = null
                    projectionCallback = null
                    onExternalStop()
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    if (width > 0 && height > 0) {
                        val (scaledW, scaledH) = scaleDimensions(width, height)
                        if (scaledW != captureWidth || scaledH != captureHeight) {
                            captureWidth = scaledW
                            captureHeight = scaledH
                            recreateImageReaderAndSurface()
                        }
                    }
                }
            }

            projection.registerCallback(callback, mainHandler)
            mediaProjection = projection
            projectionCallback = callback

            createVirtualDisplayAndReader(projection)
            true
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException starting MediaProjection", se)
            stopCapture()
            false
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start MediaProjection", t)
            stopCapture()
            false
        }
    }

    fun captureLatestBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        val image = try {
            reader.acquireLatestImage()
        } catch (t: Throwable) {
            Log.w(TAG, "acquireLatestImage failed", t)
            null
        } ?: return null

        return try {
            val planes = image.planes
            if (planes.isEmpty()) return null
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + (rowPadding / pixelStride)

            val fullBitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            fullBitmap.copyPixelsFromBuffer(buffer)

            if (paddedWidth == image.width) {
                fullBitmap
            } else {
                val cropped = Bitmap.createBitmap(fullBitmap, 0, 0, image.width, image.height)
                fullBitmap.recycle()
                cropped
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error converting captured Image to Bitmap", t)
            null
        } finally {
            image.close()
        }
    }

    fun computeFrameFingerprint(bitmap: Bitmap): Long {
        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)
        var hash = 1125899906842597L
        var y = stepY / 2
        while (y < bitmap.height) {
            var x = stepX / 2
            while (x < bitmap.width) {
                val px = bitmap.getPixel(x, y)
                // Quantize slightly to ignore tiny clock/status bar flicker
                val quantized = (px and 0x00F0F0F0)
                hash = 31L * hash + quantized
                x += stepX
            }
            y += stepY
        }
        return hash
    }

    fun stopCapture() {
        releaseVirtualDisplayOnly()
        val projection = mediaProjection
        val callback = projectionCallback
        mediaProjection = null
        projectionCallback = null
        if (projection != null) {
            if (callback != null) {
                runCatching { projection.unregisterCallback(callback) }
            }
            runCatching { projection.stop() }
        }
    }

    private fun releaseVirtualDisplayOnly() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
    }

    private fun createVirtualDisplayAndReader(projection: MediaProjection) {
        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "SmartCaptainCapture",
            captureWidth,
            captureHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            mainHandler
        )
    }

    private fun recreateImageReaderAndSurface() {
        val display = virtualDisplay ?: return
        val oldReader = imageReader
        val newReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        imageReader = newReader
        display.resize(captureWidth, captureHeight, densityDpi)
        display.surface = newReader.surface
        runCatching { oldReader?.close() }
    }

    private fun resolveDisplayMetrics() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            val (w, h) = scaleDimensions(bounds.width(), bounds.height())
            captureWidth = w
            captureHeight = h
            densityDpi = context.resources.displayMetrics.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val (w, h) = scaleDimensions(metrics.widthPixels, metrics.heightPixels)
            captureWidth = w
            captureHeight = h
            densityDpi = metrics.densityDpi
        }
    }

    private fun scaleDimensions(rawWidth: Int, rawHeight: Int): Pair<Int, Int> {
        val safeW = rawWidth.coerceAtLeast(360)
        val safeH = rawHeight.coerceAtLeast(640)
        val maxCaptureWidth = 1080
        return if (safeW > maxCaptureWidth) {
            val ratio = maxCaptureWidth.toFloat() / safeW.toFloat()
            maxCaptureWidth to (safeH * ratio).roundToInt().coerceAtLeast(480)
        } else {
            safeW to safeH
        }
    }

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }
}
