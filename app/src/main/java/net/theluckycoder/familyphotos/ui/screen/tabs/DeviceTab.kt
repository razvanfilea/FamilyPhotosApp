package net.theluckycoder.familyphotos.ui.screen.tabs

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.ui.composables.FolderFilterTextField
import net.theluckycoder.familyphotos.ui.composables.FolderPreviewItem
import net.theluckycoder.familyphotos.ui.composables.SortButton
import net.theluckycoder.familyphotos.ui.screen.FolderScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object DeviceTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            3.toUShort(),
            stringResource(R.string.section_device),
            painterResource(R.drawable.ic_storage_outline)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.ic_storage_filled)

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() = Column(
        modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
    ) {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val folders by mainViewModel.localFolders.collectAsState(emptyList())
        val sortAscending by mainViewModel.settingsStore
            .showFoldersAscending.collectAsState(true)

        val columnCount =
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 5

        var folderNameFilter by remember { mutableStateOf("") }
        FolderFilterTextField(folderNameFilter, onFilterChange = { folderNameFilter = it })

        val filteredFolders = remember(folders, folderNameFilter, sortAscending) {
            val filtered = folders.filter { it.name.contains(folderNameFilter, ignoreCase = true) }
            if (sortAscending) filtered else filtered.reversed()
        }

        LazyVerticalGrid(
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
                        navigator.push(FolderScreen(FolderScreen.Source.LocalFolderSource(folder.name)))
                    },
                )
            }
        }
    }
}