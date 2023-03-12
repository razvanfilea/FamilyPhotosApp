package net.theluckycoder.familyphotos.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import coil.ImageLoader
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import net.theluckycoder.familyphotos.ui.AppTheme
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalOkHttpClient
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.navigation.BottomSheetNavigator
import net.theluckycoder.familyphotos.ui.screen.tabs.BottomTab
import net.theluckycoder.familyphotos.ui.screen.tabs.FamilyTab
import net.theluckycoder.familyphotos.ui.screen.tabs.LocalFoldersTab
import net.theluckycoder.familyphotos.ui.screen.tabs.NetworkFoldersTab
import net.theluckycoder.familyphotos.ui.screen.tabs.PersonalTab
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var playerController: Lazy<OkHttpClient>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val view = ComposeView(this)
        view.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalImageLoader provides imageLoader,
                    LocalOkHttpClient provides playerController
                ) {
                    AppContent()
                }
            }
        }

        setContentView(view)
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: BottomTab) {
    val tabNavigator = LocalTabNavigator.current
    val selected = tabNavigator.current == tab

    NavigationBarItem(
        selected = selected,
        onClick = { tabNavigator.current = tab },
        label = { Text(tab.options.title) },
        icon = {
            Icon(
                painter = if (selected) tab.selectedIcon else tab.options.icon!!,
                contentDescription = tab.options.title
            )
        }
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppContent(
    mainViewModel: MainViewModel = viewModel()
) = TabNavigator(PersonalTab) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isBottomBarVisible by mainViewModel.showBottomAppBar.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TabNavigationItem(PersonalTab)
                    TabNavigationItem(FamilyTab)
                    TabNavigationItem(NetworkFoldersTab)
                    TabNavigationItem(LocalFoldersTab)
                }
            }
        }
    ) { paddingValues ->
        val isRefreshing by mainViewModel.isRefreshing.collectAsState()

        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            val state = rememberPullRefreshState(isRefreshing, mainViewModel::refreshPhotos)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isBottomBarVisible) paddingValues.calculateBottomPadding() else 0.dp)
                    .pullRefresh(state),
            ) {
                BottomSheetNavigator {
                    CurrentTab()
                }

                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = state,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
