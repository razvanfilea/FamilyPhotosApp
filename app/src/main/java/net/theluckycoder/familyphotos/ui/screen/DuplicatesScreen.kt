package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.PUBLIC_USER_ID
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.composables.NavBackTopAppBar
import net.theluckycoder.familyphotos.ui.composables.ZoomableImage
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialogCaller
import net.theluckycoder.familyphotos.ui.dialog.rememberDeletePhotosDialog
import net.theluckycoder.familyphotos.ui.dialog.rememberNetworkPhotoInfoDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Composable
fun DuplicatesScreen() {
    val mainViewModel: MainViewModel = viewModel()
    val backStack = LocalNavBackStack.current
    val duplicates = remember { mutableStateListOf<List<NetworkPhoto>>() }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val value = mainViewModel.getDuplicatesAsync().await()
        error = value == null
        duplicates.addAll(value.orEmpty())
    }

    val pagerState = rememberPagerState { duplicates.size }
    val deletePhotosDialog = rememberDeletePhotosDialog()
    val currentPhoto = duplicates.getOrNull(pagerState.currentPage)?.firstOrNull()
    val networkPhotoInfoDialog = rememberNetworkPhotoInfoDialog(currentPhoto)

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            NavBackTopAppBar(
                title = stringResource(R.string.title_duplicates),
                navIconOnClick = {
                    backStack.removeLastOrNull()
                },
                subtitle = duplicates.size.takeIf { it != 0 }?.toString(),
                actions = {
                    IconButton(onClick = networkPhotoInfoDialog::show) {
                        Icon(
                            painter = painterResource(R.drawable.ic_outline_info),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            if (error) {
                Text(
                    stringResource(R.string.duplicates_error),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 20.sp,
                    modifier = Modifier.align(
                        Alignment.Center
                    )
                )
                return@Scaffold
            }

            if (duplicates.isEmpty()) {
                Text(
                    stringResource(R.string.duplicates_none),
                    fontSize = 20.sp,
                    modifier = Modifier.align(
                        Alignment.Center
                    )
                )
                return@Scaffold
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            PagerContent(duplicates, page, deletePhotosDialog)
        }
    }

}

@Composable
private fun PagerContent(
    duplicates: SnapshotStateList<List<NetworkPhoto>>,
    page: Int,
    deletePhotosDialog: DeletePhotosDialogCaller
) = Box(Modifier.fillMaxSize()) {
    val duplicate = duplicates[page]

    val personalDrawable = painterResource(R.drawable.ic_person_outline)
    val familyDrawable = painterResource(R.drawable.ic_family_outline)

    ZoomableImage(
        modifier = Modifier.fillMaxSize(),
        photo = duplicate.first(),
        showUI = {}
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (duplicate.isEmpty() || duplicate.size == 1) {
            Text(stringResource(R.string.duplicates_error))
            return@Column
        }
        Text(stringResource(R.string.duplicates_which_to_keep))

        duplicate.forEach { photo ->
            Button(
                onClick = {
                    val toDelete = duplicate.map { it.id }.filterNot { it == photo.id }
                        .toLongArray()
                    deletePhotosDialog.show(
                        photoIds = toDelete,
                        onPhotosDeleted = { duplicates.removeAt(page) }
                    )
                },
            ) {
                val icon =
                    if (photo.userId == PUBLIC_USER_ID) familyDrawable else personalDrawable
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp)
                )

                if (!photo.folder.isNullOrEmpty()) {
                    Text(photo.folder)
                } else {
                    Text(
                        stringResource(R.string.duplicates_no_folder),
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}