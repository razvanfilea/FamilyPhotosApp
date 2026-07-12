package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSettingsDataStore
import java.lang.System.exit

@Composable
fun FolderNameDialog(
    actionLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, isPublic: Boolean) -> Unit,
    initialName: String = "",
    initialIsPublic: Boolean = false,
) {
    var name by remember { mutableStateOf(initialName) }
    var isPublic by remember { mutableStateOf(initialIsPublic) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(actionLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isPublic,
                        onClick = { isPublic = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) {
                        Text(stringResource(R.string.photo_type_personal))
                    }
                    SegmentedButton(
                        selected = isPublic,
                        onClick = { isPublic = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) {
                        Text(stringResource(R.string.photo_type_family))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), isPublic) },
                enabled = name.isNotBlank(),
            ) {
                Text(actionLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun UploadPhotosLayout(
    networkFolders: List<NetworkFolder>,
    actionName: String,
    photosToShowcase: List<Photo>,
    currentUserId: String? = null,
    doneAction: (choice: UploadChoice) -> Unit,
) {
    val backStack = LocalNavBackStack.current

    var choice by remember { mutableStateOf<UploadChoice?>(UploadChoice.NoFolder(isPublic = false)) }
    var selectedFolderName by remember { mutableStateOf<String?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    if (showCreateFolderDialog) {
        FolderNameDialog(
            actionLabel = stringResource(R.string.action_create_folder),
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, folderIsPublic ->
                choice = UploadChoice.NewFolder(name = name, isPublic = folderIsPublic)
                showCreateFolderDialog = false
            },
        )
    }

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
            ) {
                TextButton(onClick = { backStack.removeLastOrNull() }) {
                    Text(stringResource(R.string.action_cancel))
                }

                Button(
                    onClick = { choice?.let { doneAction(it) } },
                    enabled = choice != null,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_action_done),
                        contentDescription = actionName
                    )

                    Text(actionName)
                }
            }
        }
    ) { contentPadding ->
        Box(Modifier.padding(bottom = contentPadding.calculateBottomPadding())) {
            UploadDialogContent(
                photosToShowcase = photosToShowcase,
                selectedChoice = choice,
                onChoiceChange = { newChoice ->
                    choice = newChoice
                    if (newChoice is UploadChoice.Folder) {
                        selectedFolderName = networkFolders
                            .find { it.id == newChoice.folderId }?.name
                    }
                },
                currentUserId = currentUserId,
                foldersList = networkFolders,
                onCreateFolder = { showCreateFolderDialog = true },
                selectedFolderName = selectedFolderName,
            )
        }
    }
}


