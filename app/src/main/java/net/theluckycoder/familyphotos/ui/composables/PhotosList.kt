package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.DataOrSeparator
import net.theluckycoder.familyphotos.data.model.LazyPagingData
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.data.model.db.isVideo
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

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

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
)
@Composable
fun <T : Photo> PhotosList(
    photos: LazyPagingData<T>,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    topBarContent: @Composable () -> Unit = {},
    listHeaderContent: @Composable () -> Unit = {},
    openPhoto: (index: Int) -> Unit,
    mainViewModel: MainViewModel = viewModel()
) = Box(Modifier.fillMaxSize()) {
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }

    val hasSelection = remember { derivedStateOf { selectedPhotoIds.isNotEmpty() } }

    BackHandler(enabled = hasSelection.value) {
        selectedPhotoIds.clear()
    }

    val zoomIndexState = mainViewModel.zoomIndexState
    val highZoomLevel = zoomIndexState.intValue >= HIGH_ZOOM_LEVEL
    val columnCount = getZoomColumnCount(zoomIndexState.intValue)
    LaunchedEffect(zoomIndexState.intValue) {
        mainViewModel.settingsStore.setPhotosZoomLevel(zoomIndexState.intValue)
        if (zoomIndexState.intValue >= HIGH_ZOOM_LEVEL) {
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

    val isScrolling = remember { derivedStateOf { gridState.isScrollInProgress } }

    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .detectZoomIn(zoomIndexState, MAX_ZOOM_LEVEL_INDEX)
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
                    val sharedBoundsModifier = if (isScrolling.value) {
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
                    Text(
                        modifier = Modifier.padding(12.dp),
                        text = item.text,
                        style = if (highZoomLevel) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                null -> {}
            }
        }
    }

    val containsLocalPhotos = remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(photos.itemSnapshotList.items) {
        if (containsLocalPhotos.value == null) {
            val photo = photos.itemSnapshotList.items.asSequence()
                .take(10)
                .mapNotNull { it as? DataOrSeparator.Data<*> }
                .firstOrNull()

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
