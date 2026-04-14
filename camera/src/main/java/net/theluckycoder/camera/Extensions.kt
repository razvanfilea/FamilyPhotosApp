package net.theluckycoder.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


private const val BITMAP_DATE_FORMAT = "dd.MM.yyyy"
private const val FILENAME_FORMAT = "yyyyMMdd_HHmmssSS"

private val TEXT_COLOR = "#43f1ed".toColorInt()

/**
 * Capture state for async capture pipeline
 */
internal sealed class CaptureState {
    data object Idle : CaptureState()
    data object Capturing : CaptureState()
    data object Processing : CaptureState()
    data class Saved(val uri: Uri) : CaptureState()
    data class Error(val exception: Exception) : CaptureState()
}

/**
 * Data class holding captured image data for background processing
 */
private data class CapturedImage(
    val bitmap: Bitmap,
    val rotationDegrees: Int
)

/**
 * Phase 1: Quick capture - captures image and returns immediately with bitmap data.
 * This is the fast part that happens when user taps the shutter.
 */
@OptIn(ExperimentalGetImage::class)
private suspend fun CameraController.captureImage(): CapturedImage {
    return suspendCoroutine { continuation ->
        val callback = object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toBitmap()
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    continuation.resume(CapturedImage(bitmap, rotationDegrees))
                } finally {
                    image.close()
                }
            }

            override fun onError(ex: ImageCaptureException) {
                Log.e("CaptureImage", "Image capture failed", ex)
                continuation.resumeWithException(ex)
            }
        }

        takePicture(Dispatchers.IO.asExecutor(), callback)
    }
}

/**
 * Phase 2: Process and save - runs in background after capture.
 * Rotates, adds date overlay, compresses, and saves to MediaStore.
 */
private suspend fun processAndSave(context: Context, capturedImage: CapturedImage): Uri {
    return withContext(Dispatchers.Default) {
        val originalBitmap = capturedImage.bitmap
        val rotationDegrees = capturedImage.rotationDegrees

        // Rotate if needed, and ensure bitmap is mutable for drawing
        val rotatedBitmap = if (rotationDegrees == 0) {
            // Need mutable copy for Canvas drawing
            originalBitmap.copy(Bitmap.Config.ARGB_8888, true).also {
                originalBitmap.recycle()
            }
        } else {
            val rotationMatrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            val rotated = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                rotationMatrix,
                false
            )
            originalBitmap.recycle()
            rotated
        }

        // Add date overlay
        val formattedDate = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern(BITMAP_DATE_FORMAT)
        )

        Canvas(rotatedBitmap).apply {
            val tPaint = Paint().apply {
                textSize = 0.05f * rotatedBitmap.width
                color = TEXT_COLOR
                style = Paint.Style.FILL
            }
            val textWidth = tPaint.measureText(formattedDate)
            drawText(
                formattedDate,
                rotatedBitmap.width - textWidth - 0.02f * rotatedBitmap.width,
                rotatedBitmap.height - 0.02f * rotatedBitmap.height,
                tPaint
            )
        }

        // Save to MediaStore (I/O operation)
        withContext(Dispatchers.IO) {
            saveBitmapToMediaStore(context, rotatedBitmap)
        }
    }
}

/**
 * Combined capture and save with state updates.
 * Use this for async capture with UI feedback.
 */
internal suspend fun CameraController.takePictureAsync(
    context: Context,
    onStateChange: (CaptureState) -> Unit
): Uri? {
    return try {
        onStateChange(CaptureState.Capturing)

        // Phase 1: Quick capture
        val capturedImage = captureImage()

        // Phase 2: Process in background
        onStateChange(CaptureState.Processing)
        Log.i("takePictureAsync", "Processing image...")

        val uri = processAndSave(context, capturedImage)

        Log.i("takePictureAsync", "Image saved successfully")
        onStateChange(CaptureState.Saved(uri))
        uri
    } catch (e: Exception) {
        Log.e("takePictureAsync", "Capture failed", e)
        onStateChange(CaptureState.Error(e))
        null
    }
}

/**
 * Legacy synchronous capture - kept for compatibility.
 * Prefer takePictureAsync for better UX.
 */
internal suspend fun CameraController.takePicture(context: Context): Uri {
    val capturedImage = captureImage()
    return processAndSave(context, capturedImage)
}

private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap): Uri {
    val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
        .format(System.currentTimeMillis())

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$name")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
    }

    var uri: Uri? = null

    runCatching {
        with(context.contentResolver) {
            insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.also {
                uri = it // Keep uri reference so it can be removed on failure

                openOutputStream(it)?.buffered()?.use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                } ?: throw IOException("Failed to open output stream.")

            } ?: throw IOException("Failed to create new MediaStore record.")
        }
    }.getOrElse {
        uri?.let { orphanUri ->
            // Don't leave an orphan entry in the MediaStore
            context.contentResolver.delete(orphanUri, null, null)
        }

        throw it
    }

    return uri!!
}
