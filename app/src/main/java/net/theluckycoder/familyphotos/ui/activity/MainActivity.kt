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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, R.string.notification_missing_permission, Toast.LENGTH_SHORT)
                    .show()
            }
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
        view.setContent {
            AppTheme {
                val snackbarHostState = remember { SnackbarHostState() }

                CompositionLocalProvider(
                    LocalImageLoader provides imageLoader,
                    LocalOkHttpClient provides playerController,
                    LocalSnackbarHostState provides snackbarHostState
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
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
