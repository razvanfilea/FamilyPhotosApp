package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.SharedFolderAccess
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
import net.theluckycoder.familyphotos.core.data.model.db.isPublic
import net.theluckycoder.familyphotos.core.data.model.getFolderType
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.PhotoViewerFlowNav
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateSetOf
import net.theluckycoder.familyphotos.ui.composables.FolderNameDialog
import net.theluckycoder.familyphotos.ui.composables.PhotoUtilitiesActions
import net.theluckycoder.familyphotos.ui.composables.PhotosList
import net.theluckycoder.familyphotos.ui.composables.PhotosSelectionBar
import net.theluckycoder.familyphotos.ui.dialog.FolderSharingBottomSheet
import net.theluckycoder.familyphotos.ui.viewmodel.FolderScreenViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersTabViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun FolderScreen(
    source: FolderNav.Source,
    foldersTabViewModel: FoldersTabViewModel,
    mainViewModel: MainViewModel,
    folderScreenViewModel: FolderScreenViewModel = viewModel(),
) {
    var showLocalPhotoPicker by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (source is FolderNav.Source.Network) {
                UploadButton(
                    onClick = { showLocalPhotoPicker = true }
                )
            }
        }
    ) { contentPadding ->
        val lazyPagingItems = folderScreenViewModel.photosPager.collectAsLazyPagingItems()

        val gridState by folderScreenViewModel.photoListState.collectAsState()
        val backStack = LocalNavBackStack.current
        val timelineLayout by folderScreenViewModel.timelineLayout.collectAsState()

        LaunchedEffect(source) {
            selectedPhotoIds.clear()
            val photoCount = when (source) {
                is FolderNav.Source.Network -> source.photoCount
                is FolderNav.Source.Local -> source.photoCount
                is FolderNav.Source.Favorites -> 0
            }
            folderScreenViewModel.setSource(source, photoCount)
        }

        LaunchedEffect(folderScreenViewModel) {
            foldersTabViewModel.registerFolderViewModel(folderScreenViewModel)
        }

        var showSharingBottomSheet by remember { mutableStateOf(false) }
        var showRenameFolderDialog by remember { mutableStateOf(false) }

        val networkFolder by folderScreenViewModel.networkFolder.collectAsState(null)
        val currentUser by folderScreenViewModel.currentUser.collectAsState(UserDto("", ""))
        val folderShares by folderScreenViewModel.folderShares.collectAsState(SharedFolderAccess.EMPTY)

        Box(modifier = Modifier.fillMaxSize()) {
            PhotosList(
                gridState = gridState,
                photos = lazyPagingItems,
                timelineLayout = timelineLayout,
                selectedPhotoIds = selectedPhotoIds,
                modifier = Modifier.fillMaxSize(),
                openPhoto = { index, _ ->
                    val viewerSource = when (source) {
                        FolderNav.Source.Favorites -> PhotoViewerFlowNav.Source.Favorites
                        is FolderNav.Source.Local -> PhotoViewerFlowNav.Source.Local
                        is FolderNav.Source.Network -> PhotoViewerFlowNav.Source.Network
                    }
                    backStack.add(PhotoViewerFlowNav(index, viewerSource))
                },
                headerContent = {
                    FolderTopAppBar(
                        source = source,
                        networkFolder = networkFolder,
                        currentUserId = currentUser?.userId,
                        folderShares = folderShares,
                        photoCount = timelineLayout.totalPhotoCount,
                        onOpenSharing = { showSharingBottomSheet = true },
                        onOpenRename = { showRenameFolderDialog = true }
                    )

                    if (source is FolderNav.Source.Local) {
                        val backupEnabled by folderScreenViewModel.isLocalFolderBackupUp(source.name)
                            .collectAsState(false)

                        LocalFolderBackupCard(
                            backupEnabled = backupEnabled,
                            onToggleBackup = { enabled ->
                                folderScreenViewModel.backupLocalFolder(source.name, enabled)
                            }
                        )
                    }
                },
            )

            PhotosSelectionBar(
                selectedPhotoIds = selectedPhotoIds,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .padding(top = 8.dp)
            ) {
                PhotoUtilitiesActions(
                    isLocalPhoto = source is FolderNav.Source.Local,
                    selectedItems = selectedPhotoIds,
                    mainViewModel = mainViewModel
                )
            }
        }

        val currentFolder = networkFolder
        if (currentFolder != null) {
            if (showLocalPhotoPicker && source is FolderNav.Source.Network) {
                LocalPhotoPickerSheet(
                    targetFolderId = source.folderId,
                    targetFolderName = source.folderName,
                    onDismiss = { showLocalPhotoPicker = false }
                )
            }
            if (showRenameFolderDialog) {
                FolderNameDialog(
                    actionLabel = stringResource(R.string.action_rename_folder),
                    initialName = currentFolder.name,
                    initialIsPublic = currentFolder.isPublic,
                    onDismiss = { showRenameFolderDialog = false },
                    onConfirm = { newName, isPublic ->
                        folderScreenViewModel.renameFolder(currentFolder.id, newName, isPublic)
                        showRenameFolderDialog = false
                    },
                )
            }

            if (showSharingBottomSheet) {
                FolderSharingBottomSheet(
                    folder = currentFolder,
                    folderShares = folderShares,
                    currentUser = currentUser,
                    onAddMember = { user ->
                        folderScreenViewModel.addMemberToFolder(currentFolder.id, user.userId)
                    },
                    onUpdatePermissions = folderScreenViewModel::updateMemberFolderPermissions,
                    onRemoveMember = folderScreenViewModel::removeMemberFromFolder,
                    onDismiss = { showSharingBottomSheet = false },
                )
            }
        }
    }
}

