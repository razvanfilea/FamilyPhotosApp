package net.theluckycoder.familyphotos.ui.screen.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.composables.FoldersGridList
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel

@Composable
fun DeviceTab() {
    val foldersViewModel: FoldersViewModel = viewModel()
    val backStack = LocalNavBackStack.current
    val folders by foldersViewModel.localFolders.collectAsState()
    val backupFolders by foldersViewModel.backupFolders.collectAsState()

    val folderNameFilter = remember { mutableStateOf("") }

    FoldersGridList(
        folders = folders,
        onFolderClick = { folder ->
            backStack.add(FolderNav(FolderNav.Source.Local(folder.name)))
        },
        folderNameFilter = folderNameFilter.value,
        onSearch = { folderNameFilter.value = it },
        foldersViewModel = foldersViewModel,
        folderDetailsText = { folder ->
            pluralStringResource(R.plurals.items_photos, folder.count, folder.count)
        },
        isBackupEnabled = { folder -> folder.name in backupFolders },
        extraHeader = {
            val pendingBackupCount by foldersViewModel.pendingBackupCount.collectAsState()
            val backupProgress by foldersViewModel.backupProgress.collectAsState()

            BackupStatusCard(
                backupFolderCount = backupFolders.size,
                pendingPhotoCount = pendingBackupCount,
                backupProgress = backupProgress,
                onBackupNow = { foldersViewModel.triggerBackup() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    )
}

@Composable
private fun BackupStatusCard(
    backupFolderCount: Int,
    pendingPhotoCount: Int,
    backupProgress: FoldersViewModel.BackupProgress?,
    onBackupNow: () -> Unit,
    modifier: Modifier = Modifier
) = Card(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(if (pendingPhotoCount == 0) R.drawable.ic_cloud_done_filled else R.drawable.ic_cloud_upload_outline),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = stringResource(R.string.backup_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.backup_folders_selected,
                        backupFolderCount,
                        backupFolderCount
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.backup_photos_pending,
                        pendingPhotoCount,
                        pendingPhotoCount
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (backupProgress != null && backupProgress.total > 0) {
            LinearProgressIndicator(
                progress = { backupProgress.current.toFloat() / backupProgress.total },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${backupProgress.current} / ${backupProgress.total}",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Button(
                modifier = Modifier.wrapContentSize(Alignment.BottomEnd),
                onClick = onBackupNow,
                enabled = pendingPhotoCount > 0
            ) {
                Text(stringResource(R.string.backup_now))
            }
        }
    }
}
