package net.theluckycoder.familyphotos.ui.screen

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.preferences.EmptyPreferenceItem
import net.theluckycoder.familyphotos.ui.preferences.PreferenceGroupItem
import net.theluckycoder.familyphotos.ui.preferences.PreferenceScreen
import net.theluckycoder.familyphotos.ui.preferences.SeekbarIntPreferenceItem
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() = Scaffold(
    topBar = {
        val backStack = LocalNavBackStack.current

        TopAppBar(
            title = {
                Text(text = stringResource(id = R.string.settings))
            },
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
    val mainViewModel: MainViewModel = viewModel()
    val app = LocalContext.current.applicationContext as Application

    Box(modifier = Modifier.padding(padding)) {
        PreferenceScreen(
            dataStore = mainViewModel.settingsStore.dataStore(),
            items = getPreferenceItems(mainViewModel, app),
        )
    }
}

@Composable
private fun getPreferenceItems(
    mainViewModel: MainViewModel,
    app: Application
) = listOf(
    PreferenceGroupItem(
        title = stringResource(id = R.string.pref_category_cache),
        items = listOf(
            SeekbarIntPreferenceItem(
                title = stringResource(id = R.string.pref_cache_size),
                summary = stringResource(id = R.string.pref_restart_needed),
                icon = painterResource(id = R.drawable.ic_cloud_download_outline),
                prefKey = SettingsDataStore.CACHE_SIZE,
                defaultValue = SettingsDataStore.DEFAULT_CACHE_SIZE,
                valueRange = 1024..4096
            ),
            EmptyPreferenceItem(
                title = stringResource(id = R.string.pref_clear_cache),
                summary = stringResource(id = R.string.pref_restart_needed),
                icon = painterResource(id = R.drawable.ic_clear),
                onClick = { mainViewModel.clearAppCache(app) }
            ),
        ),
    ),
    PreferenceGroupItem(
        title = "User",
        items = listOf(
            EmptyPreferenceItem(
                title = "Sign out",
                summary = "Log out of the current user session",
                onClick = { mainViewModel.logout(app) }
            )
        )
    )
)
