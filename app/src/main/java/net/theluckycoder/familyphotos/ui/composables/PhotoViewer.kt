package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Size
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.LocalPhoto
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.Photo
import net.theluckycoder.familyphotos.data.model.getPreviewUri
import net.theluckycoder.familyphotos.data.model.getUri
import net.theluckycoder.familyphotos.data.model.isVideo
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.MovePhotosNav
import net.theluckycoder.familyphotos.ui.UploadPhotosNav
import net.theluckycoder.familyphotos.ui.composables.player.VideoPlayer
import net.theluckycoder.familyphotos.ui.dialog.rememberDeletePhotosDialog
import net.theluckycoder.familyphotos.ui.dialog.rememberNetworkPhotoInfoDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewerViewModel
import kotlin.collections.mutableListOf


@Composable
fun <T : Photo> PhotosViewer(
    lazyPagingItems: LazyPagingItems<out T>,
    initialPhotoIndex: Int,
    photoViewerViewModel: PhotoViewerViewModel = viewModel()
) {
    val pagerState = rememberPagerState(
        initialPage = initialPhotoIndex,
        pageCount = { lazyPagingItems.itemCount }
    )

    val showUi = remember { mutableStateOf(true) }
    val currentPhoto = lazyPagingItems.itemSnapshotList.getOrNull(pagerState.currentPage)

    PhotoViewerScaffold(currentPhoto, showUi.value, photoViewerViewModel) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            key = { index -> (lazyPagingItems.itemSnapshotList.getOrNull(index))?.id ?: index },
        ) { page ->
            val photo = lazyPagingItems[page]
            if (photo == null) {
                Text("Loading photo...")
                return@HorizontalPager
            }

            PagerContent(photo, showUi, paddingValues)
        }
    }
}

@Composable
fun <T : Photo> PhotosViewer(
    items: List<T>,
    photoViewerViewModel: PhotoViewerViewModel = viewModel()
) {
    val photosList = remember { mutableStateListOf<T>() }
    LaunchedEffect(items) {
        photosList.clear()
        photosList.addAll(items)
    }

    val pagerState = rememberPagerState(pageCount = { items.size })

    val showUi = remember { mutableStateOf(true) }
    val currentPhoto = items[pagerState.currentPage]

    PhotoViewerScaffold(currentPhoto, showUi.value, photoViewerViewModel) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            key = { index -> items[index].id },
        ) { page ->
            val photo = items[page]

            val photoFlow = remember(photo) {
                if (photo is NetworkPhoto)
                    photoViewerViewModel.getNetworkPhotoFlow(photo.id)
                else
                    photoViewerViewModel.getLocalPhotoFlow(photo.id)
            }
            val updatedPhoto by photoFlow.collectAsState(photo)
            LaunchedEffect(updatedPhoto == null) {
                if (updatedPhoto == null) {
                    photosList.remove(photo)
                }
            }

            PagerContent(updatedPhoto ?: photo, showUi, paddingValues)
        }
    }
}

