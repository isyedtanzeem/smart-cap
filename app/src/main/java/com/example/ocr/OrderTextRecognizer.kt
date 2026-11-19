package com.example.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

data class OcrLine(
    val text: String,
    val bounds: OcrBounds,
    val confidence: Float? = null
)

data class OcrDocument(
    val rawText: String,
    val lines: List<OcrLine>,
    val imageWidth: Int,
    val imageHeight: Int
)

class OrderTextRecognizer {
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeBitmap(bitmap: Bitmap): Result<OcrDocument> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = processImage(image)
            val lines = extractLines(visionText)
            Result.success(
                OcrDocument(
                    rawText = visionText.text,
                    lines = lines,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun processImage(image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }

    private fun extractLines(text: Text): List<OcrLine> {
        val result = mutableListOf<OcrLine>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val rect = line.boundingBox ?: continue
                val trimmed = line.text.trim()
                if (trimmed.isNotEmpty()) {
                    result += OcrLine(
                        text = trimmed,
                        bounds = OcrBounds(rect.left, rect.top, rect.right, rect.bottom),
                        confidence = line.confidence
                    )
                }
            }
        }
        return result.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    }

    fun close() {
        runCatching { recognizer.close() }
    }
}
