package net.theluckycoder.camera

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.ImageLoader

class CameraActivity : ComponentActivity() {

    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .memoryCache(null)
            .diskCache(null)
            .build()
    }

    private var hasCameraPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Lock to portrait. Note: On SDK 36+ large screens (tablets/foldables),
        // this may be ignored by the system. For full large-screen support,
        // implement adaptive layouts.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            // Track device rotation for icon rotation (works even when activity is portrait-locked)
            var deviceRotation by remember { mutableIntStateOf(0) }
            val context = LocalContext.current

            DisposableEffect(context) {
                val orientationListener = object : OrientationEventListener(context) {
                    override fun onOrientationChanged(orientation: Int) {
                        if (orientation == ORIENTATION_UNKNOWN) return
                        deviceRotation = when {
                            orientation in 45..134 -> 270   // Device rotated right (landscape)
                            orientation in 135..224 -> 180  // Upside down
                            orientation in 225..314 -> 90   // Device rotated left (landscape)
                            else -> 0                        // Portrait
                        }
                    }
                }
                orientationListener.enable()
                onDispose { orientationListener.disable() }
            }

            CompositionLocalProvider(
                LocalDeviceRotation provides deviceRotation,
            ) {
                CameraAppTheme {
                    CompositionLocalProvider(
                        LocalImageLoader provides imageLoader
                    ) {
                        if (hasCameraPermission) {
                            CameraUi()
                        } else {
                            PermissionDeniedScreen(
                                onRequestPermission = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                onOpenSettings = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    }
                                    startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission when returning from settings
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
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

@Composable
private fun PermissionDeniedScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This app needs camera access to take photos. Please grant camera permission to continue.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onOpenSettings) {
            Text("Open Settings")
        }
    }
}
