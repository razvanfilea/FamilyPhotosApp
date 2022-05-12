package net.theluckycoder.familyphotos.ui.screen.tabs

import android.content.res.Configuration
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.PhotosSlideTransition
import net.theluckycoder.familyphotos.ui.screen.FolderFilterTextField
import net.theluckycoder.familyphotos.ui.screen.FolderPreviewItem
import net.theluckycoder.familyphotos.ui.screen.NetworkFolderScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object NetworkFoldersTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            2.toUShort(),
            stringResource(R.string.section_folders),
            painterResource(R.drawable.ic_folder_outlined)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.ic_folder_filled)

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    override fun Content() {
        Navigator(NetworkFoldersScreen) {
            PhotosSlideTransition(navigator = it)
        }
    }

    private object NetworkFoldersScreen : Screen {

        @Composable
        override fun Content() = Column {
            val mainViewModel: MainViewModel = viewModel()
            val navigator = LocalNavigator.currentOrThrow

            SideEffect {
                mainViewModel.showBottomAppBar.value = true
            }

            var folderNameFilter by remember { mutableStateOf("") }
            FolderFilterTextField(folderNameFilter, onFilterChange = { folderNameFilter = it })

            val folders by mainViewModel.networkFolders.collectAsState(emptyList())
            val filteredFolders = remember(folders, folderNameFilter) {
                val searchFilter = folderNameFilter.lowercase()
                folders.filter { it.name.lowercase().contains(searchFilter) }
            }

            val columnCount =
                if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 5

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier.fillMaxSize(),
            ) { // Make a fake Network Photo to load the thumbnail
                items(filteredFolders) { folder ->
                    key(folder.coverPhotoId) {
                        // Make a fake Network Photo to load the thumbnail
                        val photo = NetworkPhoto(
                            id = folder.coverPhotoId,
                            name = "",
                            ownerUserId = folder.ownerUserId,
                            timeCreated = 0L,
                            folder = folder.name
                        )

                        FolderPreviewItem(
                            photo = photo,
                            name = folder.name,
                            photosCount = folder.count,
                            onClick = {
                                navigator.push(NetworkFolderScreen(folder.name))
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
    }

}
