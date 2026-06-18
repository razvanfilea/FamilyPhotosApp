package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.TimelineLayout
import net.theluckycoder.familyphotos.data.model.db.Photo
import androidx.paging.compose.LazyPagingItems
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
                        is FolderNav.Source.Network -> source.folder.name
                        is FolderNav.Source.Local -> source.name
                    },
                    subtitle = timelineLayout.totalItemCount.takeIf { it != 0 }?.toString(),
                    actions = {
                        if (source is FolderNav.Source.Network) {
                            IconButton(onClick = {
                                showSharingBottomSheet = true
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_action_share),
                                    contentDescription = null
                                )
                            }

                            IconButton(onClick = {
                                backStack.add(RenameFolderNav(source.folder))
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

    if (source is FolderNav.Source.Network) {
        if (showSharingBottomSheet) {
            SharingBottomSheet(foldersViewModel, onDismiss = { showSharingBottomSheet = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharingBottomSheet(
    foldersViewModel: FoldersViewModel,
    onDismiss: () -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
    ) {
        Text(
            text = "Manage access",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp)
        )

        Text(
            text = "Members",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp)
        )

        data class Member(
            val name: String,
            val id: String,
            val upload_permission: Boolean,
            val delete_permission: Boolean,
        )

        val members = listOf(
            Member("Anda", "anda", true, false),
            Member("Deborah", "deborah", true, true)
        )

        val upload_permission_string = "Can upload"
        val delete_permission_string = "Can delete"

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(members) { member ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(member.name, fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        val permissions = buildList {
                            if (member.upload_permission) add(upload_permission_string)
                            if (member.delete_permission) add(delete_permission_string)
                        }
                        Text(permissions.joinToString(" • ").ifEmpty { "View only" })
                    },
                    trailingContent = {
                        IconButton(onClick = {

                        }) {
                            Icon(
                                painterResource(R.drawable.ic_more_options_vertical),
                                contentDescription = null
                            )
                        }
                    },
                )
            }
        }
    }
}
