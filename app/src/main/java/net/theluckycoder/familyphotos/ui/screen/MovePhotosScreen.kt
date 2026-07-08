package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.UploadPhotosViewModel

@Composable
fun MovePhotosScreen(photoIds: LongArray) {
    val uploadPhotosViewModel: UploadPhotosViewModel = viewModel()
    var photosToShowcase by remember { mutableStateOf(emptyList<Photo>()) }
    val snackbarHostState = LocalSnackbarHostState.current
    val backStack = LocalNavBackStack.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        photosToShowcase = uploadPhotosViewModel.getNetworkPhotos(photoIds).await()
    }

    val moveSuccess = stringResource(R.string.status_move_success)
    val moveFailure = stringResource(R.string.status_move_failure)

    val networkFolders by uploadPhotosViewModel.networkFolders.collectAsState(emptyList())
    val userId by uploadPhotosViewModel.currentUser.collectAsState()
    var isMoving by remember { mutableStateOf(false) }

    UploadPhotosLayout(
        networkFolders = networkFolders,
        actionName = stringResource(R.string.action_move_photos),
        photosToShowcase = photosToShowcase,
        currentUserId = userId?.userId,
        enabled = !isMoving,
        doneAction = { choice ->
            isMoving = true
            scope.launch {
                val result = uploadPhotosViewModel.movePhotos(photoIds, choice).await()

                val message = if (result) moveSuccess else moveFailure
                snackbarHostState.showSnackbar(message)
                backStack.removeLastOrNull()
            }
        }
    )
}
