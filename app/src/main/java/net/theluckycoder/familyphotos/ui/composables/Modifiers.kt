package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.selectableClickable(
    inSelectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDeselect: () -> Unit
) = this
    .semantics {
        if (!inSelectionMode) {
            onLongClick("Select") {
                onSelect()
                true
            }
        }
    }
    .then(
        if (inSelectionMode) {
            Modifier.toggleable(
                value = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // do not show a ripple
                onValueChange = {
                    if (it) {
                        onSelect()
                    } else {
                        onDeselect()
                    }
                }
            )
        } else {
            Modifier.combinedClickable(onClick = { onClick() }, onLongClick = onSelect)
        }
    )

@Composable
fun Modifier.detectZoomIn(maxZoomIndex: Int, zoomIndexState: MutableIntState): Modifier {
    var zoomFloat by remember { mutableFloatStateOf(1f) }

    return this.pointerInput(Unit) {
        detectPinchGestures(
            pass = PointerEventPass.Initial,
            onGesture = { centroid: Offset, newZoom: Float ->
                val newScale = (zoomFloat * newZoom)
                zoomFloat = if (newScale > 1.3f) {
                    zoomIndexState.intValue =
                        zoomIndexState.intValue.dec().coerceIn(0, maxZoomIndex)
                    1f
                } else if (newScale < 0.7f) {
                    zoomIndexState.intValue =
                        zoomIndexState.intValue.inc().coerceIn(0, maxZoomIndex)
                    1f
                } else {
                    newScale
                }
            },
            onGestureEnd = { zoomFloat = 1f }
        )
    }
}

private suspend fun PointerInputScope.detectPinchGestures(
    pass: PointerEventPass = PointerEventPass.Main,
    onGestureStart: (PointerInputChange) -> Unit = {},
    onGesture: (
        centroid: Offset,
        zoom: Float
    ) -> Unit,
    onGestureEnd: (PointerInputChange) -> Unit = {}
) {
    awaitEachGesture {
        var zoom = 1f
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        val down: PointerInputChange =
            awaitFirstDown(requireUnconsumed = false, pass = pass)
        onGestureStart(down)
        var pointer = down
        var pointerId = down.id
        do {
            val event = awaitPointerEvent(pass = pass)
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val pointerInputChange =
                    event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.first()
                pointerId = pointerInputChange.id
                pointer = pointerInputChange

                val zoomChange = event.calculateZoom()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion =
                        abs(1 - zoom) * centroidSize
                    if (zoomMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f) {
                        onGesture(
                            centroid,
                            zoomChange
                        )
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })

        onGestureEnd(pointer)
    }
}
