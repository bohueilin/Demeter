package com.demeter.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One recognized line of text with its on-screen bounding box, used to reconstruct rows/columns. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    val height: Float get() = (bottom - top).toFloat()
}

/**
 * On-device text OCR for a user-provided screenshot of their own usage page.
 *
 * Uses the BUNDLED ML Kit Latin recognizer, so the model lives inside the APK: recognition
 * runs entirely on the device with no network call and no Play Services model download,
 * keeping the app's zero-network, no-leak posture intact. The image is read once to extract
 * text and is never copied, stored, or transmitted by Demeter.
 *
 * We decode the image ourselves into a SOFTWARE ARGB_8888 bitmap before handing it to ML Kit.
 * This matters: modern Android (ImageDecoder / InputImage.fromFilePath) often produces a
 * HARDWARE bitmap, which ML Kit cannot read — the usual cause of "the image could not be read"
 * on real device screenshots. Forcing a software allocation (and downscaling very large
 * screenshots) makes recognition work reliably across formats and image sizes.
 */
object OcrReader {

    /** Downscale the longest edge of huge screenshots — keeps memory sane and OCR fast. */
    private const val MAX_DIM = 2200

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Flat recognized text (reading order). */
    suspend fun readText(context: Context, uri: Uri): String = recognize(context, uri).text

    /** Recognized lines with positions — lets a parser pair a row's label with its value column. */
    suspend fun readLines(context: Context, uri: Uri): List<OcrLine> =
        recognize(context, uri).textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                line.boundingBox?.let { b -> OcrLine(line.text, b.left, b.top, b.right, b.bottom) }
            }

    private suspend fun recognize(context: Context, uri: Uri): Text {
        val bitmap = loadSoftwareBitmap(context, uri)
            ?: throw IllegalStateException("Could not decode image at $uri")
        return suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    /** Decode [uri] to a software (non-HARDWARE) bitmap, downscaling if very large. */
    private fun loadSoftwareBitmap(context: Context, uri: Uri): Bitmap? {
        // Primary path: ImageDecoder handles PNG/JPEG/WebP/HEIF uniformly. Force a software
        // allocation so ML Kit can read the pixels.
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (longest > MAX_DIM) {
                    val scale = MAX_DIM.toFloat() / longest
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }

        // Fallback: plain stream decode for odd content providers or formats.
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }.getOrNull()
    }
}
