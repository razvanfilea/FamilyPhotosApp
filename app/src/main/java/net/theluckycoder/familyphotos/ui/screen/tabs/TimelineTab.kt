package net.theluckycoder.familyphotos.ui.screen.tabs

import android.app.Application
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Header(selectedPhotoType, mainViewModel)
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
        selectedPhotoType: PhotoType,
        mainViewModel: MainViewModel
    ) {
        Column {
            val navigator = LocalNavigator.currentOrThrow

            val isOnline by mainViewModel.isOnlineFlow.collectAsState()
            val isRefreshing by mainViewModel.isOnlineFlow.collectAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolderTypeSegmentedButtons(
                    selectedPhotoType = selectedPhotoType,
                    modifier = Modifier.weight(1f).padding(8.dp),
                    mainViewModel = mainViewModel
                )

                /*IconButton(
                    onClick = { navigator.push(SettingsScreen()) }
                ) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.ic_settings_outline),
                        contentDescription = stringResource(R.string.settings)
                    )
                }*/
            }

            if (!isOnline && !isRefreshing) {
                FailedConnectionCard(mainViewModel)
            }
        }
    }

    @Composable
    private fun FailedConnectionCard(mainViewModel: MainViewModel) {
        val app = LocalContext.current.applicationContext as Application

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(64.dp),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer),
            onClick = { mainViewModel.refreshPhotos(app) }
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(R.drawable.ic_cloud_off_outline),
                    contentDescription = null,
                )

                Text(
                    "Failed to connect to server",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


