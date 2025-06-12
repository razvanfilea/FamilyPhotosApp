package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcelable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.composables.PhotoListWithViewer
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Parcelize
data class FolderScreen(
    private val source: Source
) : Screen, Parcelable {

    override val key: ScreenKey
        get() = "FolderScreen($source)"

    private val gridState = LazyGridState()

    @Parcelize
    sealed class Source : Parcelable {
        data object FavoritesSource : Source()
        data class NetworkFolderSource(val folder: NetworkFolder) : Source()
        data class LocalFolderSource(val name: String) : Source()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow

        val photosPager = remember {
            when (source) {
                Source.FavoritesSource -> mainViewModel.favoritePhotosFlow
                is Source.NetworkFolderSource -> mainViewModel.getNetworkFolderPhotosPaged(source.folder.name)
                is Source.LocalFolderSource -> mainViewModel.getLocalFolderPhotosPaged(source.name)
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
                                navigator.pop()
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
                                    Source.FavoritesSource -> stringResource(R.string.favorites)
                                    is Source.NetworkFolderSource -> source.folder.name
                                    is Source.LocalFolderSource -> source.name
                                },
                            )
                        },
                        actions = {
                            if (source is Source.NetworkFolderSource) {
                                IconButton(onClick = {
                                    navigator.push(RenameFolderScreen(source.folder))
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            }
                        }
                    )
                },
                memoriesContent = {
                    if (source is Source.LocalFolderSource) {
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
}