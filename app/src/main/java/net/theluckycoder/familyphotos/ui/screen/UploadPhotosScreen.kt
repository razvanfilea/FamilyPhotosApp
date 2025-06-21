package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.Photo
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.UploadChoice
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel


@Composable
fun UploadPhotosScreen(photoIds: LongArray) {
    val mainViewModel: MainViewModel = viewModel()
    val foldersViewModel: FoldersViewModel = viewModel()

    val photosToShowcase = remember { mutableStateOf(emptyList<Photo>()) }
    val backStack = LocalNavBackStack.current
    val networkFolders by foldersViewModel.networkFolders.collectAsState()

    LaunchedEffect(photoIds) {
        photosToShowcase.value = mainViewModel.getNetworkPhotos(photoIds).await()
    }

    UploadPhotosLayout(
        networkFolders = networkFolders,
        title = stringResource(R.string.action_upload_photos),
        photosToShowcase = photosToShowcase.value,
        doneAction = { choice, folderName ->
            mainViewModel.uploadPhotosAsync(
                photoIds,
                choice == UploadChoice.Public,
                folderName.trim().takeIf { it.isNotEmpty() })

            backStack.removeLastOrNull()
        }
    )
}
