package net.theluckycoder.familyphotos.ui

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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.ui.viewmodel.FolderScreenViewModel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.ImageLoader
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.BuildConfig
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.ui.composables.PhotosViewer
import net.theluckycoder.familyphotos.ui.composables.TypedSnackbar
import net.theluckycoder.familyphotos.ui.screen.DuplicatesScreen
import net.theluckycoder.familyphotos.ui.screen.FolderScreen
import net.theluckycoder.familyphotos.ui.screen.LargeFilesScreen
import net.theluckycoder.familyphotos.ui.screen.LoginScreen
import net.theluckycoder.familyphotos.ui.screen.MovePhotosScreen
import net.theluckycoder.familyphotos.ui.screen.SettingsScreen
import net.theluckycoder.familyphotos.ui.screen.TopLevelScreen
import net.theluckycoder.familyphotos.ui.screen.TrashScreen
import net.theluckycoder.familyphotos.ui.screen.UploadPhotosScreen
import net.theluckycoder.familyphotos.ui.theme.AppTheme
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersTabViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.TimelineViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    private val mainViewModel: MainViewModel by viewModels()
    private val foldersTabViewModel: FoldersTabViewModel by viewModels()
    private val timelineViewModel: TimelineViewModel by viewModels()

    private val deletePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) {
                foldersTabViewModel.refreshLocalPhotos()
            }
        }

    @Inject
    lateinit var imageLoaderLazy: Lazy<ImageLoader>

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var snackbarManager: SnackbarManager

    companion object {
        const val EXTRA_BENCHMARK_SESSION_COOKIE = "benchmark_session_cookie"
        const val EXTRA_BENCHMARK_USERNAME = "benchmark_username"
        const val EXTRA_BENCHMARK_SERVER_ADDRESS = "benchmark_server_address"
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        val viewModel = mainViewModel

        val isBenchmark = BuildConfig.BENCHMARK

        // Inject benchmark credentials from intent extras and trigger sync
        if (isBenchmark) {
            val sessionCookie = intent.getStringExtra(EXTRA_BENCHMARK_SESSION_COOKIE)
            val username = intent.getStringExtra(EXTRA_BENCHMARK_USERNAME)
            val serverAddress = intent.getStringExtra(EXTRA_BENCHMARK_SERVER_ADDRESS)
            if (sessionCookie != null && username != null && serverAddress != null) {
                lifecycleScope.launch(Dispatchers.Main.immediate) {
                    withContext(Dispatchers.IO) {
                        viewModel.loginRepository.setBenchmarkCredentials(
                            sessionCookie,
                            username,
                            serverAddress
                        )
                    }
                    // Trigger refresh after credentials are set (initial sync ran before credentials existed)
                    viewModel.refreshPhotos()
                }
            }
        }

        setContent {
            val backStack = rememberNavBackStack(TopLevelNav)
            val snackbarHostState = remember { SnackbarHostState() }

            AppTheme {
                val isLoggedIn = mainViewModel.isLoggedIn.collectAsState()
                if (!isLoggedIn.value && !isBenchmark) {
                    LoginScreen(loginAction = { serverAddress, userLogin ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            mainViewModel.loginRepository.login(serverAddress, userLogin)
                        }
                    })
                    return@AppTheme
                }

                val benchmarkModifier = if (isBenchmark) {
                    Modifier.semantics { testTagsAsResourceId = true }
                } else Modifier

                SharedTransitionLayout(benchmarkModifier) {
                    CompositionLocalProvider(
                        LocalImageLoader provides imageLoaderLazy,
                        LocalSnackbarHostState provides snackbarHostState,
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavBackStack provides backStack,
                        LocalSettingsDataStore provides settingsDataStore,
                    ) {
                        Content(
                            backStack,
                            mainViewModel,
                            foldersTabViewModel,
                            timelineViewModel,
                            snackbarManager
                        )
                    }
                }
            }
        }

        if (!isBenchmark) {
            requestPermissions()
        }

        listenToTrashRequests()
    }

    private fun requestPermissions() {
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
            permissionLauncher.launch(readImagePermission)
        }
    }

    private fun listenToTrashRequests() = lifecycleScope.launch(Dispatchers.IO) {
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

private val viewerTransitionSpec =
    (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
        .togetherWith(fadeOut(animationSpec = tween(90)))

private val forwardSlideTransitionSpec =
    (fadeIn(animationSpec = tween(450, easing = EaseOutQuart)) +
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(450, easing = EaseOutQuart)
            ))
        .togetherWith(
            fadeOut(animationSpec = tween(400, easing = EaseOutCubic)) +
                    slideOutHorizontally(
                        targetOffsetX = { -it / 6 },
                        animationSpec = tween(400, easing = EaseOutCubic)
                    )
        )

private val backwardSlideTransitionSpec =
    (fadeIn(animationSpec = tween(400, easing = EaseOutQuart)) +
            slideInHorizontally(
                initialOffsetX = { -it / 6 },
                animationSpec = tween(400, easing = EaseOutQuart)
            ))
        .togetherWith(
            fadeOut(animationSpec = tween(450, easing = EaseOutCubic)) +
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(450, easing = EaseOutCubic)
                    )
        )

