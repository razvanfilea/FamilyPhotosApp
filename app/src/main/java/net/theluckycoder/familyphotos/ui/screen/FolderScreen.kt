package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.Photo
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.PhotoViewerFlowNav
import net.theluckycoder.familyphotos.ui.RenameFolderNav
import net.theluckycoder.familyphotos.ui.composables.PhotosList
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(source: FolderNav.Source, lazyPagingItems: LazyPagingItems<out Photo>) {
    val foldersViewModel: FoldersViewModel = viewModel()
    val gridState by foldersViewModel.photoListState.collectAsState()
    val backStack = LocalNavBackStack.current

    Scaffold { paddingValues ->
        PhotosList(
            gridState = gridState,
            photos = lazyPagingItems,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding()),
            openPhoto = {
                val viewerSource = when (source) {
                    FolderNav.Source.Favorites -> PhotoViewerFlowNav.Source.Favorites
                    is FolderNav.Source.Local -> PhotoViewerFlowNav.Source.Local
                    is FolderNav.Source.Network -> PhotoViewerFlowNav.Source.Network
                }
                backStack.add(PhotoViewerFlowNav(it, viewerSource))
            },
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
                                FolderNav.Source.Favorites -> stringResource(R.string.title_favorites)
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
                    val backupEnabled by foldersViewModel.isLocalFolderBackupUp(source.name)
                        .collectAsState(false)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                foldersViewModel.backupLocalFolder(
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
        )
    }
}