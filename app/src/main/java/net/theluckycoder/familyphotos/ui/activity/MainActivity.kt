package net.theluckycoder.familyphotos.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.ImageLoader
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.ui.AppTheme
import net.theluckycoder.familyphotos.ui.DeviceNav
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalOkHttpClient
import net.theluckycoder.familyphotos.ui.LocalSharedTransitionScope
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.MovePhotosNav
import net.theluckycoder.familyphotos.ui.NetworkFolderNav
import net.theluckycoder.familyphotos.ui.PhotoViewerNav
import net.theluckycoder.familyphotos.ui.RenameFolderNav
import net.theluckycoder.familyphotos.ui.TabBackStack
import net.theluckycoder.familyphotos.ui.TimelineNav
import net.theluckycoder.familyphotos.ui.TopLevelRouteNav
import net.theluckycoder.familyphotos.ui.UploadPhotosNav
import net.theluckycoder.familyphotos.ui.composables.PhotosViewer
import net.theluckycoder.familyphotos.ui.screen.FolderScreen
import net.theluckycoder.familyphotos.ui.screen.MovePhotosScreen
import net.theluckycoder.familyphotos.ui.screen.RenameFolderScreen
import net.theluckycoder.familyphotos.ui.screen.UploadPhotosScreen
import net.theluckycoder.familyphotos.ui.screen.tabs.DeviceTab
import net.theluckycoder.familyphotos.ui.screen.tabs.NetworkFoldersTab
import net.theluckycoder.familyphotos.ui.screen.tabs.TimelineTab
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val storagePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    private val mainViewModel: MainViewModel by viewModels()

    private val deletePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) {
                mainViewModel.refreshLocalPhotos()
            }
        }

    @Inject
    lateinit var imageLoaderLazy: Lazy<ImageLoader>

    @Inject
    lateinit var okHttpClientLazy: Lazy<OkHttpClient>

    @OptIn(ExperimentalAnimationApi::class, ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)

        mainViewModel // Initialize ViewModel

        setContent {
            // TODO Use a saveable when the API becomes available
            val tabBackStack = remember { TabBackStack(TimelineNav) }

            val snackbarHostState = remember { SnackbarHostState() }

            AppTheme {
                SharedTransitionLayout {
                    CompositionLocalProvider(
                        LocalImageLoader provides imageLoaderLazy,
                        LocalOkHttpClient provides okHttpClientLazy,
                        LocalSnackbarHostState provides snackbarHostState,
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavBackStack provides tabBackStack.backStack
                    ) {
                        Content(tabBackStack, mainViewModel)
                    }
                }
            }
        }

        val readImagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        else
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )

        if (ContextCompat.checkSelfPermission(
                this,
                readImagePermission.first()
            ) == PackageManager.PERMISSION_DENIED
        ) {
            storagePermissionLauncher.launch(readImagePermission)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.localPhotosToDelete.collect { photos ->
                ensureActive()

                val uriList = photos.map { it.uri }

                withContext(Dispatchers.Main) {
                    val pendingIntent = MediaStore.createTrashRequest(
                        this@MainActivity.contentResolver,
                        uriList,
                        true
                    )

                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()

                    deletePhotoLauncher.launch(request)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun Content(tabBackStack: TabBackStack, mainViewModel: MainViewModel) {

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        bottomBar = {
            if (mainViewModel.showBars.value) {
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TabNavigationItem(tabBackStack, TimelineNav)
                    TabNavigationItem(tabBackStack, NetworkFolderNav)
                    TabNavigationItem(tabBackStack, DeviceNav)
                }
            }
        }
    ) { paddingValues ->
        NavDisplay(
            backStack = tabBackStack.backStack,
            entryProvider = { key ->
                when (key) {
                    is TopLevelRouteNav -> {
                        NavEntry(key) {
                            val isRefreshing by mainViewModel.isRefreshing.collectAsState()

                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    mainViewModel.refreshPhotos()
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = paddingValues.calculateBottomPadding()),
                            ) {
                                when (key) {
                                    TimelineNav -> {
                                        val timelinePhotos = mainViewModel.timelinePager.collectAsLazyPagingItems()
                                        TimelineTab(timelinePhotos)}
                                    NetworkFolderNav -> NetworkFoldersTab()
                                    DeviceNav -> DeviceTab()
                                }
                            }
                        }
                    }

                    is FolderNav -> NavEntry(key) {
                        FolderScreen(key.source)
                    }

                    is PhotoViewerNav -> NavEntry(key) {
                        DisposableEffect(Unit) {
                            mainViewModel.showBars.value = false
                            onDispose {
                                mainViewModel.showBars.value = true
                            }
                        }
                        PhotosViewer(
                            photos = key.photos,
                            close = { tabBackStack.removeLast() }
                        )
                    }

                    is MovePhotosNav -> NavEntry(key) {
                        MovePhotosScreen(key.photoIds)
                    }

                    is UploadPhotosNav -> NavEntry(key) {
                        UploadPhotosScreen(key.photoIds)
                    }

                    is RenameFolderNav -> NavEntry(key) {
                        RenameFolderScreen(key.folder)
                    }

                    else -> {
                        error("Unknown route: $key")
                    }
                }
            }
        )
    }
}

@Composable
private fun RowScope.TabNavigationItem(backStack: TabBackStack, route: TopLevelRouteNav) {
    val selected = backStack.topLevelKey == route

    NavigationBarItem(
        selected = selected,
        onClick = { backStack.addTopLevel(route) },
        label = { Text(stringResource(route.name)) },
        icon = {
            Icon(
                painter = painterResource(if (selected) route.selectedIcon else route.icon),
                contentDescription = stringResource(route.name)
            )
        }
    )
}
