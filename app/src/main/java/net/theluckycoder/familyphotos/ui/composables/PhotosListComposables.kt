package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.LocalSharedTransitionScope
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import kotlin.time.ExperimentalTime

private val PORTRAIT_ZOOM_LEVELS = intArrayOf(4, 5, 7)
private val LANDSCAPE_ZOOM_LEVELS = intArrayOf(8, 10, 14)
private val MAX_ZOOM_LEVEL_INDEX = PORTRAIT_ZOOM_LEVELS.size - 1

@Composable
private fun getZoomColumnCount(zoomIndex: Int): Int {
    val levels = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT)
        PORTRAIT_ZOOM_LEVELS
    else
        LANDSCAPE_ZOOM_LEVELS

    return levels[zoomIndex.coerceIn(0, MAX_ZOOM_LEVEL_INDEX)]
}

@Composable
fun <T : Photo> PhotoListWithViewer(
    gridState: LazyGridState,
    photos: LazyPagingItems<T>,
    modifier: Modifier = Modifier,
    headerContent: @Composable () -> Unit = {},
    memoriesContent: @Composable ColumnScope.() -> Unit = {},
    mainViewModel: MainViewModel = viewModel(),
) {
    val openedPhotoIndex = remember { mutableStateOf<Int?>(null) }
    val photoIndex = openedPhotoIndex.value

    LaunchedEffect(photoIndex) {
        mainViewModel.showBars.value = photoIndex == null
    }

    AnimatedContent(photoIndex == null, modifier) { targetState ->

        CompositionLocalProvider(LocalNavAnimatedContentScope provides this@AnimatedContent) {
            if (targetState) {
                PhotosList(
                    gridState = gridState,
                    photos = photos,
                    headerContent = headerContent,
                    memoriesContent = memoriesContent,
                    zoomIndexState = mainViewModel.zoomIndexState,
                    openPhoto = { openedPhotoIndex.value = it },
                )
            } else {
                val onClose = { openedPhotoIndex.value = null }
                BackHandler(onBack = onClose)

                val index = remember { photoIndex!! }

                PhotosViewer(
                    photos,
                    index,
                    photoViewModel = viewModel(),
                    onClose,
                )
            }
        }
    }

    val zoomIndex = mainViewModel.zoomIndexState.intValue
    LaunchedEffect(zoomIndex) {
        mainViewModel.settingsStore.setPhotosZoomLevel(zoomIndex)
    }
}

private const val CONTENT_TYPE_PHOTO = 1
private const val CONTENT_TYPE_TITLE = 2
private const val CONTENT_TYPE_HEADER = 3

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalTime::class
)
@Composable
private fun <T : Photo> PhotosList(
    gridState: LazyGridState,
    headerContent: @Composable () -> Unit,
    memoriesContent: @Composable ColumnScope.() -> Unit = {},
    photos: LazyPagingItems<T>,
    zoomIndexState: MutableIntState,
    openPhoto: (index: Int) -> Unit,
) = Column(Modifier.fillMaxSize()) {

    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }

    BackHandler(enabled = selectedPhotoIds.isNotEmpty()) {
        selectedPhotoIds.clear()
    }

    AnimatedVisibility(
        visible = selectedPhotoIds.isNotEmpty(),
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                IconButton(onClick = {
                    selectedPhotoIds.clear()
                }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                }
            },
            title = {
                Row {
                    VerticallyAnimatedInt(targetState = selectedPhotoIds.size) { count ->
                        Text("$count ")
                    }

                    Text(stringResource(R.string.action_selected))
                }
            },
            actions = {
                val isLocalPhoto =
                    remember { photos.itemSnapshotList.items.firstOrNull() is LocalPhoto }
                PhotoUtilitiesActions(isLocalPhoto, selectedPhotoIds)
            },
        )
    }

    val columnCount = getZoomColumnCount(zoomIndexState.intValue)

    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .detectZoomIn(zoomIndexState, MAX_ZOOM_LEVEL_INDEX)
            .photoGridDrag(
                lazyGridState = gridState,
                selectedIds = selectedPhotoIds,
                items = photos.itemSnapshotList,
            ),
        columns = GridCells.Fixed(columnCount)
    ) {
        item(
            key = "header",
            span = { GridItemSpan(columnCount) },
            contentType = CONTENT_TYPE_HEADER
        ) {
            Column {
                if (selectedPhotoIds.isEmpty()) {
                    headerContent()
                }
                memoriesContent()
            }
        }

        for (index in 0..<photos.itemCount) {
            val photo = photos.peek(index)
            if (photo == null) {
                //item {}
                continue
            }

            val headerDate = MainViewModel.computeSeparatorText(
                photos.itemSnapshotList.getOrNull(index - 1),
                photo
            )

            if (headerDate != null) {
                item(
                    key = headerDate,
                    span = { GridItemSpan(columnCount) },
                    contentType = { CONTENT_TYPE_TITLE }) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
                        text = headerDate,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            item(
                key = photo.id,
                contentType = CONTENT_TYPE_PHOTO
            ) {
                val photo = photos[index]!!
                val modifier = with(LocalSharedTransitionScope.current) {
                    Modifier
                        .sharedBounds(
                            rememberSharedContentState(key = photo.id),
                            animatedVisibilityScope = LocalNavAnimatedContentScope.current
                        )
                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                        .aspectRatio(1f)
                        .padding(0.5.dp)
                }

                PhotoItem(
                    modifier = modifier,
                    photo = photo,
                    selectedPhotoIds = selectedPhotoIds,
                    openPhoto = { openPhoto(index) },
                )
            }
        }
    }
}

@Composable
private fun PhotoItem(
    modifier: Modifier,
    photo: Photo,
    selectedPhotoIds: SnapshotStateSet<Long>,
    openPhoto: (id: Long) -> Unit
) {
    val isVideo = remember(photo) { photo.isVideo }

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
                painter = painterResource(R.drawable.ic_cloud_done_outline),
                contentDescription = null
            )
        }
    }
}
