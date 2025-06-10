package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.ui.composables.UploadChoice
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel


@Parcelize
data class RenameFolderScreen(
    private val folder: NetworkFolder,
) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow

        UploadPhotosLayout(
            mainViewModel = mainViewModel,
            title = stringResource(R.string.action_rename_folder),
            photosToShowcase = emptyList(),
            doneAction = { choice, folderName ->
                mainViewModel.renameNetworkFolder(
                    folder,
                    choice == UploadChoice.Public,
                    folderName.trim().takeIf { it.isNotEmpty() })
                navigator.pop()
                navigator.pop()
            }
        )
    }
}