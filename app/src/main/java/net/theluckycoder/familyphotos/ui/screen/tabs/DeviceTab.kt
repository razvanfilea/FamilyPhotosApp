package net.theluckycoder.familyphotos.ui.screen.tabs

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.FolderFilterTextField
import net.theluckycoder.familyphotos.ui.composables.FolderPreviewItem
import net.theluckycoder.familyphotos.ui.composables.SortButton
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.utils.normalize

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceTab() = Column(
    modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
) {
    val mainViewModel: MainViewModel = viewModel()
    val backStack = LocalNavBackStack.current
    val scope = rememberCoroutineScope()

    val gridState = rememberLazyGridState()
    val folders by mainViewModel.localFolders.collectAsState()
    val sortAscending by mainViewModel.settingsStore.showFoldersAscending.collectAsState(true)

    var folderNameFilter by remember { mutableStateOf("") }
    FolderFilterTextField(folderNameFilter, onFilterChange = { folderNameFilter = it })

    val filteredFolders = remember(folders, folderNameFilter) {
        val filterName = folderNameFilter.normalize()
        folders.filter { it.name.normalize().contains(filterName, ignoreCase = true) }
    }

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 5

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(columnCount) }) {
            SortButton(
                sortAscending = sortAscending,
                onClick = {
                    scope.launch {
                        mainViewModel.settingsStore.setShowFoldersAscending(!sortAscending)
                    }
                }
            )
        }

        items(filteredFolders, key = { it.coverPhotoId }) { folder ->
            val photo = LocalPhoto(
                id = folder.coverPhotoId,
                name = "",
                uri = folder.coverPhotoUri,
                timeCreated = 0L,
                folder = folder.name
            )

            FolderPreviewItem(
                modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                photo = photo,
                name = folder.name,
                photosCount = folder.count,
                onClick = {
                    backStack.add(FolderNav(FolderNav.Source.Local(folder.name)))
                },
            )
        }
    }
}