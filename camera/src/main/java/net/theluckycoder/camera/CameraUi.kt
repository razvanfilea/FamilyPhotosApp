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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

enum class GridMode {
    OFF, RULE_OF_THIRDS, GOLDEN_RATIO
}

enum class TimerMode(val seconds: Int) {
    OFF(0), THREE_SECONDS(3), TEN_SECONDS(10)
}

private const val DEFAULT_ASPECT_RATIO = AspectRatio.RATIO_4_3

/**
 * Animates rotation while handling wrap-around (e.g., 270° → 0° takes shortest path via 360°)
 */
@Composable
private fun rememberAnimatedRotation(): Float {
    val deviceRotation = LocalDeviceRotation.current
    val animatedRotation = remember { Animatable(0f) }

    LaunchedEffect(deviceRotation) {
        val target = deviceRotation.toFloat()
        val current = animatedRotation.value

        // Normalize current to 0-360 range
        val normalizedCurrent = ((current % 360) + 360) % 360

        // Calculate shortest path
        var delta = target - normalizedCurrent
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360

        animatedRotation.animateTo(
            targetValue = current + delta,
            animationSpec = tween(durationMillis = 300)
        )
    }

    return animatedRotation.value
}

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
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    val aspectRatio = remember { mutableIntStateOf(DEFAULT_ASPECT_RATIO) }

    // Grid state
    var gridMode by rememberSaveable { mutableStateOf(GridMode.OFF) }

    // Timer state
    var timerMode by rememberSaveable { mutableStateOf(TimerMode.OFF) }
    var isCountingDown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(0) }

    // Zoom state - observe LiveData directly with observeAsState
    val zoomState by cameraController.zoomState.observeAsState()
    val currentZoomRatio = zoomState?.zoomRatio ?: 1.0f
    val minZoomRatio = zoomState?.minZoomRatio ?: 1.0f
    val maxZoomRatio = zoomState?.maxZoomRatio ?: 1.0f

    // Exposure state
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf<IntRange?>(null) }
    var exposureStep by remember { mutableFloatStateOf(0f) }

    // Observe exposure state
    LaunchedEffect(cameraController) {
        val exposureState = cameraController.cameraInfo?.exposureState
        if (exposureState?.isExposureCompensationSupported == true) {
            exposureRange = exposureState.exposureCompensationRange.let { it.lower..it.upper }
            exposureStep = exposureState.exposureCompensationStep.toFloat()
        }
    }

    // Calculate available zoom presets
    val availablePresets = remember(minZoomRatio, maxZoomRatio) {
        buildList {
            if (minZoomRatio <= 0.6f) add(0.6f)
            else if (minZoomRatio <= 0.8f) add(0.8f)
            add(1.0f)
            if (maxZoomRatio >= 2.0f) add(2.0f)
            if (maxZoomRatio >= 3.0f) add(3.0f)
            if (maxZoomRatio >= 6.0f) add(6.0f)
        }
    }

    Scaffold(
        topBar = {
            TopSettings(
                cameraController = cameraController,
                aspectRatio = aspectRatio,
                gridMode = gridMode,
                onGridModeChange = { gridMode = it },
                timerMode = timerMode,
                onTimerModeChange = { timerMode = it },
                exposureIndex = exposureIndex,
                exposureRange = exposureRange,
                exposureStep = exposureStep,
                onExposureChange = { exposureIndex = it },
            )
        },
        bottomBar = {
            Column {
                // Zoom presets row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomPresetRow(
                        availablePresets = availablePresets,
                        currentZoom = currentZoomRatio,
                        onZoomSelected = { preset ->
                            cameraController.setZoomRatio(preset)
                        }
                    )
                }
                // Bottom bar
                BottomBar(
                    cameraController = cameraController,
                    timerMode = timerMode,
                    isCountingDown = isCountingDown,
                    onCountdownStart = { isCountingDown = true },
                    onCountdownTick = { countdownValue = it },
                    onCountdownEnd = { isCountingDown = false },
                    onCameraSwitch = {
                        // Reset zoom and exposure when switching cameras
                        cameraController.setZoomRatio(1.0f)
                        cameraController.cameraControl?.setExposureCompensationIndex(0)
                        exposureIndex = 0
                    }
                )
            }
        }
    ) { _ ->
        Box {
            key(aspectRatio.intValue) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    cameraController = cameraController,
                    gridMode = gridMode,
                )
            }

            // Countdown overlay
            CountdownOverlay(
                countdownValue = countdownValue,
                visible = isCountingDown
            )
        }
    }
}

