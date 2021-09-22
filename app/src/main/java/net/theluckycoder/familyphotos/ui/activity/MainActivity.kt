package net.theluckycoder.familyphotos.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.bottomSheet.BottomSheetNavigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import coil.ImageLoader
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import net.theluckycoder.familyphotos.ui.AppTheme
import net.theluckycoder.familyphotos.ui.DefaultSnackbar
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.screen.tabs.*
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @OptIn(ExperimentalMaterialApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = ComposeView(this)
        view.setContent {
            AppTheme {
                ProvideWindowInsets {
                    val systemUiController = rememberSystemUiController()
                    SideEffect {
                        systemUiController.setSystemBarsColor(Color.Black, darkIcons = false)
                    }

                    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                        AppContent()
                    }
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

    BottomNavigationItem(
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

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterialApi::class,
)
@Composable
private fun AppContent(
    mainViewModel: MainViewModel = viewModel()
) = TabNavigator(PersonalTab) {
    val scaffoldState = rememberScaffoldState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        snackbarHost = {
            scaffoldState.snackbarHostState
        },
        bottomBar = {
            val isVisible by mainViewModel.showBottomAppBar.collectAsState()

            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = isVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                BottomNavigation(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colors.primary
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

        CompositionLocalProvider(LocalSnackbarHostState provides scaffoldState.snackbarHostState) {
            SwipeRefresh(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onRefresh = { mainViewModel.refreshPhotos() },
                state = rememberSwipeRefreshState(isRefreshing)
            ) {
                Box(Modifier.fillMaxSize()) {
                    BottomSheetNavigator {
                        CurrentTab()
                    }

                    DefaultSnackbar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        snackbarHostState = scaffoldState.snackbarHostState,
                        onDismiss = {
                            scaffoldState.snackbarHostState.currentSnackbarData?.dismiss()
                        }
                    )
                }
            }
        }
    }
}
