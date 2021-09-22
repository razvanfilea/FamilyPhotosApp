package net.theluckycoder.familyphotos.ui.screen.tabs

import android.content.res.Configuration
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.ui.PhotosSlideTransition
import net.theluckycoder.familyphotos.ui.screen.FolderPreviewItem
import net.theluckycoder.familyphotos.ui.screen.LocalFolderScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object LocalFoldersTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            3.toUShort(),
            stringResource(R.string.section_storage),
            painterResource(R.drawable.ic_storage_outline)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.ic_storage_filled)

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    override fun Content() {
        Navigator(PhoneFoldersScreen) {
            PhotosSlideTransition(navigator = it)
        }
    }

    private object PhoneFoldersScreen : Screen {

        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        override fun Content() {
            val mainViewModel: MainViewModel = viewModel()

            SideEffect {
                mainViewModel.showBottomAppBar.value = true
            }

            val albums by mainViewModel.localFolders.collectAsState(emptyList())
            val columnCount =
                if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 5

            val navigator = LocalNavigator.currentOrThrow

            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                cells = GridCells.Fixed(columnCount),
            ) {
                items(albums) { folder ->
                    key(folder.coverPhotoId) {
                        val photo = LocalPhoto(
                            id = folder.coverPhotoId,
                            name = "",
                            uri = folder.coverPhotoUri,
                            timeCreated = 0L,
                            folder = folder.name
                        )

                        FolderPreviewItem(
                            photo = photo,
                            name = folder.name,
                            photosCount = folder.count,
                            onClick = {
                                navigator.push(LocalFolderScreen(folder.name))
                            },
                        )
                    }
                }
            }
        }
    }
}
