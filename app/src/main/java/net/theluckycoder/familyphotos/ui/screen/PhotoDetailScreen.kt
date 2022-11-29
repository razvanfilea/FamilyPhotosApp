package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.extensions.readList
import net.theluckycoder.familyphotos.model.*
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.composables.*
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.dialog.NetworkPhotoInfoDialog
import net.theluckycoder.familyphotos.ui.navigation.LocalBottomSheetNavigator
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Suppress("DataClassPrivateConstructor")
data class PhotoDetailScreen private constructor(
    val allPhotos: SnapshotStateList<Photo>, // is actually a SnapshotStateList
    var index: Int,
) : Screen, Parcelable {

    override val key: ScreenKey
        get() = "PhotoDetailScreen ${allPhotos.hashCode()}"

    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readList<Photo>()
    )

    constructor(
        index: Int,
        allPhotos: List<Photo>,
    ) : this(mutableStateListOf(*allPhotos.toTypedArray()), index)

    constructor(
        photo: Photo,
        allPhotos: List<Photo>
    ) : this(allPhotos.indexOfFirst { it.id == photo.id }, allPhotos)

    init {
        require(index != -1)
    }

    @OptIn(ExperimentalPagerApi::class)
    @Composable
    override fun Content() = Box(Modifier.fillMaxSize().background(Color.Black)) {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow

        SideEffect {
            mainViewModel.showBottomAppBar.value = false
        }

        val pagerState = rememberPagerState(index)
        var showAppBar by remember { mutableStateOf(true) }

        DisposableEffect(allPhotos.size) {
            if (allPhotos.size == 0)
                navigator.pop()

            onDispose {
                index = pagerState.currentPage
            }
        }

        HorizontalPager(
            count = allPhotos.size,
            state = pagerState,
            key = { allPhotos.getOrNull(it)?.id ?: it }
        ) { page ->
            val photo = allPhotos.getOrNull(page) ?: return@HorizontalPager
            val photoFlow: Flow<Photo?> = remember(photo.id) {
                if (photo is LocalPhoto)
                    mainViewModel.getLocalPhotoFlow(photo.id)
                else
                    mainViewModel.getNetworkPhotoFlow(photo.id)
            }

            val updatedPhoto by photoFlow.collectAsState(photo)
            LaunchedEffect(updatedPhoto) {
                if (updatedPhoto == null) {
                    index = (pagerState.currentPage + 1).coerceAtMost(allPhotos.size - 1)

                    // The photo must have been deleted so we remove it as well
                    allPhotos.removeAt(page)
                    pagerState.animateScrollToPage(index)
                }
            }

            PagerContent(
                updatedPhoto ?: photo,
                showAppBar,
                toggleShowAppBar = { showAppBar = !showAppBar },
                mainViewModel
            )
        }

        val currentPhoto = allPhotos.getOrNull(pagerState.currentPage)

        if (currentPhoto != null) {
            AnimatedVisibility(
                visible = showAppBar,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavBackTopAppBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    title = currentPhoto.getPhotoDate(),
                    subtitle = currentPhoto.name,
                    navIconOnClick = { navigator.pop() }
                )
            }
        }
    }

    @Composable
    private fun BoxScope.PagerContent(
        photo: Photo,
        showAppBar: Boolean,
        toggleShowAppBar: () -> Unit,
        mainViewModel: MainViewModel
    ) {
        val isVideo = remember(photo) { photo.isVideo }

        if (!isVideo) {
            ZoomableImage(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                photo = photo,
                onTap = { toggleShowAppBar() }
            )
        } else {
            Box(Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                toggleShowAppBar()
            }) {
                VideoPlayer(photo.getUri())
            }
        }

        if (showAppBar) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                BottomBar(
                    photo = photo,
                    mainViewModel = mainViewModel,
                )
            }
        }
    }

    @Composable
    private fun BottomBar(
        photo: Photo,
        mainViewModel: MainViewModel = viewModel()
    ) {
        val bottomSheetNavigator = LocalBottomSheetNavigator.current
        val navigator = LocalNavigator.currentOrThrow

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .windowInsetsPadding(BottomAppBarDefaults.windowInsets),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            IconButtonText(
                onClick = { bottomSheetNavigator.show(DeletePhotosDialog(listOf(photo))) },
                text = stringResource(id = R.string.action_delete),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_delete),
                    contentDescription = null
                )
            }

            SharePhotoIconButton(true, getPhotos = { listOf(photo) }, mainViewModel)

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
                    val networkPhotoState = mainViewModel.getNetworkPhotoFlow(photo.networkPhotoId)
                        .collectAsState(null)

                    IconButtonText(
                        onClick = {
                            val networkPhoto = networkPhotoState.value
                            if (networkPhoto != null)
                                bottomSheetNavigator.show(NetworkPhotoInfoDialog(networkPhoto))
                        },
                        text = stringResource(id = R.string.status_saved),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_done_filled),
                            contentDescription = null
                        )
                    }
                }
            }

            // Network only
            if (photo is NetworkPhoto) {
                IconButtonText(
                    onClick = { navigator.push(MovePhotosScreen(listOf(photo.id))) },
                    text = stringResource(id = R.string.action_move),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_move_folder),
                        contentDescription = null
                    )
                }

                IconButtonText(
                    onClick = { bottomSheetNavigator.show(NetworkPhotoInfoDialog(photo)) },
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

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(index)
        dest.writeTypedList(allPhotos.toList())
    }

    companion object CREATOR : Parcelable.Creator<PhotoDetailScreen> {
        override fun createFromParcel(parcel: Parcel): PhotoDetailScreen {
            return PhotoDetailScreen(parcel)
        }

        override fun newArray(size: Int): Array<PhotoDetailScreen?> {
            return arrayOfNulls(size)
        }
    }
}

@Composable
fun CoilPhoto(
    modifier: Modifier = Modifier,
    photo: Photo,
    thumbnail: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    AsyncImage(
        modifier = modifier,
        model = if (!thumbnail) photo.getUri() else photo.getThumbnailUri(),
        contentScale = contentScale,
        contentDescription = photo.name,
        imageLoader = LocalImageLoader.current.get(),
        placeholder = ColorPainter(Color.DarkGray),
        error = ColorPainter(Color(0xB6D63535))
    )
}

@Composable
private fun ZoomableImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    onTap: ((Offset) -> Unit)? = null
) {
    val zoomableState = rememberZoomableState(maxScale = 5f)

    Zoomable(
        state = zoomableState,
        onTap = onTap,
        doubleTapScale = {
            when {
                zoomableState.scale >= 2.0f -> 0f
                else -> 2f
            }
        }
    ) {
        val ctx = LocalContext.current
        val request = remember(photo) {
            ImageRequest.Builder(ctx)
                .data(photo.getUri())
                .size(Size.ORIGINAL)
                .build()
        }

        SubcomposeAsyncImage(
            modifier = modifier,
            model = request,
            contentDescription = photo.name,
            loading = {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp)
                    )
                }
            },
            imageLoader = LocalImageLoader.current.get(),
            contentScale = ContentScale.Fit
        )
    }
}
