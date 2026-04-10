package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.DataOrSeparator
import net.theluckycoder.familyphotos.data.model.LazyPagingData
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

private const val CONTENT_TYPE_TITLE = "title"
private const val CONTENT_TYPE_HEADER = "header"

@Composable
fun <T : Photo> PhotosList(
    photos: LazyPagingData<T>,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    monthSummaries: List<MonthSummary> = emptyList(),
    topBarContent: @Composable () -> Unit = {},
    listHeaderContent: @Composable () -> Unit = {},
    openPhoto: (index: Int) -> Unit,
) = Box(Modifier.fillMaxSize()) {
    val coroutineScope = rememberCoroutineScope()
    var showMonthOverlay by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }

    val hasSelection = remember { derivedStateOf { selectedPhotoIds.isNotEmpty() } }

    BackHandler(enabled = hasSelection.value) {
        selectedPhotoIds.clear()
    }

    val settingsDataStore = LocalSettingsDataStore.current
    val zoomIndex by settingsDataStore.zoomLevelState.collectAsState()
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
            items = photos.itemSnapshotList,
        ) else Modifier

    // Debounced scroll state to prevent mass recomposition during scroll-pause-scroll sequences
    var sharedBoundsEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(gridState) {
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
        item(
            key = "header", span = { GridItemSpan(columnCount) }, contentType = CONTENT_TYPE_HEADER
        ) {
            Column {
                topBarContent()
                listHeaderContent()
            }
        }

        items(
            count = photos.itemCount,
            key = photos.itemKey {
                when (it) {
                    is DataOrSeparator.Data<T> -> it.data.id
                    is DataOrSeparator.Separator<T> -> it.text
                }
            },
            contentType = photos.itemContentType {
                when (it) {
                    is DataOrSeparator.Data<T> -> null
                    is DataOrSeparator.Separator<T> -> CONTENT_TYPE_TITLE
                }
            },
            span = { index ->
                val span = if (photos.peek(index) is DataOrSeparator.Separator) columnCount else 1
                GridItemSpan(span)
            }) { index ->
            when (val item = photos[index]) {
                is DataOrSeparator.Data<T> -> {
                    val photo = item.data
                    // If scrolling, use an empty Modifier. If idle, use the expensive shared bounds.
                    val sharedBoundsModifier = if (!sharedBoundsEnabled && !hasSelection.value) {
                        Modifier
                    } else {
                        Modifier.photoSharedBounds(photo.id)
                    }

                    val modifier = Modifier
                        .then(sharedBoundsModifier)
                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                        .then(photosModifier)

                    if (highZoomLevel) {
                        CoilPhoto(
                            modifier = modifier.clickable(onClick = { openPhoto(index) }),
                            photo = photo,
                            preview = true,
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        PhotoListItem(
                            modifier = modifier,
                            photo = photo,
                            selectedPhotoIds = selectedPhotoIds,
                            openPhoto = { openPhoto(index) },
                        )
                    }
                }

                is DataOrSeparator.Separator<T> -> {
                    MonthSeparatorHeader(
                        text = item.text,
                        highZoomLevel = highZoomLevel,
                        selectedPhotoIds = selectedPhotoIds,
                        photos = photos,
                        separatorIndex = index,
                        onShowMonthPicker = { showMonthOverlay = true }
                    )
                }

                null -> {
                    // Draw on empty rectangle
                    Box(photosModifier)
                }
            }
        }
    }

    val containsLocalPhotos = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(photos.itemSnapshotList.items) {
        if (containsLocalPhotos.value == null) {
            val photo = photos.itemSnapshotList.items.asSequence()
                .take(10).firstNotNullOfOrNull { it as? DataOrSeparator.Data<*> }

            if (photo != null) {
                containsLocalPhotos.value = photo.data is LocalPhoto
            }
        }
    }

    containsLocalPhotos.value?.let {
        PhotosSelectionBar(selectedPhotoIds) {
            PhotoUtilitiesActions(it, selectedPhotoIds)
        }
    }

    if (showMonthOverlay && monthSummaries.isNotEmpty()) {
        MonthPickerBottomSheet(
            monthSummaries = monthSummaries,
            onMonthSelected = { summary ->
                showMonthOverlay = false
                scrollToMonth(monthSummaries, summary, coroutineScope, gridState, photos)
            },
            onDismiss = { showMonthOverlay = false },
        )
    }
}

@Composable
private fun <T : Photo> MonthSeparatorHeader(
    text: String,
    highZoomLevel: Boolean,
    selectedPhotoIds: SnapshotStateSet<Long>,
    photos: LazyPagingData<T>,
    separatorIndex: Int,
    onShowMonthPicker: () -> Unit
) {
    // Cache photo IDs - only recompute when paging snapshot changes
    val monthPhotoIds = remember(photos.itemSnapshotList) {
        buildList {
            var i = separatorIndex + 1
            while (i < photos.itemCount) {
                when (val item = photos.peek(i)) {
                    is DataOrSeparator.Data<*> -> add(item.data.id)
                    is DataOrSeparator.Separator<*> -> break
                    null -> {}
                }
                i++
            }
        }
    }

    // Derive "all selected" reactively - only recomputes when selectedPhotoIds changes
    val allSelected by remember(monthPhotoIds) {
        derivedStateOf {
            monthPhotoIds.isNotEmpty() && monthPhotoIds.all { it in selectedPhotoIds }
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

        IconButton(onClick = {
            if (allSelected) {
                selectedPhotoIds.removeAll(monthPhotoIds.toSet())
            } else {
                selectedPhotoIds.addAll(monthPhotoIds)
            }
        }) {
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
        modifier = modifier,
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

private fun <T : Photo> scrollToMonth(
    monthSummaries: List<MonthSummary>,
    summary: MonthSummary,
    coroutineScope: CoroutineScope,
    gridState: LazyGridState,
    photos: LazyPagingData<T>
) {
    val monthIndex = monthSummaries.indexOf(summary)
    if (monthIndex < 0) return

    // Count only photos (no separators) to get an approximate position
    var photosBeforeTarget = 0
    for (i in 0 until monthIndex) {
        photosBeforeTarget += monthSummaries[i].photoCount
    }

    val targetText = buildDateString(summary.timeCreated)
    coroutineScope.launch {
        // Undershoot by scrolling without counting separators
        gridState.scrollToItem(1 + photosBeforeTarget)

        // Search forward in visible items for the separator
        val visibleItems = gridState.layoutInfo.visibleItemsInfo
        for (itemInfo in visibleItems) {
            val pagingIdx = itemInfo.index - 1
            if (pagingIdx >= 0) {
                val item = photos.peek(pagingIdx)
                if (item is DataOrSeparator.Separator && item.text == targetText) {
                    gridState.scrollToItem(itemInfo.index)
                    return@launch
                }
            }
        }

        // If not found in visible items, search forward in paging data
        val searchEnd = minOf(photos.itemCount - 1, photosBeforeTarget + monthIndex + 20)
        for (pagingIdx in (gridState.firstVisibleItemIndex - 1)..searchEnd) {
            val item = photos.peek(pagingIdx)
            if (item is DataOrSeparator.Separator && item.text == targetText) {
                gridState.scrollToItem(pagingIdx + 1)
                return@launch
            }
        }
    }
}
