package net.theluckycoder.camera

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import coil3.ImageLoader

class CameraActivity : ComponentActivity() {

    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .memoryCache(null)
            .diskCache(null)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            CameraAppTheme {
                CompositionLocalProvider(
                    LocalImageLoader provides imageLoader
                ) {
                    CameraUi()
                }
            }
        }
    }

    @Composable
    private fun CameraAppTheme(content: @Composable () -> Unit) {
        val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            darkColorScheme()
        }

        MaterialTheme(colorScheme = colors, content = content)
    }
}
