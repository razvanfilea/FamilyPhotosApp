package net.theluckycoder.familyphotos.ui.screen.tabs

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
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
import net.theluckycoder.familyphotos.model.PhotoType
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.isPublic
import net.theluckycoder.familyphotos.ui.composables.FolderFilterTextField
import net.theluckycoder.familyphotos.ui.composables.FolderPreviewItem
import net.theluckycoder.familyphotos.ui.composables.FolderTypeSegmentedButtons
import net.theluckycoder.familyphotos.ui.composables.SortButton
import net.theluckycoder.familyphotos.ui.screen.FolderScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object NetworkFoldersTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            1.toUShort(),
            stringResource(R.string.section_folders),
            painterResource(R.drawable.tab_network_folder_outlined)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.tab_network_folder_filled)

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    override fun Content() = Column(
        modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
    ) {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val folders by mainViewModel.networkFolders.collectAsState(emptyList())
        val sortAscending by mainViewModel.settingsStore.showFoldersAscending.collectAsState(true)
        val selectedPhotoType by mainViewModel.settingsStore.photoType.collectAsState(
            PhotoType.All
        )
        var folderNameFilter by remember { mutableStateOf("") }
        FolderFilterTextField(folderNameFilter, onFilterChange = { folderNameFilter = it })

        val filteredFolders =
            remember(folders, folderNameFilter, sortAscending, selectedPhotoType) {
                val filtered = folders.asSequence()
                    .filter {
                        when (selectedPhotoType) {
                            PhotoType.All -> true
                            PhotoType.Personal -> !it.isPublic()
                            PhotoType.Family -> it.isPublic()
                        }
                    }
                    .filter { it.name.contains(folderNameFilter, ignoreCase = true) }
                    .toList()
                if (sortAscending) filtered else filtered.reversed()
            }

        val columnCount =
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 5

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(columnCount) }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    FolderTypeSegmentedButtons(
                        selectedPhotoType = selectedPhotoType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        mainViewModel = mainViewModel
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SortButton(
                            sortAscending = sortAscending,
                            onClick = {
                                scope.launch {
                                    mainViewModel.settingsStore.setShowFoldersAscending(!sortAscending)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navigator.push(FolderScreen(FolderScreen.Source.FavoritesSource))
                            },
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_star_outline),
                                contentDescription = null
                            )

                            Spacer(Modifier.width(12.dp))

                            Text(stringResource(R.string.favorites))
                        }

                    }
                }
            }

            items(filteredFolders, key = { it.coverPhotoId }) { folder ->
                // Make a fake Network Photo to load the preview
                val photo = NetworkPhoto(
                    id = folder.coverPhotoId,
                    name = "",
                    userId = folder.userId,
                    timeCreated = 0L,
                    folder = folder.name
                )

                FolderPreviewItem(
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    photo = photo,
                    name = folder.name,
                    photosCount = folder.count,
                    onClick = {
                        navigator.push(
                            FolderScreen(
                                FolderScreen.Source.NetworkFolderSource(folder)
                            )
                        )
                    },
                ) {
                    if (folder.isPublic()) {
                        Icon(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .padding(4.dp),
                            painter = painterResource(R.drawable.ic_family_filled),
                            tint = Color.White,
                            contentDescription = null
                        )
                    }
                }

            }
        }

    }
}

