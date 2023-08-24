package net.theluckycoder.camera

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun CameraUi(cameraSettingsState: MutableState<CameraSettings>) = Scaffold(
    topBar = {
        TopSettings(cameraSettingsState)
    },
    bottomBar = {

    }
) { _ ->
    CameraCapture(
        modifier = Modifier.fillMaxSize(),
        cameraSettings = cameraSettingsState.value,
        onImageFile = { file ->
            Log.i("File", file.absolutePath)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopSettings(cameraSettingsState: MutableState<CameraSettings>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .background(Color.DarkGray.copy(alpha = 0.7f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val cameraSettings = cameraSettingsState.value
        val flashMode = cameraSettings.flashMode

        IconButton(onClick = {
            val newFlashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
                else -> ImageCapture.FLASH_MODE_ON
            }
            cameraSettingsState.value = cameraSettings.copy(flashMode = newFlashMode)
        }) {
            val flashIcon = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> R.drawable.flash_on
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_auto
                else -> R.drawable.flash_off
            }
            Icon(painter = painterResource(flashIcon), contentDescription = null)
        }
    }
}
