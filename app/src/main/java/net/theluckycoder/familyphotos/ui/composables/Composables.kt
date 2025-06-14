package net.theluckycoder.familyphotos.ui.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateSet
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.Photo
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.getPreviewUri
import net.theluckycoder.familyphotos.data.model.getUri
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.MovePhotosNav
import net.theluckycoder.familyphotos.ui.UploadPhotosNav
import net.theluckycoder.familyphotos.ui.dialog.rememberDeletePhotosDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import java.time.format.DateTimeFormatter

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
                indication = ripple(bounded = false, radius = 24.dp)
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
            tonalElevation = 15.dp
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
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    PHOTO_DATE_FORMATTER.format(date.toJavaLocalDateTime())
}

@Composable
fun SharePhotoIconButton(
    subtitle: Boolean,
    getPhotosUris: suspend () -> List<Uri>,
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
            val uriList = getPhotosUris()

            withContext(Dispatchers.Main) {
                if (uriList.isEmpty()) {
                    snackbarHostState.showSnackbar(failedToDownloadImage)
                } else {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND_MULTIPLE
                        val arrayList = ArrayList<Uri>()
                        arrayList.addAll(uriList)
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayList)
                        type = "*/*"
                    }
                    ensureActive()

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
fun PhotoUtilitiesActions(
    isLocalPhoto: Boolean,
    selectedItems: SnapshotStateSet<Long>,
    mainViewModel: MainViewModel = viewModel()
) {
    val backStack = LocalNavBackStack.current
    val scope = rememberCoroutineScope()
    val deletePhotosDialog = rememberDeletePhotosDialog(onPhotosDeleted = { selectedItems.clear() })

    if (selectedItems.isNotEmpty()) {
        IconButton(onClick = {
            scope.launch {
                @Suppress("UNCHECKED_CAST")
                if (isLocalPhoto) {
                    mainViewModel.deleteLocalPhotos(selectedItems.toLongArray())
                    selectedItems.clear()
                } else {
                    deletePhotosDialog.show(selectedItems.toLongArray())
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
            getPhotosUris = {
                val photoIds = selectedItems.toLongArray()
                if (isLocalPhoto) {
                    mainViewModel.getLocalPhotosUriAsync(photoIds).await()
                } else {
                    mainViewModel.getNetworkPhotosUriAsync(photoIds).await()
                }
            }
        )

        if (isLocalPhoto) {
            IconButton(
                onClick = { backStack.add(UploadPhotosNav(selectedItems.toList())) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_upload_outline),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        } else {
            IconButton(
                onClick = { backStack.add(MovePhotosNav(selectedItems.toList())) }
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

@Composable
fun FolderTypeSegmentedButtons(
    selectedPhotoType: PhotoType,
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()

    SingleChoiceSegmentedButtonRow(
        modifier
    ) {
        PhotoType.entries.forEach { type ->
            val selected = selectedPhotoType == type
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = type.index,
                    count = PhotoType.entries.size
                ),
                onClick = {
                    scope.launch {
                        mainViewModel.settingsStore.setFolderFilterType(
                            type
                        )
                    }
                },
                selected = selected,
                icon = {
                    val res = if (selected) {
                        when (type) {
                            PhotoType.All -> R.drawable.ic_photo_filled
                            PhotoType.Personal -> R.drawable.ic_person_filled
                            PhotoType.Family -> R.drawable.ic_family_filled
                        }
                    } else {
                        when (type) {
                            PhotoType.All -> R.drawable.ic_photo_outline
                            PhotoType.Personal -> R.drawable.ic_person_outline
                            PhotoType.Family -> R.drawable.ic_family_outline
                        }
                    }
                    Icon(painterResource(res), contentDescription = null)
                }
            ) {
                val res = when (type) {
                    PhotoType.All -> R.string.photo_type_all
                    PhotoType.Personal -> R.string.photo_type_personal
                    PhotoType.Family -> R.string.photo_type_family
                }
                Text(stringResource(res))
            }
        }
    }
}

@Composable
fun VerticallyAnimatedInt(
    targetState: Int,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable AnimatedVisibilityScope.(targetState: Int) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            if (targetState > initialState) {
                // If the target number is larger, it slides up and fades in
                // while the initial (smaller) number slides up and fades out.
                slideInVertically { height -> height } + fadeIn() togetherWith
                        slideOutVertically { height -> -height } + fadeOut()
            } else {
                // If the target number is smaller, it slides down and fades in
                // while the initial number slides down and fades out.
                slideInVertically { height -> -height } + fadeIn() togetherWith
                        slideOutVertically { height -> height } + fadeOut()
            }.using(
                // Disable clipping since the faded slide-in/out should
                // be displayed out of bounds.
                SizeTransform(clip = false)
            )
        },
        contentAlignment = contentAlignment,
        content = content,
        label = "int_animation"
    )
}
