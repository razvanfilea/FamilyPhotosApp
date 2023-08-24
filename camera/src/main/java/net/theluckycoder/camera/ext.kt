package net.theluckycoder.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener(
            {
                continuation.resume(future.get())
            },
            executor
        )
    }
}

val Context.executor: Executor
    get() = ContextCompat.getMainExecutor(this)

suspend fun ImageCapture.takePicture(executor: Executor): File {
    return suspendCoroutine { continuation ->
        val callback = object : ImageCapture.OnImageCapturedCallback() {
            @androidx.annotation.OptIn(ExperimentalGetImage::class)
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
                val dateTime = LocalDateTime.now()
                val formattedDate = dateTime.format(
                    DateTimeFormatter.ofPattern("dd.MM.yyyy")
                )

                Canvas(bitmap).apply {
                    val tPaint = Paint().apply {
                        textSize = 90f
                        color = Color.GREEN
                        style = Paint.Style.FILL
                    }
                    val textWidth = tPaint.measureText(formattedDate)
                    drawText(
                        formattedDate,
                        bitmap.width - textWidth,
                        bitmap.height - 20f,
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
