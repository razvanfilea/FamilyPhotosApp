package net.theluckycoder.familyphotos.ui.composables

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.getPreviewUri
import net.theluckycoder.familyphotos.core.data.model.getUri
import net.theluckycoder.familyphotos.core.data.model.isVideo
import net.theluckycoder.familyphotos.core.data.model.thumbHash
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.MovePhotosNav
import net.theluckycoder.familyphotos.ui.UploadPhotosNav
import net.theluckycoder.familyphotos.ui.composables.player.VideoPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.dialog.rememberNetworkPhotoInfoDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewerViewModel


@Composable
fun <T : Photo> PhotosViewer(
    lazyPagingItems: LazyPagingItems<T>,
    initialPhotoIndex: Int,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel = viewModel()
) {
    val showUi = remember { mutableStateOf(true) }
    // Now paging items are plain photos, not DataOrSeparator
    val items = remember(lazyPagingItems.itemSnapshotList) {
        lazyPagingItems.itemSnapshotList.withIndex()
            .mapNotNull { (index, photo) -> photo?.let { index to it } }
    }
    val actualInitialIndex = items.indexOfFirst { it.first == initialPhotoIndex }.coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = actualInitialIndex,
        pageCount = { items.size }
    )

    val currentPhoto = items.getOrNull(pagerState.currentPage)?.second

    PhotoViewerScaffold(currentPhoto, showUi.value, mainViewModel, photoViewerViewModel) { paddingValues ->
        HorizontalPager(
            modifier = Modifier.testTag("photo_viewer_pager"),
            state = pagerState,
            key = { index -> items.getOrNull(index)?.second?.id ?: index },
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            val (originalIndex, photo) = item
            if (originalIndex in 0 until lazyPagingItems.itemCount) {
                lazyPagingItems[originalIndex] // Notify Paging Data!!
            }

            val localUri = remember { mutableStateOf<Uri?>(null) }
            LaunchedEffect(photo) {
                if (photo is NetworkPhoto) {
                    val d = photoViewerViewModel.getEquivalentLocalUri(photo)
                    localUri.value = d
                }
            }

            PagerContent(photo, localUri.value, showUi, paddingValues, photoViewerViewModel)
        }
    }
}

@Composable
fun <T : Photo> PhotosViewer(
    items: List<T>,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel = viewModel()
) {
    val photosList = remember { mutableStateListOf<T>() }
    LaunchedEffect(items) {
        photosList.clear()
        photosList.addAll(items)
    }

    val pagerState = rememberPagerState(pageCount = { photosList.size })

    val showUi = remember { mutableStateOf(true) }
    val currentPhoto = photosList.getOrNull(pagerState.currentPage)

    PhotoViewerScaffold(currentPhoto, showUi.value, mainViewModel, photoViewerViewModel) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            key = { index -> photosList.getOrNull(index)?.id ?: index },
        ) { page ->
            val photo = photosList.getOrNull(page) ?: return@HorizontalPager

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

            val localUri = if (photo is NetworkPhoto) {
                remember(photo) { photoViewerViewModel.getEquivalentLocalUriFlow(photo) }.collectAsState(
                    null
                )
            } else {
                null
            }

            PagerContent(updatedPhoto ?: photo, localUri?.value, showUi, paddingValues, photoViewerViewModel)
        }
    }
}

@Composable
private fun PhotoViewerScaffold(
    currentPhoto: Photo?,
    showUi: Boolean,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel,
    content: @Composable (PaddingValues) -> Unit
) {
    val backStack = LocalNavBackStack.current

    Scaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
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
                        mainViewModel = mainViewModel,
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
    localUri: Uri?,
    showUi: MutableState<Boolean>,
    paddingValues: PaddingValues,
    photoViewerViewModel: PhotoViewerViewModel,
) {
    val isVideo = remember(photo) { photo.isVideo }
    val sharedBoundsModifier = Modifier.photoSharedBounds(photo.id)

    if (!isVideo) {
        ZoomableImage(
            modifier = sharedBoundsModifier.fillMaxSize(),
            localUri = localUri,
            photo = photo
        ) { showUi.value = it }
    } else {
        VideoPlayer(
            sourceUri = localUri ?: photo.getUri(),
            dataSourceFactory = photoViewerViewModel.dataSourceFactory,
            showControls = showUi,
            modifier = sharedBoundsModifier,
            controlsPadding = paddingValues,
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
    val title = photo.photoDateText()
    val isFavoriteFlow =
        remember(photo.id) { photoViewerViewModel.isNetworkPhotoFavorite(photo.id) }
    val isFavorite by isFavoriteFlow.collectAsState(false)

    NavBackTopAppBar(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        navIconOnClick = onClose,
        actions = {
            if (photo is NetworkPhoto) {
                IconButton(onClick = {
                    photoViewerViewModel.updateFavorite(
                        photo,
                        !isFavorite
                    )
                }) {
                    val icon =
                        if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline

                    Icon(painterResource(icon), null)
                }

                Spacer(Modifier.width(12.dp))
            }
        }
    )
}

@Composable
private fun BottomBar(
    photo: Photo,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel = viewModel()
) {
    var showDeleteDialogForPhotos by remember { mutableStateOf<List<Photo>?>(null) }
    val backStack = LocalNavBackStack.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .windowInsetsPadding(BottomAppBarDefaults.windowInsets),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {

        IconButtonText(
            onClick = {
                when (photo) {
                    is NetworkPhoto -> showDeleteDialogForPhotos = listOf(photo)

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
                text = stringResource(R.string.action_move),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_move_folder),
                    contentDescription = null
                )
            }

            val networkPhotoInfoDialog = rememberNetworkPhotoInfoDialog(photo)

            IconButtonText(
                onClick = { networkPhotoInfoDialog.show() },
                text = stringResource(R.string.action_info),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_info),
                    contentDescription = null
                )
            }
        }
    }

    showDeleteDialogForPhotos?.let { photos ->
        DeletePhotosDialog(
            photos = photos,
            isPermanent = false,
            onDismissRequest = { showDeleteDialogForPhotos = null },
            onConfirmDelete = { list -> mainViewModel.trashNetworkPhotos(list.map { it.id }.toLongArray()) },
            onPhotosDeleted = {}
        )
    }
}

@Composable
fun ZoomableImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    localUri: Uri? = null,
    showUI: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val isImageLoaded = remember {mutableStateOf(false)}

    val zoomableState = rememberZoomableState()
    val zoomFraction = zoomableState.zoomFraction ?: 0.0f
    LaunchedEffect(zoomFraction < 0.1f) {
        showUI(zoomFraction < 0.1f)
    }

    val thumbHashPainter = thumbHashPainter(photo.thumbHash)

    val uri = localUri ?: photo.getUri()
    val model = remember(photo.id, uri) {
        val cacheKey = photo.getPreviewUri().toString()
        ImageRequest.Builder(ctx)
            .data(uri)
            .crossfade(true)
            .placeholderMemoryCacheKey(cacheKey)
            .listener { _, _ -> isImageLoaded.value = true }
            .size(Size.ORIGINAL)
            .build()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!isImageLoaded.value && thumbHashPainter != null) {
            Image(
                thumbHashPainter,
                modifier = Modifier.fillMaxWidth().aspectRatio(thumbHashPainter.aspectRatio),
                contentDescription = null
            )
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
}