package net.theluckycoder.familyphotos.ui.screen

import android.content.Intent
import android.os.Parcelable
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.bottomSheet.LocalBottomSheetNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.BuildConfig
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.getUri
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.*
import net.theluckycoder.familyphotos.ui.composables.*
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.dialog.MoveDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.utils.PlayerController

@Parcelize
data class PhotoDetailScreen(
    val photo: Photo,
    val allPhotos: List<Photo>,
) : Screen, Parcelable {

    override val key: ScreenKey
        get() = "PhotoDetailScreen ${photo.hashCode()}"

    constructor(photo: Photo) : this(photo, listOf(photo))

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    override fun Content() = Box(Modifier.fillMaxSize()) {
        val mainViewModel: MainViewModel = viewModel()

        val ctx = LocalContext.current

        SideEffect {
            mainViewModel.showBottomAppBar.value = false
        }

        var showAppBar by remember { mutableStateOf(true) }
        var isMoveDialogVisible by remember { mutableStateOf(false) }

        val isVideo = remember { photo.isVideo }

        if (photo is NetworkPhoto && isMoveDialogVisible) {
            val scope = rememberCoroutineScope()
            val snackbarHostState = LocalSnackbarHostState.current

            val moveSuccess = stringResource(R.string.images_move_success)
            val moveFailure = stringResource(R.string.images_move_failure)

            MoveDialog(
                onDismissRequest = { isMoveDialogVisible = false },
                onConfirm = { makePublic, newFolder ->
                    isMoveDialogVisible = false

                    scope.launch {
                        val result = mainViewModel.changePhotosLocationAsync(
                            listOf(photo.id),
                            makePublic,
                            newFolder
                        ).await()

                        val message = if (result) moveSuccess else moveFailure

                        snackbarHostState.showSnackbar(message)
                    }
                }
            )
        }

        val playerController = remember(isVideo) { if (isVideo) PlayerController(ctx) else null }

        if (playerController == null) {
            ZoomableImage(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                photo = photo,
                onTap = {
                    showAppBar = !showAppBar
                }
            )
        } else {
            CompositionLocalProvider(LocalPlayerController provides playerController) {
                DisposableEffect(Unit) {
                    playerController.prepare(photo.getUri())

                    onDispose {
                        playerController.clear()
                    }
                }

                Box(Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    showAppBar = !showAppBar
                }) {
                    VideoPlayer.Surface()
                }

                if (showAppBar)
                    VideoPlayer.PauseButton(Modifier.align(Alignment.Center))
            }
        }

        val dateTime = getPhotoDate()

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

        val photoIndex = remember(allPhotos) { allPhotos.indexOf(photo) }
        val previousPhoto = remember(photoIndex) { allPhotos.getOrNull(photoIndex - 1) }
        val nextPhoto = remember(photoIndex) { allPhotos.getOrNull(photoIndex + 1) }

        if (showAppBar) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                if (playerController != null) {
                    CompositionLocalProvider(LocalPlayerController provides playerController) {
                        VideoPlayer.Seekbar()
                    }
                }

                BottomBar(
                    previousPhoto = previousPhoto,
                    nextPhoto = nextPhoto,
                    onMoveButtonClicked = { isMoveDialogVisible = true },
                    mainViewModel = mainViewModel,
                )
            }
        }
    }

    @Composable
    private fun getPhotoDate() = remember {
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
        previousPhoto: Photo?,
        nextPhoto: Photo?,
        onMoveButtonClicked: () -> Unit,
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

            if (previousPhoto != null) {
                IconButtonText(
                    onClick = {
                        navigator.replace(PhotoDetailScreen(previousPhoto, allPhotos))
                    },
                    text = stringResource(id = R.string.action_previous)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_previous_outline),
                        contentDescription = null
                    )
                }
            }

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
                    scope.launch {
                        val uri = mainViewModel.getLocalPhotoUri(photo).await()
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
                },
                text = stringResource(id = R.string.action_share),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_share),
                    contentDescription = null
                )
            }

            /*if (photo is LocalPhoto) {
                val uploadSuccess = stringResource(R.string.image_upload_success)
                val uploadFailure = stringResource(R.string.image_upload_failure)

                IconButton(
                    enabled = !photo.isSavedToCloud,
                    onClick = {
                        TODO()
                        scope.launch {
                            val str = if (mainViewModel.uploadPhotoAsync(photo).await())
                                uploadSuccess
                            else
                                uploadFailure

                            snackbarHostState.showSnackbar(str)
                        }
                    },
                ) {
                    val iconRes =
                        if (photo.isSavedToCloud) R.drawable.ic_cloud_done_outline else R.drawable.ic_cloud_upload_outline

                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(id = R.string.action_upload)
                    )
                }
            }*/

            if (photo is NetworkPhoto) {
                IconButtonText(
                    onClick = onMoveButtonClicked,
                    text = stringResource(id = R.string.action_move),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_move_folder),
                        contentDescription = null
                    )
                }
            }

            if (nextPhoto != null) {
                IconButtonText(
                    onClick = {
                        navigator.pop()
                        navigator.push(PhotoDetailScreen(nextPhoto, allPhotos))
                    },
                    text = stringResource(R.string.action_next),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_next_outline),
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
    builder: ImageRequest.Builder.() -> Unit = {},
) {
    val request = photo.getUri()

    Image(
        modifier = modifier,
        painter = rememberImagePainter(
            imageLoader = LocalImageLoader.current.get(),
            data = request,
            builder = builder
        ),
        contentScale = contentScale,
        contentDescription = photo.name,
    )
}

@Composable
fun ZoomableImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    onTap: ((Offset) -> Unit)? = null
) {
    val zoomableState = rememberZoomableState()

    val ctx = LocalContext.current

    val request = remember {
        ImageRequest.Builder(ctx)
            .data(photo.getUri())
            .precision(Precision.INEXACT)
            .build()
    }

    Zoomable(state = zoomableState) {
        Image(
            modifier = modifier,
            painter = rememberImagePainter(
                imageLoader = LocalImageLoader.current.get(),
                request = request,
            ),
            contentDescription = photo.name,
        )
    }
}
