package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.composables.pullrefresh.PullRefreshIndicator
import net.theluckycoder.familyphotos.ui.composables.pullrefresh.pullRefresh
import net.theluckycoder.familyphotos.ui.composables.pullrefresh.rememberPullRefreshState
import net.theluckycoder.familyphotos.ui.screen.tabs.BottomTab
import net.theluckycoder.familyphotos.ui.screen.tabs.DeviceTab
import net.theluckycoder.familyphotos.ui.screen.tabs.FamilyTab
import net.theluckycoder.familyphotos.ui.screen.tabs.NetworkFoldersTab
import net.theluckycoder.familyphotos.ui.screen.tabs.PersonalTab
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object MainScreen : Screen {

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow

        TabNavigator(PersonalTab) {
            val snackbarHostState = LocalSnackbarHostState.current

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TabNavigationItem(PersonalTab)
                        TabNavigationItem(FamilyTab)
                        TabNavigationItem(NetworkFoldersTab)
                        TabNavigationItem(DeviceTab)
                    }
                }
            ) { paddingValues ->
                val isRefreshing by mainViewModel.isRefreshing.collectAsState()

                val state = rememberPullRefreshState(isRefreshing, mainViewModel::refreshPhotos)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = paddingValues.calculateBottomPadding())
                        .pullRefresh(state),
                ) {
                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        CurrentTab()
                    }

                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = state,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                        contentColor = MaterialTheme.colorScheme.secondary
                    )
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