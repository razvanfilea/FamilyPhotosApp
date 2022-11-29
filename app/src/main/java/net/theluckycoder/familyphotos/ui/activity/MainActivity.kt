package net.theluckycoder.familyphotos.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import coil.ImageLoader
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import net.theluckycoder.familyphotos.ui.*
import net.theluckycoder.familyphotos.ui.navigation.BottomSheetNavigator
import net.theluckycoder.familyphotos.ui.screen.tabs.*
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
                val systemUiController = rememberSystemUiController()
                val background = MaterialTheme.colorScheme.surface

                LaunchedEffect(background) {
                    systemUiController.setStatusBarColor(Color.Transparent)
                    systemUiController.setNavigationBarColor(Color.Transparent)
                }

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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

                DefaultSnackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    snackbarHostState = snackbarHostState,
                    onDismiss = {
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                )
            }
        }
    }
}
