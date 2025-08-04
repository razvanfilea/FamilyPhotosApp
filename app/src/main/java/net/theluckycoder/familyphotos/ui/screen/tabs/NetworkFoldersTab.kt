package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.isPublic
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.FoldersTab
import net.theluckycoder.familyphotos.ui.composables.PhotoTypeSegmentedButtons
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel

@Composable
fun NetworkFoldersTab() {
    val foldersViewModel: FoldersViewModel = viewModel()
    val backStack = LocalNavBackStack.current
    val folders by foldersViewModel.networkFolders.collectAsState()

    val personalString = stringResource(R.string.photo_type_personal)
    val familyString = stringResource(R.string.photo_type_family)

    FoldersTab(
        folders = folders,
        onFolderClick = { folder ->
            backStack.add(FolderNav(FolderNav.Source.Network(folder)))
        },
        folderDetailsText = { folder ->
            val owner = if (folder.isPublic) familyString else personalString
            "${folder.count} items • $owner"
        },
        foldersViewModel = foldersViewModel,
        extraHeader = {
            val selectedPhotoType by foldersViewModel.selectedPhotoType.collectAsState()

            PhotoTypeSegmentedButtons(
                selectedPhotoType = selectedPhotoType,
                onChangePhotoType = { type ->
                    foldersViewModel.setSelectedPhotoType(type)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    )
}
