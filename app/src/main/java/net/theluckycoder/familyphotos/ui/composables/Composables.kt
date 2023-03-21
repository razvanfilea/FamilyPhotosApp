package net.theluckycoder.familyphotos.ui.composables

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.getThumbnailUri
import net.theluckycoder.familyphotos.model.getUri
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.dialog.rememberDeletePhotosDialog
import net.theluckycoder.familyphotos.ui.screen.MovePhotosScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.withSign
import kotlin.reflect.KClass

@Composable
fun SimpleSquarePhoto(photo: Photo) {
    CoilPhoto(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp),
        photo = photo,
        thumbnail = true,
        contentScale = ContentScale.Crop,
    )
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomablePagerImage(
    modifier: Modifier = Modifier,
    photo: Photo,
    scrollEnabled: MutableState<Boolean>,
    minScale: Float = 1f,
    maxScale: Float = 5f,
) {
    var targetScale by remember { mutableStateOf(1f) }
    val scale = animateFloatAsState(targetValue = maxOf(minScale, minOf(maxScale, targetScale)))
    var rotationState by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(1f) }
    var offsetY by remember { mutableStateOf(1f) }
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }

    Box(
        modifier = Modifier
            .clip(RectangleShape)
            .background(Color.Transparent)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { },
                onDoubleClick = {
                    if (targetScale >= 2f) {
                        targetScale = 1f
                        offsetX = 1f
                        offsetY = 1f
                        scrollEnabled.value = true
                    } else targetScale = 3f
                },
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        targetScale *= zoom
                        val offset = event.calculatePan()
                        if (targetScale <= 1) {
                            offsetX = 1f
                            offsetY = 1f
                            targetScale = 1f
                            scrollEnabled.value = true
                        } else {
                            offsetX += offset.x
                            offsetY += offset.y
                            if (zoom > 1) {
                                scrollEnabled.value = false
                                rotationState += event.calculateRotation()
                            }
                            val imageWidth = screenWidthPx * scale.value
                            val borderReached = imageWidth - screenWidthPx - 2 * abs(offsetX)
                            scrollEnabled.value = borderReached <= 0
                            if (borderReached < 0) {
                                offsetX = ((imageWidth - screenWidthPx) / 2f).withSign(offsetX)
                                if (offset.x != 0f) offsetY -= offset.y
                            }
                        }
                    } while (event.changes.any { it.pressed })
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
            modifier = modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    this.scaleX = scale.value
                    this.scaleY = scale.value
                    this.translationX = offsetX
                    this.translationY = offsetY
                },
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

@Composable
fun IconButtonText(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(
                onClick = onClick,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = false, radius = 24.dp)
            )
            .defaultMinSize(48.dp, 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
        Text(
            modifier = Modifier.padding(top = 2.dp),
            text = text,
            fontSize = 12.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavBackTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    navIconOnClick: () -> Unit,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = navIconOnClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
        },
        title = {
            Column {
                if (title != null)
                    Text(text = title)
                if (subtitle != null)
                    Text(text = subtitle, style = MaterialTheme.typography.titleMedium)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
fun SelectableItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean,
    onClick: (longPress: Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val padding = animateDpAsState(if (selected) 8.dp else 0.dp).value

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick(false) },
                    onLongPress = { onClick(true) }
                )
            }
            .padding(padding),
    ) {
        content()

        if (enabled) {
            Checkbox(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                checked = selected,
                onCheckedChange = null
            )
        }
    }
}

private val PHOTO_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM uuuu・HH:mm")

@Composable
fun Photo.photoDateText(): String = remember(this) {
    val instant = Instant.fromEpochMilliseconds(this.timeCreated)
    val date = instant.toLocalDateTime(TimeZone.UTC)
    PHOTO_DATE_FORMATTER.format(date.toJavaLocalDateTime())
}

@Composable
fun SharePhotoIconButton(
    subtitle: Boolean,
    getPhotos: suspend () -> List<Photo>,
    getPhotoUri: (Photo) -> Deferred<Uri?>,
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val sendTo = stringResource(R.string.send_to)
    val failedToDownloadImage = stringResource(R.string.failed_download_image)

    val onClick: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val photos = getPhotos()
            val uriList = photos.map(getPhotoUri).awaitAll().filterNotNull()

            withContext(Dispatchers.Main) {
                if (uriList.isEmpty()) {
                    snackbarHostState.showSnackbar(failedToDownloadImage)
                } else if (uriList.size == 1) {
                    val uri = uriList.first()
                    val photo = photos.first()
                    Log.d("URI Image", uri.toString())
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                            photo.name.substringAfterLast('.')
                        )
                    }

                    context.startActivity(Intent.createChooser(shareIntent, sendTo))
                } else {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND_MULTIPLE
                        val arrayList = ArrayList<Uri>()
                        arrayList.addAll(uriList)
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayList)
                        type = "*/*"
                    }

                    context.startActivity(Intent.createChooser(shareIntent, sendTo))
                }
            }
        }
    }

    if (subtitle) {
        IconButtonText(onClick = onClick, text = stringResource(id = R.string.action_share)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_share),
                contentDescription = null,
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_share),
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
fun <T : Photo> PhotoUtilitiesActions(
    klass: KClass<T>,
    selectedItems: SnapshotStateList<Long>,
    mainViewModel: MainViewModel = viewModel()
) {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val deletePhotosDialog = rememberDeletePhotosDialog(onPhotosDeleted = { _, _ -> selectedItems.clear() })

    if (selectedItems.isNotEmpty()) {
        val isLocalPhoto = klass == LocalPhoto::class

        suspend fun getPhotos(): List<Photo> {
            val items = selectedItems.toList()
            return if (isLocalPhoto)
                items.mapNotNull { mainViewModel.getLocalPhotoFlow(it).first() }
            else
                items.mapNotNull { mainViewModel.getNetworkPhotoFlow(it).first() }
        }

        IconButton(onClick = {
            scope.launch {
                deletePhotosDialog.show(getPhotos())
            }
        }) {
            Icon(
                painter = painterResource(R.drawable.ic_action_delete),
                contentDescription = null,
                tint = Color.White,
            )
        }

        SharePhotoIconButton(false, getPhotos = { getPhotos() }, mainViewModel::getPhotoLocalUriAsync)

        if (!isLocalPhoto) {
            IconButton(
                onClick = { navigator.push(MovePhotosScreen(selectedItems.toList())) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_move_folder),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}
