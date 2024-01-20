package net.theluckycoder.familyphotos.ui.screen

import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.parcelize.Parcelize
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.composables.FolderPhotos
import net.theluckycoder.familyphotos.ui.composables.PhotoUtilitiesActions
import net.theluckycoder.familyphotos.ui.composables.SelectablePhoto
import net.theluckycoder.familyphotos.ui.composables.SimpleSquarePhoto
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Parcelize
data class NetworkFolderScreen(
    val source: Source
) : Screen, Parcelable {

    override val key: ScreenKey
        get() = "NetworkFolderScreen($source)"

    @Parcelize
    sealed class Source : Parcelable {
        data object Favorites : Source()
        data class FolderName(val name: String) : Source()
    }

    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val navigator = LocalNavigator.currentOrThrow

        val selectedIds = remember { mutableStateListOf<Long>() }
        val photosFlow = remember {
            when (source) {
                Source.Favorites -> mainViewModel.getFavoritePhotos()
                is Source.FolderName -> mainViewModel.getNetworkFolderPhotos(source.name)
            }
        }
        val photosList by photosFlow.collectAsState(emptyList())

        Surface(Modifier.fillMaxSize()) {
            FolderPhotos(
                folderName = when (source) {
                    Source.Favorites -> stringResource(R.string.favorites)
                    is Source.FolderName -> source.name
                },
                selectedIds = selectedIds,
                photosList = photosList,
                appBarActions = {
                    if (source is Source.FolderName && selectedIds.isEmpty()) {
                        IconButton(onClick = {
                            navigator.push(MovePhotosScreen(photosList.map { it.id }))
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    }
                    PhotoUtilitiesActions(NetworkPhoto::class, selectedIds, mainViewModel)
                }
            ) { photo ->
                SelectablePhoto(
                    inSelectionMode = selectedIds.isNotEmpty(),
                    selected = selectedIds.contains(photo.id),
                    onClick = {
                        navigator.push(
                            when (source) {
                                Source.Favorites -> PhotoScreen(photo, PhotoScreen.Source.Favorites)
                                is Source.FolderName -> PhotoScreen(
                                    photo,
                                    PhotoScreen.Source.Folder
                                )
                            }
                        )
                    },
                    onSelect = { selectedIds += photo.id },
                    onDeselect = { selectedIds -= photo.id }
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
}