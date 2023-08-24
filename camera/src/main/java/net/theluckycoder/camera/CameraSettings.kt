package net.theluckycoder.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FlashMode
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.compose.runtime.Immutable

@Immutable
internal data class CameraSettings(
    @FlashMode
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val aspectRatio: AspectRatioStrategy = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY,
    val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
)