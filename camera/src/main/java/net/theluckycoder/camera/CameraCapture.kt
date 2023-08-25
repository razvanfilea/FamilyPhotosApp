package net.theluckycoder.camera

import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun CameraCapture(
    cameraSettings: CameraSettings,
    resolutionSelector: ResolutionSelector,
    imageCaptureUseCase: ImageCapture,
) {
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    var previewUseCase by remember {
        mutableStateOf<UseCase>(
            Preview.Builder()
                .build()
        )
    }

    CameraPreview(
        modifier = Modifier.fillMaxSize(),
        resolutionSelector = resolutionSelector,
        onUseCase = {
            previewUseCase = it
        }
    )

    DisposableEffect(Unit) {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                // Monitors orientation values to determine the target rotation value
                val rotation: Int = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCaptureUseCase.targetRotation = rotation
            }
        }
        orientationEventListener.enable()

        onDispose {
            orientationEventListener.disable()
        }
    }

    LaunchedEffect(imageCaptureUseCase) {
        val cameraProvider = context.getCameraProvider()
        try {
            // Must unbind the use-cases before rebinding them.
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSettings.cameraSelector,
                previewUseCase,
                imageCaptureUseCase
            )
        } catch (ex: Exception) {
            Log.e("CameraCapture", "Failed to bind camera use cases", ex)
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    resolutionSelector: ResolutionSelector,
    onUseCase: (UseCase) -> Unit = { }
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context).apply {
                this.scaleType = PreviewView.ScaleType.FIT_CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            onUseCase(
                Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
            )

            previewView
        }
    )
}

