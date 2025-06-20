package net.theluckycoder.familyphotos.ui.screen.tabs

import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.isPublic
import net.theluckycoder.familyphotos.ui.DuplicatesNav
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.FolderFilterTextField
import net.theluckycoder.familyphotos.ui.composables.FolderPreviewItem
import net.theluckycoder.familyphotos.ui.composables.PhotoTypeSegmentedButtons
import net.theluckycoder.familyphotos.ui.composables.SortButton
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.utils.normalize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkFoldersTab() = Column(
    modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)
) {
    val mainViewModel: MainViewModel = viewModel()
    val foldersViewModel: FoldersViewModel = viewModel()
    val backStack = LocalNavBackStack.current

    val gridState = rememberLazyGridState()
    val folders by foldersViewModel.networkFolders.collectAsState()
    val sortAscending by foldersViewModel.showFoldersAscending.collectAsState()
    val selectedPhotoType by foldersViewModel.selectedPhotoType.collectAsState()
    var folderNameFilter by remember { mutableStateOf("") }
    FolderFilterTextField(folderNameFilter, onFilterChange = { folderNameFilter = it })

    val filteredFolders = remember(folders, folderNameFilter, selectedPhotoType) {
        val filterName = folderNameFilter.normalize()
        folders.filter {
            when (selectedPhotoType) {
                PhotoType.All -> true
                PhotoType.Personal -> !it.isPublic()
                PhotoType.Family -> it.isPublic()
            } && it.name.normalize().contains(filterName, ignoreCase = true)
        }
    }

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 5

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(columnCount) }, key = "header") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                PhotoTypeSegmentedButtons(
                    selectedPhotoType = selectedPhotoType,
                    onChangePhotoType = { type ->
                        foldersViewModel.setSelectedPhotoType(type)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            backStack.add(FolderNav(FolderNav.Source.Favorites))
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_star_outline),
                            contentDescription = null
                        )

                        Spacer(Modifier.width(12.dp))

                        Text(stringResource(R.string.title_favorites))
                    }

                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            backStack.add(DuplicatesNav)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_duplicates_outlined),
                            contentDescription = null
                        )

                        Spacer(Modifier.width(12.dp))

                        Text(stringResource(R.string.title_duplicates))
                    }
                }

                SortButton(
                    sortAscending = sortAscending,
                    onClick = {
                        foldersViewModel.setShowFoldersAscending(!sortAscending)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
                    backStack.add(FolderNav(FolderNav.Source.Network(folder)))
                },
            ) {
                if (folder.isPublic()) {
                    Icon(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(42.dp)
                            .padding(8.dp),
                        painter = painterResource(R.drawable.ic_family_filled),
                        tint = Color.White,
                        contentDescription = null
                    )
                }
            }

        }
    }

}
