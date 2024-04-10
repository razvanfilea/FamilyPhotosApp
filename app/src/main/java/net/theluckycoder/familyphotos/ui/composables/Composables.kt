package net.theluckycoder.familyphotos.ui.composables

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil.compose.AsyncImage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.PhotosApp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.getPreviewUri
import net.theluckycoder.familyphotos.model.getUri
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.dialog.rememberDeletePhotosDialog
import net.theluckycoder.familyphotos.ui.screen.MovePhotosScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

@Composable
fun SimpleSquarePhoto(photo: Photo) {
    CoilPhoto(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp),
        photo = photo,
        preview = true,
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun CoilPhoto(
    modifier: Modifier = Modifier,
    photo: Photo,
    preview: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    AsyncImage(
        modifier = modifier,
        model = if (!preview) photo.getUri() else photo.getPreviewUri(),
        contentScale = contentScale,
        contentDescription = photo.name,
        imageLoader = LocalImageLoader.current.get(),
        placeholder = ColorPainter(Color.DarkGray),
        error = ColorPainter(Color(0xB6D63535))
    )
}

@Composable
fun IconButtonText(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(
                onClick = onClick,
                role = Role.Button,
                enabled = enabled,
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
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = navIconOnClick) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        actions = actions
    )
}

@Composable
fun SelectableItem(
    modifier: Modifier = Modifier,
    inSelectionMode: Boolean,
    selected: Boolean,
    content: @Composable BoxScope.() -> Unit
) {

    if (inSelectionMode) {
        Surface(
            modifier = modifier,
            tonalElevation = 3.dp
        ) {
            val transition = updateTransition(selected, label = "selected")
            val padding by transition.animateDp(label = "padding") { selected ->
                if (selected) 10.dp else 0.dp
            }
            val roundedCornerShape by transition.animateDp(label = "corner") { selected ->
                if (selected) 16.dp else 0.dp
            }

            Box(
                modifier = Modifier
                    .padding(padding)
                    .clip(RoundedCornerShape(roundedCornerShape))
            ) {
                content()

                if (selected) {
                    val bgColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    Icon(
                        painter = painterResource(R.drawable.radio_button_checked),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.radio_button_unchecked),
                        tint = Color.White.copy(alpha = 0.7f),
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
fun SelectablePhoto(
    modifier: Modifier = Modifier,
    inSelectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit = {},
    onSelect: () -> Unit = {},
    onDeselect: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    SelectableItem(
        modifier = modifier.selectableClickable(
            inSelectionMode = inSelectionMode,
            selected = selected,
            onClick = onClick,
            onSelect = onSelect,
            onDeselect = onDeselect
        ),
        inSelectionMode = inSelectionMode,
        selected = selected,
        content = content
    )
}

private val PHOTO_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM uuuu・HH:mm")

@Composable
fun Photo.photoDateText(): String = remember(this) {
    val instant = Instant.fromEpochSeconds(this.timeCreated)
    val date = instant.toLocalDateTime(PhotosApp.LOCAL_TIME_ZONE)
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

    var isLoading by remember { mutableStateOf(false) }

    val onClick: () -> Unit = {
        isLoading = true

        scope.launch(Dispatchers.Default) {
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

                isLoading = false
            }
        }
    }

    val icon = painterResource(
        if (isLoading)
            R.drawable.ic_downloading
        else
            R.drawable.ic_action_share
    )

    if (subtitle) {
        IconButtonText(
            onClick = onClick,
            text = stringResource(id = R.string.action_share),
            enabled = !isLoading
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
            )
        }
    } else {
        IconButton(onClick = onClick, enabled = !isLoading) {
            Icon(
                painter = icon,
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
    val deletePhotosDialog = rememberDeletePhotosDialog(onPhotosDeleted = { selectedItems.clear() })

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
                @Suppress("UNCHECKED_CAST")
                when (klass) {
                    NetworkPhoto::class -> deletePhotosDialog.show(getPhotos() as List<NetworkPhoto>)
                    LocalPhoto::class -> {
                        mainViewModel.deleteLocalPhotos(
                            getPhotos() as List<LocalPhoto>
                        )
                        selectedItems.clear()
                    }
                }
            }
        }) {
            Icon(
                painter = painterResource(R.drawable.ic_action_delete),
                contentDescription = null,
                tint = Color.White,
            )
        }

        SharePhotoIconButton(
            false,
            getPhotos = { getPhotos() },
            mainViewModel::getPhotoLocalUriAsync
        )

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
