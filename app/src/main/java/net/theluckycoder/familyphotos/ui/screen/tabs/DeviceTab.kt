package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.FoldersTab
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel

@Composable
fun DeviceTab() {
    val foldersViewModel: FoldersViewModel = viewModel()
    val backStack = LocalNavBackStack.current
    val folders by foldersViewModel.localFolders.collectAsState()

    FoldersTab(
        folders = folders,
        onFolderClick = { folder ->
            backStack.add(FolderNav(FolderNav.Source.Local(folder.name)))
        },
        foldersViewModel = foldersViewModel,
        folderDetailsText = { folder -> "${folder.count} items" },
    )
}
