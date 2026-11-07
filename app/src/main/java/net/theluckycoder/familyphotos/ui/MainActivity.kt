package net.theluckycoder.familyphotos.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.BuildConfig
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.ui.composables.PhotosViewer
import net.theluckycoder.familyphotos.ui.composables.TypedSnackbar
import net.theluckycoder.familyphotos.ui.screen.DuplicatesScreen
import net.theluckycoder.familyphotos.ui.screen.FolderScreen
import net.theluckycoder.familyphotos.ui.screen.LargeFilesScreen
import net.theluckycoder.familyphotos.ui.screen.MemoryStoryScreen
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
        const val EXTRA_OPEN_FOLDER_ID = "open_folder_id"
        const val EXTRA_OPEN_FOLDER_NAME = "open_folder_name"
        const val EXTRA_OPEN_MEMORY_YEARS_AGO = "open_memory_years_ago"
    }

    private val folderToOpen = mutableStateOf<FolderNav.Source.Network?>(null)

    private val memoryToOpen = mutableStateOf<Int?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntentTarget(intent)
    }

    private fun readIntentTarget(intent: Intent) {
        // Relaunching from recents redelivers the original intent (notification tap)
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return

        val folderId = intent.getLongExtra(EXTRA_OPEN_FOLDER_ID, -1L)
        if (folderId != -1L) {
            // Consume the extra, or it would reopen the folder on every recreation
            intent.removeExtra(EXTRA_OPEN_FOLDER_ID)
            folderToOpen.value = FolderNav.Source.Network(
                folderId = folderId,
                folderName = intent.getStringExtra(EXTRA_OPEN_FOLDER_NAME).orEmpty(),
            )
        }

        val yearsAgo = intent.getIntExtra(EXTRA_OPEN_MEMORY_YEARS_AGO, -1)
        if (yearsAgo != -1) {
            intent.removeExtra(EXTRA_OPEN_MEMORY_YEARS_AGO)
            memoryToOpen.value = yearsAgo
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        val viewModel = mainViewModel

        if (savedInstanceState == null) readIntentTarget(intent)

        val isBenchmark = BuildConfig.BENCHMARK

        // Inject benchmark credentials from intent extras and trigger sync.
        // Debug builds accept them too, so tests on an emulator can skip the login screen
        if (isBenchmark || BuildConfig.DEBUG) {
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
            val openingPhotoId = remember { mutableStateOf<Long?>(null) }

            AppTheme {
                val isLoggedIn = mainViewModel.isLoggedIn.collectAsState()
                if (!isLoggedIn.value && !isBenchmark) {
                    val initialServerAddress by mainViewModel.loginRepository.serverAddress.collectAsState(
                        initial = ""
                    )
                    LoginScreen(
                        initialServerAddress = initialServerAddress ?: "",
                        testConnection = { address ->
                            mainViewModel.loginRepository.testServerConnection(address)
                        },
                        loginAction = { address, userLogin ->
                            mainViewModel.loginRepository.login(address, userLogin)
                        }
                    )
                    return@AppTheme
                }

                val requestedFolder = folderToOpen.value
                LaunchedEffect(requestedFolder) {
                    if (requestedFolder == null) return@LaunchedEffect
                    folderToOpen.value = null
                    // Only the id: a folder opened from the list also carries its photo count
                    val alreadyOpen = ((backStack.lastOrNull() as? FolderNav)?.source as? FolderNav.Source.Network)
                        ?.folderId == requestedFolder.folderId
                    if (!alreadyOpen) backStack.add(FolderNav(requestedFolder))
                }

                val requestedMemory = memoryToOpen.value
                LaunchedEffect(requestedMemory) {
                    if (requestedMemory == null) return@LaunchedEffect
                    memoryToOpen.value = null
                    val memories = timelineViewModel.memories.filterNotNull().first()
                    val alreadyOpen = (backStack.lastOrNull() as? MemoryNav)?.yearsAgo == requestedMemory
                    if (memories.any { it.yearsAgo == requestedMemory } && !alreadyOpen) {
                        backStack.add(MemoryNav(requestedMemory))
                    }
                }

                val benchmarkModifier = if (isBenchmark) {
                    Modifier.semantics { testTagsAsResourceId = true }
                } else Modifier

                // Compose has to perform lookahead node instantiation for each newly composed items
                SharedTransitionLayout(benchmarkModifier) {
                    CompositionLocalProvider(
                        LocalImageLoader provides imageLoaderLazy,
                        LocalSnackbarHostState provides snackbarHostState,
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavBackStack provides backStack,
                        LocalSettingsDataStore provides settingsDataStore,
                        LocalOpeningPhotoId provides openingPhotoId,
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

    private fun listenToTrashRequests() = lifecycleScope.launch(Dispatchers.Main) {
        mainViewModel.localPhotosToDelete.collect { photos ->
            ensureActive()

            val uriList = photos.map { it.uri }
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

private val viewerEnterTransitionSpec =
    (fadeIn(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(150, easing = FastOutSlowInEasing)))
        .togetherWith(fadeOut(animationSpec = tween(150)))

private val viewerExitTransitionSpec =
    (fadeIn(animationSpec = tween(150)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(150, easing = FastOutSlowInEasing)))
        .togetherWith(
            fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                    )
        )

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
    this is PhotoViewerFlowNav || this is PhotoViewerListNav || this is MemoryNav

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
    val folderPagingItems = foldersTabViewModel.folderPhotosPager.collectAsLazyPagingItems()

    if (backStack.isEmpty()) {
        backStack.add(TopLevelNav)
    }

    val context = LocalContext.current
    // Once "Install unknown apps" is granted, go on to the installer (the cached APK is reused)
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (context.packageManager.canRequestPackageInstalls()) mainViewModel.downloadUpdate()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // Only while started: a background activity can't launch the installer, and the
        // conflated channel keeps the intent until the user returns
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            mainViewModel.installUpdate.collect { intent ->
                if (!context.packageManager.canRequestPackageInstalls()) {
                    unknownSourcesLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    )
                    return@collect
                }
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    snackbarManager.showMessage(R.string.update_no_installer, UiMessageType.Error)
                }
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        transitionSpec = {
            val targetKey = targetState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            val initialKey = initialState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            if (targetKey.isViewer()) {
                viewerEnterTransitionSpec
            } else if (initialKey.isViewer()) {
                viewerExitTransitionSpec
            } else {
                forwardSlideTransitionSpec
            }
        },
        popTransitionSpec = {
            val targetKey = targetState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            val initialKey = initialState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            if (targetKey.isViewer() || initialKey.isViewer()) {
                viewerExitTransitionSpec
            } else {
                backwardSlideTransitionSpec
            }
        },
        predictivePopTransitionSpec = {
            val targetKey = targetState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            val initialKey = initialState.entries.lastOrNull()?.metadata?.get("key") as? NavKey
            if (targetKey.isViewer() || initialKey.isViewer()) {
                viewerExitTransitionSpec
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
                    val lazyPagingItems = when (key.source) {
                        PhotoViewerFlowNav.Source.Timeline -> timelinePagingItems
                        PhotoViewerFlowNav.Source.Network,
                        PhotoViewerFlowNav.Source.Local,
                        PhotoViewerFlowNav.Source.Favorites -> folderPagingItems
                    }

                    PhotosViewer(
                        lazyPagingItems = lazyPagingItems,
                        initialPhotoIndex = key.initialPhotoIndex,
                        mainViewModel = mainViewModel
                    )
                }

                is FolderNav -> navEntry(key) {
                    FolderScreen(key.source, foldersTabViewModel, mainViewModel = mainViewModel)
                }

                is PhotoViewerListNav -> navEntry(key) {
                    PhotosViewer(key.photos, mainViewModel = mainViewModel)
                }

                is MemoryNav -> navEntry(key) {
                    MemoryStoryScreen(key.yearsAgo, timelineViewModel, mainViewModel)
                }

                is MovePhotosNav -> navEntry(key) {
                    MovePhotosScreen(key.photoIds)
                }

                is UploadPhotosNav -> navEntry(key) {
                    UploadPhotosScreen(key.photoIds)
                }

                is DuplicatesNav -> navEntry(key) {
                    DuplicatesScreen(mainViewModel = mainViewModel)
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
