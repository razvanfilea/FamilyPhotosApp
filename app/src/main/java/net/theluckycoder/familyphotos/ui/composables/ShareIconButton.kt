package net.theluckycoder.familyphotos.ui.composables

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState

@Composable
fun SharePhotoIconButton(
    subtitle: Boolean,
    getPhotosUris: suspend () -> List<Uri>,
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val sendTo = stringResource(R.string.send_to)
    val failedToDownloadImage = stringResource(R.string.failed_download_image)

    var isLoading by remember { mutableStateOf(false) }

    val onClick: () -> Unit = {
        isLoading = true

        scope.launch(Dispatchers.Default) {
            val uriList = getPhotosUris()

            withContext(Dispatchers.Main) {
                if (uriList.isEmpty()) {
                    snackbarHostState.showSnackbar(failedToDownloadImage)
                } else {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND_MULTIPLE
                        val arrayList = ArrayList<Uri>()
                        arrayList.addAll(uriList)
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayList)
                        type = "*/*"
                    }
                    ensureActive()

                    context.startActivity(Intent.createChooser(shareIntent, sendTo))
                }

                isLoading = false
            }
        }
    }

    val icon = painterResource(
        if (isLoading)
            R.drawable.ic_downloading
        else
            R.drawable.ic_action_share
    )

    if (subtitle) {
        IconButtonText(
            onClick = onClick,
            text = stringResource(id = R.string.action_share),
            enabled = !isLoading
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
            )
        }
    } else {
        IconButton(onClick = onClick, enabled = !isLoading) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

