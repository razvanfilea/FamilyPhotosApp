package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.bottomSheet.LocalBottomSheetNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.composables.SelectableItem
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

data class LocalFolderScreen(
    val folderName: String
) : Screen {

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()

        SideEffect {
            mainViewModel.showBottomAppBar.value = true
        }

        val scope = rememberCoroutineScope()
        val selectedItems = remember { mutableStateListOf<Long>() }

        val navigator = LocalNavigator.currentOrThrow
        val bottomSheetNavigator = LocalBottomSheetNavigator.current

        val photosFlow = remember(folderName) { mainViewModel.getLocalFolderPhotos(folderName) }
        val photosList by photosFlow.collectAsState(emptyList())

        FolderPhotos(
            folderName = folderName,
            selectedItems = selectedItems,
            photosList = photosList,
            appBarActions = {
                if (selectedItems.isNotEmpty()) {
                    IconButton(onClick = {
                        val items = selectedItems.toList()

                        scope.launch {
                            val photos = items.mapNotNull { mainViewModel.getLocalPhoto(it) }
                            bottomSheetNavigator.show(DeletePhotosDialog(photos))
                            selectedItems.clear()
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_delete),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }

                    IconButton(onClick = {
                        navigator.push(UploadPhotosScreen(selectedItems.toList()))
                        selectedItems.clear()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_upload_outline),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
            }
        ) { photo ->
            SelectableItem(
                selected = selectedItems.contains(photo.id),
                enabled = selectedItems.isNotEmpty(),
                onClick = { longPress ->
                    if (selectedItems.isNotEmpty() || longPress) {
                        if (selectedItems.contains(photo.id))
                            selectedItems -= photo.id
                        else
                            selectedItems += photo.id
                    } else {
                        navigator.push(PhotoDetailScreen(photo, photosList.toList()))
                    }
                }
            ) {
                SimpleSquarePhotoItem(photo)

                val isVideo = remember(photo) { photo.isVideo }

                if (isVideo) {
                    Icon(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd),
                        painter = painterResource(R.drawable.ic_play_circle_filled),
                        contentDescription = null
                    )
                }

                if (photo.isSavedToCloud) {
                    Icon(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomEnd),
                        painter = painterResource(R.drawable.ic_cloud_done_filled),
                        contentDescription = null
                    )
                }
            }
        }
    }
}
