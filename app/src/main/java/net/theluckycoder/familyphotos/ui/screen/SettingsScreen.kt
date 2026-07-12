package net.theluckycoder.familyphotos.ui.screen

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val backStack = LocalNavBackStack.current
    var showSignOutDialog by remember { mutableStateOf(false) }
    val serverAddress by settingsViewModel.serverAddress.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Backup
            SectionHeader(stringResource(R.string.backup_title))
            BackupMobileDataItem(settingsViewModel)

            // Storage
            SectionHeader(stringResource(R.string.settings_storage))
            CacheSizeItem(settingsViewModel)
            ClearCacheItem(settingsViewModel)

            // About
            SectionHeader(stringResource(R.string.settings_about))
            AppVersionItem(settingsViewModel.appVersion)
            ServerAddressItem(serverAddress)

            // Account
            SectionHeader(stringResource(R.string.settings_account))
            SignOutItem(onClick = { showSignOutDialog = true })
        }
    }

    if (showSignOutDialog) {
        SignOutConfirmationDialog(
            onConfirm = {
                settingsViewModel.signOut()
                showSignOutDialog = false
            },
            onDismiss = { showSignOutDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 72.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun BackupMobileDataItem(viewModel: SettingsViewModel) {
    val enabled by viewModel.backupOverMobileData.collectAsStateWithLifecycle()

    ListItem(
        leadingContent = {
            Icon(painterResource(R.drawable.ic_cloud_upload_outline), contentDescription = null)
        },
        headlineContent = { Text(stringResource(R.string.settings_backup_mobile_data)) },
        supportingContent = { Text(stringResource(R.string.settings_backup_mobile_data_summary)) },
        trailingContent = {
            Switch(checked = enabled, onCheckedChange = { viewModel.setBackupOverMobileData(it) })
        },
        modifier = Modifier.clickable { viewModel.setBackupOverMobileData(!enabled) }
    )
}

@Composable
private fun CacheSizeItem(viewModel: SettingsViewModel) {
    val cacheSizeMb by viewModel.cacheSizeMb.collectAsStateWithLifecycle()
    var sliderValue by remember(cacheSizeMb) { mutableIntStateOf(cacheSizeMb) }

    ListItem(
        leadingContent = {
            Icon(painterResource(R.drawable.ic_cloud_download_outline), contentDescription = null)
        },
        headlineContent = { Text(stringResource(R.string.settings_cache_size)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_cache_size_summary))
                Slider(
                    value = sliderValue.toFloat(),
                    onValueChange = { sliderValue = it.roundToInt() },
                    onValueChangeFinished = { viewModel.setCacheSize(sliderValue) },
                    valueRange = 512f..4096f,
                )
            }
        }
    )
}

@Composable
private fun ClearCacheItem(viewModel: SettingsViewModel) {
    val cacheUsage by viewModel.cacheUsageBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val usageText = remember(cacheUsage) { Formatter.formatShortFileSize(context, cacheUsage) }

    ListItem(
        leadingContent = {
            Icon(painterResource(R.drawable.ic_action_delete), contentDescription = null)
        },
        headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
        supportingContent = { Text(stringResource(R.string.settings_cache_usage, usageText)) },
        modifier = Modifier.clickable { viewModel.clearCache() }
    )
}

@Composable
private fun AppVersionItem(version: String) {
    ListItem(
        leadingContent = {
            Icon(painterResource(R.drawable.ic_outline_info), contentDescription = null)
        },
        headlineContent = { Text(stringResource(R.string.settings_app_version)) },
        supportingContent = { Text(version) }
    )
}

@Composable
private fun ServerAddressItem(address: String) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_server_address)) },
        supportingContent = { Text(address) }
    )
}

@Composable
private fun SignOutItem(onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.settings_sign_out),
                color = MaterialTheme.colorScheme.error,
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SignOutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sign_out)) },
        text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_sign_out),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
