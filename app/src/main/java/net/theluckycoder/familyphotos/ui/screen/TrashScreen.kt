package net.theluckycoder.familyphotos.ui.screen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.composables.NavBackTopAppBar
import net.theluckycoder.familyphotos.ui.composables.PhotoListItem
import net.theluckycoder.familyphotos.ui.composables.PhotosSelectionBar
import net.theluckycoder.familyphotos.ui.composables.photoGridDrag
import net.theluckycoder.familyphotos.ui.viewmodel.TrashViewModel

@Composable
fun TrashScreen() {
    val trashViewModel: TrashViewModel = viewModel()
    val backStack = LocalNavBackStack.current

    val trashedPhotosState = trashViewModel.trashedPhotos.collectAsState(null) // TODO make this paginated
    val trashedPhotos = trashedPhotosState.value
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }
    val gridState = rememberLazyGridState()

    BackHandler(enabled = selectedPhotoIds.isNotEmpty()) {
        selectedPhotoIds.clear()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
    ) { paddingValues ->
        val columnCount =
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 4 else 8

        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxSize()
                /*.photoGridDrag( // TODO fix when this is paginated
                    lazyGridState = gridState,
                    selectedIds = selectedPhotoIds,
                    items = trashedPhotos ?: emptyList()
                )*/,
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
                PhotoListItem(
                    modifier = Modifier
                        .animateItem(fadeInSpec = null, fadeOutSpec = null)
                        .aspectRatio(1f)
                        .padding(0.5.dp),
                    photo = photo,
                    selectedPhotoIds = selectedPhotoIds,
                    openPhoto = {}
                )
            }
        }

        PhotosSelectionBar(selectedPhotoIds) {
            IconButton(onClick = {
                trashViewModel.restorePhotos(selectedPhotoIds.toLongArray())
                selectedPhotoIds.clear()
            }) {
                Icon(painterResource(R.drawable.ic_restore), contentDescription = null)
            }
        }
    }
}