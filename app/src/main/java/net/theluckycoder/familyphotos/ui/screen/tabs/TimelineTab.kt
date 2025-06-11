package net.theluckycoder.familyphotos.ui.screen.tabs

import android.app.Application
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.PhotoType
import net.theluckycoder.familyphotos.ui.composables.FolderTypeSegmentedButtons
import net.theluckycoder.familyphotos.ui.composables.MemoriesList
import net.theluckycoder.familyphotos.ui.composables.PhotoListWithViewer
import net.theluckycoder.familyphotos.ui.screen.SettingsScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object TimelineTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            0.toUShort(),
            stringResource(R.string.section_photos),
            painterResource(R.drawable.ic_photo_outline)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.ic_photo_filled)

    private val gridState = LazyGridState()

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()
        val memories = mainViewModel.memories.collectAsState(emptyMap())
        val photos = mainViewModel.timelinePager.collectAsLazyPagingItems()
        val selectedPhotoType by mainViewModel.settingsStore.photoType.collectAsState(
            PhotoType.All
        )

        PhotoListWithViewer(
            gridState = gridState,
            photos = photos,
            headerContent = {
                val isOnline by mainViewModel.isOnlineFlow.collectAsState()
                Header(isOnline, selectedPhotoType, mainViewModel)
            },
            memoriesContent = {
                MemoriesList(memories.value)
            },
            mainViewModel = mainViewModel,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Header(
        isOnline: Boolean,
        selectedPhotoType: PhotoType,
        mainViewModel: MainViewModel
    ) {
        val navigator = LocalNavigator.currentOrThrow
        val app = LocalContext.current.applicationContext as Application

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { mainViewModel.refreshPhotos(app) }
            ) {
                val res = if (isOnline)
                    R.drawable.ic_cloud_done_outline
                else
                    R.drawable.ic_cloud_off_outline

                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(res),
                    contentDescription = null,
                    tint = if (isOnline) LocalContentColor.current else MaterialTheme.colorScheme.error
                )
            }

            FolderTypeSegmentedButtons(
                selectedPhotoType = selectedPhotoType,
                modifier = Modifier.weight(1f),
                mainViewModel = mainViewModel
            )

            IconButton(
                onClick = { navigator.push(SettingsScreen()) }
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_settings_outline),
                    contentDescription = stringResource(R.string.settings)
                )
            }
        }
    }
}


