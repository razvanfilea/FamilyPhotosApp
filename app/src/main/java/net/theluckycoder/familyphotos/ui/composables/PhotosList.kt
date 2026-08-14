package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.window.layout.WindowMetricsCalculator
import coil3.size.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.TimelineLayout
import net.theluckycoder.familyphotos.core.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.core.data.model.isVideo
import net.theluckycoder.familyphotos.ui.LocalOpeningPhotoId
import net.theluckycoder.familyphotos.ui.LocalSettingsDataStore
import net.theluckycoder.familyphotos.ui.LocalSharedTransitionScope
import net.theluckycoder.familyphotos.utils.buildDateString
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

private val PORTRAIT_ZOOM_LEVELS = intArrayOf(3, 4, 5, 7)
private val LANDSCAPE_ZOOM_LEVELS = intArrayOf(5, 8, 10, 13)
private val MAX_ZOOM_LEVEL_INDEX = PORTRAIT_ZOOM_LEVELS.size - 1

@Composable
private fun getZoomColumnCount(zoomIndex: Int): Int {
    val levels =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) PORTRAIT_ZOOM_LEVELS
        else LANDSCAPE_ZOOM_LEVELS

    return levels[zoomIndex.coerceIn(0, MAX_ZOOM_LEVEL_INDEX)]
}

private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_TITLE = "title"

