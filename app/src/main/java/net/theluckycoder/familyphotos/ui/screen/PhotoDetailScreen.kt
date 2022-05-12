package net.theluckycoder.familyphotos.ui.screen

import android.content.Intent
import android.os.Parcelable
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.bottomSheet.LocalBottomSheetNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.*
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalPlayerController
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.composables.*
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Parcelize
data class PhotoDetailScreen(
    var index: Int,
    val allPhotos: List<Photo>,
) : Screen, Parcelable {

    override val key: ScreenKey
        get() = "PhotoDetailScreen ${allPhotos[index].hashCode()}"

    constructor(
        photo: Photo,
        allPhotos: List<Photo>
    ) : this(allPhotos.indexOfFirst { it.id == photo.id }, allPhotos)

    init {
        require(index != -1)
    }

    @OptIn(
        com.google.accompanist.pager.ExperimentalPagerApi::class
    )
    @Composable
    override fun Content() = Box(Modifier.fillMaxSize()) {
        val mainViewModel: MainViewModel = viewModel()

        SideEffect {
            mainViewModel.showBottomAppBar.value = false
        }

        val pagerState = rememberPagerState(index)

        DisposableEffect(Unit) {
            onDispose {
                index = pagerState.currentPage
            }
        }

        HorizontalPager(
            count = allPhotos.size,
            state = pagerState,
            key = { allPhotos[it] }
        ) { page ->
            val photo = remember(page) { allPhotos[page] }
            var showAppBar by remember { mutableStateOf(true) }

            val dateTime = getPhotoDate(photo)

            AnimatedVisibility(
                visible = showAppBar,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val navigator = LocalNavigator.currentOrThrow

                NavBackTopAppBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    title = dateTime,
                    subtitle = photo.name,
                    navIconOnClick = { navigator.pop() }
                )
            }

            PagerContent(
                photo,
                showAppBar,
                onShowAppBarChanged = { showAppBar = it },
                mainViewModel
            )
        }
    }

    @Composable
    private fun BoxScope.PagerContent(
        photo: Photo,
        showAppBar: Boolean,
        onShowAppBarChanged: (Boolean) -> Unit,
        mainViewModel: MainViewModel
    ) {
        val isVideo = remember(photo) { photo.isVideo }

        if (!isVideo) {
            ZoomableImage(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                photo = photo,
                onTap = { onShowAppBarChanged(!showAppBar) }
            )
        } else {
            val playerController = LocalPlayerController.current.get()

            DisposableEffect(photo) {
                playerController.prepare(photo.getUri())

                onDispose {
                    playerController.reset()
                }
            }

            Box(Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onShowAppBarChanged(!showAppBar)
            }) {
                VideoPlayer.Surface()
            }

            if (showAppBar)
                VideoPlayer.PauseButton(Modifier.align(Alignment.Center))
        }

        if (showAppBar) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                if (isVideo) {
                    VideoPlayer.Seekbar()
                }

                BottomBar(
                    photo = photo,
                    mainViewModel = mainViewModel,
                )
            }
        }
    }

    @Composable
    private fun getPhotoDate(photo: Photo) = remember(photo) {
        val instant = Instant.fromEpochMilliseconds(photo.timeCreated)
        val date = instant.toLocalDateTime(timeZone)

        buildString {
            append(date.dayOfMonth).append(' ')
            append(date.month).append(' ')
            append(date.year)

            if (date.hour != 0 || date.minute != 0) {
                append(" - ")
                append(date.hour).append(':').append(date.minute)
            }
        }
    }

    @Composable
    private fun BottomBar(
        photo: Photo,
        mainViewModel: MainViewModel = viewModel()
    ) {
        val snackbarHostState = LocalSnackbarHostState.current
        val bottomSheetNavigator = LocalBottomSheetNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black,
                        )
                    )
                ),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val context = LocalContext.current
            val sendTo = stringResource(R.string.send_to)

            IconButtonText(
                onClick = {
                    bottomSheetNavigator.show(DeletePhotosDialog(listOf(photo)))
                },
                text = stringResource(id = R.string.action_delete),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_delete),
                    contentDescription = null
                )
            }

            val failedToDownloadImage = stringResource(R.string.failed_download_image)

            IconButtonText(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val uri = mainViewModel.getLocalPhotoUriAsync(photo).await()

                        withContext(Dispatchers.Main) {
                            if (uri != null) {
                                Log.d("URI Image", uri.toString())
                                val shareIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                                        photo.name.substringAfterLast('.')
                                    )
                                }

                                context.startActivity(Intent.createChooser(shareIntent, sendTo))
                            } else {
                                snackbarHostState.showSnackbar(failedToDownloadImage)
                            }
                        }
                    }
                },
                text = stringResource(id = R.string.action_share),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_share),
                    contentDescription = null
                )
            }

            if (photo is LocalPhoto && !photo.isSavedToCloud) {
                IconButtonText(
                    onClick = { navigator.push(UploadPhotosScreen(listOf(photo.id))) },
                    text = stringResource(id = R.string.action_upload),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_upload_outline),
                        contentDescription = null
                    )
                }
            }

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
            }

            /*IconButton(
                enabled = false,
                onClick = {}
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_options_vertical),
                    contentDescription = stringResource(id = R.string.action_more)
                )
            }*/

        }
    }

    companion object {
        private val timeZone = TimeZone.of("Europe/Bucharest")
    }
}

@Composable
fun CoilPhoto(
    modifier: Modifier = Modifier,
    photo: Photo,
    contentScale: ContentScale = ContentScale.Fit,
) {
    AsyncImage(
        modifier = modifier,
        model = photo.getUri(),
        contentScale = contentScale,
        contentDescription = photo.name,
        imageLoader = LocalImageLoader.current.get()
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
        val request = ImageRequest.Builder(LocalContext.current)
            .data(photo.getUri())
            .size(Size.ORIGINAL)
            .build()

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
