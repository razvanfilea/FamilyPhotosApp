package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.paging.ItemSnapshotList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.theluckycoder.familyphotos.data.model.Photo
import net.theluckycoder.familyphotos.ui.LocalSharedTransitionScope
import kotlin.math.abs

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.photoSharedBounds(photoId: Long): Modifier {
    with(LocalSharedTransitionScope.current) {
        return sharedBounds(
            rememberSharedContentState(key = photoId),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }
}

fun Modifier.selectableClickable(
    inSelectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDeselect: () -> Unit
) = if (inSelectionMode) {
    this.toggleable(
        value = selected,
        interactionSource = null,
        indication = null, // do not show a ripple
        role = Role.Checkbox,
        onValueChange = {
            if (it) {
                onSelect()
            } else {
                onDeselect()
            }
        }
    )
} else {
    this.clickable(onClick = onClick, role = Role.Checkbox)
}

@Composable
fun <T : Photo> Modifier.photoGridDrag(
    lazyGridState: LazyGridState,
    selectedIds: SnapshotStateSet<Long>,
    items: ItemSnapshotList<T>,
): Modifier {
    val autoScrollSpeed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(autoScrollSpeed.floatValue) {
        if (autoScrollSpeed.floatValue != 0f) {
            while (isActive) {
                lazyGridState.scrollBy(autoScrollSpeed.floatValue)
                delay(10)
            }
        }
    }
    val scrollGestureActive = remember { mutableStateOf(false) }

    return photoGridDrag(
        lazyGridState = lazyGridState,
        haptics = LocalHapticFeedback.current,
        selectedIds = selectedIds,
        autoScrollSpeed = autoScrollSpeed,
        autoScrollThreshold = with(LocalDensity.current) { 40.dp.toPx() },
        scrollGestureActive = scrollGestureActive,
        items = items
    )
}

private fun <T : Photo> Modifier.photoGridDrag(
    lazyGridState: LazyGridState,
    haptics: HapticFeedback,
    selectedIds: SnapshotStateSet<Long>,
    autoScrollSpeed: MutableState<Float>,
    autoScrollThreshold: Float,
    scrollGestureActive: MutableState<Boolean>,
    items: ItemSnapshotList<T>,
) = pointerInput(Unit) {

    fun LazyGridState.hitKeyAt(contentOffset: Offset): Long? {
        return layoutInfo.visibleItemsInfo
            .find { info ->
                info.size.toIntRect()
                    .contains((contentOffset.round() - info.offset))
            }
            ?.key as? Long
    }

    var initialMediaIndex: Int? = null
    var currentMediaIndex: Int? = null

    detectDragGesturesAfterLongPress(
        onDragStart = { raw ->
            scrollGestureActive.value = true
            lazyGridState.hitKeyAt(raw)?.let { key ->
                val idx = items.indexOfFirst { it?.id == key }
                if (key !in selectedIds) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    initialMediaIndex = idx
                    currentMediaIndex = idx
                    selectedIds.add(key)
                }
            }
        },
        onDragCancel = {
            scrollGestureActive.value = false
            initialMediaIndex = null
            autoScrollSpeed.value = 0f
        },
        onDragEnd = {
            scrollGestureActive.value = false
            initialMediaIndex = null
            autoScrollSpeed.value = 0f
        },
        onDrag = { change, _ ->
            val raw = change.position
            if (initialMediaIndex != null) {
                // auto-scroll logic unchanged…
                val distB = lazyGridState.layoutInfo.viewportSize.height - raw.y
                val distT = raw.y
                autoScrollSpeed.value = when {
                    distB < autoScrollThreshold -> autoScrollThreshold - distB
                    distT < autoScrollThreshold -> -(autoScrollThreshold - distT)
                    else -> 0f
                }

                lazyGridState.hitKeyAt(raw)?.let { key ->
                    val newIdx = items.indexOfFirst { it?.id == key }
                    if (newIdx >= 0 && newIdx != currentMediaIndex) {
                        val start = initialMediaIndex ?: return@let
                        val oldEnd = currentMediaIndex ?: return@let
                        val oldRange = if (oldEnd >= start) start..oldEnd else oldEnd..start
                        val newRange = if (newIdx >= start) start..newIdx else newIdx..start

                        val oldIds = oldRange.mapNotNull { items.getOrNull(it)?.id }
                        val newIds = newRange.mapNotNull { items.getOrNull(it)?.id }

                        selectedIds.removeAll(oldIds)
                        selectedIds.addAll(newIds)
                        currentMediaIndex = newIdx
                    }
                }
            }
        },
    )
}

private const val UPPER_THRESHOLD = 1.8f
private const val LOWER_THRESHOLD = 0.6f

@Composable
fun Modifier.detectZoomIn(
    zoomIndexState: MutableIntState,
    maxZoomIndex: Int
): Modifier {
    var accumulatedZoom by remember { mutableFloatStateOf(1f) }

    return this.pointerInput(Unit) {
        detectPinchGestures(
            pass = PointerEventPass.Initial,
            onGesture = { _, newZoom ->
                accumulatedZoom *= newZoom

                val zoomIndex = zoomIndexState.intValue

                while (accumulatedZoom > UPPER_THRESHOLD) {
                    zoomIndexState.intValue = zoomIndex.dec().coerceIn(0, maxZoomIndex)
                    accumulatedZoom /= UPPER_THRESHOLD // retain leftover zoom beyond the threshold
                }

                while (accumulatedZoom < LOWER_THRESHOLD) {
                    zoomIndexState.intValue = zoomIndex.inc().coerceIn(0, maxZoomIndex)
                    accumulatedZoom /= LOWER_THRESHOLD
                }
            },
            onGestureEnd = { accumulatedZoom = 1f }
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
