package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.TimelineLayout
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.data.model.db.isVideo
import net.theluckycoder.familyphotos.ui.LocalSettingsDataStore
import net.theluckycoder.familyphotos.utils.buildDateString

private val PORTRAIT_ZOOM_LEVELS = intArrayOf(4, 5, 7, 9)
private val LANDSCAPE_ZOOM_LEVELS = intArrayOf(8, 10, 13, 17)
private val MAX_ZOOM_LEVEL_INDEX = PORTRAIT_ZOOM_LEVELS.size - 1

private const val HIGH_ZOOM_LEVEL = 3

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
    headerContent: @Composable ColumnScope.() -> Unit = {},
    openPhoto: (index: Int) -> Unit,
) = Box(Modifier.fillMaxSize()) {
    val coroutineScope = rememberCoroutineScope()

    // Apply offset to timeline layout to account for header at index 0
    val layout = timelineLayout.withOffset(1)
    var showMonthOverlay by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }
    val isThumbDragging = remember { mutableStateOf(false) }

    val hasSelection = remember { derivedStateOf { selectedPhotoIds.isNotEmpty() } }

    BackHandler(enabled = hasSelection.value) {
        selectedPhotoIds.clear()
    }

    val settingsDataStore = LocalSettingsDataStore.current
    val zoomIndex by settingsDataStore.zoomLevel.collectAsState()
    val highZoomLevel = zoomIndex >= HIGH_ZOOM_LEVEL
    val columnCount = getZoomColumnCount(zoomIndex)
    LaunchedEffect(zoomIndex) {
        if (zoomIndex >= HIGH_ZOOM_LEVEL) {
            selectedPhotoIds.clear()
        }
    }

    val photosModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .padding(0.5.dp)

    val photoDragModifier = if (!highZoomLevel) Modifier
        .photoGridDrag(
            lazyGridState = gridState,
            selectedIds = selectedPhotoIds,
            items = photos.itemSnapshotList.items,
            timelineLayout = layout,
        ) else Modifier

    // Debounced scroll state to prevent mass recomposition during scroll-pause-scroll sequences
    var sharedBoundsEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(gridState, isThumbDragging.value) {
        if (isThumbDragging.value) {
            sharedBoundsEnabled = false
        } else {
            snapshotFlow { gridState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { isScrolling ->
                    if (isScrolling) {
                        sharedBoundsEnabled = false
                    } else {
                        delay(150) // Only re-enable after 150ms idle
                        sharedBoundsEnabled = true
                    }
                }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .detectZoomIn(
                zoomIndex = zoomIndex,
                maxZoomIndex = MAX_ZOOM_LEVEL_INDEX,
                onZoomChange = { coroutineScope.launch { settingsDataStore.setPhotosZoomLevel(it) } }
            )
            .then(photoDragModifier)
            .then(modifier)
            .testTag("photos_list"),
        columns = GridCells.Fixed(columnCount),
    ) {
        items(
            count = layout.totalItemCount,
            key = { gridIndex ->
                if (gridIndex == 0) "header"
                else layout.getHeaderAt(gridIndex)?.timeCreated
                    ?: run {
                        val pagingIndex = layout.pagingIndexOf(gridIndex)
                        if (pagingIndex in 0 until photos.itemCount) photos.peek(pagingIndex)?.id else null
                    }
                    ?: gridIndex
            },
            contentType = { gridIndex ->
                when {
                    gridIndex == 0 -> CONTENT_TYPE_HEADER
                    layout.isHeader(gridIndex) -> CONTENT_TYPE_TITLE
                    else -> null
                }
            },
            span = { gridIndex ->
                GridItemSpan(if (gridIndex == 0 || layout.isHeader(gridIndex)) columnCount else 1)
            }
        ) { gridIndex ->
            if (gridIndex == 0) {
                // Header content at index 0
                Column(content = headerContent)
            } else {
                val headerSummary = layout.getHeaderAt(gridIndex)

                if (headerSummary != null) {
                    MonthSeparatorHeader(
                        summary = headerSummary,
                        highZoomLevel = highZoomLevel,
                        selectedPhotoIds = selectedPhotoIds,
                        photos = photos,
                        timelineLayout = layout,
                        gridIndex = gridIndex,
                        onShowMonthPicker = { showMonthOverlay = true }
                    )
                } else {
                    val pagingIndex = layout.pagingIndexOf(gridIndex)
                    val photo = if (pagingIndex in 0 until photos.itemCount) photos[pagingIndex] else null

                    if (photo != null) {
                        // If scrolling, use an empty Modifier. If idle, use the expensive shared bounds.
                        val sharedBoundsModifier = if (!sharedBoundsEnabled && !hasSelection.value) {
                            Modifier
                        } else {
                            Modifier.photoSharedBounds(photo.id)
                        }

                        val itemModifier = Modifier
                            .then(sharedBoundsModifier)
                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                            .then(photosModifier)

                        if (highZoomLevel) {
                            CoilPhoto(
                                modifier = itemModifier.clickable(onClick = { openPhoto(pagingIndex) }),
                                photo = photo,
                                preview = true,
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            PhotoListItem(
                                modifier = itemModifier,
                                photo = photo,
                                selectedPhotoIds = selectedPhotoIds,
                                openPhoto = { openPhoto(pagingIndex) },
                            )
                        }
                    } else {
                        // Gray placeholder box - Paging will auto-fetch
                        Box(photosModifier.background(Color.DarkGray))
                    }
                }
            }
        }
    }

    val containsLocalPhotos = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(photos.itemCount > 0) {
        if (containsLocalPhotos.value == null && photos.itemCount > 0) {
            val photo = photos.itemSnapshotList.items.asSequence()
                .take(10).firstNotNullOfOrNull { it }

            if (photo != null) {
                containsLocalPhotos.value = photo is LocalPhoto
            }
        }
    }

    containsLocalPhotos.value?.let {
        PhotosSelectionBar(selectedPhotoIds) {
            PhotoUtilitiesActions(it, selectedPhotoIds)
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

    MonthScrollIndicator(
        isDragging = isThumbDragging,
        gridState = gridState,
        timelineLayout = layout,
        onScrollToGridIndex = { gridIndex ->
            scrollToGridIndex(gridState, gridIndex)
        }
    )
}

@Composable
private fun <T : Photo> MonthSeparatorHeader(
    summary: MonthSummary,
    highZoomLevel: Boolean,
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
            style = if (highZoomLevel) MaterialTheme.typography.headlineSmall
            else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium
        )

        IconButton(
            modifier = Modifier.testTag("month_select_button"),
            onClick = {
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
            }
        ) {
            Icon(
                painter = painterResource(
                    if (allSelected) R.drawable.radio_button_checked
                    else R.drawable.radio_button_checked_outline
                ),
                contentDescription = "Select all"
            )
        }

        IconButton(onClick = onShowMonthPicker) {
            Icon(
                painter = painterResource(R.drawable.ic_month_picker),
                contentDescription = "Open month picker"
            )
        }
    }
}

@Composable
fun PhotoListItem(
    modifier: Modifier,
    photo: Photo,
    selectedPhotoIds: SnapshotStateSet<Long>,
    openPhoto: (id: Long) -> Unit
) {
    val isVideo = remember(photo.id) { photo.isVideo }

    SelectablePhoto(
        modifier = modifier.testTag("photo_item"),
        inSelectionMode = selectedPhotoIds.isNotEmpty(),
        selected = selectedPhotoIds.contains(photo.id),
        onClick = { openPhoto(photo.id) },
        onSelect = { selectedPhotoIds += photo.id },
        onDeselect = { selectedPhotoIds -= photo.id }
    ) {
        CoilPhoto(
            photo = photo,
            preview = true,
            contentScale = ContentScale.Crop,
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
    gridState: LazyGridState,
    gridIndex: Int
) {
    val viewportHeight = gridState.layoutInfo.viewportSize.height

    // A negative offset moves the item DOWN from the top of the viewport
    val offset = -(viewportHeight * SCROLL_TARGET_POSITION).toInt()

    gridState.requestScrollToItem(gridIndex, offset)
}
