package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.TimelineLayout

@Composable
fun BoxScope.MonthScrollIndicator(
    gridState: LazyGridState,
    timelineLayout: TimelineLayout,
    onScrollToGridIndex: (gridIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (timelineLayout.monthSummaries.size < 2) return

    val monthSummaries = timelineLayout.monthSummaries
    val totalItems = timelineLayout.totalGridItemCount

    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }
    val trackHeight = remember { mutableIntStateOf(0) }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var lastScrolledMonthIndex by remember { mutableIntStateOf(-1) }

    val thumbHeightPx = with(density) { 56.dp.toPx() }
    val maxScrollPx = maxOf(0f, trackHeight.intValue.toFloat() - thumbHeightPx)

    LaunchedEffect(gridState, isDragging) {
        if (isDragging) {
            isVisible = true
        } else {
            snapshotFlow { gridState.isScrollInProgress }
                .collectLatest { isScrolling ->
                    if (isScrolling) {
                        isVisible = true
                    } else {
                        // Wait 2.5 seconds after scrolling stops before hiding
                        delay(2500)
                        isVisible = false
                    }
                }
        }
    }

    val gridProgress by remember(gridState, totalItems) {
        derivedStateOf {
            if (totalItems <= 0) 0f else {
                (gridState.firstVisibleItemIndex.toFloat() / totalItems).coerceIn(0f, 1f)
            }
        }
    }

    val thumbY by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetY else (gridProgress * maxScrollPx),
        animationSpec = if (isDragging) tween(0) else tween(150),
        label = "thumbPosition"
    )

    AnimatedVisibility(
        visible = isVisible || isDragging,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(vertical = 72.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp)
                .onSizeChanged { trackHeight.intValue = it.height },
            contentAlignment = Alignment.TopEnd
        ) {
            Surface(
                modifier = Modifier
                    .testTag("month_scroll_indicator")
                    .offset { IntOffset(x = 0, y = thumbY.toInt()) }
                    .size(width = 44.dp, height = 56.dp)
                    .pointerInput(monthSummaries, maxScrollPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                // Snap initial drag to current visual position
                                dragOffsetY = gridProgress * maxScrollPx
                                lastScrolledMonthIndex = -1
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY = (dragOffsetY + dragAmount).coerceIn(0f, maxScrollPx)

                                // Convert offset back to list index
                                val progress =
                                    if (maxScrollPx > 0) dragOffsetY / maxScrollPx else 0f
                                val targetItemIndex = (progress * totalItems).toInt()

                                val searchResult =
                                    timelineLayout.monthCumulativeCounts.binarySearch(
                                        targetItemIndex
                                    )
                                val monthIndex =
                                    (if (searchResult >= 0) searchResult else -(searchResult + 1) - 1)
                                        .coerceIn(0, monthSummaries.lastIndex)

                                if (monthIndex != lastScrolledMonthIndex) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val gridIndex =
                                        timelineLayout.monthCumulativeCounts.getOrNull(monthIndex)
                                            ?: 0
                                    onScrollToGridIndex(gridIndex)
                                    lastScrolledMonthIndex = monthIndex
                                }
                            }
                        )
                    },
                shape = RoundedCornerShape(topStartPercent = 75, bottomStartPercent = 75),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrows_vertical),
                    contentDescription = "Scroll Timeline",
                    modifier = Modifier
                        .size(22.dp)
                        // Pad slightly to center the icon visually within the half-circle
                        .padding(start = 4.dp)
                        .wrapContentSize(Alignment.Center),
                )
            }
        }
    }
}
