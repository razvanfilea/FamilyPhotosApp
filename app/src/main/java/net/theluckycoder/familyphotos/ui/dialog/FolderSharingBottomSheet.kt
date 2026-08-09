package net.theluckycoder.familyphotos.ui.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.SharedFolderAccess
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
import net.theluckycoder.familyphotos.core.data.model.db.isPublic
import net.theluckycoder.familyphotos.core.data.model.network.UserDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSharingBottomSheet(
    folder: NetworkFolderEntity,
    folderShares: SharedFolderAccess,
    currentUser: UserDto?,
    onAddMember: (UserDto) -> Unit,
    onUpdatePermissions: (shareId: Long, canUpload: Boolean, canDelete: Boolean) -> Unit,
    onRemoveMember: (shareId: Long) -> Unit,
    onDismiss: () -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val canModifyMembers = !folder.isPublic && folder.ownerId == currentUser?.userId
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
            val ownerName =
                folderShares.availableMembers.find { it.userId == folder.ownerId }?.displayName
                    ?: currentUser?.displayName ?: ""
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
                folderShares.availableMembers.filter { it.userId !in existingUsers && it.userId != folder.ownerId }

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
            FolderMemberItem(
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
    }
}

@Composable
private fun FolderMemberItem(
    member: SharedFolderAccess.Member,
    canModifyMembers: Boolean,
    viewPermissionString: String,
    uploadPermissionString: String,
    uploadAndDeletePermissionString: String,
    onUpdatePermissions: (shareId: Long, canUpload: Boolean, canDelete: Boolean) -> Unit,
    onRemoveMember: (shareId: Long) -> Unit,
    modifier: Modifier = Modifier,
) = ListItem(
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = modifier,
    headlineContent = {
        Text(member.userDisplayName, fontWeight = FontWeight.SemiBold)
    },
    supportingContent = {
        val currentPermissionText = when {
            member.canUpload && member.canDelete -> uploadAndDeletePermissionString
            member.canUpload -> uploadPermissionString
            else -> viewPermissionString
        }

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
        var dropdownExpanded by remember { mutableStateOf(false) }

        if (!canModifyMembers) {
            return@ListItem
        }
        Box {
            IconButton(
                onClick = { dropdownExpanded = true }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_options_vertical),
                    contentDescription = stringResource(R.string.cd_edit_permissions),
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