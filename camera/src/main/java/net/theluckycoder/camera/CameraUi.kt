package net.theluckycoder.camera

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun CameraUi(cameraSettingsState: MutableState<CameraSettings>) {
    val resolutionSelector = remember(cameraSettingsState.value) {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(cameraSettingsState.value.aspectRatio)
            .build()
    }

    val imageCaptureUseCase = remember(resolutionSelector, cameraSettingsState.value) {
        mutableStateOf(
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(cameraSettingsState.value.flashMode)
                .setResolutionSelector(resolutionSelector)
                .build()
        )
    }

    Scaffold(
        topBar = {
            TopSettings(cameraSettingsState)
        },
        bottomBar = {
            BottomBar(cameraSettingsState, imageCaptureUseCase.value)
        }
    ) { _ ->
        CameraCapture(
            cameraSettings = cameraSettingsState.value,
            resolutionSelector,
            imageCaptureUseCase.value
        )
    }
}

@Composable
private fun BottomBar(
    cameraSettingsState: MutableState<CameraSettings>,
    imageCaptureUseCase: ImageCapture
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

    IconButton(
        onClick = {
            val cameraSelector = cameraSettingsState.value.cameraSelector
            val newCameraSelector = if (cameraSelector === CameraSelector.DEFAULT_FRONT_CAMERA) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else if (cameraSelector === CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                throw IllegalArgumentException("Invalid facing camera")
            }

            cameraSettingsState.value =
                cameraSettingsState.value.copy(cameraSelector = newCameraSelector)
        }
    ) {
        Icon(painterResource(R.drawable.change_camera), contentDescription = null)
    }

    CapturePictureButton(
        modifier = Modifier.size(90.dp),
        onClick = {
            coroutineScope.launch {
                imageCaptureUseCase.takePicture(context.executor)
            }
        }
    )

    IconButton(onClick = {}) {

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopSettings(cameraSettingsState: MutableState<CameraSettings>) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .background(Color.DarkGray.copy(alpha = 0.6f))
        .windowInsetsPadding(TopAppBarDefaults.windowInsets)
        .padding(8.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
) {
    val cameraSettings = cameraSettingsState.value
    val flashMode = cameraSettings.flashMode

    IconButton(
        onClick = {
            val newFlashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
                else -> ImageCapture.FLASH_MODE_ON
            }
            cameraSettingsState.value = cameraSettings.copy(flashMode = newFlashMode)
        }
    ) {
        val flashIcon = when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> R.drawable.flash_on
            ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_auto
            else -> R.drawable.flash_off
        }
        Icon(painter = painterResource(flashIcon), contentDescription = null)
    }
}
