package net.theluckycoder.familyphotos.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.core.lifecycle.LocalNavigatorScreenLifecycleProvider
import cafe.adriel.voyager.core.lifecycle.NavigatorScreenLifecycleProvider
import cafe.adriel.voyager.core.lifecycle.ScreenLifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import coil.ImageLoader
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.AppTheme
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalOkHttpClient
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.PhotosSlideTransition
import net.theluckycoder.familyphotos.ui.screen.MainScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import okhttp3.OkHttpClient
import javax.inject.Inject

@OptIn(ExperimentalVoyagerApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, R.string.notification_missing_permission, Toast.LENGTH_SHORT)
                    .show()
            }
        }

    private val storagePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    private val mainViewModel: MainViewModel by viewModels()

    private val deletePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        mainViewModel.refreshLocalPhotos(it)
    }

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var playerController: Lazy<OkHttpClient>

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val emptyLifecycleProvider = object : NavigatorScreenLifecycleProvider {
            @ExperimentalVoyagerApi
            override fun provide(screen: Screen): List<ScreenLifecycleOwner> = emptyList()
        }

        setContent {
            AppTheme {
                val snackbarHostState = remember { SnackbarHostState() }

                CompositionLocalProvider(
                    LocalImageLoader provides imageLoader,
                    LocalOkHttpClient provides playerController,
                    LocalSnackbarHostState provides snackbarHostState,
                    LocalNavigatorScreenLifecycleProvider provides emptyLifecycleProvider
                ) {
                    Navigator(MainScreen) {
                        PhotosSlideTransition(navigator = it)
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_DENIED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val readImagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)

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
