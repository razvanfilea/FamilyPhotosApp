package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.flow.emptyFlow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.SharedFolderAccess
import net.theluckycoder.familyphotos.core.data.model.TimelineLayout
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
import net.theluckycoder.familyphotos.core.data.model.db.isPublic
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.PhotoViewerFlowNav
import net.theluckycoder.familyphotos.ui.RenameFolderNav
import net.theluckycoder.familyphotos.ui.composables.NavBackTopAppBar
import net.theluckycoder.familyphotos.ui.composables.PhotosList
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel

@Composable
fun FolderScreen(source: FolderNav.Source, lazyPagingItems: LazyPagingItems<out Photo>) {
    val foldersViewModel: FoldersViewModel = viewModel()
    val gridState by foldersViewModel.photoListState.collectAsState()
    val backStack = LocalNavBackStack.current
    val timelineLayout by when (source) {
        FolderNav.Source.Favorites -> remember { mutableStateOf(TimelineLayout.EMPTY) }
        is FolderNav.Source.Local -> foldersViewModel.localFolderTimelineLayout.collectAsState()
        is FolderNav.Source.Network -> foldersViewModel.networkFolderTimelineLayout.collectAsState()
    }

    var showSharingBottomSheet by remember { mutableStateOf(false) }
    val networkFolderState =
        remember { if (source is FolderNav.Source.Network) foldersViewModel.networkFolder else emptyFlow() }.collectAsState(
            null
        )
    val currentUser = foldersViewModel.currentUser.collectAsState(UserDto("", ""))

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
    ) { paddingValues ->
        PhotosList(
            gridState = gridState,
            photos = lazyPagingItems,
            timelineLayout = timelineLayout,
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
                NavBackTopAppBar(
                    navIconOnClick = backStack::removeLastOrNull,
                    title = when (source) {
                        FolderNav.Source.Favorites -> stringResource(R.string.title_favorites)
                        is FolderNav.Source.Network -> source.folderName
                        is FolderNav.Source.Local -> source.name
                    },
                    subtitle = timelineLayout.totalGridItemCount.takeIf { it != 0 }?.toString(),
                    actions = {
                        val networkFolder = networkFolderState.value
                        if (source is FolderNav.Source.Network && networkFolder != null) {
                            IconButton(onClick = {
                                showSharingBottomSheet = true
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_action_share),
                                    contentDescription = null
                                )
                            }

                            IconButton(onClick = {
                                backStack.add(RenameFolderNav(networkFolder.id, networkFolder.name))
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_action_edit),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                )

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
                            stringResource(R.string.backup_title),
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

    val networkFolder = networkFolderState.value
    if (networkFolder != null) {
        if (showSharingBottomSheet) {
            val folderShares =
                remember(networkFolder.id) { foldersViewModel.getFolderShares(networkFolder.id) }.collectAsState(
                    SharedFolderAccess.EMPTY
                )

            SharingBottomSheet(
                folder = networkFolder,
                folderShares = folderShares.value,
                currentUser = currentUser.value,
                onAddMember = { user ->
                    foldersViewModel.addMemberToFolder(networkFolder.id, user.userId)
                },
                onUpdatePermissions = foldersViewModel::updateMemberFolderPermissions,
                onRemoveMember = foldersViewModel::removeMemberFromFolder,
                onDismiss = { showSharingBottomSheet = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharingBottomSheet(
    folder: NetworkFolderEntity,
    folderShares: SharedFolderAccess,
    currentUser: UserDto,
    onAddMember: (UserDto) -> Unit,
    onUpdatePermissions: (shareId: Long, canUpload: Boolean, canDelete: Boolean) -> Unit,
    onRemoveMember: (shareId: Long) -> Unit,
    onDismiss: () -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val canModifyMembers = !folder.isPublic && folder.ownerId == currentUser.userId
    val canAddMembers = folderShares.availableMembers.isNotEmpty() && canModifyMembers
    val viewPermissionString = stringResource(R.string.sharing_permission_view)
    val uploadPermissionString = stringResource(R.string.sharing_permission_upload)
    val uploadAndDeletePermissionString = stringResource(R.string.sharing_permission_upload_delete)

    var addPeopleExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues()
                .calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.sharing_manage),
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 16.dp,
                    top = 8.dp
                ),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        }

        item {
            Text(
                text = stringResource(R.string.sharing_members),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 16.dp,
                    top = 8.dp
                )
            )
        }

        item {
            val ownerName = folderShares.availableMembers.find { it.userId == folder.ownerId }?.displayName ?: currentUser.displayName
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(if (folder.isPublic) stringResource(R.string.sharing_owner_everyone) else ownerName) },
                supportingContent = { Text(stringResource(if (folder.isPublic) R.string.sharing_shared_access else R.string.sharing_owner)) },
                leadingContent = {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                (if (folder.isPublic) "P" else ownerName).take(1),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            )

            AnimatedVisibility(canAddMembers) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(stringResource(R.string.sharing_add_members))
                    },
                    modifier = Modifier.clickable { addPeopleExpanded = !addPeopleExpanded },
                    leadingContent = {
                        Icon(
                            painterResource(R.drawable.ic_action_add),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                )
            }
        }

        if (canAddMembers && addPeopleExpanded) {
            val existingUsers = folderShares.sharedWith.map { it.userId }.toSet()
            val filteredMembers =
                folderShares.availableMembers.filter { it.userId !in existingUsers }

            items(filteredMembers, key = { it.userId }) { user ->
                ListItem(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .animateItem()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onAddMember(user)
                            addPeopleExpanded = false
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    headlineContent = { Text(user.displayName) }
                )
            }
        }

        items(folderShares.sharedWith, key = { it.shareId }) { member ->
            MemberRowItem(
                member = member,
                canModifyMembers = canModifyMembers,
                viewPermissionString = viewPermissionString,
                uploadPermissionString = uploadPermissionString,
                uploadAndDeletePermissionString = uploadAndDeletePermissionString,
                onUpdatePermissions = onUpdatePermissions,
                onRemoveMember = onRemoveMember,
                modifier = Modifier.animateItem()
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                text = stringResource(R.string.sharing_links),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 16.dp,
                    top = 24.dp
                )
            )
        }

        items(folderShares.links) {
        }
    }
}

