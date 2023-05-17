package net.theluckycoder.familyphotos.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.core.lifecycle.LocalNavigatorScreenLifecycleProvider
import cafe.adriel.voyager.core.lifecycle.NavigatorScreenLifecycleProvider
import cafe.adriel.voyager.core.lifecycle.ScreenLifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import coil.ImageLoader
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.AppTheme
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalOkHttpClient
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.PhotosSlideTransition
import net.theluckycoder.familyphotos.ui.screen.MainScreen
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
        ) { _ ->
        }

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var playerController: Lazy<OkHttpClient>

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val view = ComposeView(this)
        val emptyLifecycleProvider = object : NavigatorScreenLifecycleProvider {
            @ExperimentalVoyagerApi
            override fun provide(screen: Screen): List<ScreenLifecycleOwner> = emptyList()
        }

        view.setContent {
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

        setContentView(view)

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
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (ContextCompat.checkSelfPermission(
                this,
                readImagePermission.first()
            ) == PackageManager.PERMISSION_DENIED
        ) {
            storagePermissionLauncher.launch(readImagePermission)
        }
    }
}
