package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.composables.PhotoUtilitiesActions
import net.theluckycoder.familyphotos.ui.composables.SelectableItem
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

data class NetworkFolderScreen(
    val folderName: String
) : Screen {

    override val key: ScreenKey
        get() = "NetworkFolderScreen($folderName)"

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()

        SideEffect {
            mainViewModel.showBottomAppBar.value = true
        }

        val navigator = LocalNavigator.currentOrThrow

        val selectedItems = remember(folderName) { mutableStateListOf<Long>() }
        val photosFlow = remember(folderName) { mainViewModel.getNetworkFolderPhotos(folderName) }
        val photosList by photosFlow.collectAsState(emptyList())

        FolderPhotos(
            folderName = folderName,
            selectedItems = selectedItems,
            photosList = photosList,
            appBarActions = {
                if (selectedItems.isEmpty()) {
                    IconButton(onClick = {
                        navigator.push(MovePhotosScreen(photosList.map { it.id }))
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                }
                PhotoUtilitiesActions(NetworkPhoto::class, selectedItems, mainViewModel)
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
                SimpleSquarePhoto(photo)

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
            }
        }
    }
}