package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import net.theluckycoder.familyphotos.R
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import net.theluckycoder.familyphotos.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.utils.buildDateString

private const val THUMB_WIDTH_DP = 36
private const val THUMB_HEIGHT_DP = 56
private const val TRACK_WIDTH_DP = 48

@Composable
fun BoxScope.MonthScrollIndicator(
    gridState: LazyGridState,
    monthSummaries: List<MonthSummary>,
    onScrollToMonth: (monthIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (monthSummaries.size < 2) return

    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var trackHeight by remember { mutableIntStateOf(0) }
    var dragOffsetY by remember { mutableIntStateOf(0) }
    var currentMonthIndex by remember { mutableIntStateOf(0) }

    val thumbSizePx = with(density) { THUMB_HEIGHT_DP.dp.toPx().toInt() }

    // Build cumulative item counts for month position mapping
    val monthCumulativeCounts = remember(monthSummaries) {
        buildList {
            var cumulative = 0
            monthSummaries.forEach { summary ->
                add(cumulative)
                cumulative += summary.photoCount + 1 // +1 for separator
            }
            add(cumulative) // Total count at end
        }
    }

    val totalItems = monthCumulativeCounts.lastOrNull() ?: 0

    // Convert grid scroll position to thumb offset
    val thumbOffsetFromGrid by remember(gridState, totalItems) {
        derivedStateOf {
            if (trackHeight <= thumbSizePx || totalItems <= 0) return@derivedStateOf 0

            val firstVisibleIndex = gridState.firstVisibleItemIndex
            val scrollableHeight = trackHeight - thumbSizePx
            val progress = (firstVisibleIndex.toFloat() / totalItems).coerceIn(0f, 1f)
            (progress * scrollableHeight).toInt()
        }
    }

    // Convert thumb position to month index
    fun thumbOffsetToMonthIndex(offsetY: Int): Int {
        if (trackHeight <= thumbSizePx || totalItems <= 0) return 0

        val scrollableHeight = trackHeight - thumbSizePx
        val progress = (offsetY.toFloat() / scrollableHeight).coerceIn(0f, 1f)
        val targetItemIndex = (progress * totalItems).toInt()

        // Find which month this item belongs to
        return monthCumulativeCounts.indexOfLast { it <= targetItemIndex }
            .coerceIn(0, monthSummaries.lastIndex)
    }

    // Update current month index when dragging
    LaunchedEffect(isDragging, dragOffsetY) {
        if (isDragging) {
            val newMonthIndex = thumbOffsetToMonthIndex(dragOffsetY)
            if (newMonthIndex != currentMonthIndex) {
                currentMonthIndex = newMonthIndex
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    // Track visibility based on scroll activity
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress || isDragging }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    isVisible = true
                }
            }
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(gridState, isDragging) {
        snapshotFlow { gridState.isScrollInProgress || isDragging }
            .debounce(1500)
            .collect { stillActive ->
                if (!stillActive) {
                    isVisible = false
                }
            }
    }

    // Month label bubble (rendered separately to avoid clipping)
    val thumbY = if (isDragging) dragOffsetY else thumbOffsetFromGrid

    // Animate thumb position when not dragging
    val animatedThumbY by animateIntAsState(
        targetValue = thumbY,
        animationSpec = if (isDragging) tween(durationMillis = 0) else tween(durationMillis = 150),
        label = "thumbPosition"
    )

    if (isDragging && currentMonthIndex in monthSummaries.indices) {
        val monthLabel = remember(currentMonthIndex) {
            buildDateString(monthSummaries[currentMonthIndex].timeCreated)
        }

        Surface(
            modifier = modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = (TRACK_WIDTH_DP + 8).dp)
                .offset { IntOffset(x = 0, y = thumbY) },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            Text(
                text = monthLabel,
                modifier = Modifier
                    .widthIn(min = 100.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
        }
    }

    // Thumb and drag track
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .width(TRACK_WIDTH_DP.dp)
            .padding(vertical = 72.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .onSizeChanged { trackHeight = it.height },
            contentAlignment = Alignment.TopEnd
        ) {
            // Thumb pill
            Surface(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = animatedThumbY) }
                    .padding(end = 4.dp)
                    .size(width = THUMB_WIDTH_DP.dp, height = THUMB_HEIGHT_DP.dp)
                    .pointerInput(monthSummaries) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragOffsetY = thumbOffsetFromGrid
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                val monthIndex = thumbOffsetToMonthIndex(dragOffsetY)
                                isDragging = false
                                onScrollToMonth(monthIndex)
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onVerticalDrag = { _, dragAmount ->
                                dragOffsetY = (dragOffsetY + dragAmount.toInt())
                                    .coerceIn(0, (trackHeight - thumbSizePx).coerceAtLeast(0))
                            }
                        )
                    },
                shape = RoundedCornerShape(THUMB_WIDTH_DP.dp / 2),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrows_vertical),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
