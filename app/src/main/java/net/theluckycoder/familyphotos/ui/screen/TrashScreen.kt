package net.theluckycoder.familyphotos.ui.screen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
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
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.TimelineLayout
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.NavBackTopAppBar
import net.theluckycoder.familyphotos.ui.composables.PhotoListItem
import net.theluckycoder.familyphotos.ui.composables.PhotosSelectionBar
import net.theluckycoder.familyphotos.ui.composables.photoGridDrag
import net.theluckycoder.familyphotos.ui.viewmodel.TrashViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen() {
    val trashViewModel: TrashViewModel = viewModel()
    val backStack = LocalNavBackStack.current

    val trashedPhotosState = trashViewModel.trashedPhotos.collectAsState(null)
    val trashedPhotos = trashedPhotosState.value
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }
    val gridState = rememberLazyGridState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val nowEpochSeconds = remember { System.currentTimeMillis() / 1000L }

    BackHandler(enabled = selectedPhotoIds.isNotEmpty()) {
        selectedPhotoIds.clear()
    }

    Scaffold { paddingValues ->
        val columnCount =
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 3 else 7

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
                        text = pluralStringResource(R.plurals.trash_days_remaining, daysRemaining, daysRemaining),
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

        PhotosSelectionBar(selectedPhotoIds) {
            IconButton(onClick = {
                trashViewModel.restorePhotos(selectedPhotoIds.toLongArray())
                selectedPhotoIds.clear()
            }) {
                Icon(painterResource(R.drawable.ic_restore), contentDescription = null)
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(painterResource(R.drawable.ic_delete_forever), contentDescription = null)
            }
        }
    }

    if (showDeleteDialog) {
        ModalBottomSheet(onDismissRequest = { showDeleteDialog = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    text = stringResource(R.string.confirm_permanent_delete),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    text = stringResource(R.string.confirm_permanent_delete_detail),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                Row {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showDeleteDialog = false }
                    ) {
                        Text(text = stringResource(R.string.action_cancel))
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            trashViewModel.deleteNetworkPhotos(selectedPhotoIds.toLongArray())
                            selectedPhotoIds.clear()
                            showDeleteDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFe53935),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }
}

private fun daysRemaining(photo: NetworkPhoto, nowEpochSeconds: Long): Int {
    val trashedOn = photo.trashedOn ?: return 30
    val elapsed = nowEpochSeconds - trashedOn
    val daysElapsed = TimeUnit.SECONDS.toDays(elapsed).toInt()
    return (30 - daysElapsed).coerceAtLeast(0)
}
