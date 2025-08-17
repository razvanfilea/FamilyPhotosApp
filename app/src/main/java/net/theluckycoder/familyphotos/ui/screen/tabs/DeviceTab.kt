package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.FoldersGridList
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel

@Composable
fun DeviceTab() {
    val foldersViewModel: FoldersViewModel = viewModel()
    val backStack = LocalNavBackStack.current
    val folders by foldersViewModel.localFolders.collectAsState()

    val folderNameFilter = remember { mutableStateOf("") }

    FoldersGridList(
        folders = folders,
        onFolderClick = { folder ->
            backStack.add(FolderNav(FolderNav.Source.Local(folder.name)))
        },
        folderNameFilter = folderNameFilter.value,
        onSearch = { folderNameFilter.value = it },
        foldersViewModel = foldersViewModel,
        folderDetailsText = { folder ->
            pluralStringResource(R.plurals.items_photos, folder.count, folder.count)
        },
    )
}
