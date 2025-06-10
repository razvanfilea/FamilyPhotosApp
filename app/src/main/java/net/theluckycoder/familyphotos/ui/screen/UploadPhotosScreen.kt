package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.first
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.composables.UploadChoice
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel


@Parcelize
data class UploadPhotosScreen(
    private val photoIds: List<Long>,
) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val photosToShowcase = remember { mutableStateOf(emptyList<Photo>()) }
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(photoIds) {
            photosToShowcase.value =
                photoIds.mapNotNull { mainViewModel.getLocalPhotoFlow(it).first() }
        }

        UploadPhotosLayout(
            mainViewModel = mainViewModel,
            title = stringResource(R.string.action_upload_photos),
            photosToShowcase = photosToShowcase.value,
            doneAction = { choice, folderName ->
                mainViewModel.uploadPhotosAsync(
                    photoIds,
                    choice == UploadChoice.Public,
                    folderName.trim().takeIf { it.isNotEmpty() })
                navigator.pop()
            }
        )
    }
}