package net.theluckycoder.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
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

//internal val Context.executor: Executor
//    get() = ContextCompat.getMainExecutor(this)

internal suspend fun CameraController.takePicture(context: Context): Uri {
    return suspendCoroutine { continuation ->
        val callback = object : ImageCapture.OnImageCapturedCallback() {
            @androidx.annotation.OptIn(ExperimentalGetImage::class)
            override fun onCaptureSuccess(image: ImageProxy) {
                val originalBitmap = image.toBitmap()
                val rotationDegrees = image.imageInfo.rotationDegrees

                val rotatedBitmap = if (rotationDegrees == 0) {
                    originalBitmap.copy(originalBitmap.config!!, true)
                } else {
                    val rotationMatrix = Matrix().apply {
                        postRotate(rotationDegrees.toFloat())
                    }
                    Bitmap.createBitmap(
                        originalBitmap,
                        0,
                        0,
                        originalBitmap.width,
                        originalBitmap.height,
                        rotationMatrix,
                        false
                    )
                }
                originalBitmap.recycle()

                val formattedDate = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern(BITMAP_DATE_FORMAT)
                )

                Canvas(rotatedBitmap).apply {
                    val tPaint = Paint().apply {
                        textSize = 0.05f * rotatedBitmap.width
                        color = Color.GREEN
                        style = Paint.Style.FILL
                    }
                    val textWidth = tPaint.measureText(formattedDate)
                    drawText(
                        formattedDate,
                        rotatedBitmap.width - textWidth - 0.01f * rotatedBitmap.width,
                        rotatedBitmap.height - 0.01f * rotatedBitmap.height,
                        tPaint
                    )
                }

                try {
                    Log.i("onCaptureSuccess", "Saving image")

                    val uri = saveBitmapToMediaStore(context, rotatedBitmap)

                    Log.i("onCaptureSuccess", "Image saved successfully")

                    continuation.resume(uri)
                } catch (e: Exception) {
                    Log.e("onCaptureSuccess", "failed to save image", e)
                    continuation.resumeWithException(e)
                } finally {
                    image.close()
                }

            }

            override fun onError(ex: ImageCaptureException) {
                Log.e("TakePicture", "Image capture failed", ex)
                continuation.resumeWithException(ex)
            }
        }

        takePicture(
            Dispatchers.IO.asExecutor(),
            callback
        )
    }
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
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
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
