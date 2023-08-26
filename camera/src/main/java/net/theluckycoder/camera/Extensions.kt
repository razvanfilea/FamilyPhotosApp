package net.theluckycoder.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal val Context.executor: Executor
    get() = ContextCompat.getMainExecutor(this)

internal suspend fun CameraController.takePicture(executor: Executor): File {
    return suspendCoroutine { continuation ->
        val callback = object : ImageCapture.OnImageCapturedCallback() {
            @androidx.annotation.OptIn(ExperimentalGetImage::class)
            override fun onCaptureSuccess(image: ImageProxy) {
                val originalBitmap = image.toBitmap()
                val rotationMatrix = Matrix().apply {
                    val rotation = image.imageInfo.rotationDegrees
                    postRotate(rotation.toFloat())
                }
                val formattedDate = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd.MM.yyyy")
                )
                val rotatedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    rotationMatrix,
                    false
                )

                Canvas(rotatedBitmap).apply {
                    val tPaint = Paint().apply {
                        textSize = 90f
                        color = Color.GREEN
                        style = Paint.Style.FILL
                    }
                    val textWidth = tPaint.measureText(formattedDate)
                    drawText(
                        formattedDate,
                        rotatedBitmap.width - textWidth - 2f,
                        rotatedBitmap.height - 20f,
                        tPaint
                    )
                }
                Log.i("onCaptureSuccess", "Saving image")
            }

            override fun onError(ex: ImageCaptureException) {
                Log.e("TakePicture", "Image capture failed", ex)
                continuation.resumeWithException(ex)
            }
        }
        takePicture(
            executor,
            callback
        )
    }
}
