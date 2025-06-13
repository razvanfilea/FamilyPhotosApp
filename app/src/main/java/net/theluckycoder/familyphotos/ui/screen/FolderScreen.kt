package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.RenameFolderNav
import net.theluckycoder.familyphotos.ui.composables.PhotoListWithViewer
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(source: FolderNav.Source) {
    val mainViewModel: MainViewModel = viewModel()
    val gridState = rememberLazyGridState()
    val backStack = LocalNavBackStack.current

    val photosPager = remember(source) {
        when (source) {
            FolderNav.Source.Favorites -> mainViewModel.favoritePhotosFlow
            is FolderNav.Source.Network -> mainViewModel.getNetworkFolderPhotosPaged(source.folder.name)
            is FolderNav.Source.Local -> mainViewModel.getLocalFolderPhotosPaged(source.name)
        }
    } as Flow<PagingData<Photo>>

    Scaffold { paddingValues ->
        val photos = photosPager.collectAsLazyPagingItems()
        PhotoListWithViewer(
            gridState = gridState,
            photos = photos,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding()),
            headerContent = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            backStack.removeLastOrNull()
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = null
                            )
                        }
                    },
                    title = {
                        Text(
                            when (source) {
                                FolderNav.  Source.Favorites -> stringResource(R.string.favorites)
                                is FolderNav.Source.Network -> source.folder.name
                                is FolderNav.Source.Local -> source.name
                            },
                        )
                    },
                    actions = {
                        if (source is FolderNav.Source.Network) {
                            IconButton(onClick = {
                                backStack.add(RenameFolderNav(source.folder))
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        }
                    }
                )
            },
            memoriesContent = {
                if (source is FolderNav.Source.Local) {
                    val backupEnabled by mainViewModel.isLocalFolderBackupUp(source.name)
                        .collectAsState(false)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                mainViewModel.backupLocalFolder(
                                    source.name,
                                    !backupEnabled
                                )
                            })
                            .padding(16.dp)
                    ) {
                        Text(
                            "Backup",
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        )

                        Switch(
                            checked = backupEnabled,
                            onCheckedChange = null
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            },
            mainViewModel = mainViewModel,
        )
    }
}