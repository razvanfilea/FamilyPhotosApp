package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowWidthSizeClass
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import androidx.paging.compose.LazyPagingItems
import net.theluckycoder.familyphotos.ui.TopLevelTab
import net.theluckycoder.familyphotos.ui.screen.tabs.DeviceTab
import net.theluckycoder.familyphotos.ui.screen.tabs.NetworkFoldersTab
import net.theluckycoder.familyphotos.ui.screen.tabs.TimelineTab
import net.theluckycoder.familyphotos.ui.screen.tabs.UtilitiesTab
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelScreen(
    timelinePagingItems: LazyPagingItems<NetworkPhoto>,
    mainViewModel: MainViewModel,
) {
    val modifier = Modifier.fillMaxSize()

    val selectedTabState = mainViewModel.selectedTabState

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val customNavSuiteType = with(adaptiveInfo) {
        if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
        }
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = customNavSuiteType,
        navigationSuiteItems = {
            TopLevelTab.entries.forEach { tab ->
                item(
                    icon = {
                        Icon(
                            painter = painterResource(if (selectedTabState.value == tab) tab.selectedIcon else tab.icon),
                            contentDescription = stringResource(tab.sectionName)
                        )
                    },
                    label = { Text(stringResource(tab.sectionName)) },
                    selected = selectedTabState.value == tab,
                    onClick = { selectedTabState.value = tab }
                )
            }
        }
    ) {
        val isRefreshing by mainViewModel.isRefreshing.collectAsState()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = mainViewModel::refreshPhotos,
            modifier = modifier
        ) {
            when (selectedTabState.value) {
                TopLevelTab.Timeline -> TimelineTab(timelinePagingItems)
                TopLevelTab.NetworkFolders -> NetworkFoldersTab()
                TopLevelTab.Device -> DeviceTab()
                TopLevelTab.Utility -> UtilitiesTab()
            }
        }
    }
}
