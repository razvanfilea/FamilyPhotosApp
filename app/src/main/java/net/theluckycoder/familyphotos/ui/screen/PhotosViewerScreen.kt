package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.composables.PhotosViewer
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewModel

@Parcelize
data class PhotosViewerScreen(
    private val photos: List<Photo>,
) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val photoViewModel: PhotoViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow

        PhotosViewer(
            photos = photos,
            photoViewModel = photoViewModel,
            close = {
                navigator.pop()
            }
        )
    }

}