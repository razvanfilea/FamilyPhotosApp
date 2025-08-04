package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.data.model.db.PhotoFolder
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel
import net.theluckycoder.familyphotos.utils.normalize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : PhotoFolder> FoldersTab(
    folders: List<T>,
    onFolderClick: (T) -> Unit,
    folderDetailsText: (T) -> String,
    foldersViewModel: FoldersViewModel = viewModel(),
    extraHeader: @Composable () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val sortAscending by foldersViewModel.showFoldersAscending.collectAsState()
    val showAsGrid by foldersViewModel.showFoldersAsGrid.collectAsState()

    var folderNameFilter by remember { mutableStateOf("") }

    val filteredFolders = remember(folders, folderNameFilter) {
        val filterName = folderNameFilter.normalize()
        folders.filter {
            it.name.normalize().contains(filterName, ignoreCase = true)
        }
    }

    val columnCount = when {
        !showAsGrid -> 1
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT -> 2
        else -> 5
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = TopAppBarDefaults.windowInsets.asPaddingValues()
    ) {
        item(span = { GridItemSpan(columnCount) }, key = "header") {
            Column(
                Modifier.fillMaxWidth()
            ) {
                FolderFilterTextField(folderNameFilter, onFilterChange = { folderNameFilter = it })

                extraHeader()

                SortButton(
                    sortAscending = sortAscending,
                    onChangeSort = {
                        foldersViewModel.setShowFoldersAscending(!sortAscending)
                    },
                    showAsGrid = showAsGrid,
                    onShowAsGrid = {
                        foldersViewModel.setShowAsGrid(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(filteredFolders, key = { it.coverPhotoId }) { folder ->
            // Make a fake photo to load the preview
            val photo = folder.getCoverPhoto()
            val modifier = Modifier.animateItem()
            val detailsText = remember(folder) { folderDetailsText(folder) }

            if (showAsGrid) {
                GridFolderPreviewItem(
                    modifier = modifier,
                    photo = photo,
                    detailsText = detailsText,
                    onClick = { onFolderClick(folder) },
                ) {
                    /*if (folder.isPublic) {
                        Icon(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(42.dp)
                                .padding(8.dp),
                            painter = painterResource(R.drawable.ic_family_filled),
                            tint = Color.White,
                            contentDescription = null
                        )
                    }*/
                }
            } else {
                ListFolderPreviewItem(
                    modifier = modifier,
                    photo = photo,
                    detailsText = detailsText,
                    onClick = { onFolderClick(folder) },
                )
            }

        }
    }

}

@Composable
fun FolderFilterTextField(folderNameFilter: String, onFilterChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        value = folderNameFilter,
        onValueChange = onFilterChange,
        label = { Text(stringResource(R.string.folder_name)) },
        singleLine = true,
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null)
        },
        trailingIcon = {
            AnimatedVisibility(
                folderNameFilter.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = { onFilterChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                }
            }
        }
    )
}

@Composable
private fun SortButton(
    sortAscending: Boolean,
    onChangeSort: () -> Unit,
    showAsGrid: Boolean,
    onShowAsGrid: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) = Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
    TextButton(
        onClick = onChangeSort
    ) {
        Icon(
            painterResource(R.drawable.ic_sort_ascending),
            contentDescription = null
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(if (sortAscending) R.string.ascending else R.string.descending),
            fontSize = 14.sp
        )
    }

    IconButton(onClick = { onShowAsGrid(!showAsGrid) }) {
        Icon(
            painterResource(if (showAsGrid) R.drawable.ic_grid_view else R.drawable.ic_list_view),
            contentDescription = null
        )
    }
}

@Composable
private fun GridFolderPreviewItem(
    modifier: Modifier = Modifier,
    photo: Photo,
    detailsText: String,
    onClick: () -> Unit,
    content: @Composable (BoxScope.() -> Unit)? = null
) = Column(modifier = modifier) {
    Box(
        Modifier
            .photoSharedBounds(photo.id)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        CoilPhoto(
            photo = photo,
            modifier = Modifier.fillMaxSize(),
            preview = true,
            contentScale = ContentScale.Crop,
        )

        if (content != null)
            content()
    }

    Text(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        text = photo.folder ?: photo.name,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        text = detailsText,
        fontSize = 14.sp,
        fontWeight = FontWeight.Light
    )
}

@Composable
private fun ListFolderPreviewItem(
    modifier: Modifier = Modifier,
    photo: Photo,
    detailsText: String,
    onClick: () -> Unit,
) = Row(
    modifier = modifier.clickable(onClick = onClick)
) {
    CoilPhoto(
        photo = photo,
        modifier = Modifier
            .photoSharedBounds(photo.id)
            .size(82.dp)
            .clip(RoundedCornerShape(18.dp)),
        preview = true,
        contentScale = ContentScale.Crop,
    )

    Column(
        Modifier
            .fillMaxHeight()
            .padding(start = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            modifier = Modifier.padding(top = 4.dp),
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            text = photo.folder ?: photo.name,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = detailsText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light
        )
    }
}