@Composable
private fun ZoomPresetRow(
    availablePresets: List<Float>,
    currentZoom: Float,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Find which preset is closest to current zoom
        val closestPreset = availablePresets.minByOrNull { abs(it - currentZoom) }

        availablePresets.forEach { preset ->
            val isClosest = preset == closestPreset
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (isClosest) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                        CircleShape
                    )
                    .clickable { onZoomSelected(preset) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    // Show actual zoom level when this is the closest preset
                    text = when {
                        isClosest -> "%.1fx".format(currentZoom)
                        preset < 1f -> "%.1fx".format(preset)
                        else -> "%.0fx".format(preset)
                    },
                    color = if (isClosest) Color.Yellow else Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (isClosest) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CountdownOverlay(countdownValue: Int, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            val scale = remember { Animatable(1f) }
            LaunchedEffect(countdownValue) {
                scale.snapTo(1.3f)
                scale.animateTo(1f, animationSpec = tween(800))
            }
            Text(
                text = countdownValue.toString(),
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.scale(scale.value)
            )
        }
    }
}

@Composable
private fun BottomBar(
    cameraController: CameraController,
    timerMode: TimerMode,
    isCountingDown: Boolean,
    onCountdownStart: () -> Unit,
    onCountdownTick: (Int) -> Unit,
    onCountdownEnd: () -> Unit,
    onCameraSwitch: () -> Unit,
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
    val animatedRotation = rememberAnimatedRotation()

    var captureState by remember { mutableStateOf<CaptureState>(CaptureState.Idle) }
    var lastSavedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val switchCameraRotation = remember { Animatable(0f) }

    val isCapturing = captureState is CaptureState.Capturing || captureState is CaptureState.Processing

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

            // Reset zoom and exposure when switching cameras
            onCameraSwitch()

            coroutineScope.launch {
                switchCameraRotation.animateTo(
                    targetValue = 180f,
                    animationSpec = tween(800)
                )
                switchCameraRotation.snapTo(0f)
            }
        }
    ) {
        Icon(
            painterResource(R.drawable.change_camera),
            modifier = Modifier.rotate(animatedRotation + switchCameraRotation.value),
            contentDescription = null
        )
    }

    CapturePictureButton(
        modifier = Modifier.size(90.dp),
        enabled = !isCapturing && !isCountingDown,
        onClick = {
            coroutineScope.launch {
                if (timerMode == TimerMode.OFF) {
                    // Capture immediately
                    cameraController.takePictureAsync(context) { state ->
                        captureState = state
                        if (state is CaptureState.Saved) {
                            lastSavedImageUri = state.uri
                        }
                    }
                    captureState = CaptureState.Idle
                } else {
                    // Start countdown
                    onCountdownStart()
                    for (i in timerMode.seconds downTo 1) {
                        onCountdownTick(i)
                        delay(1000)
                    }
                    // Capture after countdown
                    cameraController.takePictureAsync(context) { state ->
                        captureState = state
                        if (state is CaptureState.Saved) {
                            lastSavedImageUri = state.uri
                        }
                    }
                    captureState = CaptureState.Idle
                    onCountdownEnd()
                }
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
        when {
            captureState is CaptureState.Processing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            uri != null -> {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    imageLoader = LocalImageLoader.current,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Icon(
                    painterResource(R.drawable.gallery),
                    modifier = Modifier.rotate(animatedRotation),
                    contentDescription = null
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopSettings(
    cameraController: CameraController,
    aspectRatio: MutableIntState,
    gridMode: GridMode,
    onGridModeChange: (GridMode) -> Unit,
    timerMode: TimerMode,
    onTimerModeChange: (TimerMode) -> Unit,
    exposureIndex: Int,
    exposureRange: IntRange?,
    exposureStep: Float,
    onExposureChange: (Int) -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .background(Color.DarkGray.copy(alpha = 0.6f))
        .windowInsetsPadding(TopAppBarDefaults.windowInsets)
        .padding(8.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
) {
    val animatedRotation = rememberAnimatedRotation()
    var flashMode by remember { mutableIntStateOf(cameraController.imageCaptureFlashMode) }

    // Flash dropdown
    var showFlashMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showFlashMenu = true }) {
            val flashIcon = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> R.drawable.flash_on
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.flash_auto
                else -> R.drawable.flash_off
            }
            Icon(
                painter = painterResource(flashIcon),
                modifier = Modifier.rotate(animatedRotation),
                contentDescription = "Flash"
            )
        }
        DropdownMenu(
            expanded = showFlashMenu,
            onDismissRequest = { showFlashMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Off") },
                leadingIcon = { Icon(painterResource(R.drawable.flash_off), null) },
                onClick = {
                    flashMode = ImageCapture.FLASH_MODE_OFF
                    cameraController.imageCaptureFlashMode = flashMode
                    showFlashMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Auto") },
                leadingIcon = { Icon(painterResource(R.drawable.flash_auto), null) },
                onClick = {
                    flashMode = ImageCapture.FLASH_MODE_AUTO
                    cameraController.imageCaptureFlashMode = flashMode
                    showFlashMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("On") },
                leadingIcon = { Icon(painterResource(R.drawable.flash_on), null) },
                onClick = {
                    flashMode = ImageCapture.FLASH_MODE_ON
                    cameraController.imageCaptureFlashMode = flashMode
                    showFlashMenu = false
                }
            )
        }
    }

    // Aspect ratio dropdown
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showAspectRatioMenu = true }) {
            val aspectRatioIcon = when (aspectRatio.intValue) {
                AspectRatio.RATIO_4_3 -> R.drawable.aspect_ratio_4_3
                else -> R.drawable.aspect_ratio_16_9
            }
            Icon(
                painter = painterResource(aspectRatioIcon),
                modifier = Modifier.rotate(animatedRotation),
                contentDescription = "Aspect Ratio"
            )
        }
        DropdownMenu(
            expanded = showAspectRatioMenu,
            onDismissRequest = { showAspectRatioMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("4:3") },
                leadingIcon = { Icon(painterResource(R.drawable.aspect_ratio_4_3), null) },
                onClick = {
                    aspectRatio.intValue = AspectRatio.RATIO_4_3
                    val resolution = ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()
                    cameraController.setImageCaptureResolutionSelector(resolution)
                    showAspectRatioMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("16:9") },
                leadingIcon = { Icon(painterResource(R.drawable.aspect_ratio_16_9), null) },
                onClick = {
                    aspectRatio.intValue = AspectRatio.RATIO_16_9
                    val resolution = ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .build()
                    cameraController.setImageCaptureResolutionSelector(resolution)
                    showAspectRatioMenu = false
                }
            )
        }
    }

    // Grid dropdown
    var showGridMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showGridMenu = true }) {
            Icon(
                painter = painterResource(
                    when (gridMode) {
                        GridMode.OFF -> R.drawable.camera_grid_off
                        GridMode.RULE_OF_THIRDS -> R.drawable.camera_grid_3x3
                        GridMode.GOLDEN_RATIO -> R.drawable.camera_grid_goldenratio
                    }
                ),
                modifier = Modifier.rotate(animatedRotation),
                contentDescription = "Grid"
            )
        }
        DropdownMenu(
            expanded = showGridMenu,
            onDismissRequest = { showGridMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Off") },
                leadingIcon = { Icon(painterResource(R.drawable.camera_grid_off), null) },
                onClick = { onGridModeChange(GridMode.OFF); showGridMenu = false }
            )
            DropdownMenuItem(
                text = { Text("3×3 Grid") },
                leadingIcon = { Icon(painterResource(R.drawable.camera_grid_3x3), null) },
                onClick = { onGridModeChange(GridMode.RULE_OF_THIRDS); showGridMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Golden Ratio") },
                leadingIcon = { Icon(painterResource(R.drawable.camera_grid_goldenratio), null) },
                onClick = { onGridModeChange(GridMode.GOLDEN_RATIO); showGridMenu = false }
            )
        }
    }

    // Timer dropdown
    var showTimerMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showTimerMenu = true }) {
            Box {
                Icon(
                    painter = painterResource(R.drawable.camera_timer),
                    modifier = Modifier.rotate(animatedRotation),
                    contentDescription = "Timer"
                )
                if (timerMode != TimerMode.OFF) {
                    Text(
                        text = timerMode.seconds.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(2.dp)
                    )
                }
            }
        }
        DropdownMenu(
            expanded = showTimerMenu,
            onDismissRequest = { showTimerMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Off") },
                leadingIcon = { Icon(painterResource(R.drawable.camera_timer), null) },
                onClick = { onTimerModeChange(TimerMode.OFF); showTimerMenu = false }
            )
            DropdownMenuItem(
                text = { Text("3 seconds") },
                leadingIcon = { Icon(painterResource(R.drawable.camera_timer), null) },
                onClick = { onTimerModeChange(TimerMode.THREE_SECONDS); showTimerMenu = false }
            )
            DropdownMenuItem(
                text = { Text("10 seconds") },
                leadingIcon = { Icon(painterResource(R.drawable.camera_timer), null) },
                onClick = { onTimerModeChange(TimerMode.TEN_SECONDS); showTimerMenu = false }
            )
        }
    }

    // Exposure compensation controls
    exposureRange?.let { range ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            IconButton(
                onClick = {
                    val newIndex = (exposureIndex - 1).coerceIn(range)
                    cameraController.cameraControl?.setExposureCompensationIndex(newIndex)
                    onExposureChange(newIndex)
                },
                enabled = exposureIndex > range.first,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.camera_exposure_neg_1),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(animatedRotation),
                    contentDescription = "Decrease exposure",
                    tint = if (exposureIndex > range.first) Color.White else Color.Gray
                )
            }

            Text(
                text = if (exposureIndex == 0) "0"
                else "%+.1f".format(exposureIndex * exposureStep),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .rotate(animatedRotation)
                    .width(36.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = {
                    val newIndex = (exposureIndex + 1).coerceIn(range)
                    cameraController.cameraControl?.setExposureCompensationIndex(newIndex)
                    onExposureChange(newIndex)
                },
                enabled = exposureIndex < range.last,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.camera_exposure_plus_1),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(animatedRotation),
                    contentDescription = "Increase exposure",
                    tint = if (exposureIndex < range.last) Color.White else Color.Gray
                )
            }
        }
    }
}
