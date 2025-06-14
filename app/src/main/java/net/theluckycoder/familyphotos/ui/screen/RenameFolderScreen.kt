package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.NetworkFolder
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.UploadChoice
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Composable
fun RenameFolderScreen(folder: NetworkFolder) {
    val mainViewModel: MainViewModel = viewModel()
    val backStack = LocalNavBackStack.current

    UploadPhotosLayout(
        mainViewModel = mainViewModel,
        title = stringResource(R.string.action_rename_folder),
        photosToShowcase = emptyList(),
        doneAction = { choice, folderName ->
            mainViewModel.renameNetworkFolder(
                folder,
                choice == UploadChoice.Public,
                folderName
            )

            // Close the rename screen
            backStack.removeLastOrNull()
            // Close the folder screen
            backStack.removeLastOrNull()
        }
    )

}