@Composable
private fun MemberRowItem(
    member: SharedFolderAccess.Member,
    canModifyMembers: Boolean,
    viewPermissionString: String,
    uploadPermissionString: String,
    uploadAndDeletePermissionString: String,
    onUpdatePermissions: (shareId: Long, canUpload: Boolean, canDelete: Boolean) -> Unit,
    onRemoveMember: (shareId: Long) -> Unit,
    modifier: Modifier = Modifier
) {

    var dropdownExpanded by remember { mutableStateOf(false) }

    val currentPermissionText = when {
        member.canUpload && member.canDelete -> uploadAndDeletePermissionString
        member.canUpload -> uploadPermissionString
        else -> viewPermissionString
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier,
        headlineContent = {
            Text(member.userDisplayName, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(
                currentPermissionText,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = member.userDisplayName.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        trailingContent = {
            if (!canModifyMembers) {
                return@ListItem
            }
            Box {
                IconButton(
                    onClick = { dropdownExpanded = true }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_options_vertical),
                        contentDescription = "Edit permissions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    val isViewOnly = !member.canUpload && !member.canDelete
                    val isUploadOnly = member.canUpload && !member.canDelete
                    val isUploadAndDelete = member.canUpload && member.canDelete

                    DropdownMenuItem(
                        text = { Text(viewPermissionString) },
                        leadingIcon = {
                            RadioButton(selected = isViewOnly, onClick = null)
                        },
                        onClick = {
                            onUpdatePermissions(member.shareId, false, false)
                            dropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(uploadPermissionString) },
                        leadingIcon = {
                            RadioButton(selected = isUploadOnly, onClick = null)
                        },
                        onClick = {
                            onUpdatePermissions(member.shareId, true, false)
                            dropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(uploadAndDeletePermissionString) },
                        leadingIcon = {
                            RadioButton(selected = isUploadAndDelete, onClick = null)
                        },
                        onClick = {
                            onUpdatePermissions(member.shareId, true, true)
                            dropdownExpanded = false
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.sharing_remove_member),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            onRemoveMember(member.shareId)
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }
    )
}