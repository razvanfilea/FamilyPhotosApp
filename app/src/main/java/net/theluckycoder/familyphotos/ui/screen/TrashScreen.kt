package net.theluckycoder.familyphotos.ui.screen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.TimelineLayout
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.NavBackTopAppBar
import net.theluckycoder.familyphotos.ui.composables.PhotoListItem
import net.theluckycoder.familyphotos.ui.composables.PhotosSelectionBar
import net.theluckycoder.familyphotos.ui.composables.photoGridDrag
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.viewmodel.TrashViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen() = Scaffold { paddingValues ->
    val trashViewModel: TrashViewModel = viewModel()
    val backStack = LocalNavBackStack.current

    val trashedPhotosState = trashViewModel.trashedPhotos.collectAsState(null)
    val trashedPhotos = trashedPhotosState.value
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var showDeleteDialogForPhotos by remember { mutableStateOf<List<Photo>?>(null) }

    val nowEpochSeconds = remember { System.currentTimeMillis() / 1000L }

    BackHandler(enabled = selectedPhotoIds.isNotEmpty()) {
        selectedPhotoIds.clear()
    }

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 3 else 7

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                .photoGridDrag(
                    lazyGridState = gridState,
                    selectedIds = selectedPhotoIds,
                    items = trashedPhotos ?: emptyList(),
                    timelineLayout = TimelineLayout.EMPTY,
                ),
            state = gridState,
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding())
        ) {
            item(
                key = "header",
                span = { GridItemSpan(columnCount) },
                contentType = "header"
            ) {
                NavBackTopAppBar(
                    title = stringResource(R.string.title_trash),
                    navIconOnClick = {
                        backStack.removeLastOrNull()
                    },
                    subtitle = trashedPhotos?.size.takeIf { it != 0 }?.toString(),
                )
            }

            item(
                key = "info_banner",
                span = { GridItemSpan(columnCount) },
                contentType = "info"
            ) {
                Text(
                    text = stringResource(R.string.trash_auto_delete_info),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (trashedPhotos == null) {
                item(
                    span = { GridItemSpan(columnCount) },
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyVerticalGrid
            }

            if (trashedPhotos.isEmpty()) {
                item(
                    span = { GridItemSpan(columnCount) },
                ) {
                    Text(
                        stringResource(R.string.trash_is_empty),
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                    )
                }
            }

            items(trashedPhotos, key = { it.id }) { photo ->
                Box {
                    PhotoListItem(
                        modifier = Modifier
                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                            .aspectRatio(1f)
                            .padding(0.5.dp),
                        photo = photo,
                        inSelectionMode = selectedPhotoIds.isNotEmpty(),
                        selectedPhotoIds = selectedPhotoIds,
                        openPhoto = {}
                    )

                    val daysRemaining = daysRemaining(photo, nowEpochSeconds)
                    val badgeColor = if (daysRemaining <= 7) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Black.copy(alpha = 0.6f)
                    }

                    Text(
                        text = pluralStringResource(
                            R.plurals.trash_days_remaining,
                            daysRemaining,
                            daysRemaining
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                color = badgeColor,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        PhotosSelectionBar(
            selectedPhotoIds = selectedPhotoIds,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .padding(top = 8.dp)
        ) {
            IconButton(onClick = {
                trashViewModel.restorePhotos(selectedPhotoIds.toLongArray())
                selectedPhotoIds.clear()
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_restore),
                    contentDescription = stringResource(R.string.action_restore),
                )
            }
            IconButton(onClick = {
                scope.launch {
                    val photos = trashViewModel.getNetworkPhotos(selectedPhotoIds.toLongArray())
                    showDeleteDialogForPhotos = photos
                }
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_forever),
                    contentDescription = stringResource(R.string.confirm_permanent_delete),
                )
            }
        }
    }

    showDeleteDialogForPhotos?.let { photos ->
        DeletePhotosDialog(
            photos = photos,
            isPermanent = true,
            onDismissRequest = { showDeleteDialogForPhotos = null },
            onConfirmDelete = { list ->
                trashViewModel.deleteNetworkPhotos(list.map { it.id }.toLongArray())
            },
            onPhotosDeleted = { selectedPhotoIds.clear() }
        )
    }
}

private fun daysRemaining(photo: NetworkPhoto, nowEpochSeconds: Long): Int {
    val trashedOn = photo.trashedOn ?: return 30
    val elapsed = nowEpochSeconds - trashedOn
    val daysElapsed = TimeUnit.SECONDS.toDays(elapsed).toInt()
    return (30 - daysElapsed).coerceAtLeast(0)
}
