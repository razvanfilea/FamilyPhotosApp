package net.theluckycoder.familyphotos.ui.screen

import android.app.Application
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.screen.tabs.BottomTab
import net.theluckycoder.familyphotos.ui.screen.tabs.DeviceTab
import net.theluckycoder.familyphotos.ui.screen.tabs.NetworkFoldersTab
import net.theluckycoder.familyphotos.ui.screen.tabs.TimelineTab
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object MainScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow

        TabNavigator(TimelineTab) {
            val snackbarHostState = LocalSnackbarHostState.current

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (mainViewModel.showBars.value) {
                        NavigationBar(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TabNavigationItem(TimelineTab)
                            TabNavigationItem(NetworkFoldersTab)
                            TabNavigationItem(DeviceTab)
                        }
                    }
                }
            ) { paddingValues ->
                val isRefreshing by mainViewModel.isRefreshing.collectAsState()
                val app = LocalContext.current.applicationContext as Application

                val pullRefreshState = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        mainViewModel.refreshPhotos(app)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = paddingValues.calculateBottomPadding()),
                    state = pullRefreshState,
                ) {
                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        CurrentTab()
                    }
                }
            }
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
}