@Composable
private fun PhotoViewerScaffold(
    currentPhoto: Photo?,
    showUi: Boolean,
    photoViewerViewModel: PhotoViewerViewModel,
    content: @Composable (PaddingValues) -> Unit
) {
    val backStack = LocalNavBackStack.current

    Scaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            if (currentPhoto != null) {
                TopBar(currentPhoto, showUi, { backStack.removeLastOrNull() }, photoViewerViewModel)
            }
        },
        bottomBar = {
            if (currentPhoto != null) {
                AnimatedVisibility(
                    visible = showUi,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BottomBar(
                        photo = currentPhoto,
                        photoViewerViewModel = photoViewerViewModel,
                    )
                }
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PagerContent(
    photo: Photo,
    showUi: MutableState<Boolean>,
    paddingValues: PaddingValues,
) {
    val isVideo = remember(photo) { photo.isVideo }
    val sharedBoundsModifier = Modifier.photoSharedBounds(photo.id)

    if (!isVideo) {
        ZoomableImage(
            modifier = sharedBoundsModifier.fillMaxSize(),
            photo = photo
        ) { showUi.value = it }
    } else {
        VideoPlayer(
            sourceUri = photo.getUri(),
            showControls = showUi,
            modifier = sharedBoundsModifier.padding(paddingValues)
        )
    }
}

@Composable
private fun TopBar(
    photo: Photo,
    showUi: Boolean,
    onClose: () -> Unit,
    photoViewerViewModel: PhotoViewerViewModel,
) = AnimatedVisibility(
    visible = showUi,
    enter = fadeIn(),
    exit = fadeOut()
) {
    NavBackTopAppBar(
        modifier = Modifier.Companion.fillMaxWidth(),
        title = photo.photoDateText(),
        navIconOnClick = onClose,
        actions = {
            if (photo is NetworkPhoto) {
                IconButton(onClick = {
                    photoViewerViewModel.updateFavorite(
                        photo,
                        !photo.isFavorite
                    )
                }) {
                    val icon =
                        if (photo.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline

                    Icon(painterResource(icon), null)
                }

                Spacer(Modifier.Companion.width(12.dp))
            }
        }
    )
}

@Composable
private fun BottomBar(
    photo: Photo,
    photoViewerViewModel: PhotoViewerViewModel = viewModel()
) {
    val deletePhotosDialog = rememberDeletePhotosDialog()
    val backStack = LocalNavBackStack.current
    val mainViewModel: MainViewModel = viewModel()

    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .windowInsetsPadding(BottomAppBarDefaults.windowInsets),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        IconButtonText(
            onClick = {
                when (photo) {
                    is NetworkPhoto -> deletePhotosDialog.show(
                        photoIds = longArrayOf(photo.id),
                        onPhotosDeleted = {}
                    )

                    is LocalPhoto -> mainViewModel.deleteLocalPhotos(longArrayOf(photo.id))
                }
            },
            text = stringResource(id = R.string.action_delete),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_delete),
                contentDescription = null
            )
        }

        SharePhotoIconButton(
            true,
            getPhotosUris = {
                val photoIds = longArrayOf(photo.id)
                if (photo is LocalPhoto) {
                    mainViewModel.getLocalPhotosUriAsync(photoIds).await()
                } else {
                    mainViewModel.getNetworkPhotosUriAsync(photoIds).await()
                }
            }
        )

        // Local only
        if (photo is LocalPhoto) {
            if (!photo.isSavedToCloud) {
                IconButtonText(
                    onClick = { backStack.add(UploadPhotosNav(longArrayOf(photo.id))) },
                    text = stringResource(id = R.string.action_upload),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_upload_outline),
                        contentDescription = null
                    )
                }
            } else {
                // Find the network equivalent of this photo
                val networkPhotoState =
                    photoViewerViewModel.getNetworkPhotoFlow(photo.networkPhotoId)
                        .collectAsState(null)

                val networkPhotoInfoDialog =
                    rememberNetworkPhotoInfoDialog(networkPhotoState.value)

                IconButtonText(
                    onClick = {
                        val networkPhoto = networkPhotoState.value
                        if (networkPhoto != null)
                            networkPhotoInfoDialog.show()
                    },
                    text = stringResource(id = R.string.status_saved),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_done_filled),
                        contentDescription = null
                    )
                }
            }
        } else if (photo is NetworkPhoto) { // Network only
            IconButtonText(
                onClick = { backStack.add(MovePhotosNav(longArrayOf(photo.id))) },
                text = stringResource(id = R.string.action_move),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_move_folder),
                    contentDescription = null
                )
            }

            val networkPhotoInfoDialog = rememberNetworkPhotoInfoDialog(photo)

            IconButtonText(
                onClick = { networkPhotoInfoDialog.show() },
                text = stringResource(id = R.string.action_info),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_info),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    showUI: (Boolean) -> Unit,
) {
//    val configuration = LocalConfiguration.current
//    val maxWidth = with(LocalDensity.current) { (configuration.screenWidthDp.dp * 2).roundToPx() }

    val ctx = LocalContext.current

    val zoomableState = rememberZoomableState()

    val zoomFraction = zoomableState.zoomFraction ?: 0.0f
    LaunchedEffect(zoomFraction < 0.1f) {
        showUI(zoomFraction < 0.1f)
    }

    val model = remember(photo.id) {
        val cacheKey = photo.getPreviewUri().toString()
        ImageRequest.Builder(ctx)
            .data(photo.getUri())
            .crossfade(true)
            .placeholderMemoryCacheKey(cacheKey)
//            .placeholder(R.drawable.ic_hourglass_bottom)
//            .size(Size(width = maxWidth, height = Dimension.Undefined))
            .maxBitmapSize(Size.Companion.ORIGINAL)
            .build()
    }

    ZoomableAsyncImage(
        modifier = modifier,
        model = model,
        imageLoader = LocalImageLoader.current.get(),
        state = rememberZoomableImageState(zoomableState),
        contentDescription = photo.name,
        onDoubleClick = DoubleClickToZoomListener.cycle(0.8f)
    )
}