package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.MovePhotosNav
import net.theluckycoder.familyphotos.ui.UploadPhotosNav
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Composable
fun PhotoUtilitiesActions( // TODO row
    isLocalPhoto: Boolean,
    selectedItems: SnapshotStateSet<Long>,
    mainViewModel: MainViewModel
) {
    val backStack = LocalNavBackStack.current
    val scope = rememberCoroutineScope()
    var showDeleteDialogForPhotos by remember { mutableStateOf<List<Photo>?>(null) }

    if (selectedItems.isNotEmpty()) {
        IconButton(onClick = {
            scope.launch {
                @Suppress("UNCHECKED_CAST")
                if (isLocalPhoto) {
                    mainViewModel.deleteLocalPhotos(selectedItems.toLongArray())
                    selectedItems.clear()
                } else {
                    val photos = mainViewModel.getNetworkPhotos(selectedItems.toLongArray())
                    showDeleteDialogForPhotos = photos
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

    showDeleteDialogForPhotos?.let { photos ->
        DeletePhotosDialog(
            photos = photos,
            isPermanent = false,
            onDismissRequest = { showDeleteDialogForPhotos = null },
            onConfirmDelete = { list -> 
                mainViewModel.trashNetworkPhotos(list.map { it.id }.toLongArray())
            },
            onPhotosDeleted = { selectedItems.clear() }
        )
    }
}