@Composable
fun <T : Photo> PhotosList(
    photos: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    timelineLayout: TimelineLayout = TimelineLayout.EMPTY,
    selectedPhotoIds: SnapshotStateSet<Long> = remember { mutableStateSetOf() },
    headerContent: @Composable ColumnScope.() -> Unit = {},
    openPhoto: (index: Int, photoId: Long) -> Unit,
) = Box(Modifier.fillMaxSize()) {
    val coroutineScope = rememberCoroutineScope()

    // Apply offset to timeline layout to account for header at index 0
    val layout = timelineLayout.withOffset(1)
    var showMonthOverlay by remember { mutableStateOf(false) }
    val isThumbDragging = remember { mutableStateOf(false) }
    val hasSelection = remember { derivedStateOf { selectedPhotoIds.isNotEmpty() } }

    BackHandler(enabled = hasSelection.value) {
        selectedPhotoIds.clear()
    }

    val settingsDataStore = LocalSettingsDataStore.current
    val zoomIndex by settingsDataStore.zoomLevel.collectAsState()
    var appliedZoomIndex by remember { mutableIntStateOf(zoomIndex) }

    LaunchedEffect(zoomIndex) {
        if (zoomIndex != appliedZoomIndex) {
            delay(150.milliseconds)
            appliedZoomIndex = zoomIndex
        }
    }

    val columnCount = getZoomColumnCount(appliedZoomIndex)
    val coilImageSize = getCoilImageSize(columnCount)

    val photosModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .padding(0.5.dp)

    val photoDragModifier = Modifier.photoGridDrag(
        lazyGridState = gridState,
        selectedIds = selectedPhotoIds,
        items = photos.itemSnapshotList.items,
        timelineLayout = layout,
    )

    val openingPhotoIdState = LocalOpeningPhotoId.current

    LazyVerticalGrid(
        state = gridState,
        flingBehavior = rememberGalleryFlingBehavior(),
        modifier = Modifier
            .fillMaxSize()
            .then(with(LocalSharedTransitionScope.current) {
                Modifier.skipToLookaheadSize(enabled = { true })
            })
            .detectZoomIn(
                zoomIndex = zoomIndex,
                maxZoomIndex = MAX_ZOOM_LEVEL_INDEX,
                onZoomChange = { coroutineScope.launch { settingsDataStore.setPhotosZoomLevel(it) } },
                onGestureEnd = { appliedZoomIndex = zoomIndex })
            .then(photoDragModifier)
            .then(modifier)
            .testTag("photos_list"),
        columns = GridCells.Fixed(columnCount),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        items(count = layout.totalItemCount, key = { gridIndex ->
            if (gridIndex == 0) "top_header"
            else {
                val header = layout.getHeaderAt(gridIndex)
                if (header != null) {
                    "month_header_${header.timeCreated}_$gridIndex"
                } else {
                    val pagingIndex = layout.pagingIndexOf(gridIndex)
                    val photoId = if (pagingIndex in 0 until photos.itemCount) photos.peek(pagingIndex)?.id else null
                    if (photoId != null) "photo_${photoId}_$gridIndex" else "placeholder_$gridIndex"
                }
            }
        }, contentType = { gridIndex ->
            when {
                gridIndex == 0 -> CONTENT_TYPE_HEADER
                layout.isHeader(gridIndex) -> CONTENT_TYPE_TITLE
                else -> {
                    val pagingIndex = layout.pagingIndexOf(gridIndex)
                    if (pagingIndex in 0 until photos.itemCount && photos.peek(pagingIndex) != null) "photo" else "placeholder"
                }
            }
        }, span = { gridIndex ->
            GridItemSpan(if (gridIndex == 0 || layout.isHeader(gridIndex)) columnCount else 1)
        }) { gridIndex ->
            if (gridIndex == 0) {
                // Header content at index 0
                Column(content = headerContent)
            } else {
                val headerSummary = layout.getHeaderAt(gridIndex)

                if (headerSummary != null) {
                    MonthSeparatorHeader(
                        summary = headerSummary,
                        selectedPhotoIds = selectedPhotoIds,
                        photos = photos,
                        timelineLayout = layout,
                        gridIndex = gridIndex,
                        onShowMonthPicker = { showMonthOverlay = true })
                } else {
                    val pagingIndex = layout.pagingIndexOf(gridIndex)
                    val photo =
                        if (pagingIndex in 0 until photos.itemCount) photos[pagingIndex] else null

                    if (photo != null) {
                        val sharedBoundsModifier =
                            if (photo.id == openingPhotoIdState.value) {
                                Modifier.photoSharedBounds(photo.id)
                            } else {
                                Modifier
                            }

                        val itemModifier = Modifier
                            .then(sharedBoundsModifier)
                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                            .then(photosModifier)

                        PhotoListItem(
                            modifier = itemModifier,
                            photo = photo,
                            inSelectionMode = hasSelection.value,
                            selectedPhotoIds = selectedPhotoIds,
                            requestedPhotoSize = coilImageSize,
                            openPhoto = {
                                openingPhotoIdState.value = photo.id
                                openPhoto(pagingIndex, photo.id)
                            },
                        )
                    } else {
                        // Gray placeholder box - Paging will auto-fetch
                        Box(photosModifier.background(Color.DarkGray))
                    }
                }
            }
        }
    }

    if (showMonthOverlay && layout.isNotEmpty()) {
        MonthPickerBottomSheet(
            timelineLayout = layout,
            onMonthSelected = { summary ->
                showMonthOverlay = false
                val gridIndex = layout.gridIndexOf(summary)
                if (gridIndex >= 0) {
                    scrollToGridIndex(gridState, gridIndex)
                }
            },
            onDismiss = { showMonthOverlay = false },
        )
    }

    if (!hasSelection.value) {
        MonthScrollIndicator(
            isDragging = isThumbDragging,
            gridState = gridState,
            timelineLayout = layout,
            onScrollToGridIndex = { gridIndex ->
                scrollToGridIndex(gridState, gridIndex)
            }
        )
    }
}

