package net.theluckycoder.familyphotos.ui.dialog

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.theme.AppTheme
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel


fun interface DeletePhotosDialogCaller {
    fun show(photoIds: LongArray, onPhotosDeleted: () -> Unit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberDeletePhotosDialog(): DeletePhotosDialogCaller {
    var receivedData by remember { mutableStateOf<Pair<LongArray, () -> Unit>?>(null) }
    val visible = receivedData != null

    val mainViewModel: MainViewModel = viewModel()
    val scope = rememberCoroutineScope()

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = {
                receivedData = null
            }
        ) {
            DeleteDialogContent(
                onCancel = { receivedData = null },
                onDelete = {
                    scope.launch {
                        val (photosIds, callback) = receivedData!!
                        mainViewModel.deleteNetworkPhotos(photosIds)

                        callback.invoke()

                        receivedData = null
                    }
                }
            )
        }
    }

    return DeletePhotosDialogCaller { photos, callback ->
        if (photos.isNotEmpty()) {
            receivedData = photos to callback
        } else {
            Log.e("DeletePhotosDialog", "Failed to open dialog, no photos to delete")
        }
    }
}

@Composable
private fun DeleteDialogContent(
    onCancel: () -> Unit,
    onDelete: () -> Unit
) = Column(
    Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .safeDrawingPadding()
) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        text = stringResource(R.string.confirm_delete),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )

    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        text = stringResource(R.string.confirm_delete_detail),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )

    Row {
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = onCancel
        ) {
            Text(text = stringResource(R.string.action_cancel))
        }

        Button(
            modifier = Modifier.weight(1f),
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFe53935),
                contentColor = Color.White
            )
        ) {
            Text(text = stringResource(R.string.action_delete))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeleteDialogPreview() {
    AppTheme {
        DeleteDialogContent(
            onCancel = {},
            onDelete = {},
        )
    }
}