@Composable
private fun UploadDialogContent(
    photosToShowcase: List<Photo>,
    selectedChoice: UploadChoice?,
    onChoiceChange: (UploadChoice?) -> Unit,
    currentUserId: String?,
    foldersList: List<NetworkFolder>,
    onCreateFolder: () -> Unit,
    selectedFolderName: String?,
) {
    val settingsDataStore = LocalSettingsDataStore.current
    val selectedPhotoType by settingsDataStore.photoType.collectAsState()

    LaunchedEffect(selectedPhotoType) {
        when (selectedPhotoType) {
            PhotoType.Family -> onChoiceChange(UploadChoice.NoFolder(true))
            PhotoType.Personal -> onChoiceChange(UploadChoice.NoFolder(false))
            PhotoType.Shared -> onChoiceChange(null)
            else -> {}
        }
    }

    val hasFolderSelected =
        selectedChoice is UploadChoice.Folder || selectedChoice is UploadChoice.NewFolder

    FoldersGridList(
        folders = if (hasFolderSelected) emptyList() else foldersList,
        onFolderClick = { onChoiceChange(UploadChoice.Folder(it.id)) },
        currentUserId = currentUserId,
        extraHeader = {
            if (photosToShowcase.isNotEmpty()) {
                PhotoShowcaseRow(
                    photos = photosToShowcase,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            PhotoTypeChips(
                selectedPhotoType = selectedPhotoType,
                onChangePhotoType = settingsDataStore::setSelectedPhotoType,
                modifier = Modifier.padding(top = 8.dp),
            )

            DestinationSelector(
                selectedChoice = selectedChoice,
                onChoiceChange = onChoiceChange,
                selectedPhotoType = selectedPhotoType,
                foldersList = foldersList,
                selectedFolderName = selectedFolderName,
                onCreateFolder = onCreateFolder,
            )
        },
    )
}


@Composable
private fun DestinationItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: Painter,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SelectedFolderRow(
    folderName: String,
    typeLabel: String,
    onClear: () -> Unit,
    icon: Painter,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.onPrimaryContainer, shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = folderName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }

        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClear)
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CreateFolderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_action_add),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = stringResource(R.string.action_create_folder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PhotoShowcaseRow(
    photos: List<Photo>,
    modifier: Modifier = Modifier,
) = Row(
    modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    LazyRow(
        Modifier.weight(1f, fill = false),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(photos) { photo ->
            CoilPhoto(
                photo = photo,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .photoSharedBounds(photo.id),
                preview = true,
                contentScale = ContentScale.Crop,
            )
        }
    }

    Spacer(Modifier.width(12.dp))

    Text(
        text = pluralStringResource(R.plurals.items_photos, photos.size, photos.size),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColumnScope.DestinationSelector(
    selectedChoice: UploadChoice?,
    onChoiceChange: (UploadChoice?) -> Unit,
    selectedPhotoType: PhotoType,
    foldersList: List<NetworkFolder>,
    selectedFolderName: String?,
    onCreateFolder: () -> Unit,
) {
    val personalGallery = stringResource(R.string.action_into_personal_gallery)
    val familyGallery = stringResource(R.string.action_into_family_gallery)

    val showPersonal =
        selectedPhotoType == PhotoType.All || selectedPhotoType == PhotoType.Personal
    val showFamily =
        selectedPhotoType == PhotoType.All || selectedPhotoType == PhotoType.Family

    AnimatedVisibility(showPersonal) {
        DestinationItem(
            selected = selectedChoice is UploadChoice.NoFolder && !selectedChoice.isPublic,
            label = personalGallery,
            onClick = { onChoiceChange(UploadChoice.NoFolder(isPublic = false)) },
            icon = painterResource(R.drawable.ic_person_outline),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    AnimatedVisibility(showFamily) {
        DestinationItem(
            selected = selectedChoice is UploadChoice.NoFolder && selectedChoice.isPublic,
            label = familyGallery,
            onClick = { onChoiceChange(UploadChoice.NoFolder(isPublic = true)) },
            icon = painterResource(R.drawable.ic_family_outline),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    AnimatedContent(
        selectedChoice,
        transitionSpec = {
            (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
        },
        contentKey = { it?.let { it::class } },
        contentAlignment = Alignment.TopCenter,
    ) { selectedChoice ->
        when (selectedChoice) {
            is UploadChoice.NewFolder -> {
                val typeLabel = if (selectedChoice.isPublic) {
                    stringResource(R.string.photo_type_family)
                } else {
                    stringResource(R.string.photo_type_personal)
                }
                SelectedFolderRow(
                    folderName = selectedChoice.name,
                    typeLabel = typeLabel,
                    onClear = { onChoiceChange(UploadChoice.NoFolder(isPublic = false)) },
                    icon = painterResource(R.drawable.ic_new_folder),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            is UploadChoice.Folder -> {
                val folder = foldersList.find { it.id == selectedChoice.folderId }
                val typeLabel = if (folder?.userId == null) {
                    stringResource(R.string.photo_type_family)
                } else {
                    stringResource(R.string.photo_type_personal)
                }
                SelectedFolderRow(
                    folderName = selectedFolderName ?: "",
                    typeLabel = typeLabel,
                    onClear = { onChoiceChange(UploadChoice.NoFolder(isPublic = false)) },
                    icon = painterResource(R.drawable.ic_folder_outlined),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            else -> {
                if (selectedPhotoType != PhotoType.Shared) {
                    CreateFolderButton(
                        onClick = onCreateFolder,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

