package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcelable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.smarttoolfactory.zoom.enhancedZoom
import com.smarttoolfactory.zoom.rememberEnhancedZoomState
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.*
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.composables.*
import net.theluckycoder.familyphotos.ui.dialog.rememberDeletePhotosDialog
import net.theluckycoder.familyphotos.ui.dialog.rememberNetworkPhotoInfoDialog
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewModel

@Suppress("DataClassPrivateConstructor")
@Parcelize
data class PhotoScreen private constructor(
    private val startPhoto: Photo,
    private val listSource: ListSource,
    private var index: Int,
) : Screen, Parcelable {

    constructor(startPhoto: Photo, listSource: ListSource) : this(startPhoto, listSource, -1)

    override val key: ScreenKey
        get() = "PhotoDetailScreen ${startPhoto.id} $listSource"

    @Composable
    override fun Content() = Box(Modifier.fillMaxSize()) {
        val photoViewModel: PhotoViewModel = viewModel()

        val allPhotosState = remember {
            when (listSource) {
                ListSource.PagedList -> photoViewModel.getPhotosInMonth(startPhoto)
                ListSource.Folder -> when (startPhoto) {
                    is LocalPhoto -> photoViewModel.getLocalFolderPhotos(startPhoto.folder!!)
                    is NetworkPhoto -> photoViewModel.getNetworkFolderPhotos(startPhoto.folder!!)
                }

                ListSource.Memories -> photoViewModel.getPhotosInWeek(startPhoto as NetworkPhoto)
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
        val pagerState = rememberPagerState(index)
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
                            navIconOnClick = { navigator.pop() }
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
                pageCount = allPhotos.size,
                state = pagerState,
                key = { allPhotos.getOrNull(it)?.id ?: it },
                beyondBoundsPageCount = 1,
            ) { page ->
                val photo = allPhotos.getOrNull(page)

                if (photo != null) {
                    PagerContent(photo, showUi)
                }
            }

        }
    }

    @Composable
    private fun BoxScope.PagerContent(
        photo: Photo,
        showUi: MutableState<Boolean>,
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
            VideoPlayer(photo.getUri(), showUI = { showUi.value = it })
        }
    }

    @Composable
    private fun BottomBar(
        photo: Photo,
        photoViewModel: PhotoViewModel = viewModel()
    ) {
        val deletePhotosDialog = rememberDeletePhotosDialog()
        val navigator = LocalNavigator.currentOrThrow

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            IconButtonText(
                onClick = { deletePhotosDialog.show(listOf(photo)) },
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
    enum class ListSource : Parcelable {
        PagedList,
        Folder,
        Memories,
    }
}


@Composable
private fun ZoomableImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    showUI: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val request = rememberAsyncImagePainter(
        imageLoader = LocalImageLoader.current.get(),
        model = ImageRequest.Builder(ctx)
            .data(photo.getUri())
            .placeholder(R.drawable.ic_hourglass_bottom)
            .size(Size.ORIGINAL)
            .build()
    )

    val size = request.intrinsicSize
    val intSize = when {
        size.isUnspecified -> IntSize(0, 0)
        else -> IntSize(size.width.toInt(), size.height.toInt())
    }

    Image(
        modifier = modifier.enhancedZoom(
            clip = false,
            enhancedZoomState = rememberEnhancedZoomState(
                imageSize = intSize,
                rotatable = false,
                limitPan = true,
                maxZoom = 4f,
            ),
            enabled = { zoom, _, _ ->
                val enabled = zoom > 1f
                showUI(!enabled)
                enabled
            },
            key = intSize,
        ),
        painter = request,
        contentDescription = photo.name,
    )
}
