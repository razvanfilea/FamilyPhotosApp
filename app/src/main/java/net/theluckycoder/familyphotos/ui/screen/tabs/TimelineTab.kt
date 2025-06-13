package net.theluckycoder.familyphotos.ui.screen.tabs

import android.app.Application
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.PhotoType
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSharedTransitionScope
import net.theluckycoder.familyphotos.ui.PhotoViewerNav
import net.theluckycoder.familyphotos.ui.composables.CoilPhoto
import net.theluckycoder.familyphotos.ui.composables.FolderTypeSegmentedButtons
import net.theluckycoder.familyphotos.ui.composables.PhotoListWithViewer
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TimelineTab(photos: LazyPagingItems<NetworkPhoto>) {
    val mainViewModel: MainViewModel = viewModel()
    val memories = mainViewModel.memories.collectAsState(emptyMap())
    val selectedPhotoType by mainViewModel.settingsStore.photoType.collectAsState(
        PhotoType.All
    )
    PhotoListWithViewer(
        gridState = rememberLazyGridState(),
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MemoriesList(
    memories: Map<Int, List<NetworkPhoto>>
) {
    val backStack = LocalNavBackStack.current

    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
        if (memories.isEmpty()) {
            item("placeholder") {
                Surface(
                    Modifier
                        .width(140.dp)
                        .aspectRatio(0.75f)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    color = Color.DarkGray
                ) {}
            }
        }
        memories.forEach { (yearsAgo, photos) ->
            item(key = yearsAgo) {
                val photo = photos.first()

                Box(
                    Modifier
                        .width(140.dp)
                        .aspectRatio(0.75f)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            backStack.add(PhotoViewerNav(photos))
                        }
                ) {

                    CoilPhoto(
                        modifier = Modifier.fillMaxSize(),
                        photo = photo,
                        preview = true,
                        contentScale = ContentScale.Crop,
                    )

                    Text(
                        pluralStringResource(R.plurals.years_ago, yearsAgo, yearsAgo),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(
    selectedPhotoType: PhotoType,
    mainViewModel: MainViewModel
) {
    Column {
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
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(64.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer),
        onClick = { mainViewModel.refreshPhotos() }
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


