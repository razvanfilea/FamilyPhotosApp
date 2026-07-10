package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSettingsDataStore
import net.theluckycoder.familyphotos.ui.composables.FoldersGridList
import net.theluckycoder.familyphotos.ui.composables.PhotoTypeChips
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel

@Composable
fun NetworkFoldersTab() {
    val foldersViewModel: FoldersViewModel = viewModel()
    val backStack = LocalNavBackStack.current
    val folders by foldersViewModel.networkFolders.collectAsState()
    val currentUser by foldersViewModel.currentUser.collectAsState(null)

    FoldersGridList(
        folders = folders,
        onFolderClick = { folder ->
            backStack.add(FolderNav(FolderNav.Source.Network(folder.id, folder.name)))
        },
        currentUserId = currentUser?.userId,
        extraHeader = {
            val settingsDataStore = LocalSettingsDataStore.current
            val selectedPhotoType by settingsDataStore.photoType.collectAsState()

            PhotoTypeChips(
                selectedPhotoType = selectedPhotoType,
                onChangePhotoType = settingsDataStore::setSelectedPhotoType,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    )
}
