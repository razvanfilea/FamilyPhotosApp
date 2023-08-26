package net.theluckycoder.camera

import android.annotation.SuppressLint
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val DEFAULT_ASPECT_RATIO = AspectRatio.RATIO_4_3

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun CameraUi() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraController: CameraController = remember {
        LifecycleCameraController(context).apply {
            bindToLifecycle(lifecycleOwner)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            imageCaptureTargetSize = CameraController.OutputSize(DEFAULT_ASPECT_RATIO)
            previewTargetSize = CameraController.OutputSize(DEFAULT_ASPECT_RATIO)
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    val aspectRatio = remember { mutableIntStateOf(DEFAULT_ASPECT_RATIO) }

    Scaffold(
        topBar = {
            TopSettings(cameraController, aspectRatio)
        },
        bottomBar = {
            BottomBar(cameraController)
        }
    ) { _ ->
        key(aspectRatio.intValue) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                cameraController = cameraController,
            )
        }
    }
}

@Composable
private fun BottomBar(
    cameraController: CameraController,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .background(Color.DarkGray.copy(alpha = 0.6f))
        .windowInsetsPadding(BottomAppBarDefaults.windowInsets)
        .padding(16.dp),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSavingPicture by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            val cameraSelector = cameraController.cameraSelector
            cameraController.cameraSelector =
                if (cameraSelector === CameraSelector.DEFAULT_FRONT_CAMERA) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (cameraSelector === CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    throw IllegalArgumentException("Invalid facing camera")
                }
        }
    ) {
        Icon(painterResource(R.drawable.change_camera), contentDescription = null)
    }

    CapturePictureButton(
        modifier = Modifier.size(90.dp),
        enabled = !isSavingPicture,
        onClick = {
            coroutineScope.launch {
                isSavingPicture = true
                cameraController.takePicture(context)
                isSavingPicture = false
            }
        }
    )

    IconButton(onClick = {}) {

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopSettings(
    cameraController: CameraController,
    aspectRatio: MutableIntState
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .background(Color.DarkGray.copy(alpha = 0.6f))
        .windowInsetsPadding(TopAppBarDefaults.windowInsets)
        .padding(8.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
) {
    var flashMode by remember { mutableIntStateOf(cameraController.imageCaptureFlashMode) }

    IconButton(
        onClick = {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
                else -> ImageCapture.FLASH_MODE_ON
            }
            cameraController.imageCaptureFlashMode = flashMode
        }
    ) {
        val flashIcon = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> R.drawable.flash_on
            ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_auto
            else -> R.drawable.flash_off
        }
        Icon(painter = painterResource(flashIcon), contentDescription = null)
    }

    IconButton(
        onClick = {
            aspectRatio.intValue = when (aspectRatio.intValue) {
                AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
                AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
                else -> throw IllegalArgumentException("Invalid Aspect Ratio")
            }

            val outputSize = CameraController.OutputSize(aspectRatio.intValue)
            cameraController.imageCaptureTargetSize = outputSize
            cameraController.previewTargetSize = outputSize
        }
    ) {
        val aspectRatioIcon = when (aspectRatio.intValue) {
            AspectRatio.RATIO_4_3 -> R.drawable.aspect_ratio_4_3
            else -> R.drawable.aspect_ratio_16_9
        }
        Icon(painter = painterResource(aspectRatioIcon), contentDescription = null)
    }
}
