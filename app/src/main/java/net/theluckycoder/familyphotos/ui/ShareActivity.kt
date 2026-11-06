package net.theluckycoder.familyphotos.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.IntentCompat
import coil3.ImageLoader
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.ui.composables.TypedSnackbar
import net.theluckycoder.familyphotos.ui.screen.LoginScreen
import net.theluckycoder.familyphotos.ui.screen.UploadSharedScreen
import net.theluckycoder.familyphotos.ui.theme.AppTheme
import net.theluckycoder.familyphotos.ui.viewmodel.ShareViewModel
import javax.inject.Inject

/**
 * Target of "Share to FamilyPhotos". Runs in the sender's task and only shows the upload step
 * (and login if needed), then finishes so the user is back in the app they shared from.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    @Inject
    lateinit var imageLoaderLazy: Lazy<ImageLoader>

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            // A share relaunched from recents is stale: it was already handled or abandoned
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
                finish()
                return
            }
            // Copied right away: the URI grant ends with this activity, uploads run later
            viewModel.importSharedItems(readSharedUris(intent).distinct(), intent.type)
        }

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }

            AppTheme {
                CompositionLocalProvider(
                    LocalImageLoader provides imageLoaderLazy,
                    LocalSnackbarHostState provides snackbarHostState,
                    LocalSettingsDataStore provides settingsDataStore,
                ) {
                    Content()
                }
            }
        }
    }

    @Composable
    private fun Content() = Box(Modifier.fillMaxSize()) {
        val finished by viewModel.finished.collectAsState()
        LaunchedEffect(finished) {
            if (finished) finish()
        }

        // Registered first, so LoginScreen's own back handling wins while it is shown
        BackHandler { finish() }

        val isLoggedIn by viewModel.isLoggedIn.collectAsState()
        when (isLoggedIn) {
            null -> {}

            false -> {
                val initialServerAddress by viewModel.loginRepository.serverAddress
                    .collectAsState(initial = "")
                LoginScreen(
                    initialServerAddress = initialServerAddress ?: "",
                    testConnection = { address ->
                        viewModel.loginRepository.testServerConnection(address)
                    },
                    loginAction = { address, userLogin ->
                        viewModel.loginRepository.login(address, userLogin)
                    }
                )
            }

            true -> {
                val previews by viewModel.previews.collectAsState()
                UploadSharedScreen(
                    photos = previews,
                    onUpload = { choice ->
                        viewModel.upload(choice)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }

        val importProgress by viewModel.importProgress.collectAsState()
        importProgress?.let { SharedImportProgressDialog(it, onCancel = ::finish) }

        TypedSnackbar(viewModel.snackbarManager)
    }

    private fun readSharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        )

        Intent.ACTION_SEND_MULTIPLE -> IntentCompat
            .getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            .orEmpty()

        else -> emptyList()
    }
}

@Composable
private fun SharedImportProgressDialog(progress: Float, onCancel: () -> Unit) {
    AlertDialog(
        // Back cancels the share; the partial copies are deleted
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        confirmButton = {},
        title = { Text(stringResource(R.string.status_preparing_shared_items)) },
        text = {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
