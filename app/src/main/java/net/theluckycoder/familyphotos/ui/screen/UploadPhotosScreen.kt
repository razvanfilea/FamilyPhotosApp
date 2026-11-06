package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.UploadPhotosViewModel


@Composable
fun UploadPhotosScreen(photoIds: LongArray) {
    val uploadPhotosViewModel: UploadPhotosViewModel = viewModel()
    val backStack = LocalNavBackStack.current
    var photosToShowcase by remember { mutableStateOf(emptyList<Photo>()) }

    LaunchedEffect(Unit) {
        photosToShowcase = uploadPhotosViewModel.getLocalPhotos(photoIds).await()
    }

    UploadScreen(
        uploadPhotosViewModel = uploadPhotosViewModel,
        photosToShowcase = photosToShowcase,
        onUpload = { choice ->
            uploadPhotosViewModel.uploadPhotosAsync(photoIds, choice)
            backStack.removeLastOrNull()
        },
        onCancel = { backStack.removeLastOrNull() },
    )
}

/** Files shared from another app, shown by [net.theluckycoder.familyphotos.ui.ShareActivity] */
@Composable
fun UploadSharedScreen(
    photos: List<Photo>,
    onUpload: (UploadChoice) -> Unit,
    onCancel: () -> Unit,
) = UploadScreen(
    uploadPhotosViewModel = viewModel(),
    photosToShowcase = photos,
    onUpload = onUpload,
    onCancel = onCancel,
    animateSharedBounds = false,
)

@Composable
private fun UploadScreen(
    uploadPhotosViewModel: UploadPhotosViewModel,
    photosToShowcase: List<Photo>,
    onUpload: (UploadChoice) -> Unit,
    onCancel: () -> Unit,
    animateSharedBounds: Boolean = true,
) {
    val networkFolders by uploadPhotosViewModel.networkFolders.collectAsState(emptyList())
    val userId by uploadPhotosViewModel.currentUser.collectAsState()

    UploadPhotosLayout(
        networkFolders = networkFolders,
        actionName = stringResource(R.string.action_upload_photos),
        photosToShowcase = photosToShowcase,
        currentUserId = userId?.userId,
        onCancel = onCancel,
        animateSharedBounds = animateSharedBounds,
        doneAction = onUpload,
    )
}
