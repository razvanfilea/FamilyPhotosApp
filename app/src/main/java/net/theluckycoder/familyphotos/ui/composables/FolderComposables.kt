package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalSettingsDataStore
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.data.model.db.PhotoFolder
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel
import net.theluckycoder.familyphotos.utils.normalize

@Composable
fun <T : PhotoFolder> FoldersGridList(
    folders: List<T>,
    onFolderClick: (T) -> Unit,
    folderDetailsText: @Composable (T) -> String?,
    folderNameFilter: String,
    onSearch: (String) -> Unit = {},
    foldersViewModel: FoldersViewModel = viewModel(),
    isBackupEnabled: (T) -> Boolean = { false },
    extraHeader: @Composable ColumnScope.() -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val settingsDataStore = LocalSettingsDataStore.current
    val sortAscending by settingsDataStore.showFoldersAscending.collectAsState()
    val showAsGrid by settingsDataStore.showFoldersAsGrid.collectAsState()

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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = TopAppBarDefaults.windowInsets.asPaddingValues()
    ) {
        item(span = { GridItemSpan(columnCount) }, key = "header") {
            Column {
                FolderFilterTextField(folderNameFilter, onSearch = onSearch)

                extraHeader()

                SortButton(
                    sortAscending = sortAscending,
                    onChangeSort = {
                        settingsDataStore.setShowFoldersAscending(!sortAscending)
                    },
                    showAsGrid = showAsGrid,
                    onShowAsGrid = settingsDataStore::setShowFoldersAsGrid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }

        items(filteredFolders, key = { it.coverPhotoId }) { folder ->
            // Make a fake photo to load the preview
            val photo = folder.getCoverPhoto()
            val modifier = Modifier
                .padding(horizontal = 16.dp)
                .animateItem()
            val detailsText = folderDetailsText(folder)
            val backupEnabled = isBackupEnabled(folder)

            if (showAsGrid) {
                GridFolderPreviewItem(
                    modifier = modifier,
                    photo = photo,
                    detailsText = detailsText,
                    onClick = { onFolderClick(folder) },
                    showBackupIndicator = backupEnabled,
                )
            } else {
                ListFolderPreviewItem(
                    modifier = modifier,
                    photo = photo,
                    detailsText = detailsText,
                    onClick = { onFolderClick(folder) },
                    showBackupIndicator = backupEnabled,
                )
            }

        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderFilterTextField(query: String, onSearch: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    val interactionSource = remember { MutableInteractionSource() }
    val colors = SearchBarDefaults.inputFieldColors()
    val textColor = colors.focusedTextColor

    BasicTextField(
        value = query,
        onValueChange = onSearch,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics {
                    onClick {
                        focusRequester.requestFocus()
                        true
                    }
                },
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(TextStyle(color = textColor)),
        cursorBrush = SolidColor(colors.cursorColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        interactionSource = interactionSource,
        decorationBox =
            @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = query,
                    innerTextField = innerTextField,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = { Text(stringResource(R.string.folder_name)) },
                    leadingIcon = {
                        Box(Modifier.offset(x = 4.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                tint = textColor,
                                contentDescription = null
                            )
                        }
                    },
                    trailingIcon = {
                        Box(Modifier.offset(x = (-4).dp)) {
                            AnimatedVisibility(
                                query.isNotEmpty(),
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                IconButton(onClick = { onSearch("") }) {
                                    Icon(
                                        painterResource(R.drawable.ic_close),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    },
                    shape = SearchBarDefaults.inputFieldShape,
                    colors = colors,
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                    enabled = true,
                    container = {},
                )
            }
    )

    HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 16.dp))
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
            painterResource(if (showAsGrid) R.drawable.ic_list_view else R.drawable.ic_grid_view),
            contentDescription = null
        )
    }
}

@Composable
private fun GridFolderPreviewItem(
    modifier: Modifier = Modifier,
    photo: Photo,
    detailsText: String?,
    onClick: () -> Unit,
    showBackupIndicator: Boolean = false,
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

        if (showBackupIndicator) {
            Icon(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .padding(8.dp),
                painter = painterResource(R.drawable.ic_cloud_done_filled),
                tint = Color.White,
                contentDescription = null
            )
        }
    }

    Text(
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        text = photo.folder ?: photo.name,
        fontWeight = FontWeight.SemiBold
    )

    if (detailsText != null) {
        Text(
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            text = detailsText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
private fun ListFolderPreviewItem(
    modifier: Modifier = Modifier,
    photo: Photo,
    detailsText: String?,
    onClick: () -> Unit,
    showBackupIndicator: Boolean = false,
) = Row(
    modifier = modifier.clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box {
        CoilPhoto(
            photo = photo,
            modifier = Modifier
                .photoSharedBounds(photo.id)
                .size(82.dp)
                .clip(RoundedCornerShape(18.dp)),
            preview = true,
            contentScale = ContentScale.Crop,
        )

        if (showBackupIndicator) {
            Icon(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .padding(8.dp),
                painter = painterResource(R.drawable.ic_cloud_done_filled),
                tint = Color.White,
                contentDescription = null
            )
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .padding(start = 8.dp),
    ) {
        Text(
            modifier = Modifier.padding(top = 4.dp),
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            text = photo.folder ?: photo.name,
            fontWeight = FontWeight.SemiBold
        )

        if (detailsText != null) {
            Text(
                modifier = Modifier.padding(bottom = 8.dp),
                text = detailsText,
                fontSize = 14.sp,
            )
        }
    }
}
