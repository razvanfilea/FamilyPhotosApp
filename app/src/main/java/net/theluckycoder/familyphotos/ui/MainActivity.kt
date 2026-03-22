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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.ImageLoader
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.BuildConfig
import net.theluckycoder.familyphotos.data.model.LazyPagingData
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.ui.composables.PhotosViewer
import net.theluckycoder.familyphotos.ui.screen.DuplicatesScreen
import net.theluckycoder.familyphotos.ui.screen.FolderScreen
import net.theluckycoder.familyphotos.ui.screen.LoginScreen
import net.theluckycoder.familyphotos.ui.screen.MovePhotosScreen
import net.theluckycoder.familyphotos.ui.screen.RenameFolderScreen
import net.theluckycoder.familyphotos.ui.screen.SettingsScreen
import net.theluckycoder.familyphotos.ui.screen.TopLevelScreen
import net.theluckycoder.familyphotos.ui.screen.TrashScreen
import net.theluckycoder.familyphotos.ui.screen.UploadPhotosScreen
import net.theluckycoder.familyphotos.ui.theme.AppTheme
import net.theluckycoder.familyphotos.ui.viewmodel.FoldersViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.TimelineViewModel
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    private val mainViewModel: MainViewModel by viewModels()
    private val foldersViewModel: FoldersViewModel by viewModels()
    private val timelineViewModel: TimelineViewModel by viewModels()

    private val deletePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) {
                foldersViewModel.refreshLocalPhotos()
            }
        }

    @Inject
    lateinit var imageLoaderLazy: Lazy<ImageLoader>

    @Inject
    lateinit var okHttpClientLazy: Lazy<OkHttpClient>

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)

        val isBenchmark = BuildConfig.BENCHMARK

        setContent {
            val backStack = rememberNavBackStack(TopLevelNav)
            val snackbarHostState = remember { SnackbarHostState() }

            AppTheme {
                val isLoggedIn = mainViewModel.loginRepository.isLoggedIn.collectAsState(true)
                if (!isLoggedIn.value && !isBenchmark) {
                    LoginScreen(loginAction = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            mainViewModel.loginRepository.login(it)
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
                        LocalOkHttpClient provides okHttpClientLazy,
                        LocalSnackbarHostState provides snackbarHostState,
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavBackStack provides backStack,
                    ) {
                        Content(backStack, mainViewModel, foldersViewModel, timelineViewModel)
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
            ) == PackageManager.PERMISSION_DENIED && !isBenchmark
        ) {
            permissionLauncher.launch(readImagePermission)
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

private val transitionSpec =
    (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
        .togetherWith(fadeOut(animationSpec = tween(90)))

@Composable
private fun Content(
    backStack: NavBackStack<NavKey>,
    mainViewModel: MainViewModel,
    foldersViewModel: FoldersViewModel,
    timelineViewModel: TimelineViewModel,
) {
    val timelinePagingItems = timelineViewModel.timelinePager.collectAsLazyPagingItems()
    val networkFolderPagingItems =
        foldersViewModel.networkFolderPhotosPager.collectAsLazyPagingItems()
    val localFolderPagingItems = foldersViewModel.localFolderPhotosPager.collectAsLazyPagingItems()
    val favoritesFolderPagingItems = foldersViewModel.favoritePhotosPager.collectAsLazyPagingItems()

    NavDisplay(
        backStack = backStack,
        transitionSpec = { transitionSpec },
        popTransitionSpec = { transitionSpec },
        predictivePopTransitionSpec = { transitionSpec },
        entryProvider = { key ->
            when (key) {
                is TopLevelNav -> NavEntry(key) {
                    TopLevelScreen(
                        timelinePagingItems,
                        mainViewModel,
                    )
                }

                is PhotoViewerFlowNav -> NavEntry(key) {
                    val lazyPagingItems = when (key.source) {
                        PhotoViewerFlowNav.Source.Timeline -> timelinePagingItems
                        PhotoViewerFlowNav.Source.Network -> networkFolderPagingItems
                        PhotoViewerFlowNav.Source.Local -> localFolderPagingItems
                        PhotoViewerFlowNav.Source.Favorites -> favoritesFolderPagingItems
                    } as LazyPagingData<Photo>

                    PhotosViewer(
                        lazyPagingItems = lazyPagingItems,
                        initialPhotoIndex = key.initialPhotoIndex
                    )
                }

                is FolderNav -> NavEntry(key) {
                    val source = key.source
                    LaunchedEffect(source) {
                        when (source) {
                            is FolderNav.Source.Network -> foldersViewModel.loadNetworkFolderPhotos(
                                source.folder.name
                            )

                            is FolderNav.Source.Local -> foldersViewModel.loadLocalFolderPhotos(
                                source.name
                            )

                            else -> Unit
                        }
                    }

                    val lazyPagingItems = when (source) {
                        FolderNav.Source.Favorites -> favoritesFolderPagingItems
                        is FolderNav.Source.Network -> networkFolderPagingItems
                        is FolderNav.Source.Local -> localFolderPagingItems
                    } as LazyPagingData<Photo>

                    FolderScreen(key.source, lazyPagingItems)
                }

                is PhotoViewerListNav -> NavEntry(key) {
                    PhotosViewer(key.photos)
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

                is DuplicatesNav -> NavEntry(key) {
                    DuplicatesScreen()
                }

                is SettingsNav -> NavEntry(key) {
                    SettingsScreen()
                }

                is TrashNav -> NavEntry(key) {
                    TrashScreen()
                }

                else -> NavEntry(key) {
                    Text("Invalid route: $key")
                }
            }
        }
    )
}
