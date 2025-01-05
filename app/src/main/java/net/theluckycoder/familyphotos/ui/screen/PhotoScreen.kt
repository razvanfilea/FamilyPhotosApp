package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcelable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.placeholder
import coil3.size.Dimension
import coil3.size.Size
import kotlinx.parcelize.Parcelize
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.getPreviewUri
import net.theluckycoder.familyphotos.model.getUri
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.composables.IconButtonText
import net.theluckycoder.familyphotos.ui.composables.NavBackTopAppBar
import net.theluckycoder.familyphotos.ui.composables.SharePhotoIconButton
import net.theluckycoder.familyphotos.ui.composables.VideoPlayer
import net.theluckycoder.familyphotos.ui.composables.photoDateText
import net.theluckycoder.familyphotos.ui.dialog.rememberDeletePhotosDialog
import net.theluckycoder.familyphotos.ui.dialog.rememberNetworkPhotoInfoDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewModel

@ConsistentCopyVisibility
@Parcelize
data class PhotoScreen private constructor(
    private val startPhoto: Photo,
    private val source: Source,
    private var index: Int,
) : Screen, Parcelable {

    constructor(startPhoto: Photo, source: Source) : this(startPhoto, source, -1)

    override val key: ScreenKey
        get() = "PhotoDetailScreen ${startPhoto.id} $source"

    @Composable
    override fun Content() = Box(Modifier.fillMaxSize()) {
        val photoViewModel: PhotoViewModel = viewModel()

        val allPhotosState = remember {
            when (source) {
                Source.PagedList -> photoViewModel.getPhotosInMonth(startPhoto)
                Source.Folder -> when (startPhoto) {
                    is LocalPhoto -> photoViewModel.getLocalFolderPhotos(startPhoto.folder!!)
                    is NetworkPhoto -> photoViewModel.getNetworkFolderPhotos(startPhoto.folder!!)
                }
                Source.Memories -> photoViewModel.getPhotosInWeek(startPhoto as NetworkPhoto)
                Source.Favorites -> photoViewModel.getFavoritePhotos()
            }
        }.collectAsState(null)

        val allPhotos = allPhotosState.value
        index = remember(startPhoto, allPhotos) {
            allPhotos ?: return@remember -1
            val foundIndex = allPhotos.indexOf(startPhoto)
            when {
                index == -1 && foundIndex == -1 -> 0
                foundIndex == -1 -> index
                index == -1 -> foundIndex
                else -> 0
            }
        }

        if (allPhotos != null && index != -1) {
            PhotosPager(allPhotos, photoViewModel)
        }
    }

    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    private fun BoxScope.PhotosPager(
        allPhotos: List<Photo>,
        photoViewModel: PhotoViewModel
    ) {
        val navigator = LocalNavigator.currentOrThrow
        val pagerState = rememberPagerState(initialPage = index) { allPhotos.size }
        val showUi = remember { mutableStateOf(true) }

        DisposableEffect(allPhotos.size) {
            if (allPhotos.isEmpty())
                navigator.pop()

            onDispose {
                index = pagerState.currentPage
            }
        }

        val currentPhoto = allPhotos.getOrNull(pagerState.currentPage)

        Scaffold(
            containerColor = Color.Black,
            contentColor = Color.White,
            snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
            topBar = {
                if (currentPhoto != null) {
                    AnimatedVisibility(
                        visible = showUi.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        NavBackTopAppBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            title = currentPhoto.photoDateText(),
                            navIconOnClick = { navigator.pop() },
                            actions = {
                                if (currentPhoto is NetworkPhoto) {
                                    IconButton(onClick = { photoViewModel.updateFavorite(currentPhoto, !currentPhoto.isFavorite) }) {
                                        Icon(
                                            painterResource(if (currentPhoto.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                                            contentDescription = null
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))
                                }
                            }
                        )
                    }
                }
            },
            bottomBar = {
                if (currentPhoto != null) {
                    AnimatedVisibility(
                        visible = showUi.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        BottomBar(
                            photo = currentPhoto,
                            photoViewModel = photoViewModel,
                        )
                    }
                }
            }
        ) { paddingValues ->

            HorizontalPager(
                state = pagerState,
                key = { allPhotos.getOrNull(it)?.id ?: it },
            ) { page ->
                val photo = allPhotos.getOrNull(page)

                if (photo != null) {
                    PagerContent(photo, showUi, paddingValues)
                }
            }

        }
    }

    @Composable
    private fun BoxScope.PagerContent(
        photo: Photo,
        showUi: MutableState<Boolean>,
        paddingValues: PaddingValues,
    ) {
        val isVideo = remember(photo) { photo.isVideo }

        if (!isVideo) {
            ZoomableImage(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                photo = photo
            ) { showUi.value = it }
        } else {
            VideoPlayer(photo.getUri(), paddingValues, showUI = { showUi.value = it })
        }
    }

    @Composable
    private fun BottomBar(
        photo: Photo,
        photoViewModel: PhotoViewModel = viewModel()
    ) {
        val deletePhotosDialog = rememberDeletePhotosDialog()
        val navigator = LocalNavigator.currentOrThrow
        val mainViewModel: MainViewModel = viewModel()

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
                        is NetworkPhoto -> deletePhotosDialog.show(listOf(photo))
                        is LocalPhoto -> mainViewModel.deleteLocalPhotos(listOf(photo))
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
                getPhotos = { listOf(photo) },
                photoViewModel::getPhotoLocalUriAsync
            )

            // Local only
            if (photo is LocalPhoto) {
                if (!photo.isSavedToCloud) {
                    IconButtonText(
                        onClick = { navigator.push(UploadPhotosScreen(listOf(photo.id))) },
                        text = stringResource(id = R.string.action_upload),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_upload_outline),
                            contentDescription = null
                        )
                    }
                } else {
                    // Find the network equivalent of this photo
                    val networkPhotoState = photoViewModel.getNetworkPhotoFlow(photo.networkPhotoId)
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
                    onClick = { navigator.push(MovePhotosScreen(listOf(photo.id))) },
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

    @Parcelize
    enum class Source : Parcelable {
        PagedList,
        Folder,
        Memories,
        Favorites,
    }
}


@Composable
private fun ZoomableImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    showUI: (Boolean) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maxWidth = with(LocalDensity.current) { (configuration.screenWidthDp.dp * 2).roundToPx() }

    val ctx = LocalContext.current

    val zoomableState = rememberZoomableState()

    LaunchedEffect(zoomableState.zoomFraction) {
        zoomableState.zoomFraction?.let { zoom ->
            showUI(zoom == 0.0f)
        }
    }

    ZoomableAsyncImage(
        modifier = modifier,
        model = ImageRequest.Builder(ctx)
            .data(photo.getUri())
            .placeholderMemoryCacheKey(photo.getPreviewUri().toString())
            .placeholder(R.drawable.ic_hourglass_bottom)
            .size(Size(width = maxWidth, height = Dimension.Undefined))
            .maxBitmapSize(Size.ORIGINAL)
            .build(),
        imageLoader = LocalImageLoader.current.get(),
        state = rememberZoomableImageState(zoomableState),
        contentDescription = photo.name,
    )
}
