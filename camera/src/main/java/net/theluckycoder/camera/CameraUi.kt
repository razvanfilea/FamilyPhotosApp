package net.theluckycoder.camera

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private const val DEFAULT_ASPECT_RATIO = AspectRatio.RATIO_4_3

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun CameraUi() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraController: CameraController = remember {
        val resolution =
            ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

        LifecycleCameraController(context).apply {
            bindToLifecycle(lifecycleOwner)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            setImageCaptureResolutionSelector(resolution)
            setImageCaptureResolutionSelector(resolution)
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
    var lastSavedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val rotation = remember { Animatable(0f) }

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

            coroutineScope.launch {
                rotation.animateTo(
                    targetValue = 180f,
                    animationSpec = tween(800)
                )
                rotation.snapTo(0f)
            }
        }
    ) {

        Icon(
            painterResource(R.drawable.change_camera),
            modifier = Modifier.rotate(rotation.value),
            contentDescription = null
        )
    }

    CapturePictureButton(
        modifier = Modifier.size(90.dp),
        enabled = !isSavingPicture,
        onClick = {
            coroutineScope.launch {
                isSavingPicture = true
                lastSavedImageUri = cameraController.takePicture(context)
                isSavingPicture = false
            }
        }
    )

    IconButton(
        modifier = Modifier
            .size(64.dp)
            .border(1.dp, Color.White, CircleShape),
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setDataAndType(lastSavedImageUri, "image/*")
            }
            context.startActivity(intent)
        },
    ) {
        val uri = lastSavedImageUri
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                imageLoader = LocalImageLoader.current,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(painterResource(R.drawable.gallery), contentDescription = null)
        }
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
                else -> AspectRatio.RATIO_4_3
            }

            val aspectRatioStrategy = when (aspectRatio.intValue) {
                AspectRatio.RATIO_4_3 -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                AspectRatio.RATIO_16_9 -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                else -> throw IllegalArgumentException("Invalid Aspect Ratio")
            }

            val resolution =
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .setAspectRatioStrategy(aspectRatioStrategy)
                    .build()
            cameraController.setImageCaptureResolutionSelector(resolution)
            cameraController.setImageCaptureResolutionSelector(resolution)
        }
    ) {
        val aspectRatioIcon = when (aspectRatio.intValue) {
            AspectRatio.RATIO_4_3 -> R.drawable.aspect_ratio_4_3
            else -> R.drawable.aspect_ratio_16_9
        }
        Icon(painter = painterResource(aspectRatioIcon), contentDescription = null)
    }
}
