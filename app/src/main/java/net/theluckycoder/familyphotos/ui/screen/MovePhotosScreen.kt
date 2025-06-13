package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.composables.UploadChoice
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Composable
fun MovePhotosScreen(photoIds: List<Long>) {
    val mainViewModel: MainViewModel = viewModel()
    val photosToShowcase = remember { mutableStateOf(emptyList<Photo>()) }
    val snackbarHostState = LocalSnackbarHostState.current
    val backStack = LocalNavBackStack.current

    LaunchedEffect(photoIds) {
        photosToShowcase.value =
            photoIds.mapNotNull { mainViewModel.getNetworkPhotoFlow(it).first() }
    }

    val moveSuccess = stringResource(R.string.status_move_success)
    val moveFailure = stringResource(R.string.status_move_failure)

    UploadPhotosLayout(
        mainViewModel = mainViewModel,
        title = stringResource(R.string.action_move_photos),
        photosToShowcase = photosToShowcase.value,
        doneAction = { choice, folderName ->
            mainViewModel.viewModelScope.launch {
                val result = mainViewModel.changePhotosLocationAsync(
                    photoIds,
                    choice == UploadChoice.Public,
                    folderName
                ).await()

                val message = if (result) moveSuccess else moveFailure

                snackbarHostState.showSnackbar(message)
            }
            backStack.removeLastOrNull()
        }
    )
}
