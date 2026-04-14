package net.theluckycoder.camera

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.CameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraController: CameraController,
    gridMode: GridMode = GridMode.OFF,
) {
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusIndicator by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                previewView.controller = cameraController

                // Enable pinch-to-zoom (should be enabled by default but ensure it)
                cameraController.isPinchToZoomEnabled = true

                // Enable tap-to-focus
                cameraController.isTapToFocusEnabled = true

                // Add tap listener for focus indicator
                previewView.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        focusPoint = Offset(event.x, event.y)
                        showFocusIndicator = true

                        // Start focus and metering
                        val meteringPointFactory = previewView.meteringPointFactory
                        val meteringPoint = meteringPointFactory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(meteringPoint)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        cameraController.cameraControl?.startFocusAndMetering(action)
                    }
                    false // Let the default touch handling continue
                }

                previewView
            },
        )

        // Grid overlay
        GridOverlay(gridMode = gridMode)

        // Focus indicator
        focusPoint?.let { point ->
            FocusIndicator(
                visible = showFocusIndicator,
                position = point,
                onHide = { showFocusIndicator = false }
            )
        }
    }
}

@Composable
private fun GridOverlay(gridMode: GridMode, modifier: Modifier = Modifier) {
    if (gridMode == GridMode.OFF) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val lineColor = Color.White.copy(alpha = 0.4f)
        val strokeWidth = 1.dp.toPx()

        when (gridMode) {
            GridMode.RULE_OF_THIRDS -> {
                val thirdW = size.width / 3
                val thirdH = size.height / 3
                // Vertical lines
                drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth)
                drawLine(lineColor, Offset(thirdW * 2, 0f), Offset(thirdW * 2, size.height), strokeWidth)
                // Horizontal lines
                drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth)
                drawLine(lineColor, Offset(0f, thirdH * 2), Offset(size.width, thirdH * 2), strokeWidth)
            }
            GridMode.GOLDEN_RATIO -> {
                val phi = 1.618f
                val w1 = size.width / phi
                val w2 = size.width - w1
                val h1 = size.height / phi
                val h2 = size.height - h1
                // Vertical lines
                drawLine(lineColor, Offset(w2, 0f), Offset(w2, size.height), strokeWidth)
                drawLine(lineColor, Offset(w1, 0f), Offset(w1, size.height), strokeWidth)
                // Horizontal lines
                drawLine(lineColor, Offset(0f, h2), Offset(size.width, h2), strokeWidth)
                drawLine(lineColor, Offset(0f, h1), Offset(size.width, h1), strokeWidth)
            }
            GridMode.OFF -> {} // Already handled by early return
        }
    }
}

@Composable
private fun FocusIndicator(
    visible: Boolean,
    position: Offset,
    onHide: () -> Unit
) {
    val scale = remember { Animatable(1.5f) }

    LaunchedEffect(visible, position) {
        if (visible) {
            scale.snapTo(1.5f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
            delay(1000)
            onHide()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (position.x - 32.dp.toPx()).toInt(),
                        (position.y - 32.dp.toPx()).toInt()
                    )
                }
                .size(64.dp)
                .scale(scale.value)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

