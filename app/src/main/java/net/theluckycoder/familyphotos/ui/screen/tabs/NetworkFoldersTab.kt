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
import androidx.compose.runtime.rememberCoroutineScope
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
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.isPublic
import net.theluckycoder.familyphotos.ui.composables.FolderPreviewItem
import net.theluckycoder.familyphotos.ui.screen.NetworkFolderScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object NetworkFoldersTab : BottomTab, FoldersTab<NetworkFolder>() {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            2.toUShort(),
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
        val filteredFolders = rememberFiltering(folders, sortAscending)

        val columnCount =
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 5

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(columnCount) }) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                ) {
                    FilledTonalButton(
                        modifier = Modifier.padding(4.dp),
                        onClick = {
                            navigator.push(NetworkFolderScreen(NetworkFolderScreen.Source.Favorites))
                        },
                    ) {
                        Icon(painterResource(R.drawable.ic_star_outline), contentDescription = null)

                        Spacer(Modifier.width(12.dp))

                        Text(stringResource(R.string.favorites))
                    }

                    SortButton(sortAscending) {
                        scope.launch {
                            mainViewModel.settingsStore.setShowFoldersAscending(!sortAscending)
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
                    modifier = Modifier.animateItemPlacement(),
                    photo = photo,
                    name = folder.name,
                    photosCount = folder.count,
                    onClick = {
                        navigator.push(
                            NetworkFolderScreen(
                                NetworkFolderScreen.Source.FolderName(
                                    folder.name
                                )
                            )
                        )
                    },
                ) {
                    if (folder.isPublic) {
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

