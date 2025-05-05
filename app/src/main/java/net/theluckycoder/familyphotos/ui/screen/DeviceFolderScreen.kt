package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.composables.FolderPhotos
import net.theluckycoder.familyphotos.ui.composables.PhotoUtilitiesActions
import net.theluckycoder.familyphotos.ui.composables.SelectableItem
import net.theluckycoder.familyphotos.ui.composables.SelectablePhoto
import net.theluckycoder.familyphotos.ui.composables.SimpleSquarePhoto
import net.theluckycoder.familyphotos.ui.composables.selectableClickable
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

data class DeviceFolderScreen(
    val folderName: String
) : Screen {

    override val key: ScreenKey
        get() = "DeviceFolderScreen($folderName)"

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val selectedIds = remember { mutableStateListOf<Long>() }
        val inSelectionMode = selectedIds.isNotEmpty()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            floatingActionButton = {
                AnimatedVisibility(
                    inSelectionMode,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    FloatingActionButton(
                        modifier = Modifier
                            .padding(16.dp),
                        onClick = {
                            navigator.push(UploadPhotosScreen(selectedIds.toList()))
                            selectedIds.clear()
                        }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_upload_outline),
                            contentDescription = null,
                        )
                    }
                }
            }
        ) { paddingValues ->

            val photosFlow = remember(folderName) { mainViewModel.getLocalFolderPhotos(folderName) }
            val photosList by photosFlow.collectAsState(emptyList())

            FolderPhotos(
                folderName = folderName,
                selectedIds = selectedIds,
                photosList = photosList,
                appBarActions = {
                    PhotoUtilitiesActions(
                        LocalPhoto::class,
                        selectedIds,
                        mainViewModel
                    )
                }
            ) { photo ->
                SelectablePhoto(
                    inSelectionMode = selectedIds.isNotEmpty(),
                    selected = selectedIds.contains(photo.id),
                    onClick = { navigator.push(PhotoScreen(photo, PhotoScreen.Source.Folder)) },
                    onSelect = { selectedIds += photo.id },
                    onDeselect = { selectedIds -= photo.id }
                ) {
                    SimpleSquarePhoto(
                        photo,
                        Modifier
                            .padding(0.5.dp)
                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                    )

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
                            tint = Color.White,
                            contentDescription = null,
                        )
                    }
                }
            }
        }

    }
}
