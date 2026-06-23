package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.UploadChoice
import net.theluckycoder.familyphotos.ui.composables.UploadPhotosLayout
import net.theluckycoder.familyphotos.ui.viewmodel.UploadPhotosViewModel

@Composable
fun RenameFolderScreen(folderId: Long, folderName: String) {
    val uploadPhotosViewModel: UploadPhotosViewModel = viewModel()
    val backStack = LocalNavBackStack.current

    val networkFolders by uploadPhotosViewModel.networkFolders.collectAsState(emptyList())

    UploadPhotosLayout(
        networkFolders = networkFolders,
        actionName = stringResource(R.string.action_rename_folder),
        photosToShowcase = emptyList(),
        doneAction = { choice, newName ->
            uploadPhotosViewModel.renameNetworkFolder(
                folderId,
                choice == UploadChoice.Public,
                newName
            )

            // Close the rename screen
            backStack.removeLastOrNull()
            // Close the folder screen
            backStack.removeLastOrNull()
        }
    )

}