@Composable
private fun UploadButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick
    ) {
        Icon(
            painterResource(R.drawable.ic_cloud_upload_outline),
            stringResource(R.string.action_upload)
        )
    }
}

@Composable
private fun LocalFolderBackupCard(
    backupEnabled: Boolean,
    onToggleBackup: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = { onToggleBackup(!backupEnabled) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    if (backupEnabled) R.drawable.ic_cloud_done_filled
                    else R.drawable.ic_cloud_off_outline
                ),
                contentDescription = null,
                tint = if (backupEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            Switch(
                checked = backupEnabled,
                onCheckedChange = null
            )
        }
    }
}

@Composable
private fun FolderBadge(
    label: String,
    painter: Painter,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
            horizontal = 10.dp,
            vertical = 3.dp
        )
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderTopAppBar(
    source: FolderNav.Source,
    networkFolder: NetworkFolderEntity?,
    currentUserId: String?,
    folderShares: SharedFolderAccess,
    photoCount: Int,
    onOpenSharing: () -> Unit,
    onOpenRename: () -> Unit,
) {
    val titleText = when (source) {
        FolderNav.Source.Favorites -> stringResource(R.string.title_favorites)
        is FolderNav.Source.Network -> networkFolder?.name ?: source.folderName
        is FolderNav.Source.Local -> source.name
    }

    val folderType = remember(networkFolder, currentUserId) {
        if (networkFolder != null) {
            val domainFolder = NetworkFolder(
                id = networkFolder.id,
                name = networkFolder.name,
                coverPhotoId = 0L,
                userId = networkFolder.ownerId,
                count = 0
            )
            domainFolder.getFolderType(currentUserId)
        } else null
    }

    val sharedWithCount = folderShares.sharedWith.size
    val favoritesLabel = stringResource(R.string.title_favorites)
    val localDeviceLabel = stringResource(R.string.folder_badge_local_device)
    val publicLabel = stringResource(R.string.photo_type_family)
    val sharedCountLabel = stringResource(R.string.folder_badge_shared_count, sharedWithCount)
    val personalLabel = stringResource(R.string.photo_type_personal)
    val sharedLabel = stringResource(R.string.photo_type_shared)

    val badgeData: Pair<String, Int>? = remember(source, folderType, sharedWithCount) {
        when {
            source is FolderNav.Source.Favorites -> Pair(favoritesLabel, R.drawable.ic_star_filled)
            source is FolderNav.Source.Local -> Pair(
                localDeviceLabel,
                R.drawable.tab_device_outline
            )

            folderType == PhotoType.Family -> Pair(publicLabel, R.drawable.ic_family_filled)
            folderType == PhotoType.Personal && sharedWithCount > 0 -> Pair(
                sharedCountLabel,
                R.drawable.ic_action_share
            )

            folderType == PhotoType.Personal -> Pair(personalLabel, R.drawable.ic_person_filled)
            folderType == PhotoType.Shared -> Pair(sharedLabel, R.drawable.ic_action_share)
            else -> null
        }
    }

    val backStack = LocalNavBackStack.current
    MediumTopAppBar(
        navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
            }
        },
        title = {
            Column {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (photoCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.items_photos,
                                    photoCount,
                                    photoCount
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 3.dp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    badgeData?.let { (label, iconRes) ->
                        FolderBadge(label = label, painterResource(iconRes))
                    }
                }
            }
        },
        actions = {
            if (source is FolderNav.Source.Network && networkFolder != null) {
                IconButton(onClick = onOpenSharing) {
                    Icon(
                        painterResource(R.drawable.ic_action_share),
                        contentDescription = null
                    )
                }

                IconButton(onClick = onOpenRename) {
                    Icon(
                        painterResource(R.drawable.ic_action_edit),
                        contentDescription = null
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}