private fun NavKey?.isViewer(): Boolean =
    this is PhotoViewerFlowNav || this is PhotoViewerListNav

private fun <T : NavKey> navEntry(
    key: T,
    content: @Composable (T) -> Unit
): NavEntry<NavKey> = NavEntry(
    key = key,
    metadata = mapOf("key" to key),
    content = { @Suppress("UNCHECKED_CAST") content(it as T) }
)

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun Content(
    backStack: NavBackStack<NavKey>,
    mainViewModel: MainViewModel,
    foldersTabViewModel: FoldersTabViewModel,
    timelineViewModel: TimelineViewModel,
    snackbarManager: SnackbarManager,
) = Box(Modifier.fillMaxSize()) {

    val timelinePagingItems = timelineViewModel.timelinePager.collectAsLazyPagingItems()
    if (backStack.isEmpty()) {
        backStack.add(TopLevelNav)
    }

    NavDisplay(
        backStack = backStack,
        transitionSpec = {
            val targetKey = targetState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            val initialKey = initialState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            if (targetKey.isViewer() || initialKey.isViewer()) {
                viewerTransitionSpec
            } else {
                forwardSlideTransitionSpec
            }
        },
        popTransitionSpec = {
            val targetKey = targetState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            val initialKey = initialState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            if (targetKey.isViewer() || initialKey.isViewer()) {
                viewerTransitionSpec
            } else {
                backwardSlideTransitionSpec
            }
        },
        predictivePopTransitionSpec = {
            val targetKey = targetState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            val initialKey = initialState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            if (targetKey.isViewer() || initialKey.isViewer()) {
                viewerTransitionSpec
            } else {
                backwardSlideTransitionSpec
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = { key ->
            when (key) {
                is TopLevelNav -> navEntry(key) {
                    TopLevelScreen(
                        timelinePagingItems,
                        mainViewModel,
                        timelineViewModel,
                        foldersTabViewModel,
                    )
                }

                is PhotoViewerFlowNav -> navEntry(key) {
                    val folderScreenViewModel: FolderScreenViewModel = viewModel()
                    LaunchedEffect(key.folderSource) {
                        key.folderSource?.let { folderScreenViewModel.setSource(it) }
                    }
                    val lazyPagingItems = when (key.source) {
                        PhotoViewerFlowNav.Source.Timeline -> timelinePagingItems
                        PhotoViewerFlowNav.Source.Network -> folderScreenViewModel.networkFolderPhotosPager.collectAsLazyPagingItems()
                        PhotoViewerFlowNav.Source.Local -> folderScreenViewModel.localFolderPhotosPager.collectAsLazyPagingItems()
                        PhotoViewerFlowNav.Source.Favorites -> folderScreenViewModel.favoritePhotosPager.collectAsLazyPagingItems()
                    }

                    PhotosViewer(
                        lazyPagingItems = lazyPagingItems,
                        initialPhotoIndex = key.initialPhotoIndex
                    )
                }

                is FolderNav -> navEntry(key) {
                    FolderScreen(key.source, foldersTabViewModel)
                }

                is PhotoViewerListNav -> navEntry(key) {
                    PhotosViewer(key.photos)
                }

                is MovePhotosNav -> navEntry(key) {
                    MovePhotosScreen(key.photoIds)
                }

                is UploadPhotosNav -> navEntry(key) {
                    UploadPhotosScreen(key.photoIds)
                }

                is DuplicatesNav -> navEntry(key) {
                    DuplicatesScreen()
                }

                is LargeFilesNav -> navEntry(key) {
                    LargeFilesScreen()
                }

                is SettingsNav -> navEntry(key) {
                    SettingsScreen()
                }

                is TrashNav -> navEntry(key) {
                    TrashScreen()
                }

                else -> navEntry(key) {
                    Text("Invalid route: $key")
                }
            }
        }
    )

    TypedSnackbar(snackbarManager)
}