@Composable
private fun <T : Photo> MonthSeparatorHeader(
    summary: MonthSummary,
    selectedPhotoIds: SnapshotStateSet<Long>,
    photos: LazyPagingItems<T>,
    timelineLayout: TimelineLayout,
    gridIndex: Int,
    onShowMonthPicker: () -> Unit
) {
    val text = remember(summary.timeCreated) { buildDateString(summary.timeCreated) }

    val allSelected by remember(summary, gridIndex) {
        derivedStateOf {
            if (selectedPhotoIds.isEmpty()) return@derivedStateOf false

            val startPagingIndex = timelineLayout.pagingIndexOf(gridIndex + 1)
            val end = minOf(startPagingIndex + summary.photoCount, photos.itemCount)

            var hasValidPhotos = false

            for (i in startPagingIndex until end) {
                val id = photos.peek(i)?.id ?: continue
                hasValidPhotos = true

                if (!selectedPhotoIds.contains(id)) return@derivedStateOf false
            }

            hasValidPhotos
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        IconButton(
            modifier = Modifier.testTag("month_select_button"), onClick = {
                val startPagingIndex = timelineLayout.pagingIndexOf(gridIndex + 1)
                val end = minOf(startPagingIndex + summary.photoCount, photos.itemCount)

                if (allSelected) {
                    for (i in startPagingIndex until end) {
                        photos.peek(i)?.id?.let { selectedPhotoIds.remove(it) }
                    }
                } else {
                    for (i in startPagingIndex until end) {
                        photos.peek(i)?.id?.let { selectedPhotoIds.add(it) }
                    }
                }
            }) {
            Icon(
                painter = painterResource(
                    if (allSelected) R.drawable.radio_button_checked
                    else R.drawable.radio_button_checked_outline
                ), contentDescription = stringResource(R.string.cd_select_all)
            )
        }

        if (timelineLayout.monthSummaries.size > 2) {
            IconButton(onClick = onShowMonthPicker) {
                Icon(
                    painter = painterResource(R.drawable.ic_month_picker),
                    contentDescription = stringResource(R.string.cd_open_month_picker)
                )
            }
        }
    }
}

@Composable
fun <T : Photo> PhotoListItem(
    modifier: Modifier,
    photo: T,
    inSelectionMode: Boolean,
    selectedPhotoIds: SnapshotStateSet<Long>,
    openPhoto: (id: Long) -> Unit,
    requestedPhotoSize: Size? = null,
) {
    val isVideo = remember(photo.id) { photo.isVideo }
    val selected by remember(photo.id) { derivedStateOf { selectedPhotoIds.contains(photo.id) } }

    SelectablePhoto(
        modifier = modifier.testTag("photo_item"),
        inSelectionMode = inSelectionMode,
        selected = selected,
        onClick = { openPhoto(photo.id) },
        onSelect = { selectedPhotoIds += photo.id },
        onDeselect = { selectedPhotoIds -= photo.id }) {
        CoilPhoto(
            modifier = Modifier.fillMaxSize(),
            photo = photo,
            preview = true,
            contentScale = ContentScale.Crop,
            requestedPhotoSize = requestedPhotoSize,
        )

        if (isVideo) {
            Icon(
                modifier = Modifier
                    .padding(4.dp)
                    .size(20.dp)
                    .align(Alignment.TopEnd),
                painter = painterResource(R.drawable.ic_play_circle_filled),
                contentDescription = null
            )
        }

        if (photo is LocalPhoto && photo.isSavedToCloud) {
            Icon(
                modifier = Modifier
                    .padding(4.dp)
                    .size(20.dp)
                    .align(Alignment.BottomEnd),
                painter = painterResource(R.drawable.ic_cloud_done_filled),
                contentDescription = null
            )
        }
    }
}

private const val SCROLL_TARGET_POSITION = 0.2f // Position separator at 20% from top

private fun scrollToGridIndex(
    gridState: LazyGridState, gridIndex: Int
) {
    val viewportHeight = gridState.layoutInfo.viewportSize.height

    // A negative offset moves the item DOWN from the top of the viewport
    val offset = -(viewportHeight * SCROLL_TARGET_POSITION).toInt()

    gridState.requestScrollToItem(gridIndex, offset)
}

@Composable
private fun getCoilImageSize(columnCount: Int): Size {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return remember(configuration, density, columnCount) {
        with(density) {
            val windowMetrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(context)
            val windowWidthPx = windowMetrics.bounds.width()

            val rawItemPx = windowWidthPx / columnCount
            val px = rawItemPx.roundUpTo64()
            Size(px, px)
        }
    }
}

private fun Int.roundUpTo64(): Int {
    if (this <= 0) return 64
    return (ceil(this.toDouble() / 64.0) * 64).toInt()
}

@Composable
private fun rememberGalleryFlingBehavior(): FlingBehavior {
    val defaultFling = ScrollableDefaults.flingBehavior()
    return remember(defaultFling) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                // Boost velocity for faster, Google Photos-like scrolling
                return with(defaultFling) {
                    performFling(initialVelocity * 1.5f)
                }
            }
        }
    }
}