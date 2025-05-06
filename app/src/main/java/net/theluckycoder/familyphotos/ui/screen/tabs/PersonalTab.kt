package net.theluckycoder.familyphotos.ui.screen.tabs

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.PhotosApp
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.ui.composables.MemoriesList
import net.theluckycoder.familyphotos.ui.composables.PhotoListWithViewer
import net.theluckycoder.familyphotos.ui.screen.SettingsScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

object PersonalTab : BottomTab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            0.toUShort(),
            stringResource(R.string.section_personal),
            painterResource(R.drawable.ic_person_outline)
        )

    override val selectedIcon: Painter
        @Composable get() = painterResource(R.drawable.ic_person_filled)

    private val gridState = LazyGridState()

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val mainViewModel: MainViewModel = viewModel()

        val memoriesList = remember { mutableStateListOf<Pair<Int, List<NetworkPhoto>>>() }

        LaunchedEffect(Unit) {
            memoriesList.addAll(mainViewModel.getPersonalMemories())
        }

        val photos = mainViewModel.personalPhotosPager.collectAsLazyPagingItems()
        PhotoListWithViewer(
            gridState = gridState,
            photos = photos,
            headerContent = {
                val isOnline by mainViewModel.isOnlineFlow.collectAsState()
                val displayName by mainViewModel.displayNameFlow.collectAsState(null)
                Header(isOnline, displayName)
            },
            memoriesContent = {
                if (memoriesList.isNotEmpty()) {
                    MemoriesList(
                        memoriesList = memoriesList
                    )
                }
            },
            mainViewModel = mainViewModel,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Header(isOnline: Boolean, displayName: String?) {
        val navigator = LocalNavigator.currentOrThrow

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerStatusIcon(online = isOnline)

            if (!displayName.isNullOrEmpty()) {
                val str = remember {
                    val localDateTime =
                        Clock.System.now().toLocalDateTime(PhotosApp.LOCAL_TIME_ZONE)
                    val hour = localDateTime.hour
                    when {
                        hour in 6..10 -> R.string.message_morning
                        hour < 6 || hour > 22 -> R.string.message_night
                        else -> R.string.message_normal
                    }
                }

                val resources = LocalContext.current.resources
                val message = resources.getString(str, displayName)

                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 16.dp, horizontal = 48.dp),
                    text = message,
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp
                )
            }

            IconButton(
                onClick = { navigator.push(SettingsScreen()) }
            ) {
                Icon(
                    modifier = Modifier
                        .size(48.dp)
                        .padding(8.dp),
                    painter = painterResource(R.drawable.ic_settings_outline),
                    contentDescription = null
                )
            }
        }
    }

    @Composable
    private fun ServerStatusIcon(modifier: Modifier = Modifier, online: Boolean) {
        val res = if (online)
            R.drawable.ic_cloud_done_outline
        else
            R.drawable.ic_cloud_off_outline

        Icon(
            modifier = modifier
                .size(48.dp)
                .padding(8.dp),
            painter = painterResource(res),
            contentDescription = null,
            tint = if (online) LocalContentColor.current else MaterialTheme.colorScheme.error
        )
    }
}


