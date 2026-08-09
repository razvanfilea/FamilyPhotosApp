package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.FolderSortOrder
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.PhotoFolder
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.getFolderType
import net.theluckycoder.familyphotos.ui.LocalSettingsDataStore
import net.theluckycoder.familyphotos.utils.normalize

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : PhotoFolder> FoldersGridList(
    folders: List<T>,
    onFolderClick: (T) -> Unit,
    sortOrder: FolderSortOrder,
    onSortOrderChange: (FolderSortOrder) -> Unit,
    currentUserId: String? = null,
    isBackupEnabled: (T) -> Boolean = { false },
    contentPadding: PaddingValues = TopAppBarDefaults.windowInsets.asPaddingValues(),
    extraHeader: @Composable ColumnScope.() -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val settingsDataStore = LocalSettingsDataStore.current
    val showAsGrid by settingsDataStore.showFoldersAsGrid.collectAsState()
    var folderNameFilter by remember { mutableStateOf("") }

    val filteredFolders = remember(folders, folderNameFilter) {
        val filterName = folderNameFilter.normalize()
        folders.filter {
            it.name.normalize().contains(filterName, ignoreCase = true)
        }
    }

    val orientation = LocalConfiguration.current.orientation
    val columnCount = when (showAsGrid) {
        true -> if (orientation == Configuration.ORIENTATION_PORTRAIT) 3 else 5
        false -> if (orientation == Configuration.ORIENTATION_PORTRAIT) 1 else 2
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = contentPadding,
    ) {
        item(span = { GridItemSpan(columnCount) }, key = "header") {
            Column {
                FolderFilterTextField(folderNameFilter, onSearch = { folderNameFilter = it })

                extraHeader()

                SortButton(
                    sortOrder = sortOrder,
                    onChangeSortOrder = onSortOrderChange,
                    showAsGrid = showAsGrid,
                    onShowAsGrid = settingsDataStore::setShowFoldersAsGrid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }

        items(filteredFolders, key = { it.coverPhotoId }) { folder ->
            val photo = folder.getCoverPhoto()
            val modifier = Modifier
                .padding(horizontal = if (!showAsGrid) 16.dp else 8.dp)
                .animateItem()
                .testTag("folder_item")

            val photosCount =
                pluralStringResource(R.plurals.items_photos, folder.count, folder.count)
            val folderType = folder.getFolderType(currentUserId)

            val detailsText = if (!showAsGrid && folderType != PhotoType.All) {
                val ownerLabel = when (folderType) {
                    PhotoType.Family -> stringResource(R.string.photo_type_family)
                    PhotoType.Personal -> stringResource(R.string.photo_type_personal)
                    PhotoType.Shared -> stringResource(R.string.photo_type_shared)
                }
                "$photosCount • $ownerLabel"
            } else {
                photosCount
            }
            val backupEnabled = isBackupEnabled(folder)

            if (showAsGrid) {
                GridFolderPreviewItem(
                    modifier = modifier,
                    photo = photo,
                    folderName = folder.name,
                    detailsText = detailsText,
                    onClick = { onFolderClick(folder) },
                    showBackupIndicator = backupEnabled,
                    folderType = folderType,
                )
            } else {
                ListFolderPreviewItem(
                    modifier = modifier,
                    photo = photo,
                    folderName = folder.name,
                    detailsText = detailsText,
                    onClick = { onFolderClick(folder) },
                    showBackupIndicator = backupEnabled,
                    folderType = folderType,
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderFilterTextField(query: String, onSearch: (String) -> Unit) {
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
                    placeholder = { Text(stringResource(R.string.action_search_folders)) },
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
    sortOrder: FolderSortOrder,
    onChangeSortOrder: (FolderSortOrder) -> Unit,
    showAsGrid: Boolean,
    onShowAsGrid: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) = Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabelRes = when (sortOrder) {
        FolderSortOrder.DATE_DESC -> R.string.sort_date_desc
        FolderSortOrder.NAME_ASC -> R.string.sort_name_asc
        FolderSortOrder.NAME_DESC -> R.string.sort_name_desc
        FolderSortOrder.COUNT_DESC -> R.string.sort_count_desc
    }

    Box {
        TextButton(
            onClick = { expanded = true }
        ) {
            Icon(
                painterResource(R.drawable.ic_sort_ascending),
                contentDescription = null
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(currentLabelRes),
                fontSize = 14.sp
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FolderSortOrder.entries.forEach { option ->
                val textRes = when (option) {
                    FolderSortOrder.NAME_ASC -> R.string.sort_name_asc
                    FolderSortOrder.NAME_DESC -> R.string.sort_name_desc
                    FolderSortOrder.DATE_DESC -> R.string.sort_date_desc
                    FolderSortOrder.COUNT_DESC -> R.string.sort_count_desc
                }
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(option == sortOrder, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(textRes),
                                fontWeight = if (option == sortOrder) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onChangeSortOrder(option)
                    }
                )
            }
        }
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
    folderName: String,
    detailsText: String?,
    onClick: () -> Unit,
    showBackupIndicator: Boolean = false,
    folderType: PhotoType? = null,
) = Column(modifier = modifier) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        CoilPhoto(
            photo = photo,
            modifier = Modifier.fillMaxSize(),
            preview = true,
            contentScale = ContentScale.Crop,
        )

        FolderStatusIndicators(
            folderType = folderType,
            showBackupIndicator = showBackupIndicator
        )
    }

    Text(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth(),
        text = folderName,
        textAlign = TextAlign.Center,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium
    )

    if (detailsText != null) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = detailsText,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ListFolderPreviewItem(
    modifier: Modifier = Modifier,
    photo: Photo,
    folderName: String,
    detailsText: String?,
    onClick: () -> Unit,
    showBackupIndicator: Boolean = false,
    folderType: PhotoType? = null,
) = Row(
    modifier = modifier.clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        CoilPhoto(
            photo = photo,
            modifier = Modifier.fillMaxSize(),
            preview = true,
            contentScale = ContentScale.Crop,
        )

        FolderStatusIndicators(
            folderType = folderType,
            showBackupIndicator = showBackupIndicator
        )
    }

    Spacer(Modifier.width(4.dp))

    Column(
        Modifier
            .fillMaxHeight()
            .padding(start = 8.dp),
    ) {
        Text(
            modifier = Modifier.padding(top = 4.dp),
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            text = folderName,
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

@Composable
private fun BoxScope.FolderStatusIndicators(
    folderType: PhotoType?,
    showBackupIndicator: Boolean,
    modifier: Modifier = Modifier,
) {
    if (folderType == null && !showBackupIndicator) return

    val folderIconRes = when (folderType) {
        null, PhotoType.All -> null
        PhotoType.Personal -> R.drawable.ic_person_filled
        PhotoType.Family -> R.drawable.ic_family_filled
        PhotoType.Shared -> R.drawable.ic_action_share
    }

    if (folderIconRes == null && !showBackupIndicator) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.60f)
            .align(Alignment.BottomCenter)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.40f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
    )

    Row(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (folderIconRes != null) {
            Icon(
                painter = painterResource(folderIconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        if (showBackupIndicator) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_done_filled),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
