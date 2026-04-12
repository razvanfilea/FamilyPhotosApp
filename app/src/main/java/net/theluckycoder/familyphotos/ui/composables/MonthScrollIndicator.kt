package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.TimelineLayout
import kotlin.ranges.coerceIn

@Composable
fun BoxScope.MonthScrollIndicator(
    isDragging: MutableState<Boolean>,
    gridState: LazyGridState,
    timelineLayout: TimelineLayout,
    onScrollToGridIndex: (gridIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (timelineLayout.monthSummaries.size < 2) return

    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val totalItems = timelineLayout.totalItemCount

    var isVisible by remember { mutableStateOf(false) }
    val trackHeight = remember { mutableIntStateOf(0) }
    var lastScrolledMonthIndex by remember { mutableIntStateOf(-1) }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val thumbHeightPx = with(density) { 56.dp.toPx() }
    val maxScrollPx = maxOf(0f, trackHeight.intValue.toFloat() - thumbHeightPx)
    val currentMaxScroll by rememberUpdatedState(maxScrollPx)

    LaunchedEffect(gridState, isDragging.value) {
        if (isDragging.value) {
            isVisible = true
        } else {
            snapshotFlow { gridState.isScrollInProgress }
                .collectLatest { isScrolling ->
                    if (isScrolling) {
                        isVisible = true
                    } else {
                        delay(2500)
                        isVisible = false
                    }
                }
        }
    }

    // Use maxVisibleIndex to ensure the drag scale matches the grid's scrollable scale
    val maxVisibleIndex by remember(gridState, totalItems) {
        derivedStateOf {
            val visibleItemsCount = gridState.layoutInfo.visibleItemsInfo.size
            (totalItems - visibleItemsCount).coerceAtLeast(1)
        }
    }

    val gridProgress by remember(gridState, maxVisibleIndex) {
        derivedStateOf {
            if (totalItems <= 0) 0f else {
                (gridState.firstVisibleItemIndex.toFloat() / maxVisibleIndex).coerceIn(0f, 1f)
            }
        }
    }

    val thumbOffsetY by animateFloatAsState(
        targetValue = if (isDragging.value) dragOffsetY else (gridProgress * currentMaxScroll),
        animationSpec = if (isDragging.value) snap() else tween(150),
        label = "thumbOffsetY"
    )

    AnimatedVisibility(
        visible = isVisible || isDragging.value,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(top = 128.dp, bottom = 64.dp)
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
                    .offset { IntOffset(x = 0, y = thumbOffsetY.toInt()) }
                    .size(width = 44.dp, height = 56.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging.value = true
                                dragOffsetY = thumbOffsetY
                                lastScrolledMonthIndex = -1
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { isDragging.value = false },
                            onDragCancel = { isDragging.value = false },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY =
                                    (dragOffsetY + dragAmount).coerceIn(0f, currentMaxScroll)

                                val progress =
                                    if (currentMaxScroll > 0) dragOffsetY / currentMaxScroll else 0f

                                // Find where we are in the overall timeline based on the drag
                                val targetItemIndex = (progress * maxVisibleIndex).toInt()

                                // Determine which month this item index falls into
                                val searchResult =
                                    timelineLayout.monthCumulativeCounts.binarySearch(
                                        targetItemIndex
                                    )
                                val monthIndex =
                                    (if (searchResult >= 0) searchResult else -(searchResult + 1) - 1)
                                        .coerceIn(0, timelineLayout.monthSummaries.lastIndex)

                                // ONLY trigger the scroll and haptics if we crossed into a new month
                                if (monthIndex != lastScrolledMonthIndex) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                    // Get the grid index of the month's header to snap to it
                                    val targetGridIndex =
                                        timelineLayout.monthCumulativeCounts.getOrNull(monthIndex)
                                            ?: 0

                                    onScrollToGridIndex(targetGridIndex)
                                    lastScrolledMonthIndex = monthIndex
                                }
                            }
                        )
                    },
                shape = RoundedCornerShape(topStartPercent = 80, bottomStartPercent = 80),
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
                        .padding(start = 4.dp)
                        .wrapContentSize(Alignment.Center),
                )
            }
        }
    }
}