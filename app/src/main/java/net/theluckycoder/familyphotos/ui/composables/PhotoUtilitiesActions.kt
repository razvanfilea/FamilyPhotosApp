package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.MovePhotosNav
import net.theluckycoder.familyphotos.ui.UploadPhotosNav
import net.theluckycoder.familyphotos.ui.dialog.rememberDeletePhotosDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Composable
fun PhotoUtilitiesActions(
    isLocalPhoto: Boolean,
    selectedItems: SnapshotStateSet<Long>,
    mainViewModel: MainViewModel = viewModel()
) {
    val backStack = LocalNavBackStack.current
    val scope = rememberCoroutineScope()
    val deletePhotosDialog = rememberDeletePhotosDialog()

    if (selectedItems.isNotEmpty()) {
        IconButton(onClick = {
            scope.launch {
                @Suppress("UNCHECKED_CAST")
                if (isLocalPhoto) {
                    mainViewModel.deleteLocalPhotos(selectedItems.toLongArray())
                    selectedItems.clear()
                } else {
                    deletePhotosDialog.show(
                        photoIds = selectedItems.toLongArray(),
                        onPhotosDeleted = { selectedItems.clear() })
                }
            }
        }) {
            Icon(
                painter = painterResource(R.drawable.ic_action_delete),
                contentDescription = null,
                tint = Color.White,
            )
        }

        SharePhotoIconButton(
            false,
            getPhotosUris = {
                val photoIds = selectedItems.toLongArray()
                if (isLocalPhoto) {
                    mainViewModel.getLocalPhotosUriAsync(photoIds).await()
                } else {
                    mainViewModel.getNetworkPhotosUriAsync(photoIds).await()
                }
            }
        )

        if (isLocalPhoto) {
            IconButton(
                onClick = { backStack.add(UploadPhotosNav(selectedItems.toLongArray())) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_upload_outline),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        } else {
            IconButton(
                onClick = { backStack.add(MovePhotosNav(selectedItems.toLongArray())) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_move_folder),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

