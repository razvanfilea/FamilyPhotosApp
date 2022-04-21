package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.bottomSheet.LocalBottomSheetNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.composables.SelectableItem
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

data class NetworkFolderScreen(
    val folderName: String
) : Screen {

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()

        SideEffect {
            mainViewModel.showBottomAppBar.value = true
        }

        val scope = rememberCoroutineScope()
        val selectedItems = remember(folderName) { mutableStateListOf<Long>() }

        val navigator = LocalNavigator.currentOrThrow
        val bottomSheetNavigator = LocalBottomSheetNavigator.current

        val photosFlow = remember(folderName) { mainViewModel.getNetworkFolderPhotos(folderName) }
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
                            val photos = items.mapNotNull { mainViewModel.getNetworkPhoto(it) }
                            bottomSheetNavigator.show(DeletePhotosDialog(photos))
                            selectedItems.clear()
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_delete),
                            contentDescription = stringResource(R.string.action_delete),
                            tint = Color.White,
                        )
                    }

                    IconButton(onClick = { navigator.push(MovePhotosScreen(selectedItems.toList())) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_move_folder),
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
                        navigator.push(PhotoDetailScreen(photo, photosList))
                    }
                }
            ) {
                SimpleSquarePhotoItem(photo)
            }
        }
    }
}