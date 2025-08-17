package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.UploadChoice
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.UploadPhotosViewModel


@Composable
fun UploadPhotosScreen(photoIds: LongArray) {
    val uploadPhotosViewModel: UploadPhotosViewModel = viewModel()

    val photosToShowcase = remember { mutableStateOf(emptyList<Photo>()) }
    val backStack = LocalNavBackStack.current
    val networkFolders by uploadPhotosViewModel.networkFolders.collectAsState(emptyList())

    LaunchedEffect(photoIds) {
        photosToShowcase.value = uploadPhotosViewModel.getLocalPhotos(photoIds).await()
    }

    UploadPhotosLayout(
        networkFolders = networkFolders,
        actionName = stringResource(R.string.action_upload_photos),
        photosToShowcase = photosToShowcase.value,
        doneAction = { choice, folderName ->
            uploadPhotosViewModel.uploadPhotosAsync(
                photoIds,
                choice == UploadChoice.Public,
                folderName.trim().takeIf { it.isNotEmpty() })

            backStack.removeLastOrNull()
        }
    )
}
