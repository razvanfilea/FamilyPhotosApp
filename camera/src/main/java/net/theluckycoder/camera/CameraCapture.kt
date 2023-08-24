package net.theluckycoder.camera

import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun CameraCapture(
    modifier: Modifier = Modifier,
    cameraSettings: CameraSettings,
    onImageFile: (File) -> Unit = { }
) {
    val context = LocalContext.current

    val resolutionSelector = remember(cameraSettings) {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(cameraSettings.aspectRatio)
            .build()
    }

    Box(modifier = modifier) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val coroutineScope = rememberCoroutineScope()
        var previewUseCase by remember(resolutionSelector) {
            mutableStateOf<UseCase>(
                Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
            )
        }
        val imageCaptureUseCase by remember(resolutionSelector, cameraSettings) {
            mutableStateOf(
                ImageCapture.Builder()
                    .setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(cameraSettings.flashMode)
                    .setResolutionSelector(resolutionSelector)
                    .build()
            )
        }

        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onUseCase = {
                previewUseCase = it
            }
        )

        CapturePictureButton(
            modifier = Modifier
                .size(100.dp)
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            onClick = {
                coroutineScope.launch {
                    onImageFile(imageCaptureUseCase.takePicture(context.executor))
                }
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

        LaunchedEffect(previewUseCase, imageCaptureUseCase) {
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
}
