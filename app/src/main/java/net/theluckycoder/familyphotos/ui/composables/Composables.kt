package net.theluckycoder.familyphotos.ui.composables

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.TIME_ZONE
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.navigation.LocalBottomSheetNavigator
import net.theluckycoder.familyphotos.ui.screen.MovePhotosScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import kotlin.reflect.KClass

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
        colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.Transparent)
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

@Composable
fun Photo.getPhotoDate() = remember(this) {
    val instant = Instant.fromEpochMilliseconds(this.timeCreated)
    val date = instant.toLocalDateTime(TIME_ZONE)

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
fun SharePhotoIconButton(
    subtitle: Boolean,
    getPhotos: suspend () -> List<Photo>,
    mainViewModel: MainViewModel
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val sendTo = stringResource(R.string.send_to)
    val failedToDownloadImage = stringResource(R.string.failed_download_image)

    fun onClick() {
        scope.launch(Dispatchers.IO) {
            val photos = getPhotos()
            val uriList = photos.map { mainViewModel.getPhotoLocalUriAsync(it) }.awaitAll()
                .filterNotNull()

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
        IconButtonText(onClick = ::onClick, text = stringResource(id = R.string.action_share)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_share),
                contentDescription = null,
            )
        }
    } else {
        IconButton(onClick = ::onClick) {
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
    val bottomSheetNavigator = LocalBottomSheetNavigator.current
    val scope = rememberCoroutineScope()

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
                bottomSheetNavigator.show(DeletePhotosDialog(getPhotos()))
                selectedItems.clear()
            }
        }) {
            Icon(
                painter = painterResource(R.drawable.ic_action_delete),
                contentDescription = null,
                tint = Color.White,
            )
        }

        SharePhotoIconButton(false, getPhotos = { getPhotos() }, mainViewModel)

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
