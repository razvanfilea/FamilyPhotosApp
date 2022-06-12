package net.theluckycoder.familyphotos.ui.screen.tabs

import android.content.res.Configuration
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.ui.PhotosSlideTransition
import net.theluckycoder.familyphotos.ui.screen.FolderFilterTextField
import net.theluckycoder.familyphotos.ui.screen.FolderPreviewItem
import net.theluckycoder.familyphotos.ui.screen.LocalFolderScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object LocalFoldersTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            3.toUShort(),
            stringResource(R.string.section_storage),
            painterResource(R.drawable.ic_storage_outline)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.ic_storage_filled)

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    override fun Content() {
        Navigator(PhoneFoldersScreen) {
            PhotosSlideTransition(navigator = it)
        }
    }

    private object PhoneFoldersScreen : Screen {

        @Composable
        override fun Content() = Column {
            val mainViewModel: MainViewModel = viewModel()
            val navigator = LocalNavigator.currentOrThrow

            SideEffect {
                mainViewModel.showBottomAppBar.value = true
            }

            var folderNameFilter by remember { mutableStateOf("") }
            FolderFilterTextField(folderNameFilter, onFilterChange = { folderNameFilter = it })

            val albums by mainViewModel.localFolders.collectAsState(emptyList())
            val filteredAlbums = remember(albums, folderNameFilter) {
                val searchFilter = folderNameFilter.lowercase()
                albums.filter { it.name.lowercase().contains(searchFilter) }
            }

            val autoUpload by mainViewModel.autoBackupFlow.collectAsState(false)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Backup Camera Photos",
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
                Switch(autoUpload, onCheckedChange = { mainViewModel.setAutoBackup(it) })
            }

            val columnCount =
                if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 5

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier.fillMaxSize(),
            ) {

                items(filteredAlbums) { folder ->
                    key(folder.coverPhotoId) {
                        val photo = LocalPhoto(
                            id = folder.coverPhotoId,
                            name = "",
                            uri = folder.coverPhotoUri,
                            timeCreated = 0L,
                            folder = folder.name
                        )

                        FolderPreviewItem(
                            photo = photo,
                            name = folder.name,
                            photosCount = folder.count,
                            onClick = {
                                navigator.push(LocalFolderScreen(folder.name))
                            },
                        )
                    }
                }
            }
        }
    }
}
